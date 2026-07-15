package com.solstore.coupangalim

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 앱 설정 저장:
 *  - ntfy 서버/토픽 (서버 알림 수신용)
 *  - 종류별 소리 + 채널 버전
 *  - 계정 목록(1~10, 이름) — 윙 바로가기용
 *  - 계정별 로그인 쿠키(세션 유지)
 */
object Prefs {
    private const val P = "coupang_app_prefs"
    const val MAX_ACCOUNTS = 10

    const val TYPE_ORDER = "order"
    const val TYPE_INQUIRY = "inquiry"
    const val TYPE_RETURN = "return"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)

    // ---- ntfy ----
    fun ntfyServer(ctx: Context): String =
        sp(ctx).getString("server", "https://ntfy.sh") ?: "https://ntfy.sh"

    fun topic(ctx: Context): String = sp(ctx).getString("topic", "") ?: ""
    fun setTopic(ctx: Context, topic: String) =
        sp(ctx).edit().putString("topic", topic.trim()).apply()

    fun isEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, on: Boolean) =
        sp(ctx).edit().putBoolean("enabled", on).apply()

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

    // ---- 계정 목록 (이름만; 윙 바로가기용) ----
    fun labels(ctx: Context): MutableMap<Int, String> {
        val raw = sp(ctx).getString("labels", null)
        val map = linkedMapOf<Int, String>()
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    map[o.optInt("id")] = o.optString("label")
                }
            } catch (_: Exception) {
            }
        }
        return map
    }

    fun label(ctx: Context, id: Int): String = labels(ctx)[id] ?: ""

    fun setLabel(ctx: Context, id: Int, label: String) {
        val map = labels(ctx)
        if (label.isBlank()) map.remove(id) else map[id] = label.trim()
        val arr = JSONArray()
        map.toSortedMap().forEach { (k, v) ->
            arr.put(JSONObject().put("id", k).put("label", v))
        }
        sp(ctx).edit().putString("labels", arr.toString()).apply()
    }

    // ---- 계정별 로그인 쿠키(세션 유지) ----
    fun cookies(ctx: Context, id: Int): String = sp(ctx).getString("cookie_$id", "") ?: ""
    fun setCookies(ctx: Context, id: Int, cookieBlob: String) =
        sp(ctx).edit().putString("cookie_$id", cookieBlob).apply()
}
