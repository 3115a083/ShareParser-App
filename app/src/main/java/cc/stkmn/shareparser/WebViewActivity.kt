package cc.stkmn.shareparser

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
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
            safeToast("Die Web-Adresse ist ungültig.")
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
                webViewClient = object : WebViewClient() {
                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                        // If the Chromium renderer dies and this callback returns false,
                        // Android terminates the app process. Samsung devices can surface
                        // this as "app closed because it has a bug". Clean up and close
                        // only the in-app browser instead.
                        runCatching { (view?.parent as? ViewGroup)?.removeView(view) }
                        runCatching { view?.destroy() }
                        if (webView === view) webView = null
                        safeToast("Die In-App-Webansicht wurde beendet. Bitte öffne den Link im Browser-Modus.")
                        finish()
                        return true
                    }
                }
            }
        } catch (_: Throwable) {
            safeToast("Die In-App-Webansicht ist auf diesem Gerät nicht verfügbar. Bitte nutze den Browser-Modus.")
            finish()
            return
        }

        runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(view, false) }
        webView = view
        setContentView(view)
        runCatching { view.loadUrl(url) }.onFailure {
            safeToast("Die Seite konnte nicht geladen werden.")
            finish()
        }

        onBackPressedDispatcher.addCallback(this) {
            val current = webView
            if (current?.canGoBack() == true) current.goBack() else finish()
        }
    }

    private fun safeToast(message: String) {
        runCatching { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroy() {
        webView?.let { view ->
            runCatching { view.stopLoading() }
            runCatching { view.loadUrl("about:blank") }
            runCatching { view.clearHistory() }
            runCatching { (view.parent as? ViewGroup)?.removeView(view) }
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
