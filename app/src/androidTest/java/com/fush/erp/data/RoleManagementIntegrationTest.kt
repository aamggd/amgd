package com.fush.erp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fush.erp.domain.ReauthenticationResult
import com.fush.erp.domain.SecurityService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleManagementIntegrationTest {
    private lateinit var db: FushDatabase
    private lateinit var service: SecurityService
    private var adminId: Long = 0L
    private val adminPassword = "StrongAdmin@2026#Role".toCharArray()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FushDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = SecurityService(db)
        service.seedDefaults()
        val admin = service.bootstrapFirstAdmin("admin_role_test", "مدير الاختبار", adminPassword)
        adminId = admin.id
        assertEquals(
            ReauthenticationResult.Success,
            service.reauthenticate(adminId, adminPassword)
        )
    }

    @After
    fun tearDown() {
        adminPassword.fill('\u0000')
        db.close()
    }

    @Test
    fun customRoleCanBeDeletedAndDoesNotReturnAfterSeeding() = runBlocking {
        val role = service.saveCustomRole(adminId, "STORE_MANAGER", "مدير مخزن", "اختبار حذف الدور")
        assertNotNull(db.securityDao().roleByCode(role.code))

        service.deleteRole(adminId, role.code)
        assertNull(db.securityDao().roleByCode(role.code))

        service.seedDefaults()
        assertNull(db.securityDao().roleByCode(role.code))
    }

    @Test
    fun nonSystemDefaultRoleCanBeDeletedAndDoesNotGetReseeded() = runBlocking {
        assertNotNull(db.securityDao().roleByCode("SALES"))
        service.deleteRole(adminId, "SALES")
        assertNull(db.securityDao().roleByCode("SALES"))
        assertEquals(0, db.securityDao().rolePermissionCount("SALES"))

        service.seedDefaults()
        assertNull(db.securityDao().roleByCode("SALES"))
    }

    @Test
    fun roleAssignedToUserCannotBeDeleted() = runBlocking {
        service.saveCustomRole(adminId, "TEMP_ROLE", "دور مؤقت", "اختبار الارتباط")
        service.createUser(
            actorUserId = adminId,
            username = "role_user",
            displayName = "مستخدم الدور",
            roleCode = "TEMP_ROLE",
            temporaryPassword = "StrongUser@2026#Temp".toCharArray()
        )

        val error = runCatching { service.deleteRole(adminId, "TEMP_ROLE") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("مستخدم مرتبط"))
        assertNotNull(db.securityDao().roleByCode("TEMP_ROLE"))
    }

    @Test
    fun adminRoleCannotBeDeleted() = runBlocking {
        val error = runCatching { service.deleteRole(adminId, "ADMIN") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertNotNull(db.securityDao().roleByCode("ADMIN"))
    }
}
