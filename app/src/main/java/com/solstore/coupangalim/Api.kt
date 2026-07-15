package com.solstore.coupangalim

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** 서버 API 통신 (대시보드 상태 조회 / 계정 등록). */
object Api {

    private fun conn(ctx: Context, path: String, method: String): HttpURLConnection {
        val url = Prefs.serverUrl(ctx) + path
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 12000
        c.readTimeout = 15000
        c.setRequestProperty("X-Auth", Crypto.authToken(Prefs.apiSecret(ctx)))
        return c
    }

    private fun readBody(c: HttpURLConnection): String {
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        return stream?.let {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
        } ?: ""
    }

    /** 대시보드 상태 조회. 성공 시 JSON 문자열, 실패 시 null. */
    fun getStatus(ctx: Context): String? {
        if (Prefs.serverUrl(ctx).isBlank() || Prefs.apiSecret(ctx).isBlank()) return null
        return try {
            val c = conn(ctx, "/status", "GET")
            val body = readBody(c)
            val code = c.responseCode
            c.disconnect()
            if (code in 200..299) {
                Prefs.setCachedStatus(ctx, body)
                body
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /** 서버가 쿠팡을 지금 즉시 조회하게 하고(완료까지 대기) 최신 상태를 받는다.
     *  구버전 서버(엔드포인트 없음)면 일반 상태 조회로 폴백. */
    fun pollNow(ctx: Context): String? {
        if (Prefs.serverUrl(ctx).isBlank() || Prefs.apiSecret(ctx).isBlank()) return null
        return try {
            val c = conn(ctx, "/poll", "POST")
            c.readTimeout = 120000  // 폴링은 오래 걸릴 수 있음
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(ByteArray(0)) }
            val body = readBody(c)
            val code = c.responseCode
            c.disconnect()
            when {
                code in 200..299 -> {
                    Prefs.setCachedStatus(ctx, body); body
                }
                code == 404 -> getStatus(ctx)  // 구버전 서버
                else -> getStatus(ctx)
            }
        } catch (_: Exception) {
            getStatus(ctx)
        }
    }

    /** 계정 등록 (암호화 전송). 성공 여부 + 메시지. */
    fun addAccount(ctx: Context, label: String, vendorId: String, accessKey: String, secretKey: String): Pair<Boolean, String> {
        if (Prefs.serverUrl(ctx).isBlank() || Prefs.apiSecret(ctx).isBlank())
            return false to "서버 주소/보안 키를 먼저 설정하세요 (⚙)"
        return try {
            val payload = JSONObject()
                .put("label", label).put("vendorId", vendorId)
                .put("accessKey", accessKey).put("secretKey", secretKey)
                .toString()
            val blob = Crypto.encrypt(Prefs.apiSecret(ctx), payload)
            val c = conn(ctx, "/account", "POST")
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(blob.toByteArray(Charsets.UTF_8)) }
            val body = readBody(c)
            val code = c.responseCode
            c.disconnect()
            if (code in 200..299) true to "등록 완료"
            else false to (try { JSONObject(body).optString("error") } catch (_: Exception) { "서버 오류 $code" })
        } catch (e: Exception) {
            false to "연결 실패: ${e.message}"
        }
    }

    /** 계정 삭제 (암호화 전송). 성공 여부 + 메시지. */
    fun deleteAccount(ctx: Context, id: Int): Pair<Boolean, String> {
        if (Prefs.serverUrl(ctx).isBlank() || Prefs.apiSecret(ctx).isBlank())
            return false to "서버 주소/보안 키를 먼저 설정하세요 (⚙)"
        return try {
            val payload = JSONObject().put("id", id).toString()
            val blob = Crypto.encrypt(Prefs.apiSecret(ctx), payload)
            val c = conn(ctx, "/account/delete", "POST")
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(blob.toByteArray(Charsets.UTF_8)) }
            val body = readBody(c)
            val code = c.responseCode
            c.disconnect()
            if (code in 200..299) true to "삭제 완료"
            else false to (try { JSONObject(body).optString("error") } catch (_: Exception) { "서버 오류 $code" })
        } catch (e: Exception) {
            false to "연결 실패: ${e.message}"
        }
    }
}
