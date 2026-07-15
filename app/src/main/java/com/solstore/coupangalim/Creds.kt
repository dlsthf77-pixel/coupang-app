package com.solstore.coupangalim

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 계정별 쿠팡 윙 로그인 정보(아이디/비밀번호)를 기기에만 암호화 저장한다.
 * - 서버로는 절대 전송하지 않는다. (오직 이 폰의 WebView 자동 로그인에만 사용)
 * - 안드로이드 보안 저장소(EncryptedSharedPreferences) 사용. 기기에서 지원이 안 되면
 *   앱 전용 저장소로 안전하게 대체한다(다른 앱은 못 읽음).
 */
object Creds {
    private const val FILE = "coupang_wing_creds"

    private fun prefs(ctx: Context): SharedPreferences {
        return try {
            val master = MasterKey.Builder(ctx.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx.applicationContext,
                FILE,
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            ctx.applicationContext.getSharedPreferences(FILE + "_fb", Context.MODE_PRIVATE)
        }
    }

    fun id(ctx: Context, accountId: Int): String =
        prefs(ctx).getString("id_$accountId", "") ?: ""

    fun pw(ctx: Context, accountId: Int): String =
        prefs(ctx).getString("pw_$accountId", "") ?: ""

    fun has(ctx: Context, accountId: Int): Boolean =
        id(ctx, accountId).isNotBlank() && pw(ctx, accountId).isNotBlank()

    fun set(ctx: Context, accountId: Int, uid: String, upw: String) {
        prefs(ctx).edit()
            .putString("id_$accountId", uid.trim())
            .putString("pw_$accountId", upw)
            .apply()
    }

    fun clear(ctx: Context, accountId: Int) {
        prefs(ctx).edit()
            .remove("id_$accountId")
            .remove("pw_$accountId")
            .apply()
    }
}
