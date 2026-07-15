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
 * 계정별 쿠팡 윙.
 *  - 같은 계정을 다시 열면 쿠키를 그대로 유지해 로그인 상태를 이어간다.
 *  - 다른 계정으로 바꿀 때만 저장/복원한다.
 *  - 저장된 아이디/비밀번호가 있으면 로그인 페이지에서 자동으로 채워 로그인한다.
 */
class WingActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var accountId: Int = 0
    private var autoLoginTried = false

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
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("; wv", "")
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tryAutoLogin()
            }
        }
        web.webChromeClient = WebChromeClient()

        val prev = Prefs.currentWing(this)
        if (accountId > 0 && prev != accountId) {
            if (prev > 0) saveCookiesFor(prev)
            cm.removeAllCookies {
                restoreCookies()
                Prefs.setCurrentWing(this, accountId)
                loadWing()
            }
        } else {
            if (accountId > 0) Prefs.setCurrentWing(this, accountId)
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

    /** 로그인 페이지면 저장된 아이디/비번을 채우고 로그인 버튼을 누른다. (계정당 1회만 시도) */
    private fun tryAutoLogin() {
        if (autoLoginTried || accountId <= 0) return
        if (!Creds.has(this, accountId)) return
        val uid = JSONObject.quote(Creds.id(this, accountId))
        val pw = JSONObject.quote(Creds.pw(this, accountId))
        val js = """
            (function(){
              try{
                var pwEl=document.querySelector('input[type=password]');
                if(!pwEl){return 'no-form';}
                function setVal(el,v){
                  try{
                    var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value');
                    if(d&&d.set){d.set.call(el,v);}else{el.value=v;}
                  }catch(e){el.value=v;}
                  el.dispatchEvent(new Event('input',{bubbles:true}));
                  el.dispatchEvent(new Event('change',{bubbles:true}));
                }
                var inputs=Array.prototype.slice.call(document.querySelectorAll('input'));
                var idEl=null;
                for(var i=0;i<inputs.length;i++){
                  if(inputs[i]===pwEl){break;}
                  var t=(inputs[i].type||'').toLowerCase();
                  if(t==='text'||t==='email'||t===''){idEl=inputs[i];}
                }
                if(idEl){setVal(idEl,$uid);}
                setVal(pwEl,$pw);
                var btn=null;
                var cands=Array.prototype.slice.call(document.querySelectorAll('button,input[type=submit],a'));
                for(var j=0;j<cands.length;j++){
                  var tx=(cands[j].innerText||cands[j].value||'').replace(/\s/g,'');
                  if(tx.indexOf('로그인')>=0){btn=cands[j];break;}
                }
                if(btn){btn.click();return 'submitted';}
                if(pwEl.form){pwEl.form.submit();return 'form-submit';}
                return 'filled';
              }catch(e){return 'err';}
            })();
        """.trimIndent()
        web.evaluateJavascript(js) { result ->
            val r = result ?: ""
            // 로그인 폼을 실제로 채웠을 때만 '시도함'으로 표시 (일반 페이지면 다음에 다시 시도)
            if (r.contains("submit") || r.contains("filled")) {
                autoLoginTried = true
            }
        }
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
                    if (c.isBlank()) continue
                    cm.setCookie("$host/", "$c; Domain=.coupang.com; Path=/")
                    cm.setCookie("$host/", c)
                }
            }
            cm.flush()
        } catch (_: Exception) {
        }
    }

    private fun saveCookiesFor(id: Int) {
        if (id <= 0) return
        try {
            val cm = CookieManager.getInstance()
            cm.flush()
            val o = JSONObject()
            for (host in hosts) {
                val c = cm.getCookie("$host/")
                if (!c.isNullOrBlank()) o.put(host, c)
            }
            Prefs.setCookies(this, id, o.toString())
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        if (accountId > 0) saveCookiesFor(accountId)
    }
}
