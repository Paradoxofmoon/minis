package cn.edu.ubaa.ui.screens.network

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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
fun NetworkScreen(
    uiState: NetworkUiState,
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
                    text = "流量加载失败",
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
            FreeTrafficCard(
                total = uiState.trafficData.freeTrafficTotal,
                remaining = uiState.trafficData.freeTrafficRemaining,
            )
          }

          uiState.trafficData.giftTrafficTotal?.let { total ->
            item {
              TrafficInfoCard(
                  title = "赠送流量",
                  subtitle = "剩余 ${formatGb(uiState.trafficData.giftTrafficRemaining ?: 0.0)} / 总额 ${formatGb(total)}",
                  icon = Icons.Default.CardGiftcard,
                  isSecondary = true,
              )
            }
          }

          uiState.trafficData.paidTraffic?.let { paid ->
            item {
              TrafficInfoCard(
                  title = "计费流量",
                  subtitle = "${formatGb(paid)}",
                  icon = Icons.Default.Paid,
                  isSecondary = true,
              )
            }
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
private fun FreeTrafficCard(
    total: Double,
    remaining: Double,
    modifier: Modifier = Modifier,
) {
  val used = (total - remaining).coerceAtLeast(0.0)
  val progress = if (total > 0) (used / total).toFloat().coerceIn(0f, 1f) else 0f

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
            imageVector = Icons.Default.NetworkWifi,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "免费流量",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
              text = "已用 ${formatGb(used)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
              text = "总额 ${formatGb(total)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
          text = "${formatGb(remaining)} 剩余",
          style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun TrafficInfoCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSecondary: Boolean = false,
    modifier: Modifier = Modifier,
) {
  val containerColor =
      if (isSecondary) {
        MaterialTheme.colorScheme.surfaceVariant
      } else {
        MaterialTheme.colorScheme.surface
      }

  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = containerColor),
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
          text = subtitle,
          style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp),
          fontWeight = FontWeight.Bold,
          color = if (isSecondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

private fun formatGb(value: Double): String {
  val scaled = (value * 100).toLong()
  val whole = scaled / 100
  val fraction = kotlin.math.abs(scaled % 100)
  return "$whole.${fraction.toString().padStart(2, '0')} GB"
}
