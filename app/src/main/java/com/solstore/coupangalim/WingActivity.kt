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
 *  - 같은 계정을 다시 열면 쿠키를 그대로 둬서 로그인 상태를 유지한다. (WebView 쿠키는 앱을 꺼도 유지)
 *  - 다른 계정으로 바꿀 때는 쿠키를 깨끗이 비우고, 저장된 아이디/비번으로 자동 로그인한다.
 *
 * (예전의 계정별 쿠키 저장/복원 방식은 쿠키가 중복 누적돼 차단되는 문제가 있어 제거함)
 */
class WingActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var accountId: Int = 0
    private var autoLoginTried = false

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
            // 다른 계정으로 전환 → 쿠키 깨끗이 비우고 새로 로그인 (자동 로그인 정보 있으면 자동)
            cm.removeAllCookies {
                cm.flush()
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
            if (r.contains("submit") || r.contains("filled")) {
                autoLoginTried = true
            }
        }
    }
}
