package cn.edu.ubaa.ui.screens.mail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.api.local.CoremailMessage
import cn.edu.ubaa.api.local.MailRepository

/**
 * 北航邮箱页：原生收件箱邮件列表。
 *
 * 不再使用 WebView（北航 Vue/Angular SPA 在 WebView 渲染空白），改为 Ktor 调 Coremail JSON 接口、
 * 用 Compose 原生列表展示。顶栏由全局 AppTopBar 提供；刷新按钮在右下角 FAB。
 *
 * @param messages 邮件列表。
 * @param loading 加载中。
 * @param isLoadingMore 是否正在加载下一页。
 * @param hasMore 是否还有更多邮件未加载。
 * @param error 错误信息（非空时展示）。
 * @param onRefresh 点击刷新触发上层重新拉取。
 * @param onLoadMore 滚动到底/点击"加载更多"触发。
 */
@Composable
fun MailScreen(
    messages: List<CoremailMessage>,
    loading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var selected by remember { mutableStateOf<CoremailMessage?>(null) }

  Box(modifier = modifier.fillMaxSize()) {
    if (loading && messages.isEmpty()) {
      // 首次加载
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Spacer(Modifier.height(12.dp))
          Text("正在加载邮件...", style = MaterialTheme.typography.bodyMedium)
        }
      }
    } else if (messages.isEmpty() && error == null) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("收件箱为空", style = MaterialTheme.typography.bodyLarge)
      }
    } else if (messages.isEmpty() && error != null) {
      // 加载失败
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("邮箱加载失败", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Text(error, style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(16.dp))
          Button(onClick = onRefresh) { Text("重试") }
        }
      }
    } else {
      // 邮件列表
      LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 72.dp),
      ) {
        items(messages, key = { it.id }) { msg ->
          MailListItem(msg, onClick = { selected = msg })
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        // 底部：加载更多 / 没有更多了
        item(key = "__footer") {
          Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            when {
              isLoadingMore -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("加载中...", style = MaterialTheme.typography.bodySmall)
              }
              hasMore -> {
                TextButton(onClick = onLoadMore) { Text("加载更多") }
              }
              else -> {
                Text("已显示全部邮件", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }

    // 右下角刷新 FAB（列表/空/错都显示）
    FloatingActionButton(
        onClick = onRefresh,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
      if (loading && messages.isNotEmpty()) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      } else {
        Icon(Icons.Default.Refresh, contentDescription = "刷新")
      }
    }
  }

  // 读信预览对话框
  selected?.let { msg ->
    AlertDialog(
        onDismissRequest = { selected = null },
        title = { Text(msg.subject, maxLines = 3) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "发件人：${MailRepository.displayFrom(msg.from)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "时间：${MailRepository.displayTime(msg.sentDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (msg.to.isNotBlank()) {
              Text(
                  "收件人：${msg.to}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
              )
            }
            HorizontalDivider()
            Text(
                msg.summary.ifBlank { "（无内容预览）" },
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        },
        confirmButton = {
          TextButton(onClick = { selected = null }) { Text("关闭") }
        },
    )
  }
}

/** 单条邮件列表项。 */
@Composable
private fun MailListItem(msg: CoremailMessage, onClick: () -> Unit) {
  Column(
      modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onClick)
          .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          MailRepository.displayFrom(msg.from),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      Text(
          MailRepository.displayTime(msg.sentDate),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.height(3.dp))
    Text(
        msg.subject,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (msg.summary.isNotBlank()) {
      Spacer(Modifier.height(2.dp))
      Text(
          msg.summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
