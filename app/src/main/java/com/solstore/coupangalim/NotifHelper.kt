package com.solstore.coupangalim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 알림 채널(종류별) + 소리 관리 + 알림 발송.
 * 종류별 채널을 우리가 직접 만들어서, 소리를 앱 안에서 마음대로 지정한다.
 * (안드로이드는 채널 소리를 바꾸려면 채널을 새로 만들어야 해서 버전 번호를 붙인다.)
 */
object NotifHelper {
    const val CHANNEL_SERVICE = "service"
    const val SERVICE_NOTIF_ID = 1000

    private val TYPES = listOf(Prefs.TYPE_ORDER, Prefs.TYPE_INQUIRY, Prefs.TYPE_RETURN)

    fun typeLabel(type: String): String = when (type) {
        Prefs.TYPE_ORDER -> "신규 주문"
        Prefs.TYPE_INQUIRY -> "고객 문의"
        Prefs.TYPE_RETURN -> "반품/취소"
        else -> "알림"
    }

    fun channelId(ctx: Context, type: String): String =
        "${type}_v${Prefs.channelVersion(ctx, type)}"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val service = NotificationChannel(
            CHANNEL_SERVICE, "백그라운드 실행", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "알림을 받기 위해 계속 실행 중임을 표시합니다."
            setShowBadge(false)
        }
        mgr.createNotificationChannel(service)

        for (type in TYPES) createAlertChannel(ctx, mgr, type)
    }

    private fun createAlertChannel(ctx: Context, mgr: NotificationManager, type: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val name = when (type) {
            Prefs.TYPE_ORDER -> "주문 알림"
            Prefs.TYPE_INQUIRY -> "문의 알림"
            else -> "반품/취소 알림"
        }
        val ch = NotificationChannel(
            channelId(ctx, type), name, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            val uriStr = Prefs.soundUri(ctx, type)
            if (uriStr != null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(Uri.parse(uriStr), attrs)
            }
            // uriStr == null 이면 채널 기본 소리 사용
        }
        mgr.createNotificationChannel(ch)
    }

    /** 소리를 바꾼다: 새 채널 버전을 만들어 적용 (삼성 제한 우회). */
    fun applySound(ctx: Context, type: String, uri: String?) {
        Prefs.setSoundUri(ctx, type, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // 기존 채널 삭제 후 새 버전 생성
            try {
                mgr.deleteNotificationChannel(channelId(ctx, type))
            } catch (_: Exception) {
            }
            Prefs.bumpChannelVersion(ctx, type)
            createAlertChannel(ctx, mgr, type)
        }
    }

    fun serviceNotification(ctx: Context, text: String): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setContentTitle("쿠팡 셀러 알리미")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 실제 알림 발송. accountId 가 있으면 탭 시 그 계정 윙을 연다. */
    fun postAlert(ctx: Context, type: String, title: String, body: String, accountId: Int, notifId: Int) {
        val intent = if (accountId > 0) {
            Intent(ctx, WingActivity::class.java).putExtra("accountId", accountId)
        } else {
            Intent(ctx, MainActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pi = PendingIntent.getActivity(
            ctx, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, channelId(ctx, type))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(notifId, n)
        } catch (_: SecurityException) {
        }
    }
}
