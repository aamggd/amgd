package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.ExpenseWorkflowRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseWorkflowDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: ExpenseWorkflowRequestEntity): Long

    @Update
    suspend fun update(row: ExpenseWorkflowRequestEntity)

    @Query("SELECT * FROM expense_workflow_requests WHERE id=:id LIMIT 1")
    suspend fun byId(id: Long): ExpenseWorkflowRequestEntity?

    @Query("SELECT * FROM expense_workflow_requests ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseWorkflowRequestEntity>>
}
