package com.solstore.coupangalim

import android.content.Context
import org.json.JSONObject

/**
 * 앱 설정 저장:
 *  - 서버 주소 + 보안 키 (대시보드/계정등록 API)
 *  - ntfy 서버/토픽 (실시간 알림)
 *  - 종류별 소리 + 채널 버전
 *  - 대시보드 최신 상태 캐시 (계정 이름/숫자)
 *  - 계정별 로그인 쿠키 (윙 세션 유지)
 */
object Prefs {
    private const val P = "coupang_app_prefs"

    const val TYPE_ORDER = "order"
    const val TYPE_INQUIRY = "inquiry"
    const val TYPE_RETURN = "return"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)

    // ---- 서버 API ----
    fun serverUrl(ctx: Context): String =
        (sp(ctx).getString("serverUrl", "") ?: "").trimEnd('/')
    fun setServerUrl(ctx: Context, v: String) =
        sp(ctx).edit().putString("serverUrl", v.trim().trimEnd('/')).apply()

    fun apiSecret(ctx: Context): String = sp(ctx).getString("apiSecret", "") ?: ""
    fun setApiSecret(ctx: Context, v: String) =
        sp(ctx).edit().putString("apiSecret", v.trim()).apply()

    // ---- ntfy ----
    fun ntfyServer(ctx: Context): String =
        sp(ctx).getString("ntfyServer", "https://ntfy.sh") ?: "https://ntfy.sh"
    fun topic(ctx: Context): String = sp(ctx).getString("topic", "") ?: ""
    fun setTopic(ctx: Context, t: String) = sp(ctx).edit().putString("topic", t.trim()).apply()

    fun isEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("enabled", on).apply()

    // ---- 소리 ----
    fun soundUri(ctx: Context, type: String): String? = sp(ctx).getString("sound_$type", null)
    fun setSoundUri(ctx: Context, type: String, uri: String?) =
        sp(ctx).edit().putString("sound_$type", uri).apply()

    fun channelVersion(ctx: Context, type: String): Int = sp(ctx).getInt("ver_$type", 1)
    fun bumpChannelVersion(ctx: Context, type: String): Int {
        val v = channelVersion(ctx, type) + 1
        sp(ctx).edit().putInt("ver_$type", v).apply()
        return v
    }

    // ---- 대시보드 상태 캐시 ----
    fun cachedStatus(ctx: Context): String? = sp(ctx).getString("status", null)
    fun setCachedStatus(ctx: Context, json: String) =
        sp(ctx).edit().putString("status", json).apply()

    /** 캐시된 상태에서 계정 이름 -> id 매칭 (알림 매칭용). */
    fun idForLabel(ctx: Context, label: String): Int {
        val raw = cachedStatus(ctx) ?: return 0
        return try {
            val arr = JSONObject(raw).optJSONArray("accounts") ?: return 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("label") == label) return o.optInt("id")
            }
            0
        } catch (_: Exception) {
            0
        }
    }

    fun labelForId(ctx: Context, id: Int): String {
        val raw = cachedStatus(ctx) ?: return ""
        return try {
            val arr = JSONObject(raw).optJSONArray("accounts") ?: return ""
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optInt("id") == id) return o.optString("label")
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    // ---- 계정별 로그인 쿠키 (윙 세션 유지) ----
    fun cookies(ctx: Context, id: Int): String = sp(ctx).getString("cookie_$id", "") ?: ""
    fun setCookies(ctx: Context, id: Int, blob: String) =
        sp(ctx).edit().putString("cookie_$id", blob).apply()

    /** 현재 WebView 쿠키 저장소가 담고 있는 계정 id (같은 계정이면 쿠키를 안 지우고 유지). */
    fun currentWing(ctx: Context): Int = sp(ctx).getInt("currentWing", 0)
    fun setCurrentWing(ctx: Context, id: Int) =
        sp(ctx).edit().putInt("currentWing", id).apply()
}
