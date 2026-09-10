package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "roles")
data class RoleEntity(
    @androidx.room.PrimaryKey val code: String,
    val nameAr: String,
    val nameEn: String,
    val description: String = "",
    val isSystem: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "permissions", indices = [Index("moduleKey"), Index("sortOrder")])
data class PermissionEntity(
    @androidx.room.PrimaryKey val code: String,
    val moduleKey: String,
    val actionKey: String,
    val nameAr: String,
    val nameEn: String,
    val description: String = "",
    val sortOrder: Int = 0
)

@Entity(
    tableName = "role_permissions",
    primaryKeys = ["roleCode", "permissionCode"],
    foreignKeys = [
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["code"],
            childColumns = ["roleCode"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PermissionEntity::class,
            parentColumns = ["code"],
            childColumns = ["permissionCode"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("permissionCode")]
)
data class RolePermissionEntity(
    val roleCode: String,
    val permissionCode: String
)

@Entity(
    tableName = "user_password_history",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId"), Index("createdAt")]
)
data class UserPasswordHistoryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val passwordHash: String,
    val salt: String,
    val createdAt: Long = System.currentTimeMillis()
)


@Entity(
    tableName = "user_mfa_recovery_codes",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId"), Index("usedAt")]
)
data class MfaRecoveryCodeEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val codeHash: String,
    val salt: String,
    val usedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
