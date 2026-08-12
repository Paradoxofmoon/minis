package cn.edu.ubaa.ui.screens.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CardScreen(
    uiState: CardUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
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
        uiState.error != null -> {
          item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
              Column(
                  modifier = Modifier.fillMaxWidth().padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text(
                    text = "余额加载失败",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Button(onClick = onRetry) { Text("重试") }
              }
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

@Composable
private fun BalanceCard(
    title: String,
    amount: String,
    icon: ImageVector,
    isSecondary: Boolean = false,
    modifier: Modifier = Modifier,
) {
  val amountTextSize = if (isSecondary) 24.sp else 32.sp
  val containerColor =
      if (isSecondary) {
        MaterialTheme.colorScheme.surfaceVariant
      } else {
        MaterialTheme.colorScheme.surface
      }
  val contentColor =
      if (isSecondary) {
        MaterialTheme.colorScheme.onSurfaceVariant
      } else {
        MaterialTheme.colorScheme.onSurface
      }

  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
      elevation = CardDefaults.cardElevation(defaultElevation = if (isSecondary) 1.dp else 2.dp),
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
          style = MaterialTheme.typography.headlineLarge.copy(fontSize = amountTextSize),
          fontWeight = FontWeight.Bold,
          color = if (isSecondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
