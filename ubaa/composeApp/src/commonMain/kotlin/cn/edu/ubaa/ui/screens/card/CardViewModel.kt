package cn.edu.ubaa.ui.screens.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.CardApi
import cn.edu.ubaa.api.feature.CardRechargeResult
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
    // ---- 充值状态 ----
    val amount: String = "",
    val isRecharging: Boolean = false,
    val payUrl: String? = null,
    val cashierUrl: String? = null,
)

/** 校园卡余额查询 + 充值 ViewModel。 */
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

  // ===== 充值 =====

  fun onAmountChange(value: String) {
    _state.value = _state.value.copy(amount = value)
  }

  /** 发起充值：创建订单，跳转收银台网页（支付方式在网页里选择）。 */
  fun beginRecharge() {
    val amount = _state.value.amount
    if (amount.isBlank()) {
      _state.value = _state.value.copy(error = "请输入充值金额")
      return
    }
    val amountValue = amount.toDoubleOrNull()
    if (amountValue == null || amountValue < 1 || amountValue > 90000) {
      _state.value = _state.value.copy(error = "充值金额需在 1~90000 元之间")
      return
    }
    _state.value = _state.value.copy(isRecharging = true, error = null)
    viewModelScope.launch {
      // payWayId 在收银台网页里选择，此处固定传空串（LocalCardApi 直接返 cashierUrl 不发起支付）
      cardApi
          .beginRecharge(amount, "")
          .onSuccess { result ->
            val cashier = result.cashierUrl?.takeIf { it.isNotBlank() }
            val directPay = resolvePayTarget(result)
            _state.value =
                _state.value.copy(
                    isRecharging = false,
                    // 方案A：加载收银台网页(WebView 内选支付方式并唤起)；否则退回直接 scheme
                    cashierUrl = cashier,
                    payUrl = if (cashier != null) null else directPay,
                    error =
                        if (cashier == null && directPay.isNullOrBlank()) "未获取到支付地址" else null,
                )
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(isRecharging = false, error = error.message ?: "充值失败，请稍后重试")
          }
    }
  }

  private fun resolvePayTarget(result: CardRechargeResult): String? =
      result.payUrl?.takeIf { it.isNotBlank() }
          ?: result.payQrCode?.takeIf { it.isNotBlank() }

  /** 支付地址已处理完成后清理。 */
  fun clearPayUrl() {
    _state.value = _state.value.copy(payUrl = null)
  }

  /** 关闭收银台网页覆盖层。 */
  fun clearCashier() {
    _state.value = _state.value.copy(cashierUrl = null)
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
