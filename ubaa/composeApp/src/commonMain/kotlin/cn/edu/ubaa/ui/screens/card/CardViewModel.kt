package cn.edu.ubaa.ui.screens.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.CardApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 校园卡界面 UI 状态。 */
data class CardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val balance: String = "",
    val error: String? = null,
)

/** 校园卡余额查询的 ViewModel。 */
class CardViewModel(
    private val cardApi: CardApi = CardApi(),
) : ViewModel() {
  private var loadedOnce = false

  private val _state = MutableStateFlow(CardUiState())
  val state: StateFlow<CardUiState> = _state.asStateFlow()

  /** 首次加载或按需刷新余额。 */
  fun ensureLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && loadedOnce) return
    loadBalance()
  }

  /** 下拉刷新入口。 */
  fun refresh() {
    loadBalance()
  }

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    loadedOnce = false
    _state.value = CardUiState()
  }

  private fun loadBalance() {
    loadedOnce = true
    viewModelScope.launch {
      val current = _state.value
      _state.value =
          current.copy(
              isLoading = current.balance.isBlank() && !current.isRefreshing,
              isRefreshing = current.balance.isNotBlank(),
              error = null,
          )

      cardApi
          .getBalance()
          .onSuccess { data ->
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    balance = formatMoney(data.balance),
                    error = null,
                )
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = error.message ?: "加载校园卡余额失败",
                )
          }
    }
  }

  /** 清空错误提示。 */
  fun clearError() {
    _state.value = _state.value.copy(error = null)
  }
}

private fun formatMoney(amount: String): String {
  val value = amount.toDoubleOrNull() ?: 0.0
  return "¥ ${formatTwoDecimals(value)}"
}

private fun formatTwoDecimals(value: Double): String {
  val scaled = (value * 100).toLong()
  val whole = scaled / 100
  val fraction = kotlin.math.abs(scaled % 100)
  return "$whole.${fraction.toString().padStart(2, '0')}"
}
