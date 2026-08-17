package com.fush.erp.domain

import java.nio.ByteBuffer
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object MfaPolicy {
    const val TOTP_PERIOD_SECONDS = 30L
    const val TOTP_DIGITS = 6
    const val RECOVERY_CODE_COUNT = 10
    const val SETUP_SECRET_BYTES = 20

    val privilegedPermissionCodes = setOf(
        SecurityPermissions.USERS_MANAGE,
        SecurityPermissions.ROLES_MANAGE,
        SecurityPermissions.BACKUP_RESTORE
    )
}

object Totp {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val random = SecureRandom()

    fun newSecret(): String {
        val bytes = ByteArray(MfaPolicy.SETUP_SECRET_BYTES)
        random.nextBytes(bytes)
        return base32Encode(bytes)
    }

    fun provisioningUri(account: String, secret: String, issuer: String = "FUSH ERP"): String {
        fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
        return "otpauth://totp/${enc(issuer)}:${enc(account)}" +
            "?secret=${enc(secret)}&issuer=${enc(issuer)}&algorithm=SHA1&digits=${MfaPolicy.TOTP_DIGITS}&period=${MfaPolicy.TOTP_PERIOD_SECONDS}"
    }

    fun verify(secret: String, code: String, nowMillis: Long = System.currentTimeMillis(), window: Int = 1): Boolean {
        val normalized = code.filter(Char::isDigit)
        if (normalized.length != MfaPolicy.TOTP_DIGITS) return false
        val counter = nowMillis / 1000L / MfaPolicy.TOTP_PERIOD_SECONDS
        for (offset in -window..window) {
            if (constantTimeEquals(generate(secret, counter + offset), normalized)) return true
        }
        return false
    }

    fun generate(secret: String, counter: Long): String {
        val key = base32Decode(secret)
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val mod = 1_000_000
        return (binary % mod).toString().padStart(MfaPolicy.TOTP_DIGITS, '0')
    }

    internal fun base32Encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                out.append(ALPHABET[(buffer shr bitsLeft) and 31])
            }
        }
        if (bitsLeft > 0) out.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 31])
        return out.toString()
    }

    internal fun base32Decode(value: String): ByteArray {
        val clean = value.trim().uppercase().replace("=", "").replace(" ", "")
        require(clean.all { ALPHABET.indexOf(it) >= 0 }) { "سر MFA غير صالح" }
        val out = ArrayList<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (char in clean) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(char)
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out += ((buffer shr bitsLeft) and 0xff).toByte()
            }
        }
        return out.toByteArray()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}

object MfaSecretCrypto {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val VERSION = "v1"
    private val random = SecureRandom()

    fun encrypt(secret: String, password: CharArray): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val enc = Base64.getUrlEncoder().withoutPadding()
        return listOf(VERSION, enc.encodeToString(salt), enc.encodeToString(iv), enc.encodeToString(ciphertext)).joinToString(":")
    }

    fun decrypt(payload: String, password: CharArray): String {
        val parts = payload.split(':')
        require(parts.size == 4 && parts[0] == VERSION) { "بيانات MFA غير صالحة" }
        val dec = Base64.getUrlDecoder()
        val salt = dec.decode(parts[1])
        val iv = dec.decode(parts[2])
        val ciphertext = dec.decode(parts[3])
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}

object MfaRecoveryCodes {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate(count: Int = MfaPolicy.RECOVERY_CODE_COUNT): List<String> = List(count) {
        val raw = buildString(12) { repeat(12) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        raw.chunked(4).joinToString("-")
    }

    fun normalize(value: String): String = value.uppercase().filter { it.isLetterOrDigit() }
}


object RecoveryCodeHasher {
    private val random = SecureRandom()

    fun newSalt(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(code: String, saltBase64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.getDecoder().decode(saltBase64))
        val bytes = digest.digest(MfaRecoveryCodes.normalize(code).toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun verify(code: String, saltBase64: String, expectedHash: String): Boolean {
        val actual = Base64.getDecoder().decode(hash(code, saltBase64))
        val expected = runCatching { Base64.getDecoder().decode(expectedHash) }.getOrElse { return false }
        return MessageDigest.isEqual(actual, expected)
    }
}
