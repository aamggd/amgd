package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MfaSecurityTest {
    @Test
    fun totpMatchesRfc6238Sha1VectorTruncatedToSixDigits() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals("287082", Totp.generate(secret, 1L))
        assertTrue(Totp.verify(secret, "287082", nowMillis = 59_000L, window = 0))
        assertFalse(Totp.verify(secret, "287083", nowMillis = 59_000L, window = 0))
    }

    @Test
    fun mfaSecretEncryptionRoundTripsAndUsesRandomSaltAndIv() {
        val password = "Strong-Admin-Passphrase-2026!".toCharArray()
        val secret = Totp.newSecret()
        val encrypted1 = MfaSecretCrypto.encrypt(secret, password)
        val encrypted2 = MfaSecretCrypto.encrypt(secret, password)
        assertNotEquals(secret, encrypted1)
        assertNotEquals(encrypted1, encrypted2)
        assertEquals(secret, MfaSecretCrypto.decrypt(encrypted1, password))
        assertEquals(secret, MfaSecretCrypto.decrypt(encrypted2, password))
        assertTrue(runCatching { MfaSecretCrypto.decrypt(encrypted1, "Wrong-Password-2026!".toCharArray()) }.isFailure)
    }

    @Test
    fun recoveryCodeHashesAreSaltedAndVerifiable() {
        val code = "ABCD-EFGH-JK23"
        val salt1 = RecoveryCodeHasher.newSalt()
        val salt2 = RecoveryCodeHasher.newSalt()
        val hash1 = RecoveryCodeHasher.hash(code, salt1)
        val hash2 = RecoveryCodeHasher.hash(code, salt2)
        assertNotEquals(hash1, hash2)
        assertTrue(RecoveryCodeHasher.verify("abcd efgh jk23", salt1, hash1))
        assertTrue(RecoveryCodeHasher.verify(code.replace("-", ""), salt1, hash1))
        assertFalse(RecoveryCodeHasher.verify("ZZZZ-ZZZZ-ZZZZ", salt1, hash1))
    }

    @Test
    fun recoveryCodesAreUniqueAndNormalized() {
        val codes = MfaRecoveryCodes.generate()
        assertEquals(MfaPolicy.RECOVERY_CODE_COUNT, codes.size)
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { MfaRecoveryCodes.normalize(it).length == 12 })
    }
}
