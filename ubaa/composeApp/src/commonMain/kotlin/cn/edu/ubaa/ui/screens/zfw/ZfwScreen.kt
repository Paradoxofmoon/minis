package cn.edu.ubaa.ui.screens.zfw

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.component.InAppWebView
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val ZFW_DASHBOARD_URL = "https://zfw.buaa.edu.cn/"

@Composable
fun ZfwScreen(
    uiState: ZfwUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCaptchaChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onSubmitSmsClick: () -> Unit,
    onRefreshCaptchaClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      when {
        uiState.loginSuccess ->
            ZfwSuccessContent(cookies = uiState.cookies, onResetClick = onResetClick)
        uiState.needsSms ->
            ZfwSmsForm(
                uiState = uiState,
                onSmsCodeChange = onSmsCodeChange,
                onSubmitSmsClick = onSubmitSmsClick,
                onResetClick = onResetClick,
            )
        else ->
            ZfwLoginForm(
                uiState = uiState,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onCaptchaChange = onCaptchaChange,
                onLoginClick = onLoginClick,
                onRefreshCaptchaClick = onRefreshCaptchaClick,
            )
      }
    }
  }
}

@Composable
private fun ZfwLoginForm(
    uiState: ZfwUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCaptchaChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRefreshCaptchaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = "登录深澜自助服务门户",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )

    OutlinedTextField(
        value = uiState.username,
        onValueChange = onUsernameChange,
        label = { Text("账号") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = uiState.password,
        onValueChange = onPasswordChange,
        label = { Text("密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
        modifier = Modifier.fillMaxWidth(),
    )

    CaptchaInputRow(
        captchaBase64 = uiState.captchaImageBase64,
        isLoading = uiState.isLoadingCaptcha,
        onRefreshClick = onRefreshCaptchaClick,
    )

    OutlinedTextField(
        value = uiState.captcha,
        onValueChange = onCaptchaChange,
        label = { Text("验证码") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = onLoginClick,
        enabled =
            !uiState.isLoggingIn &&
                uiState.username.isNotBlank() &&
                uiState.password.isNotBlank() &&
                uiState.captcha.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
      if (uiState.isLoggingIn) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
      } else {
        Text("登录")
      }
    }

    uiState.error?.let { ErrorCard(message = it) }
  }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun CaptchaInputRow(
    captchaBase64: String?,
    isLoading: Boolean,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val imageBytes =
      remember(captchaBase64) {
        captchaBase64?.takeIf { it.isNotBlank() }?.let { Base64.decode(it) }
      }

  Card(
      modifier = modifier.fillMaxWidth().height(200.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
      if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).size(24.dp),
            strokeWidth = 2.dp,
        )
      } else if (imageBytes != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(imageBytes)
                .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                .diskCachePolicy(coil3.request.CachePolicy.DISABLED)
                .build(),
            contentDescription = "验证码",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillWidth,
        )
      } else {
        Text(
            text = "点击刷新验证码",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center),
        )
      }

      IconButton(
          onClick = onRefreshClick,
          enabled = !isLoading,
          modifier = Modifier.align(Alignment.CenterEnd),
      ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "刷新验证码",
        )
      }

      // 点击卡片空白区域也可刷新
      if (!isLoading) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(end = 48.dp)
                    .clickable { onRefreshClick() },
        )
      }
    }
  }
}

@Composable
private fun ZfwSmsForm(
    uiState: ZfwUiState,
    onSmsCodeChange: (String) -> Unit,
    onSubmitSmsClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        text = "短信验证",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )

    uiState.smsMessage?.let { message ->
      Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
      )
    }

    uiState.smsRemainSeconds?.let { remain ->
      Text(
          text = "剩余时间：${remain} 秒",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    OutlinedTextField(
        value = uiState.smsCode,
        onValueChange = onSmsCodeChange,
        label = { Text("短信验证码") },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = onSubmitSmsClick,
        enabled = !uiState.isLoggingIn && uiState.smsCode.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
      if (uiState.isLoggingIn) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
      } else {
        Text("提交验证码")
      }
    }

    OutlinedButton(
        onClick = onResetClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text("返回重新登录")
    }

    uiState.error?.let { ErrorCard(message = it) }
  }
}

@Composable
private fun ZfwSuccessContent(
    cookies: List<io.ktor.http.Cookie>,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val cookieStrings =
      remember(cookies) {
        cookies.map { cookie -> "${cookie.name}=${cookie.value}" }
      }

  Column(modifier = modifier.fillMaxSize()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = "登录成功，正在打开充值页面",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
      )
      OutlinedButton(onClick = onResetClick) {
        Text("重新登录")
      }
    }
    InAppWebView(
        url = ZFW_DASHBOARD_URL,
        modifier = Modifier.fillMaxSize(),
        cookies = cookieStrings,
    )
  }
}

@Composable
private fun ErrorCard(message: String, modifier: Modifier = Modifier) {
  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
  ) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(16.dp),
    )
  }
}
