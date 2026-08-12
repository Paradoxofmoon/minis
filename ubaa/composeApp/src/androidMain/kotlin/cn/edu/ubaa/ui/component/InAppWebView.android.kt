package cn.edu.ubaa.ui.component

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun InAppWebView(
    url: String,
    modifier: Modifier,
    injectJsOnLoad: String?,
    cookies: List<String>,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                // 先创建 WebView，再尝试注入 Cookie（部分厂商 WebView 实现下
                // CookieManager.getInstance() 在 WebView 创建前可能返回 null）
                if (cookies.isNotEmpty()) {
                  runCatching {
                    val cookieManager = CookieManager.getInstance()
                    if (cookieManager != null) {
                      cookieManager.setAcceptCookie(true)
                      cookies.forEach { cookie -> cookieManager.setCookie(url, cookie) }
                      cookieManager.flush()
                    }
                  }
                }

                webViewClient =
                    object : WebViewClient() {
                      override fun onPageFinished(view: WebView, loadedUrl: String) {
                        super.onPageFinished(view, loadedUrl)
                        injectJsOnLoad?.takeIf { it.isNotBlank() }?.let { js ->
                          view.evaluateJavascript(js, null)
                        }
                      }
                    }
                loadUrl(url)
            }
        },
        modifier = modifier,
        update = { it.loadUrl(url) },
    )
}
