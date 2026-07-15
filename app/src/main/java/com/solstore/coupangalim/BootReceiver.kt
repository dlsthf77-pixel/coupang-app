package com.solstore.coupangalim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 재부팅 후, 알림 수신이 켜져 있었다면 서비스를 다시 시작. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Prefs.isEnabled(context) && Prefs.topic(context).isNotBlank()) {
                StreamService.start(context)
            }
        }
    }
}
