package cc.stkmn.shareparser

import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback

class WebViewActivity : ComponentActivity() {
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri?.scheme !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
            Toast.makeText(this, "Die Web-Adresse ist ungültig.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val view = try {
            WebView(this).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setGeolocationEnabled(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.safeBrowsingEnabled = true
                webViewClient = WebViewClient()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Die In-App-Webansicht ist auf diesem Gerät nicht verfügbar. Bitte nutze den Browser-Modus.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(view, false) }
        webView = view
        setContentView(view)
        runCatching { view.loadUrl(url) }.onFailure {
            Toast.makeText(this, "Die Seite konnte nicht geladen werden.", Toast.LENGTH_LONG).show()
            finish()
        }

        onBackPressedDispatcher.addCallback(this) {
            val current = webView
            if (current?.canGoBack() == true) current.goBack() else finish()
        }
    }

    override fun onDestroy() {
        webView?.let { view ->
            runCatching { view.stopLoading() }
            runCatching { view.loadUrl("about:blank") }
            runCatching { view.clearHistory() }
            runCatching { view.removeAllViews() }
            runCatching { view.destroy() }
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
