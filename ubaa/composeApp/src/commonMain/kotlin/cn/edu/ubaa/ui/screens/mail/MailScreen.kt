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

/** 北航邮箱 WAP 入口（能触发 CAS 认证，WebView 加载后自动跳转到 Coremail 收件箱）。 */
internal const val MAIL_URL = "https://it.buaa.edu.cn/frontend/mail/login"

/**
 * 北航邮箱页：全屏 WebView 展示，复用 CAS 会话 cookie。
 *
 * WebView 加载 WAP 入口(MAIL_URL)，已注入 SSO/.buaa/it 各域会话 cookie，
 * 由 WebView 自己走 CAS 认证链（SSO 已登录则自动放行）最终落到 Coremail 收件箱。
 *
 * 不包含自身 Scaffold/顶栏——顶栏由 MainAppScreen 的全局 AppTopBar 提供。
 * 刷新按钮在右下角 FAB。带登录加载态。
 *
 * @param domainCookies 按 cookie 真实域名分组的注入数据（List of <注入URL, "name=value;...">）。
 * @param diagnostic Coremail 会话探测报告（阶段0诊断，显示在页面底部）。
 * @param onRefresh 点击刷新后由上层重新触发 ensureSession 并更新 cookie
 */
@Composable
fun MailScreen(
    domainCookies: List<Pair<String, String>>,
    loading: Boolean,
    diagnostic: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var reloadKey by remember { mutableStateOf(0) }
  // SSO 会话就绪 = 有任一 buaa 相关域的会话 cookie 可注入；有则让 WebView 走完整认证链。
  val ready = domainCookies.isNotEmpty()
  Box(modifier = modifier.fillMaxSize()) {
    if (ready) {
      // 用 reloadKey 区分 WebView 实例，刷新时重建重载
      key(reloadKey) {
        InAppWebView(
            url = MAIL_URL,
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

    // 阶段0诊断：Coremail 会话探测报告（临时显示，便于验证 Ktor 能否换到 sid）
    if (diagnostic.isNotBlank()) {
      Surface(
          modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
          tonalElevation = 2.dp,
          shape = MaterialTheme.shapes.small,
      ) {
        Column(
            modifier = Modifier.padding(8.dp).widthIn(max = 320.dp),
        ) {
          Text("诊断(探测)", style = MaterialTheme.typography.labelSmall)
          Spacer(Modifier.height(4.dp))
          Text(
              diagnostic,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 40,
          )
        }
      }
    }

    // 右下角刷新浮层按钮
    if (ready) {
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

