package com.solstore.coupangalim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {

    private lateinit var etServer: EditText
    private lateinit var etSecret: EditText
    private lateinit var etTopic: EditText
    private lateinit var toggleBtn: Button
    private var pickingType: String? = null

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val ringtonePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val type = pickingType ?: return@registerForActivityResult
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            NotifHelper.applySound(this, type, uri?.toString())
            Toast.makeText(this, "${NotifHelper.typeLabel(type)} 소리를 바꿨어요.", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.title = "설정"

        etServer = findViewById(R.id.etServer)
        etSecret = findViewById(R.id.etSecret)
        etTopic = findViewById(R.id.etTopic)
        toggleBtn = findViewById(R.id.btnToggle)

        etServer.setText(Prefs.serverUrl(this))
        etSecret.setText(Prefs.apiSecret(this))
        etTopic.setText(Prefs.topic(this))

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnPing).setOnClickListener { ping() }
        findViewById<Button>(R.id.btnSoundOrder).setOnClickListener { pickSound(Prefs.TYPE_ORDER) }
        findViewById<Button>(R.id.btnSoundInquiry).setOnClickListener { pickSound(Prefs.TYPE_INQUIRY) }
        findViewById<Button>(R.id.btnSoundReturn).setOnClickListener { pickSound(Prefs.TYPE_RETURN) }
        toggleBtn.setOnClickListener { toggle() }
        refreshToggle()
    }

    private fun save() {
        Prefs.setServerUrl(this, etServer.text.toString())
        Prefs.setApiSecret(this, etSecret.text.toString())
        Prefs.setTopic(this, etTopic.text.toString())
        Toast.makeText(this, "저장했어요.", Toast.LENGTH_SHORT).show()
        if (Prefs.isEnabled(this) && Prefs.topic(this).isNotBlank()) StreamService.start(this)
    }

    private fun ping() {
        save()
        Toast.makeText(this, "연결 확인 중...", Toast.LENGTH_SHORT).show()
        thread {
            val ok = try {
                val url = java.net.URL(Prefs.serverUrl(applicationContext) + "/ping")
                val c = url.openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val r = c.responseCode in 200..299
                c.disconnect(); r
            } catch (_: Exception) { false }
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) "✅ 서버 연결 성공!" else "❌ 연결 실패 (주소·포트 확인)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun refreshToggle() {
        toggleBtn.text = if (Prefs.isEnabled(this)) "알림 끄기" else "알림 받기 시작"
    }

    private fun toggle() {
        if (Prefs.isEnabled(this)) {
            StreamService.stop(this)
            Toast.makeText(this, "알림을 껐어요.", Toast.LENGTH_SHORT).show()
        } else {
            save()
            if (Prefs.topic(this).isBlank()) {
                Toast.makeText(this, "먼저 ntfy 토픽을 입력·저장하세요.", Toast.LENGTH_LONG).show()
                return
            }
            requestNotifPermission()
            requestIgnoreBattery()
            StreamService.start(this)
            Toast.makeText(this, "알림을 켰어요.", Toast.LENGTH_SHORT).show()
        }
        refreshToggle()
    }

    private fun pickSound(type: String) {
        pickingType = type
        val existing = Prefs.soundUri(this, type)?.let { Uri.parse(it) }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "${NotifHelper.typeLabel(type)} 소리 선택")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                existing ?: Settings.System.DEFAULT_NOTIFICATION_URI
            )
        }
        ringtonePicker.launch(intent)
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBattery() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        } catch (_: Exception) {
        }
    }
}
