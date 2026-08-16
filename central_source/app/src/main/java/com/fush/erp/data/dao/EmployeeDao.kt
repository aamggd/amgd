package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEmployee(row: EmployeeEntity): Long

    @Update
    suspend fun updateEmployee(row: EmployeeEntity)

    @Query("SELECT * FROM employees ORDER BY fullNameAr, id")
    fun observeEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE status = 'ACTIVE' ORDER BY fullNameAr, id")
    fun observeActiveEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE status = 'ACTIVE' ORDER BY fullNameAr, id")
    suspend fun activeEmployees(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun employeeById(id: Long): EmployeeEntity?

    @Query("SELECT COUNT(*) FROM employees WHERE status = 'ACTIVE'")
    fun observeActiveEmployeeCount(): Flow<Int>

    @Query("SELECT MAX(CAST(SUBSTR(code, 5) AS INTEGER)) FROM employees WHERE code LIKE 'EMP-%'")
    suspend fun maxEmployeeSequence(): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCourse(row: TrainingCourseEntity): Long

    @Query("SELECT * FROM training_courses WHERE isActive = 1 ORDER BY titleAr, id")
    fun observeActiveCourses(): Flow<List<TrainingCourseEntity>>

    @Query("SELECT * FROM training_courses WHERE code = :code LIMIT 1")
    suspend fun courseByCode(code: String): TrainingCourseEntity?

    @Query("SELECT * FROM training_courses WHERE id = :id LIMIT 1")
    suspend fun courseById(id: Long): TrainingCourseEntity?

    @Query("SELECT COUNT(*) FROM training_courses")
    suspend fun courseCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTraining(row: EmployeeTrainingEntity): Long

    @Query("""
        SELECT et.id AS id, et.employeeId AS employeeId, e.fullNameAr AS employeeName, c.titleAr AS courseTitle,
               et.completedAt AS completedAt, et.expiresAt AS expiresAt,
               et.result AS result, et.practicalObserved AS practicalObserved
        FROM employee_trainings et
        JOIN employees e ON e.id = et.employeeId
        JOIN training_courses c ON c.id = et.courseId
        ORDER BY et.completedAt DESC, et.id DESC
    """)
    fun observeTrainingSummaries(): Flow<List<EmployeeTrainingSummary>>

    @Query("""
        SELECT COUNT(*) FROM employee_trainings et
        JOIN training_courses c ON c.id = et.courseId
        WHERE et.employeeId = :employeeId
          AND et.courseId = :courseId
          AND et.result = 'PASS'
          AND (c.requiresPracticalObservation = 0 OR et.practicalObserved = 1)
          AND et.completedAt <= :at
          AND (et.expiresAt IS NULL OR et.expiresAt >= :at)
    """)
    suspend fun validTrainingCount(employeeId: Long, courseId: Long, at: Long): Int

    @Query("SELECT COUNT(*) FROM employee_trainings WHERE expiresAt IS NOT NULL AND expiresAt < :at")
    fun observeExpiredTrainingCount(at: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuthorization(row: EquipmentAuthorizationEntity): Long

    @Update
    suspend fun updateAuthorization(row: EquipmentAuthorizationEntity)

    @Query("SELECT * FROM equipment_authorizations WHERE id = :id LIMIT 1")
    suspend fun authorizationById(id: Long): EquipmentAuthorizationEntity?

    @Query("""
        SELECT * FROM equipment_authorizations
        WHERE employeeId = :employeeId AND assetId = :assetId
          AND status = 'ACTIVE' AND issuedAt <= :at
          AND (expiresAt IS NULL OR expiresAt >= :at)
        ORDER BY issuedAt DESC, id DESC LIMIT 1
    """)
    suspend fun activeAuthorization(employeeId: Long, assetId: Long, at: Long): EquipmentAuthorizationEntity?

    @Query("""
        SELECT ea.id AS id, ea.authorizationNo AS authorizationNo, ea.employeeId AS employeeId,
               e.fullNameAr AS employeeName, a.nameAr AS assetName,
               c.titleAr AS courseTitle, ea.issuedAt AS issuedAt,
               ea.expiresAt AS expiresAt, ea.status AS status
        FROM equipment_authorizations ea
        JOIN employees e ON e.id = ea.employeeId
        JOIN assets a ON a.id = ea.assetId
        JOIN training_courses c ON c.id = ea.courseId
        ORDER BY ea.issuedAt DESC, ea.id DESC
    """)
    fun observeAuthorizationSummaries(): Flow<List<EquipmentAuthorizationSummary>>

    @Query("SELECT COUNT(*) FROM equipment_authorizations WHERE status = 'ACTIVE' AND (expiresAt IS NULL OR expiresAt >= :at)")
    fun observeActiveAuthorizationCount(at: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM equipment_authorizations WHERE status = 'ACTIVE' AND expiresAt IS NOT NULL AND expiresAt < :at")
    fun observeExpiredAuthorizationCount(at: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperatorAssignment(row: ProductionOperatorAssignmentEntity): Long

    @Update
    suspend fun updateOperatorAssignment(row: ProductionOperatorAssignmentEntity)

    @Query("SELECT * FROM production_operator_assignments WHERE orderId = :orderId LIMIT 1")
    suspend fun operatorAssignment(orderId: Long): ProductionOperatorAssignmentEntity?

    @Query("""
        SELECT pa.employeeId AS employeeId, po.id AS orderId, po.orderNo AS orderNo, po.plannedDate AS plannedDate,
               po.status AS orderStatus, po.directLaborCostBase AS laborCostBase,
               CASE WHEN EXISTS (
                   SELECT 1 FROM journal_entries je
                   WHERE je.sourceType = 'PRODUCTION_LABOR'
                     AND je.sourceId = po.orderNo
                     AND je.status = 'POSTED'
               ) THEN 1 ELSE 0 END AS isAccrued
        FROM production_operator_assignments pa
        JOIN production_orders po ON po.id = pa.orderId
        WHERE pa.employeeId = :employeeId
        ORDER BY po.plannedDate DESC, po.id DESC
    """)
    fun observeProductionCompensations(employeeId: Long): Flow<List<EmployeeProductionCompensationRow>>

    @Query("""
        SELECT COALESCE(SUM(pv.amountBase), 0.0)
        FROM party_vouchers pv
        JOIN accounts a ON a.id = pv.offsetAccountId
        WHERE pv.employeeId = :employeeId
          AND a.code = '2200'
          AND pv.voucherType = 'PAYMENT'
          AND pv.status = 'POSTED'
    """)
    fun observeProductionLaborPayments(employeeId: Long): Flow<Double>
}
