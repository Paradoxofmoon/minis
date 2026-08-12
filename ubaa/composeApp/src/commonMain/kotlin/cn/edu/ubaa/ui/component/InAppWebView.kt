package cn.edu.ubaa.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 跨平台应用内 WebView。
 * Android: 原生 WebView，桌面/iOS: 打开系统浏览器。
 *
 * @param url 要加载的页面地址。
 * @param injectJsOnLoad 页面加载完成后注入执行的 JavaScript（如自动填充表单）。仅 Android 生效。
 * @param cookies 注入的 Cookie，格式 "name=value"。仅 Android 生效。
 */
@Composable
expect fun InAppWebView(
    url: String,
    modifier: Modifier = Modifier,
    injectJsOnLoad: String? = null,
    cookies: List<String> = emptyList(),
)
