package cn.edu.ubaa.ui.component

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
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
    onSchemeUrl: ((String) -> Boolean)?,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // 不覆盖 UA：让收银台页按真实 Android WebView 环境正常渲染（避免支付方式图标/文字空白）。
                // 微信支付唤起依赖网页 JS location.href 触发 weixin://，与 UA 无关。

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
                      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        val isHttp = target.startsWith("http://") || target.startsWith("https://")
                        if (!isHttp) {
                          // 截获 weixin:// / alipays:// 等自定义 scheme，交给系统 Intent 唤起支付 App
                          val handled = onSchemeUrl?.invoke(target) ?: false
                          if (!handled) {
                            runCatching {
                              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                              context.startActivity(intent)
                            }
                          }
                          return true
                        }
                        return super.shouldOverrideUrlLoading(view, request)
                      }

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
