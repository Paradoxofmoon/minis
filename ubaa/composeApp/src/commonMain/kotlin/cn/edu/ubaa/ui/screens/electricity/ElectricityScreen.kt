package cn.edu.ubaa.ui.screens.electricity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.api.storage.MeterNumberStore
import cn.edu.ubaa.ui.component.InAppWebView

private const val QUERY_URL = "https://shsd.buaa.edu.cn/PubBuaa"
private const val PAY_URL = "https://shsd.buaa.edu.cn/BuaaPay"

@Composable
fun ElectricityScreen(modifier: Modifier = Modifier) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var meterNumber by remember { mutableStateOf("") }
  var meterHistory by remember { mutableStateOf(MeterNumberStore.getAll()) }
  var showPayWebView by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = selectedTab) {
      Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("电表查询") },
      )
      Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("电费缴费") },
      )
    }

    when (selectedTab) {
      0 -> InAppWebView(url = QUERY_URL, modifier = Modifier.fillMaxSize())
      1 ->
          if (showPayWebView) {
            PayWebView(
                meterNumber = meterNumber,
                onBack = { showPayWebView = false },
            )
          } else {
            MeterNumberForm(
                meterNumber = meterNumber,
                onMeterNumberChange = { meterNumber = it },
                meterHistory = meterHistory,
                onHistorySelect = { meterNumber = it },
                onHistoryRemove = { num ->
                  MeterNumberStore.remove(num)
                  meterHistory = MeterNumberStore.getAll()
                },
                onSubmit = {
                  if (meterNumber.isNotBlank()) {
                    MeterNumberStore.add(meterNumber)
                    meterHistory = MeterNumberStore.getAll()
                    showPayWebView = true
                  }
                },
            )
          }
    }
  }
}

@Composable
private fun PayWebView(meterNumber: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val injectJs =
      """
      (function(){
        var el = document.getElementById('meterId');
        if (el) {
          el.value = '$meterNumber';
          el.dispatchEvent(new Event('input', {bubbles: true}));
          el.dispatchEvent(new Event('change', {bubbles: true}));
        }
      })();
      """
          .trimIndent()

  Column(modifier = modifier.fillMaxSize()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
      }
      Text(
          text = "缴费表号：$meterNumber",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
      )
    }
    InAppWebView(
        url = PAY_URL,
        modifier = Modifier.fillMaxSize(),
        injectJsOnLoad = injectJs,
    )
  }
}

@Composable
private fun MeterNumberForm(
    meterNumber: String,
    onMeterNumberChange: (String) -> Unit,
    meterHistory: List<String>,
    onHistorySelect: (String) -> Unit,
    onHistoryRemove: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
        text = "输入购电表号",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    )
    Text(
        text = "不知道表号？先在上方「电表查询」里查，再回来缴费。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = meterNumber,
        onValueChange = onMeterNumberChange,
        label = { Text("购电表号") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = onSubmit,
        enabled = meterNumber.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
      Text("去缴费")
    }

    if (meterHistory.isNotEmpty()) {
      Text(
          text = "历史表号",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      meterHistory.forEach { num ->
        AssistChip(
            onClick = { onHistorySelect(num) },
            label = { Text(num) },
            leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = {
              Icon(
                  Icons.Default.Close,
                  contentDescription = "删除",
                  modifier = Modifier.size(16.dp),
              )
            },
            modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}
