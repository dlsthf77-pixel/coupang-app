package com.solstore.coupangalim

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * ntfy 서버의 실시간 스트림에 붙어 알림을 받는 포그라운드 서비스.
 * 서버가 쿠팡을 5분마다 확인해서 이 토픽으로 알림을 보내면, 여기서 받아
 * 종류별 소리로 알림을 띄운다. 화면을 꺼도 계속 받는다.
 */
class StreamService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val seen = LinkedHashSet<String>()
    private var lastTime: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotifHelper.SERVICE_NOTIF_ID,
            NotifHelper.serviceNotification(this, "새 주문·문의를 실시간으로 받는 중")
        )
        if (job == null || job?.isActive != true) {
            job = scope.launch { loop() }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val topic = Prefs.topic(applicationContext)
            if (topic.isBlank()) {
                delay(10_000)
                continue
            }
            try {
                connectAndRead(topic)
            } catch (_: Exception) {
                // 연결 끊김/오류 → 잠시 후 재접속
            }
            delay(5_000)
        }
    }

    private fun connectAndRead(topic: String) {
        val server = Prefs.ntfyServer(applicationContext).trimEnd('/')
        var urlStr = "$server/$topic/json"
        if (lastTime > 0) urlStr += "?since=${lastTime}s"  // 끊긴 사이 놓친 알림도 받기
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 0  // 스트림은 계속 열어둠
        try {
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { r ->
                var line: String?
                while (scope.isActive) {
                    line = r.readLine() ?: break
                    if (line.isNullOrBlank()) continue
                    try {
                        handle(JSONObject(line))
                    } catch (_: Exception) {
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun handle(o: JSONObject) {
        val event = o.optString("event")
        if (event == "message") {
            val id = o.optString("id")
            val time = o.optLong("time")
            if (time > lastTime) lastTime = time
            if (id.isNotEmpty()) {
                if (seen.contains(id)) return
                seen.add(id)
                if (seen.size > 300) seen.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
            }
            val title = o.optString("title")
            val message = o.optString("message")
            val priority = o.optInt("priority", 3)
            val type = when (priority) {
                5 -> Prefs.TYPE_ORDER
                4 -> Prefs.TYPE_INQUIRY
                else -> Prefs.TYPE_RETURN
            }
            val accountId = matchAccount(title)
            val notifId = (System.currentTimeMillis() % 100000).toInt()
            NotifHelper.postAlert(
                applicationContext, type,
                title.ifBlank { NotifHelper.typeLabel(type) },
                message, accountId, notifId
            )
        }
        // event == open / keepalive / poll_request → 무시
    }

    /** 제목 "○○상점 · 신규 주문" 에서 계정 이름을 뽑아 해당 계정 id 를 찾는다. */
    private fun matchAccount(title: String): Int {
        val label = title.substringBefore(" · ").trim()
        if (label.isBlank()) return 0
        val map = Prefs.labels(applicationContext)
        for ((id, l) in map) if (l == label) return id
        return 0
    }

    override fun onDestroy() {
        scope.cancel()
        job = null
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            Prefs.setEnabled(ctx, true)
            val i = Intent(ctx, StreamService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            Prefs.setEnabled(ctx, false)
            ctx.stopService(Intent(ctx, StreamService::class.java))
        }
    }
}
