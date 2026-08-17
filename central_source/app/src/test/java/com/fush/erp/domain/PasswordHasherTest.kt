package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun verifiesCorrectPasswordOnly() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("StrongPass1!".toCharArray(), salt)
        assertTrue(PasswordHasher.verify("StrongPass1!".toCharArray(), salt, hash))
        assertFalse(PasswordHasher.verify("Wrong".toCharArray(), salt, hash))
    }
}
