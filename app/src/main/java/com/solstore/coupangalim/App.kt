package com.solstore.coupangalim

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NotifHelper.ensureChannels(this)
    }
}
