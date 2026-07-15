package com.solstore.coupangalim

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvTotal: TextView
    private lateinit var tvStatus: TextView
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvTotal = findViewById(R.id.tvTotal)
        tvStatus = findViewById(R.id.tvStatus)
        container = findViewById(R.id.accountsContainer)

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnAddAccount).setOnClickListener { addAccountDialog() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshNow() }
    }

    override fun onResume() {
        super.onResume()
        // 앱을 열 때, 알림이 켜져 있으면 실시간 수신 서비스가 살아있도록 보장
        if (Prefs.isEnabled(this) && Prefs.topic(this).isNotBlank()) {
            try { StreamService.start(this) } catch (_: Exception) {}
        }
        Prefs.cachedStatus(this)?.let { render(it) }
        updateStatusLine()
        fetchStatus(false)
    }

    private fun updateStatusLine() {
        tvStatus.text = when {
            Prefs.serverUrl(this).isBlank() || Prefs.apiSecret(this).isBlank() ->
                "⚠ ⚙설정에서 서버 주소·보안 키를 입력하세요"
            Prefs.isEnabled(this) -> "🟢 알림 받는 중"
            else -> "⚪ 알림 꺼짐 (⚙설정에서 시작)"
        }
    }

    private fun refreshNow() {
        if (Prefs.serverUrl(this).isBlank() || Prefs.apiSecret(this).isBlank()) {
            Toast.makeText(this, "먼저 ⚙설정에서 서버 주소·보안 키를 입력하세요.", Toast.LENGTH_LONG).show()
            return
        }
        tvStatus.text = "🔄 지금 조회 중... (최대 30초)"
        thread {
            val json = Api.pollNow(applicationContext)
            runOnUiThread {
                if (json != null) render(json)
                else Toast.makeText(this, "조회 실패 (⚙설정 확인)", Toast.LENGTH_LONG).show()
                updateStatusLine()
            }
        }
    }

    private fun fetchStatus(toast: Boolean) {
        if (Prefs.serverUrl(this).isBlank() || Prefs.apiSecret(this).isBlank()) return
        if (toast) Toast.makeText(this, "새로고침 중...", Toast.LENGTH_SHORT).show()
        thread {
            val json = Api.getStatus(applicationContext)
            runOnUiThread {
                if (json != null) render(json)
                else if (toast) Toast.makeText(this, "서버 연결 실패 (⚙설정 확인)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun render(json: String) {
        container.removeAllViews()
        var totalOrders = 0
        var totalAmount = 0L
        try {
            val arr = JSONObject(json).optJSONArray("accounts")
            if (arr == null || arr.length() == 0) {
                container.addView(hint("아직 등록된 계정이 없어요.\n'＋ 계정 추가'로 넣으세요."))
            } else {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    totalOrders += o.optInt("orders")
                    totalAmount += o.optLong("amount")
                    container.addView(card(o))
                }
                container.addView(hint("계정을 누르면 쿠팡 윙 · 길게 누르면 로그인정보·삭제").apply {
                    textSize = 12f
                })
            }
        } catch (_: Exception) {
            container.addView(hint("상태를 불러오지 못했어요."))
        }
        tvTotal.text = "${totalOrders}건 · ${won(totalAmount)}"
    }

    private fun hint(text: String): TextView = TextView(this).apply {
        this.text = text
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
    }

    private fun card(o: JSONObject): View {
        val id = o.optInt("id")
        val label = o.optString("label")
        val err = if (o.isNull("error")) null else o.optString("error").ifBlank { null }

        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.color.card)
            elevation = dp(1).toFloat()
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dp(12), dp(8), dp(12), 0)
            layoutParams = lp
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            setOnClickListener { openWing(id) }
            setOnLongClickListener { cardMenu(id, label); true }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = label
            setTypeface(typeface, Typeface.BOLD)
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(this).apply {
            text = "${o.optInt("orders")}건 · ${won(o.optLong("amount"))}"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.coupang_red))
        })
        cardView.addView(top)

        if (err != null) {
            cardView.addView(TextView(this).apply {
                text = "⚠ $err"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding(0, dp(6), 0, 0)
            })
        } else {
            val stats = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
            stats.addView(statCol("신규주문", o.optInt("newOrders")))
            stats.addView(statCol("배송전", o.optInt("toShip")))
            stats.addView(statCol("문의", o.optInt("inquiries")))
            stats.addView(statCol("취소", o.optInt("cancels")))
            cardView.addView(stats)
        }
        return cardView
    }

    private fun statCol(label: String, value: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addView(TextView(this@MainActivity).apply {
            text = value.toString()
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (value > 0) R.color.text_primary else R.color.text_secondary
                )
            )
        })
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
        })
    }

    private fun openWing(id: Int) {
        startActivity(Intent(this, WingActivity::class.java).putExtra("accountId", id))
    }

    private fun cardMenu(id: Int, label: String) {
        val hasCreds = Creds.has(this, id)
        val loginItem = if (hasCreds) "윙 로그인 정보 수정" else "윙 로그인 정보 입력 (자동 로그인)"
        val items = arrayOf(loginItem, "계정 삭제")
        AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> credsDialog(id, label)
                    1 -> confirmDelete(id, label)
                }
            }
            .show()
    }

    private fun credsDialog(id: Int, label: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val info = TextView(this).apply {
            text = "이 폰에만 저장되고 서버로 전송되지 않아요.\n윙을 열 때 자동으로 로그인합니다."
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(0, 0, 0, dp(8))
        }
        val etId = field("쿠팡 윙 아이디").apply { setText(Creds.id(this@MainActivity, id)) }
        val etPw = EditText(this).apply {
            hint = "쿠팡 윙 비밀번호"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Creds.pw(this@MainActivity, id))
        }
        box.addView(info); box.addView(etId); box.addView(etPw)

        AlertDialog.Builder(this)
            .setTitle("$label · 윙 로그인 정보")
            .setView(box)
            .setPositiveButton("저장") { _, _ ->
                val uid = etId.text.toString().trim()
                val upw = etPw.text.toString()
                if (uid.isBlank() || upw.isBlank()) {
                    Toast.makeText(this, "아이디와 비밀번호를 모두 입력하세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Creds.set(this, id, uid, upw)
                Toast.makeText(this, "저장했어요. 이제 자동 로그인됩니다.", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("저장 삭제") { _, _ ->
                Creds.clear(this, id)
                Toast.makeText(this, "로그인 정보를 지웠어요.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirmDelete(id: Int, label: String) {
        AlertDialog.Builder(this)
            .setTitle("계정 삭제")
            .setMessage("'$label' 계정을 삭제할까요?\n(서버에서 등록 정보가 지워집니다.)")
            .setPositiveButton("삭제") { _, _ ->
                Toast.makeText(this, "삭제 중...", Toast.LENGTH_SHORT).show()
                thread {
                    val (ok, msg) = Api.deleteAccount(applicationContext, id)
                    runOnUiThread {
                        if (ok) Creds.clear(this, id)
                        Toast.makeText(this, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                        if (ok) fetchStatus(false)
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addAccountDialog() {
        if (Prefs.serverUrl(this).isBlank() || Prefs.apiSecret(this).isBlank()) {
            Toast.makeText(this, "먼저 ⚙설정에서 서버 주소·보안 키를 입력하세요.", Toast.LENGTH_LONG).show()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val etLabel = field("계정 이름 (예: 퍼페드그린)")
        val etVid = field("업체코드 (Vendor ID, 예: A01600263)")
        val etAk = field("Access Key")
        val etSk = field("Secret Key")
        listOf(etLabel, etVid, etAk, etSk).forEach { box.addView(it) }

        AlertDialog.Builder(this)
            .setTitle("계정 추가 (서버에 등록)")
            .setView(box)
            .setPositiveButton("등록") { _, _ ->
                val label = etLabel.text.toString().trim()
                val vid = etVid.text.toString().trim()
                val ak = etAk.text.toString().trim()
                val sk = etSk.text.toString().trim()
                if (label.isBlank() || vid.isBlank() || ak.isBlank() || sk.isBlank()) {
                    Toast.makeText(this, "4가지를 모두 입력하세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Toast.makeText(this, "등록 중...", Toast.LENGTH_SHORT).show()
                thread {
                    val (ok, msg) = Api.addAccount(applicationContext, label, vid, ak, sk)
                    runOnUiThread {
                        Toast.makeText(this, msg, if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                        if (ok) fetchStatus(false)
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun field(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun won(v: Long): String =
        "₩" + NumberFormat.getNumberInstance(Locale.KOREA).format(v)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
