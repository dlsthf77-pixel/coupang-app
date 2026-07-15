package com.solstore.coupangalim

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 서버와 동일한 방식의 암호화. (Python crypto_util.py 와 호환)
 *  - cryptoKey = SHA256(secret + "|crypto")
 *  - authToken = SHA256(secret + "|auth")  (hex)
 *  - AES-256-GCM, 전송 = base64( nonce(12) + ciphertext + tag(16) )
 */
object Crypto {

    private fun sha256(s: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))

    fun authToken(secret: String): String {
        val h = sha256("$secret|auth")
        val sb = StringBuilder()
        for (b in h) sb.append(String.format("%02x", b.toInt() and 0xff))
        return sb.toString()
    }

    fun encrypt(secret: String, plaintext: String): String {
        val key = SecretKeySpec(sha256("$secret|crypto"), "AES")
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(nonce.size + ct.size)
        System.arraycopy(nonce, 0, out, 0, nonce.size)
        System.arraycopy(ct, 0, out, nonce.size, ct.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }
}
