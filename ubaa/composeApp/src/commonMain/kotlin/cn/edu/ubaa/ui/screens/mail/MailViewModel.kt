package cn.edu.ubaa.ui.screens.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.local.CoremailMessage
import cn.edu.ubaa.api.local.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MailUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val messages: List<CoremailMessage> = emptyList(),
    val hasMore: Boolean = false,
    val total: Long = 0,
    val error: String? = null,
    val diagnostic: String = "",
    val lastRefresh: Long = 0L,
)

/**
 * 北航邮箱 ViewModel：原生列表展示收件箱邮件。
 * 用 Ktor 调 Coremail JSON 接口（复用 UBAA SSO 会话换 sid），无需 WebView 渲染。
 */
class MailViewModel : ViewModel() {
  private val _state = MutableStateFlow(MailUiState())
  val state: StateFlow<MailUiState> = _state.asStateFlow()

  private var loadedOnce = false

  fun ensureLoaded(force: Boolean = false) {
    if (loadedOnce && !force) return
    if (_state.value.isLoading) return
    loadedOnce = true
    refresh()
  }

  fun refresh() {
    if (_state.value.isLoading) return
    _state.value = _state.value.copy(isLoading = true, error = null)
    viewModelScope.launch {
      // 诊断（临时定位sid问题）
      val diag = MailRepository.diagnose()
      _state.value = _state.value.copy(diagnostic = diag)
      MailRepository.listMessages(start = 0, limit = PAGE_SIZE, fid = 1)
          .onSuccess { page ->
            _state.value = _state.value.copy(
                isLoading = false,
                messages = page.items,
                hasMore = page.hasMore,
                total = page.total,
                lastRefresh = System.currentTimeMillis(),
            )
          }
          .onFailure { e ->
            _state.value =
                _state.value.copy(isLoading = false, error = e.message ?: "加载邮件失败")
          }
    }
  }

  /** 加载下一页（滚动到底触发）。 */
  fun loadMore() {
    val s = _state.value
    if (s.isLoading || s.isLoadingMore || !s.hasMore) return
    _state.value = s.copy(isLoadingMore = true)
    viewModelScope.launch {
      MailRepository.listMessages(start = s.messages.size, limit = PAGE_SIZE, fid = 1)
          .onSuccess { page ->
            // 追加去重
            val seen = s.messages.map { it.id }.toHashSet()
            val fresh = page.items.filter { it.id !in seen }
            _state.value = _state.value.copy(
                isLoadingMore = false,
                messages = s.messages + fresh,
                hasMore = page.hasMore,
                total = page.total,
            )
          }
          .onFailure { e ->
            _state.value = _state.value.copy(isLoadingMore = false, error = e.message ?: "加载更多失败")
          }
    }
  }

  private companion object {
    const val PAGE_SIZE = 30
  }

  /** 重置加载标记与状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    loadedOnce = false
    _state.value = MailUiState()
  }

  fun reset() {
    resetLoadedState()
  }
}
