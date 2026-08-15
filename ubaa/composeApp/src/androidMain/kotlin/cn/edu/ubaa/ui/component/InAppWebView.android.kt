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
    domainCookies: List<Pair<String, String>>,
    onSchemeUrl: ((String) -> Boolean)?,
    onPageError: ((String) -> Unit)?,
    htmlContent: String?,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // 关键：允许第三方 Cookie(跨域请求带 cookie)，否则收银台 socket(rmc.cc-pay.cn)
                // 等跨域请求无会话，被拒为 CORS/network error，导致 Angular 初始化/渲染不完整。
                runCatching {
                  android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }
                // 不覆盖 UA：让收银台页按真实 Android WebView 环境正常渲染（避免支付方式图标/文字空白）。
                // 微信支付唤起依赖网页 JS location.href 触发 weixin://，与 UA 无关。

                fun applyCookies() {
                  if (cookies.isNotEmpty() || domainCookies.isNotEmpty()) {
                    runCatching {
                      val cookieManager = CookieManager.getInstance()
                      if (cookieManager != null) {
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        // 按真实域名注入（关键：CASTGC 需独立注入 sso.buaa.edu.cn，否则跨域无会话）
                        domainCookies.forEach { (domainUrl, cookieStr) ->
                          if (domainUrl.isNotBlank() && cookieStr.isNotBlank()) {
                            cookieManager.setCookie(domainUrl, cookieStr)
                          }
                        }
                        cookies.forEach { cookie -> cookieManager.setCookie(url, cookie) }
                        cookieManager.flush()
                      }
                    }
                  }
                }
                applyCookies()

                webChromeClient =
                    object : android.webkit.WebChromeClient() {
                      override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        val msg = "${message.message()} (${message.lineNumber()})"
                        if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR ||
                            msg.contains("PAYDEBUG") || msg.contains("error") || msg.contains("Error") || msg.contains("undefined")) {
                          onPageError?.invoke(msg.take(300))
                        }
                        return super.onConsoleMessage(message)
                      }
                      override fun onJsAlert(
                          view: WebView?,
                          url: String?,
                          message: String?,
                          result: android.webkit.JsResult?,
                      ): Boolean {
                        message?.let { onPageError?.invoke("JS:${it.take(200)}") }
                        result?.confirm()
                        return true
                      }
                    }

                webViewClient =
                    object : WebViewClient() {
                      override fun onReceivedError(
                          view: WebView,
                          request: WebResourceRequest,
                          error: android.webkit.WebResourceError,
                      ) {
                        if (request.url.toString().contains("bundle.js") ||
                            request.url.toString().contains(".js")) {
                          onPageError?.invoke("加载JS失败: ${error.errorCode} ${error.description} ${request.url}")
                        }
                      }

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
                if (!htmlContent.isNullOrBlank()) {
                  // 提供 htmlContent 时，仅加载 HTML（用于触发自定义 scheme），不加载 url
                  loadDataWithBaseURL(url, htmlContent, "text/html; charset=utf-8", "UTF-8", null)
                } else {
                  loadUrl(url)
                }
            }
        },
        modifier = modifier,
        update = { view ->
          if (!htmlContent.isNullOrBlank()) {
            view.loadDataWithBaseURL(url, htmlContent, "text/html; charset=utf-8", "UTF-8", null)
          } else {
            view.loadUrl(url)
          }
        },
    )
}
