# Keep youtubedl-android and python classes
-keep class com.yausername.youtubedl_android.** { *; }
-keep class io.github.junkfood02.youtubedl_android.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.offlinevault.app.data.local.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jspecify.**

# RSS Parser
-keep class com.prof18.rssparser.** { *; }

# Coroutines & Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# NanoHTTPD (Local sharing server)
-keep class fi.iki.elonen.** { *; }

# Llama AI engine
-keep class dev.ffmpegkit_maintained.llama.** { *; }
-keep class com.arthenica.** { *; }

