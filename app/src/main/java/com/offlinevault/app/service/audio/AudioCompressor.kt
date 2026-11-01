package com.offlinevault.app.service.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CompressionResult(
    val success: Boolean,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val outputFilePath: String,
    val errorMessage: String? = null
) {
    val savedPercentage: Int
        get() = if (originalSizeBytes > 0 && compressedSizeBytes < originalSizeBytes) {
            (((originalSizeBytes - compressedSizeBytes).toDouble() / originalSizeBytes.toDouble()) * 100).toInt()
        } else 0
}

class AudioCompressor(private val context: Context) {

    companion object {
        private const val TAG = "AudioCompressor"
        private const val TIMEOUT_US = 10000L
        private const val TARGET_BITRATE = 32000 // 32 kbps - optimal for voice & podcasts
        private const val TARGET_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    }

    /**
     * Compresses any audio file into a 32kbps Mono AAC/M4A file.
     * Reduces podcast file size by 75% to 85% with crystal clear speech intelligibility.
     */
    suspend fun compressAudio(
        inputFile: File,
        targetBitrate: Int = TARGET_BITRATE,
        onProgress: (progress: Float) -> Unit = {}
    ): CompressionResult = withContext(Dispatchers.IO) {
        val originalSize = inputFile.length()
        if (!inputFile.exists() || originalSize == 0L) {
            return@withContext CompressionResult(
                success = false,
                originalSizeBytes = originalSize,
                compressedSizeBytes = 0L,
                outputFilePath = inputFile.absolutePath,
                errorMessage = "Source file does not exist or is empty"
            )
        }

        val baseName = inputFile.nameWithoutExtension
        val outputFile = File(inputFile.parentFile, "${baseName}_compressed.m4a")

        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            // Find audio track
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                return@withContext CompressionResult(
                    success = false,
                    originalSizeBytes = originalSize,
                    compressedSizeBytes = 0L,
                    outputFilePath = inputFile.absolutePath,
                    errorMessage = "No valid audio track found in file"
                )
            }

            extractor.selectTrack(audioTrackIndex)

            val inputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val inputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            // 1. Setup Decoder
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // 2. Setup Encoder: 32 kbps Mono AAC
            val outputFormat = MediaFormat.createAudioFormat(TARGET_MIME, inputSampleRate, 1).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }

            encoder = MediaCodec.createEncoderByType(TARGET_MIME)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // 3. Setup Muxer
            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var muxerTrackIndex = -1
            var isMuxerStarted = false

            val decBufferInfo = MediaCodec.BufferInfo()
            val encBufferInfo = MediaCodec.BufferInfo()

            var extractorDone = false
            var decoderDone = false
            var encoderDone = false

            while (!encoderDone) {
                // A. Feed extractor samples into decoder
                if (!extractorDone) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIdx)
                        if (inBuffer != null) {
                            val sampleSize = extractor.readSampleData(inBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                extractorDone = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // B. Read decoded PCM samples and feed into encoder
                if (!decoderDone) {
                    val outIdx = decoder.dequeueOutputBuffer(decBufferInfo, TIMEOUT_US)
                    if (outIdx >= 0) {
                        val pcmBuffer = decoder.getOutputBuffer(outIdx)
                        val isEos = (decBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        if (pcmBuffer != null && decBufferInfo.size > 0) {
                            // Downmix stereo to mono if needed
                            val monoPcm = if (inputChannels > 1) {
                                downmixStereoToMono(pcmBuffer, decBufferInfo.offset, decBufferInfo.size)
                            } else {
                                val bytes = ByteArray(decBufferInfo.size)
                                pcmBuffer.position(decBufferInfo.offset)
                                pcmBuffer.get(bytes)
                                bytes
                            }

                            // Pass to encoder
                            feedEncoder(encoder, monoPcm, decBufferInfo.presentationTimeUs, isEos)
                        } else if (isEos) {
                            feedEncoder(encoder, ByteArray(0), decBufferInfo.presentationTimeUs, true)
                        }

                        decoder.releaseOutputBuffer(outIdx, false)

                        if (durationUs > 0) {
                            val progress = (decBufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }

                        if (isEos) {
                            decoderDone = true
                        }
                    }
                }

                // C. Read encoded compressed AAC buffers and write to Muxer
                var encOutIdx = encoder.dequeueOutputBuffer(encBufferInfo, TIMEOUT_US)
                while (encOutIdx >= 0) {
                    val encodedBuffer = encoder.getOutputBuffer(encOutIdx)

                    if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        encBufferInfo.size = 0
                    }

                    if (encodedBuffer != null && encBufferInfo.size > 0 && isMuxerStarted) {
                        encodedBuffer.position(encBufferInfo.offset)
                        encodedBuffer.limit(encBufferInfo.offset + encBufferInfo.size)
                        muxer.writeSampleData(muxerTrackIndex, encodedBuffer, encBufferInfo)
                    }

                    if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderDone = true
                        encoder.releaseOutputBuffer(encOutIdx, false)
                        break
                    }

                    encoder.releaseOutputBuffer(encOutIdx, false)
                    encOutIdx = encoder.dequeueOutputBuffer(encBufferInfo, 0L)
                }

                if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = encoder.outputFormat
                    muxerTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    isMuxerStarted = true
                }
            }

            onProgress(1f)
            val compressedSize = outputFile.length()
            Log.d(TAG, "Compression complete: $originalSize bytes -> $compressedSize bytes (${((originalSize - compressedSize) * 100) / originalSize}% reduction)")

            CompressionResult(
                success = true,
                originalSizeBytes = originalSize,
                compressedSizeBytes = compressedSize,
                outputFilePath = outputFile.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Audio compression failed: ${e.message}", e)
            if (outputFile.exists()) outputFile.delete()
            CompressionResult(
                success = false,
                originalSizeBytes = originalSize,
                compressedSizeBytes = 0L,
                outputFilePath = inputFile.absolutePath,
                errorMessage = e.localizedMessage ?: "Unknown compression failure"
            )
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    private fun feedEncoder(encoder: MediaCodec, data: ByteArray, ptsUs: Long, isEos: Boolean) {
        var offset = 0
        while (offset < data.size || isEos) {
            val inIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inIdx >= 0) {
                val inBuffer = encoder.getInputBuffer(inIdx) ?: break
                inBuffer.clear()
                val chunk = (data.size - offset).coerceAtMost(inBuffer.capacity())
                if (chunk > 0) {
                    inBuffer.put(data, offset, chunk)
                    offset += chunk
                }
                val flags = if (isEos && offset >= data.size) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                encoder.queueInputBuffer(inIdx, 0, chunk, ptsUs, flags)
                if (offset >= data.size) break
            }
        }
    }

    private fun downmixStereoToMono(buffer: ByteBuffer, offset: Int, size: Int): ByteArray {
        val stereoBytes = ByteArray(size)
        buffer.position(offset)
        buffer.get(stereoBytes)

        val shortCount = size / 2
        val monoShortCount = shortCount / 2
        val monoBytes = ByteArray(monoShortCount * 2)

        val srcShorts = ByteBuffer.wrap(stereoBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val destShorts = ByteBuffer.wrap(monoBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (i in 0 until monoShortCount) {
            val left = srcShorts.get(i * 2).toInt()
            val right = srcShorts.get(i * 2 + 1).toInt()
            val mono = ((left + right) / 2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            destShorts.put(mono)
        }

        return monoBytes
    }
}
