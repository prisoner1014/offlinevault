package com.offlinevault.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offlinevault.app.data.local.entity.ChatMessageEntity
import com.offlinevault.app.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET title = :newTitle, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(sessionId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSessionFlow(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)
}
