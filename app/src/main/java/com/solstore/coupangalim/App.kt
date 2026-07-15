package com.solstore.coupangalim

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NotifHelper.ensureChannels(this)

        // 15분마다 서비스 생존 확인 (꺼져 있으면 되살림)
        try {
            val req = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "keepalive", ExistingPeriodicWorkPolicy.UPDATE, req
            )
        } catch (_: Exception) {
        }

        // 앱 프로세스 시작 시, 켜져 있었다면 서비스 재시작
        try {
            if (Prefs.isEnabled(this) && Prefs.topic(this).isNotBlank()) {
                StreamService.start(this)
            }
        } catch (_: Exception) {
        }
    }
}
