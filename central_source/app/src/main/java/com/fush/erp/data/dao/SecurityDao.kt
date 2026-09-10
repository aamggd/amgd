package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fush.erp.data.entity.PermissionEntity
import com.fush.erp.data.entity.MfaRecoveryCodeEntity
import com.fush.erp.data.entity.RoleEntity
import com.fush.erp.data.entity.RolePermissionEntity
import com.fush.erp.data.entity.UserPasswordHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityDao {
    @Query("SELECT * FROM roles ORDER BY isSystem DESC, nameAr, code")
    fun observeRoles(): Flow<List<RoleEntity>>

    @Query("SELECT * FROM roles WHERE isActive = 1 ORDER BY isSystem DESC, nameAr, code")
    fun observeActiveRoles(): Flow<List<RoleEntity>>

    @Query("SELECT * FROM roles WHERE isActive = 1 ORDER BY isSystem DESC, nameAr, code")
    suspend fun activeRoles(): List<RoleEntity>

    @Query("SELECT * FROM roles WHERE code = :code LIMIT 1")
    suspend fun roleByCode(code: String): RoleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRolesIgnore(rows: List<RoleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRole(row: RoleEntity)

    @Query("SELECT * FROM permissions ORDER BY moduleKey, sortOrder, code")
    fun observePermissions(): Flow<List<PermissionEntity>>

    @Query("SELECT * FROM permissions ORDER BY moduleKey, sortOrder, code")
    suspend fun allPermissions(): List<PermissionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPermissions(rows: List<PermissionEntity>)

    @Query("SELECT permissionCode FROM role_permissions WHERE roleCode = :roleCode ORDER BY permissionCode")
    fun observePermissionCodesForRole(roleCode: String): Flow<List<String>>

    @Query("SELECT permissionCode FROM role_permissions WHERE roleCode = :roleCode ORDER BY permissionCode")
    suspend fun permissionCodesForRole(roleCode: String): List<String>

    @Query("SELECT COUNT(*) FROM role_permissions WHERE roleCode = :roleCode")
    suspend fun rolePermissionCount(roleCode: String): Int

    @Query("SELECT COUNT(*) FROM role_permissions WHERE roleCode = :roleCode AND permissionCode = :permissionCode")
    suspend fun hasPermission(roleCode: String, permissionCode: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRolePermissionsIgnore(rows: List<RolePermissionEntity>)

    @Query("DELETE FROM role_permissions WHERE roleCode = :roleCode")
    suspend fun deleteRolePermissions(roleCode: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRolePermissions(rows: List<RolePermissionEntity>)

    @Transaction
    suspend fun replaceRolePermissions(roleCode: String, permissionCodes: Set<String>) {
        deleteRolePermissions(roleCode)
        if (permissionCodes.isNotEmpty()) {
            insertRolePermissions(permissionCodes.map { RolePermissionEntity(roleCode, it) })
        }
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPasswordHistory(row: UserPasswordHistoryEntity): Long

    @Query("SELECT * FROM user_password_history WHERE userId = :userId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun passwordHistory(userId: Long, limit: Int = 10): List<UserPasswordHistoryEntity>

    @Query("DELETE FROM user_password_history WHERE userId = :userId AND id NOT IN (SELECT id FROM user_password_history WHERE userId = :userId ORDER BY createdAt DESC, id DESC LIMIT :keep)")
    suspend fun prunePasswordHistory(userId: Long, keep: Int = 10)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMfaRecoveryCodes(rows: List<MfaRecoveryCodeEntity>)

    @Query("SELECT * FROM user_mfa_recovery_codes WHERE userId = :userId AND usedAt IS NULL ORDER BY id")
    suspend fun unusedMfaRecoveryCodes(userId: Long): List<MfaRecoveryCodeEntity>

    @Query("UPDATE user_mfa_recovery_codes SET usedAt = :usedAt WHERE id = :id AND usedAt IS NULL")
    suspend fun markMfaRecoveryCodeUsed(id: Long, usedAt: Long): Int

    @Query("DELETE FROM user_mfa_recovery_codes WHERE userId = :userId")
    suspend fun deleteMfaRecoveryCodes(userId: Long)
}
