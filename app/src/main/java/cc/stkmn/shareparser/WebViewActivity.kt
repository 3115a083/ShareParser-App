package cc.stkmn.shareparser

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
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
        if (!isAllowedWebUri(runCatching { Uri.parse(url) }.getOrNull())) {
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
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val target = request?.url
                        if (isAllowedWebUri(target)) return false
                        safeToast("Diese Weiterleitung wurde aus Sicherheitsgründen blockiert.")
                        return true
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val target = runCatching { Uri.parse(url.orEmpty()) }.getOrNull()
                        if (isAllowedWebUri(target)) return false
                        safeToast("Diese Weiterleitung wurde aus Sicherheitsgründen blockiert.")
                        return true
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
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

    private fun isAllowedWebUri(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase()
        return scheme in setOf("http", "https") && !uri?.host.isNullOrBlank() && uri?.userInfo.isNullOrBlank()
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
