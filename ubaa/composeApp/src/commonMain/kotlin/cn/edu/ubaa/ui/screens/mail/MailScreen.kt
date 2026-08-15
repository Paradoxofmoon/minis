package cn.edu.ubaa.ui.screens.mail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.component.InAppWebView

/** 北航 Coremail 邮箱收件箱入口 URL 兜底（WebView 展示，需注入 SSO+Coremail 会话 cookie）。 */
internal const val MAIL_URL = "https://mail.buaa.edu.cn/coremail/XT/index.jsp"

/**
 * 北航邮箱页：全屏 WebView 展示，复用 CAS 会话 cookie。
 *
 * 不包含自身 Scaffold/顶栏——顶栏由 MainAppScreen 的全局 AppTopBar 提供（统一风格、避免重复返回按钮）。
 * 刷新按钮放在右下角 FAB。带登录加载态。
 *
 * @param domainCookies 按 cookie 真实域名分组的注入数据（List of <注入URL, "name=value;...">）。
 * @param mailUrl WebView 加载的收件箱 URL（含 sid 参数）。
 * @param onRefresh 点击刷新后由上层重新触发 ensureSession 并更新 cookie
 */
@Composable
fun MailScreen(
    domainCookies: List<Pair<String, String>>,
    mailUrl: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var reloadKey by remember { mutableStateOf(0) }
  // Coremail 会话就绪 = 注入了 mail.buaa.edu.cn 域的 cookie（含 Coremail.sid）。
  // 仅当拿到 Coremail 会话 cookie 才加载收件箱，避免只有 SSO cookie 时 WebView 跳到登录页。
  val mailReady = domainCookies.any { it.first.startsWith("https://mail.buaa.edu.cn") }
  Box(modifier = modifier.fillMaxSize()) {
    if (mailReady) {
      // cookie 就绪后用 WebView 展示邮箱；刷新时通过 reloadKey 重建 WebView 重载
      key(reloadKey) {
        InAppWebView(
            url = mailUrl,
            modifier = Modifier.fillMaxSize(),
            domainCookies = domainCookies.filter { it.second.isNotBlank() },
        )
      }
    } else if (loading) {
      // 登录中
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Spacer(Modifier.height(12.dp))
          Text("正在登录邮箱...", style = MaterialTheme.typography.bodyMedium)
        }
      }
    } else {
      // cookie 空且未在加载：示意用户下拉/点刷新重试
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("邮箱登录未完成", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Text("请稍后点击右下角刷新重试", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }

    // 右下角刷新浮层按钮
    if (mailReady) {
      FloatingActionButton(
          onClick = {
            reloadKey++
            onRefresh()
          },
          modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
      ) {
        Icon(Icons.Default.Refresh, contentDescription = "刷新")
      }
    }
  }
}

