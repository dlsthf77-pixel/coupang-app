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
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var toggleBtn: Button
    private lateinit var accountsContainer: LinearLayout
    private lateinit var etTopic: EditText

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
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        toggleBtn = findViewById(R.id.btnToggle)
        accountsContainer = findViewById(R.id.accountsContainer)
        etTopic = findViewById(R.id.etTopic)

        etTopic.setText(Prefs.topic(this))
        toggleBtn.setOnClickListener { toggle() }
        findViewById<Button>(R.id.btnAddAccount).setOnClickListener { addAccount() }
        findViewById<Button>(R.id.btnSaveTopic).setOnClickListener { saveTopic() }
        findViewById<Button>(R.id.btnSoundOrder).setOnClickListener { pickSound(Prefs.TYPE_ORDER) }
        findViewById<Button>(R.id.btnSoundInquiry).setOnClickListener { pickSound(Prefs.TYPE_INQUIRY) }
        findViewById<Button>(R.id.btnSoundReturn).setOnClickListener { pickSound(Prefs.TYPE_RETURN) }

        requestNotifPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val enabled = Prefs.isEnabled(this)
        val topic = Prefs.topic(this)
        toggleBtn.text = if (enabled) "알림 끄기" else "알림 받기 시작"
        tvStatus.text = when {
            topic.isBlank() -> "⚠ 서버 토픽을 먼저 입력하세요 (맨 아래)"
            enabled -> "🟢 알림 받는 중 · 토픽 $topic"
            else -> "⚪ 알림 꺼짐"
        }
        buildAccountRows()
    }

    private fun buildAccountRows() {
        accountsContainer.removeAllViews()
        val labels = Prefs.labels(this)
        if (labels.isEmpty()) {
            val tv = TextView(this).apply {
                text = "아직 등록된 계정이 없어요.\n아래 '＋ 계정 추가'로 계정을 넣으세요."
                setPadding(dp(4), dp(8), dp(4), dp(8))
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            }
            accountsContainer.addView(tv)
            return
        }
        for ((id, label) in labels.toSortedMap()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val open = Button(this).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { openWing(id) }
            }
            val edit = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                setBackgroundResource(0)
                contentDescription = "편집"
                setOnClickListener { renameAccount(id, label) }
            }
            row.addView(open)
            row.addView(edit)
            accountsContainer.addView(row)
        }
    }

    private fun openWing(id: Int) {
        startActivity(Intent(this, WingActivity::class.java).putExtra("accountId", id))
    }

    private fun addAccount() {
        val labels = Prefs.labels(this)
        var newId = -1
        for (i in 1..Prefs.MAX_ACCOUNTS) if (!labels.containsKey(i)) { newId = i; break }
        if (newId == -1) {
            Toast.makeText(this, "계정은 최대 ${Prefs.MAX_ACCOUNTS}개까지예요.", Toast.LENGTH_SHORT).show()
            return
        }
        promptLabel("계정 이름 (예: 1번-우리상점)", "") { text ->
            if (text.isNotBlank()) {
                Prefs.setLabel(this, newId, text)
                refresh()
            }
        }
    }

    private fun renameAccount(id: Int, current: String) {
        promptLabel("계정 이름 수정 (지우려면 비우고 저장)", current) { text ->
            Prefs.setLabel(this, id, text)
            refresh()
        }
    }

    private fun promptLabel(title: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            setSingleLine()
        }
        val pad = dp(20)
        val box = LinearLayout(this).apply {
            setPadding(pad, dp(8), pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("저장") { _, _ -> onOk(input.text.toString().trim()) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveTopic() {
        val t = etTopic.text.toString().trim()
        Prefs.setTopic(this, t)
        Toast.makeText(this, "토픽을 저장했어요.", Toast.LENGTH_SHORT).show()
        if (Prefs.isEnabled(this) && t.isNotBlank()) StreamService.start(this)
        refresh()
    }

    private fun toggle() {
        if (Prefs.isEnabled(this)) {
            StreamService.stop(this)
            Toast.makeText(this, "알림을 껐어요.", Toast.LENGTH_SHORT).show()
        } else {
            if (Prefs.topic(this).isBlank()) {
                Toast.makeText(this, "먼저 맨 아래 서버 토픽을 입력·저장하세요.", Toast.LENGTH_LONG).show()
                return
            }
            requestNotifPermission()
            requestIgnoreBattery()
            StreamService.start(this)
            Toast.makeText(this, "알림을 켰어요.", Toast.LENGTH_SHORT).show()
        }
        refresh()
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
            ) {
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
