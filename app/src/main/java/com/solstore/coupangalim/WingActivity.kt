package com.solstore.coupangalim

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * 계정별 쿠팡 윙. 계정마다 로그인 쿠키를 따로 저장/복원해서
 * 각 계정이 로그인된 상태로 유지된다. (한 번 로그인하면 다음부터 바로 접속)
 */
class WingActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var accountId: Int = 0

    private val hosts = listOf(
        "https://wing.coupang.com",
        "https://xauth.coupang.com",
        "https://www.coupang.com",
        "https://coupang.com"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wing)

        accountId = intent.getIntExtra("accountId", 0)
        val label = if (accountId > 0) Prefs.labelForId(this, accountId) else ""
        supportActionBar?.title = if (label.isNotBlank()) "$label · 쿠팡 윙" else "쿠팡 윙"

        web = findViewById(R.id.webview)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("; wv", "")
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)

        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()

        // 다른 계정 쿠키가 섞이지 않게 비운 뒤, 이 계정 쿠키 복원 후 로드
        if (accountId > 0) {
            cm.removeAllCookies {
                restoreCookies()
                loadWing()
            }
        } else {
            loadWing()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
    }

    private fun loadWing() {
        web.loadUrl("https://wing.coupang.com/")
    }

    private fun restoreCookies() {
        if (accountId <= 0) return
        val blob = Prefs.cookies(this, accountId)
        if (blob.isBlank()) return
        try {
            val o = JSONObject(blob)
            val cm = CookieManager.getInstance()
            for (host in hosts) {
                val cookieStr = o.optString(host, "")
                if (cookieStr.isBlank()) continue
                for (c in cookieStr.split("; ")) {
                    if (c.isNotBlank()) cm.setCookie("$host/", c)
                }
            }
            cm.flush()
        } catch (_: Exception) {
        }
    }

    private fun saveCookies() {
        if (accountId <= 0) return
        try {
            val cm = CookieManager.getInstance()
            cm.flush()
            val o = JSONObject()
            for (host in hosts) {
                val c = cm.getCookie("$host/")
                if (!c.isNullOrBlank()) o.put(host, c)
            }
            Prefs.setCookies(this, accountId, o.toString())
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        saveCookies()
    }
}
