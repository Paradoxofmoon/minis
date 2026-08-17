package cn.edu.ubaa.ui.screens.cgyy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cn.edu.ubaa.api.feature.SportVenueApi
import cn.edu.ubaa.api.local.buildBuaaEduCnDomainCookies
import cn.edu.ubaa.ui.component.InAppWebView

/** cgyy 移动预约页按 UA 区分版本；用移动 Chrome UA 让 SPA 走移动端渲染（否则 WebView 空白）。 */
private const val mobileChromeUserAgent =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

/**
 * 体育场馆网页预约屏（方案 A1）。
 *
 * 先静默触发一次运动场(cgyy venue-server)登录，把 `sso_buaa_token` + cgyy 会话
 * cookie 种进 LocalCookieStore；再渲染 WebView 加载官方网页预约页并注入这些 cookie，
 * 让用户在网页内完成 时段选择 + 点选验证码 + 下单 + 同伴 + 支付。
 *
 * 不注入 cookie 直接打开会在手机上遇到「返回数据格式不正确 / 一直加载」——
 * 因为 cgyy 场馆数据接口要求已登录会话。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgyyWebViewReserveScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val reserveUrl = "https://cgyy.buaa.edu.cn/venue/mobileReservation"
  var preparing by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }

  // 先确保 cgyy 体育会话建立（登录会种 sso_buaa_token 等 cookie），再注入 WebView。
  var ssoCookies by remember { mutableStateOf(buildBuaaEduCnDomainCookies()) }

  LaunchedEffect(Unit) {
    runCatching { SportVenueApi().getVenueSites() }
        .onSuccess { ssoCookies = buildBuaaEduCnDomainCookies() }
        .onFailure { error = "预登录场馆系统失败：${it.message ?: "未知错误"}" }
    preparing = false
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("网页预约场馆") },
            navigationIcon = {
              IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        )
      },
      modifier = modifier,
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when {
        preparing -> {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
        error != null -> {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        else -> {
          InAppWebView(
              url = reserveUrl,
              modifier = Modifier.fillMaxSize(),
              domainCookies = ssoCookies,
              // cgyy 移动版 SPA 需移动 Chrome UA + viewport 适配才能正常渲染（否则空白）
              userAgentOverride = mobileChromeUserAgent,
              enableMobileViewport = true,
              onPageError = { /* 诊断日志，可扩展提示 */ },
          )
        }
      }
    }
  }
}
