package cn.edu.ubaa.ui.screens.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.component.InAppWebView

/** 北航邮箱入口 URL（WebView 展示）。 */
internal const val MAIL_URL = "https://it.buaa.edu.cn/frontend/mail/login"

/**
 * 北航邮箱页：全屏 WebView 展示，复用 CAS 会话 cookie。带返回与刷新。
 * @param mailCookie 由上层(MailViewModel)触发 CAS 登录后提取的 cookie
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(
    mailCookie: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var reloadKey by remember { mutableStateOf(0) }
  Scaffold(
      modifier = modifier.fillMaxSize(),
      topBar = {
        TopAppBar(
            title = { Text("北航邮箱") },
            navigationIcon = {
              IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            },
            actions = {
              IconButton(onClick = { reloadKey++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
              }
            },
        )
      },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      // cookie 就绪后用 WebView 展示邮箱；刷新时通过 reloadKey 重建 WebView 重载
      if (mailCookie.isNotBlank()) {
        key(reloadKey) {
          InAppWebView(
              url = MAIL_URL,
              modifier = Modifier.fillMaxSize(),
              cookies = mailCookie.split("; ").filter { it.trim().isNotEmpty() },
          )
        }
      } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("正在登录邮箱...", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }
  }
}
