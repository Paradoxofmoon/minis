package cn.edu.ubaa.ui.screens.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.local.MailPortal
import cn.edu.ubaa.api.local.MailProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MailUiState(
    val isLoading: Boolean = false,
    val domainCookies: List<Pair<String, String>> = emptyList(),
    val diagnostic: String = "",
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
      // 阶段0诊断：用 Ktor 尝试换 Coremail.sid，报告显示在页面
      val report = MailProbe.probe()
      _state.value = _state.value.copy(diagnostic = report)
      MailPortal.ensureSession()
          .onSuccess {
            val cookies = MailPortal.domainCookieHeaders()
            _state.value =
                _state.value.copy(isLoading = false, domainCookies = cookies, error = null)
          }
          .onFailure { e ->
            _state.value =
                _state.value.copy(isLoading = false, error = e.message ?: "邮箱登录失败，请稍后重试")
          }
    }
  }

  /** 重置加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    loadedOnce = false
    _state.value = MailUiState()
  }

  fun reset() {
    resetLoadedState()
  }
}
