package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employees",
    indices = [Index(value = ["code"], unique = true), Index("status"), Index("department")]
)
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val fullNameAr: String,
    val fullNameEn: String = "",
    val phone: String = "",
    val jobTitle: String,
    val department: String,
    val hireDate: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE",
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "training_courses",
    indices = [Index(value = ["code"], unique = true), Index("category"), Index("isActive")]
)
data class TrainingCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val titleAr: String,
    val category: String,
    val assetType: String? = null,
    val description: String = "",
    val requiresPracticalObservation: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "employee_trainings",
    indices = [Index("employeeId"), Index("courseId"), Index("completedAt"), Index("expiresAt")],
    foreignKeys = [
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TrainingCourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class EmployeeTrainingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val courseId: Long,
    val completedAt: Long,
    val expiresAt: Long? = null,
    val result: String = "PASS",
    val practicalObserved: Boolean = false,
    val trainer: String = "",
    val certificateRef: String = "",
    val notes: String = "",
    val recordedBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "equipment_authorizations",
    indices = [
        Index(value = ["authorizationNo"], unique = true),
        Index("employeeId"), Index("assetId"), Index("courseId"), Index("status"), Index("expiresAt")
    ],
    foreignKeys = [
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TrainingCourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class EquipmentAuthorizationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val authorizationNo: String,
    val employeeId: Long,
    val assetId: Long,
    val courseId: Long,
    val issuedAt: Long,
    val expiresAt: Long? = null,
    val status: String = "ACTIVE",
    val notes: String = "",
    val authorizedBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "production_operator_assignments",
    indices = [Index(value = ["orderId"], unique = true), Index("employeeId")],
    foreignKeys = [
        ForeignKey(entity = ProductionOrderEntity::class, parentColumns = ["id"], childColumns = ["orderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class ProductionOperatorAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val employeeId: Long,
    val assignedBy: Long,
    val assignedAt: Long = System.currentTimeMillis()
)

data class EmployeeTrainingSummary(
    val id: Long,
    val employeeId: Long,
    val employeeName: String,
    val courseTitle: String,
    val completedAt: Long,
    val expiresAt: Long?,
    val result: String,
    val practicalObserved: Boolean
)

data class EquipmentAuthorizationSummary(
    val id: Long,
    val authorizationNo: String,
    val employeeId: Long,
    val employeeName: String,
    val assetName: String,
    val courseTitle: String,
    val issuedAt: Long,
    val expiresAt: Long?,
    val status: String
)

data class EmployeeProductionCompensationRow(
    val orderId: Long,
    val orderNo: String,
    val plannedDate: Long,
    val orderStatus: String,
    val laborCostBase: Double,
    val isAccrued: Boolean
)

