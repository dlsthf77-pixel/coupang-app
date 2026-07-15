package com.solstore.coupangalim

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * 15분마다 실행되는 감시 작업. 알림이 켜져 있는데 실시간 수신 서비스가
 * (삼성 절전 등으로) 꺼져 있으면 다시 살린다.
 */
class KeepAliveWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        try {
            val c = applicationContext
            if (Prefs.isEnabled(c) && Prefs.topic(c).isNotBlank()) {
                StreamService.start(c)
            }
        } catch (_: Exception) {
        }
        return Result.success()
    }
}
