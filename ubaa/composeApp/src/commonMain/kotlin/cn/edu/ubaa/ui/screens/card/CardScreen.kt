package cn.edu.ubaa.ui.screens.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.ubaa.api.feature.CardPayWay
import cn.edu.ubaa.ui.component.InAppWebView

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CardScreen(
    uiState: CardUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadPayWays: () -> Unit,
    onAmountChange: (String) -> Unit,
    onBeginRecharge: (String) -> Unit,
    onClearPayScheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val payScheme = uiState.payScheme
  // 用户点"确认支付"后拿到支付 scheme(weixin:// / alipays://)，
  // 用一个不可见的 WebView 加载一段 HTML，由浏览器内核触发 location.href 跳 scheme，
  // 从而按"浏览器来源"可靠唤起支付 App（App 直接 Intent 唤起微信不可靠）。
  if (payScheme != null) {
    SchemeTriggerWebView(
        scheme = payScheme,
        modifier = Modifier.size(1.dp),
        onConsumed = onClearPayScheme,
    )
  }

  val pullRefreshState = rememberPullRefreshState(refreshing = uiState.isRefreshing, onRefresh = onRefresh)

  Box(modifier = modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item { Spacer(modifier = Modifier.height(16.dp)) }

      when {
        uiState.isLoading -> {
          item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          }
        }
        else -> {
          item {
            BalanceCard(
                title = "卡余额",
                amount = uiState.balance,
                icon = Icons.Default.AccountBalanceWallet,
            )
          }

          if (uiState.error != null) {
            item {
              Card(
                  modifier = Modifier.fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
              ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(uiState.error, color = MaterialTheme.colorScheme.onErrorContainer)
                  if (uiState.balance.isBlank()) {
                    Button(onClick = onRetry) { Text("重试") }
                  }
                }
              }
            }
          }

          item { Spacer(modifier = Modifier.height(8.dp)) }

          item {
            RechargeSection(
                uiState = uiState,
                onLoadPayWays = onLoadPayWays,
                onAmountChange = onAmountChange,
                onBeginRecharge = onBeginRecharge,
            )
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    PullRefreshIndicator(
        refreshing = uiState.isRefreshing,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter),
    )
  }
}

/** 用不可见 WebView 的浏览器环境触发自定义支付 scheme 唤起支付 App。 */
@Composable
private fun SchemeTriggerWebView(
    scheme: String,
    modifier: Modifier,
    onConsumed: () -> Unit,
) {
  val html =
      "<!DOCTYPE html><html><body style='margin:0;background:#fff' " +
          "onload=\"window.location.href='${htmlEscape(scheme)}'\"></body></html>"
  InAppWebView(
      url = "https://cashier.cc-pay.cn/cashier",
      modifier = modifier,
      htmlContent = html,
  )
  LaunchedEffect(scheme) {
    // 待 WebView 触发 scheme 后，短暂延迟清理触发态，避免重复
    kotlinx.coroutines.delay(1500)
    onConsumed()
  }
}

/** 将字符串转义为可安全放入 HTML 属性(单引号字符串)的形式。 */
private fun htmlEscape(s: String): String =
    s.replace("&", "&amp;").replace("'", "&#39;").replace("\"", "&quot;")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RechargeSection(
    uiState: CardUiState,
    onLoadPayWays: () -> Unit,
    onAmountChange: (String) -> Unit,
    onBeginRecharge: (String) -> Unit,
) {
  var selectedPayWay by remember { mutableStateOf<String?>(null) }

  Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.AddCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("校园卡充值", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }

      OutlinedTextField(
          value = uiState.amount,
          onValueChange = onAmountChange,
          label = { Text("充值金额（元）") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
      )

      Text("充值金额需在 1~90000 元之间（开放时段 04:00~23:00）",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)

      // 支付方式
      if (uiState.isLoadingPayWays) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          Text("加载支付方式...", style = MaterialTheme.typography.bodySmall)
        }
      } else if (uiState.payWays.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("选择支付方式", style = MaterialTheme.typography.bodySmall)
          TextButton(onClick = onLoadPayWays) { Text("加载") }
        }
      } else {
        Text("选择支付方式", style = MaterialTheme.typography.titleSmall)
        FlowRowForPayWays(
            payWays = uiState.payWays,
            selectedPayWay = selectedPayWay,
            onSelect = { selectedPayWay = it },
        )
      }

      Button(
          onClick = {
            val way = selectedPayWay ?: return@Button
            onBeginRecharge(way)
          },
          enabled = uiState.amount.isNotBlank() && selectedPayWay != null && !uiState.isRecharging,
          modifier = Modifier.fillMaxWidth().height(48.dp),
      ) {
        if (uiState.isRecharging) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Text("确认充值")
        }
      }
    }
  }
}

@Composable
private fun FlowRowForPayWays(
    payWays: List<CardPayWay>,
    selectedPayWay: String?,
    onSelect: (String) -> Unit,
) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    payWays.forEach { way ->
      val selected = way.id == selectedPayWay
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .clip(MaterialTheme.shapes.small)
              .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
              .clickable { onSelect(way.id) }
              .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(way.text.ifBlank { way.name }, style = MaterialTheme.typography.bodyLarge)
        if (selected) {
          Icon(Icons.Default.CheckCircle, contentDescription = "已选择",
              tint = MaterialTheme.colorScheme.primary)
        }
      }
    }
  }
}

@Composable
private fun BalanceCard(
    title: String,
    amount: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      shape = MaterialTheme.shapes.medium,
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
      }
      Text(
          text = amount,
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
