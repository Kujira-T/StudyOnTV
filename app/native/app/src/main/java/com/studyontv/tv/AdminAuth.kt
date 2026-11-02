package com.studyontv.tv

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AdminAuth {
    private val random = SecureRandom()
    private val sessions = mutableMapOf<String, Long>()
    val deviceToken: String = randomToken()

    private fun randomToken(): String = ByteArray(32).also { random.nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    private fun prefs(context: Context) = context.getSharedPreferences("parent_access", Context.MODE_PRIVATE)
    fun configured(context: Context): Boolean = prefs(context).contains("hash")

    private fun hash(value: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(PBEKeySpec(value.toCharArray(), salt, 60000, 256)).encoded

    @Synchronized
    fun setPassword(context: Context, value: String) {
        require(value.length in 6..64) { "管理密码需要 6–64 位" }
        val salt = ByteArray(16).also { random.nextBytes(it) }
        prefs(context).edit().putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(value, salt), Base64.NO_WRAP))
            .remove("failures").remove("blocked_until").commit()
        sessions.clear()
    }

    @Synchronized
    fun verify(context: Context, value: String): Boolean {
        val p = prefs(context)
        if (!configured(context) || value.length !in 6..64 || p.getLong("blocked_until", 0) > System.currentTimeMillis()) return false
        val salt = Base64.decode(p.getString("salt", ""), Base64.NO_WRAP)
        val expected = Base64.decode(p.getString("hash", ""), Base64.NO_WRAP)
        val ok = MessageDigest.isEqual(expected, hash(value, salt))
        if (ok) p.edit().remove("failures").apply()
        else {
            val failures = p.getInt("failures", 0) + 1
            p.edit().putInt("failures", if (failures >= 5) 0 else failures)
                .putLong("blocked_until", if (failures >= 5) System.currentTimeMillis() + 60000 else 0).commit()
        }
        return ok
    }

    @Synchronized
    fun login(context: Context, value: String): String? {
        if (!verify(context, value)) return null
        sessions.entries.removeAll { it.value < System.currentTimeMillis() }
        return randomToken().also { sessions[it] = System.currentTimeMillis() + 12 * 60 * 60 * 1000L }
    }

    @Synchronized
    fun valid(token: String?): Boolean = token != null && (sessions[token] ?: 0) > System.currentTimeMillis()
}
