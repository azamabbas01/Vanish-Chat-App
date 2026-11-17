package com.example.fieldsync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY expiresAt ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    // Only messages that are still alive and not yet delivered.
    @Query("SELECT * FROM messages WHERE dirty = 1 AND expiresAt > :now")
    suspend fun pending(now: Long): List<MessageEntity>

    // IGNORE: if the id already exists we keep the first copy.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET version = :version, dirty = 0 WHERE id = :id")
    suspend fun markSynced(id: String, version: Int)

    // The "vanish" step.
    @Query("DELETE FROM messages WHERE expiresAt <= :now")
    suspend fun purgeExpired(now: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)
}
