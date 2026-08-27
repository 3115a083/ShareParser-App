package cc.stkmn.shareparser

import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback

class WebViewActivity : ComponentActivity() {
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri?.scheme !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
            finish()
            return
        }

        val view = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setGeolocationEnabled(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.safeBrowsingEnabled = true
            webViewClient = WebViewClient()
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
        webView = view
        setContentView(view)
        view.loadUrl(url)

        onBackPressedDispatcher.addCallback(this) {
            val current = webView
            if (current?.canGoBack() == true) current.goBack() else finish()
        }
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
