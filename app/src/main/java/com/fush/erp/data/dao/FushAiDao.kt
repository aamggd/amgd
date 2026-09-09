package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.FushAiDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FushAiDao {
    @Insert
    suspend fun insert(row: FushAiDraftEntity): Long

    @Update
    suspend fun update(row: FushAiDraftEntity)

    @Query("SELECT * FROM fush_ai_drafts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): FushAiDraftEntity?

    @Query("SELECT * FROM fush_ai_drafts WHERE requestedBy = :userId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(userId: Long, limit: Int = 30): Flow<List<FushAiDraftEntity>>
}
