package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LumiDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Query("SELECT value FROM memories WHERE `key` = :key LIMIT 1")
    suspend fun getMemoryValue(key: String): String?

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("DELETE FROM memories WHERE `key` = :key")
    suspend fun deleteMemoryByKey(key: String)
}
