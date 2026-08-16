package cn.edu.ubaa.ui.screens.cgyy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.edu.ubaa.api.local.buildBuaaEduCnDomainCookies
import cn.edu.ubaa.ui.component.InAppWebView

/**
 * 体育场馆网页预约屏（方案 A1）。
 *
 * 加载 cgyy 官网网页版预约页，注入本 App 已登录的 `.buaa.edu.cn` SSO cookie，
 * 让用户在网页内完成 时段选择 + 点选验证码 + 下单 + 同伴 + 支付 等完整流程。
 * orderPin 由网页前端真实点击生成，clickWord 验证码由用户人工点选，均无需原生逆向。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgyyWebViewReserveScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val reserveUrl = "https://cgyy.buaa.edu.cn/venue/mobileReservation"
  val ssoCookies = buildBuaaEduCnDomainCookies()

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
      InAppWebView(
          url = reserveUrl,
          modifier = Modifier.fillMaxSize(),
          domainCookies = ssoCookies,
          onPageError = { /* 诊断日志，可扩展提示 */ },
      )
    }
  }
}
