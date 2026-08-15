package cn.edu.ubaa.ui.screens.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.local.MailPortal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MailUiState(
    val isLoading: Boolean = false,
    val cookie: String = "",
    val error: String? = null,
)

/**
 * 北航邮箱 ViewModel：进入页面时触发 CAS 登录到 it.buaa.edu.cn，提取会话 cookie 供 WebView 使用。
 */
class MailViewModel : ViewModel() {
  private val _state = MutableStateFlow(MailUiState())
  val state: StateFlow<MailUiState> = _state.asStateFlow()

  private var loadedOnce = false

  fun ensureLoaded(force: Boolean = false) {
    if (loadedOnce && !force) return
    if (_state.value.isLoading) return
    loadedOnce = true
    _state.value = _state.value.copy(isLoading = true, error = null)
    viewModelScope.launch {
      MailPortal.ensureSession()
          .onSuccess {
            val cookie = MailPortal.cookieHeader()
            _state.value = _state.value.copy(isLoading = false, cookie = cookie, error = null)
          }
          .onFailure { e ->
            _state.value =
                _state.value.copy(isLoading = false, error = e.message ?: "邮箱登录失败，请稍后重试")
          }
    }
  }

  fun reset() {
    loadedOnce = false
    _state.value = MailUiState()
  }
}
