package cn.edu.ubaa.ui.screens.card

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import cn.edu.ubaa.api.local.buildCcpayCookieHeader
import cn.edu.ubaa.ui.component.InAppWebView

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CardScreen(
    uiState: CardUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onAmountChange: (String) -> Unit,
    onBeginRecharge: () -> Unit,
    onOpenPay: (String) -> Unit,
    onClearPayUrl: () -> Unit,
    onClearCashier: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

          item { RechargeSection(uiState, onAmountChange, onBeginRecharge) }
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

  // 支付跳转确认对话框
  uiState.payUrl?.let { url ->
    AlertDialog(
        onDismissRequest = onClearPayUrl,
        title = { Text("去支付") },
        text = { Text("将拉起支付应用完成付款。") },
        confirmButton = {
          TextButton(
              onClick = {
                onOpenPay(url)
                onClearPayUrl()
              }
          ) {
            Text("打开支付")
          }
        },
        dismissButton = { TextButton(onClick = onClearPayUrl) { Text("取消") } },
    )
  }

  // 方案A：收银台网页覆盖层（在网页里点微信支付，由 WebView 浏览器环境唤起微信）
  uiState.cashierUrl?.let { cashierUrl ->
    Dialog(onDismissRequest = onClearCashier, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        InAppWebView(
            url = cashierUrl,
            modifier = Modifier.fillMaxSize(),
            cookies = buildCcpayCookieHeader().split("; ").filter { it.trim().isNotEmpty() },
        )
        IconButton(
            onClick = onClearCashier,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
        ) {
          Icon(Icons.Default.Close, contentDescription = "关闭支付")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RechargeSection(
    uiState: CardUiState,
    onAmountChange: (String) -> Unit,
    onBeginRecharge: () -> Unit,
) {
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

      Text("支付方式将在收银台页面中选择", style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)

      Button(
          onClick = onBeginRecharge,
          enabled = uiState.amount.isNotBlank() && !uiState.isRecharging,
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
