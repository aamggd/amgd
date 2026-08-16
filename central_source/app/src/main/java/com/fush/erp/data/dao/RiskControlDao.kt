package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RiskControlDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRisk(row: RiskEntity): Long

    @Update
    suspend fun updateRisk(row: RiskEntity)

    @Query("SELECT * FROM risk_register ORDER BY CASE status WHEN 'OPEN' THEN 0 WHEN 'MITIGATING' THEN 1 ELSE 2 END, inherentScore DESC, createdAt DESC")
    fun observeRisks(): Flow<List<RiskEntity>>

    @Query("SELECT COUNT(*) FROM risk_register WHERE status NOT IN ('CLOSED','ACCEPTED')")
    fun observeOpenRiskCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM risk_register WHERE status NOT IN ('CLOSED','ACCEPTED') AND residualScore >= 15")
    fun observeHighRiskCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertControl(row: InternalControlEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertControls(rows: List<InternalControlEntity>): List<Long>

    @Update
    suspend fun updateControl(row: InternalControlEntity)

    @Query("SELECT * FROM internal_controls ORDER BY isActive DESC, controlCode")
    fun observeControls(): Flow<List<InternalControlEntity>>

    @Query("SELECT * FROM internal_controls WHERE id = :id LIMIT 1")
    suspend fun controlById(id: Long): InternalControlEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTest(row: ControlTestEntity): Long

    @Query("SELECT * FROM control_tests ORDER BY testedAt DESC, id DESC LIMIT 200")
    fun observeTests(): Flow<List<ControlTestEntity>>

    @Query("SELECT COUNT(*) FROM control_tests WHERE result = 'FAIL'")
    fun observeFailedTestCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertException(row: ControlExceptionEntity): Long

    @Update
    suspend fun updateException(row: ControlExceptionEntity)

    @Query("SELECT * FROM control_exceptions ORDER BY CASE status WHEN 'OPEN' THEN 0 WHEN 'APPROVED' THEN 1 ELSE 2 END, detectedAt DESC")
    fun observeExceptions(): Flow<List<ControlExceptionEntity>>

    @Query("SELECT COUNT(*) FROM control_exceptions WHERE status NOT IN ('CLOSED','REJECTED')")
    fun observeOpenExceptionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM control_exceptions WHERE status NOT IN ('CLOSED','REJECTED') AND dueAt IS NOT NULL AND dueAt < :now")
    fun observeOverdueExceptionCount(now: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSegregationRules(rows: List<SegregationRuleEntity>): List<Long>

    @Query("SELECT * FROM segregation_rules WHERE isActive = 1 ORDER BY ruleCode")
    fun observeSegregationRules(): Flow<List<SegregationRuleEntity>>
}
