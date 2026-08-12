package cn.edu.ubaa.ui.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.feature.NetworkApi
import cn.edu.ubaa.model.dto.TrafficData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 校园网流量界面 UI 状态。 */
data class NetworkUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val trafficData: TrafficData = TrafficData(),
    val error: String? = null,
)

/** 校园网流量查询的 ViewModel。 */
class NetworkViewModel(
    private val networkApi: NetworkApi = NetworkApi(),
) : ViewModel() {
  private var loadedOnce = false

  private val _state = MutableStateFlow(NetworkUiState())
  val state: StateFlow<NetworkUiState> = _state.asStateFlow()

  /** 首次加载或按需刷新流量。 */
  fun ensureLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && loadedOnce) return
    loadTraffic()
  }

  /** 下拉刷新入口。 */
  fun refresh() {
    loadTraffic()
  }

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    loadedOnce = false
    _state.value = NetworkUiState()
  }

  private fun loadTraffic() {
    loadedOnce = true
    viewModelScope.launch {
      val current = _state.value
      _state.value =
          current.copy(
              isLoading = !current.trafficData.hasAnyData() && !current.isRefreshing,
              isRefreshing = current.trafficData.hasAnyData(),
              error = null,
          )

      networkApi
          .getTraffic()
          .onSuccess { data ->
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    trafficData = data,
                    error = null,
                )
          }
          .onFailure { error ->
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = error.message ?: "加载校园网流量失败",
                )
          }
    }
  }

  /** 清空错误提示。 */
  fun clearError() {
    _state.value = _state.value.copy(error = null)
  }

  private fun TrafficData.hasAnyData(): Boolean {
    return freeTrafficTotal > 0.0 ||
        freeTrafficRemaining > 0.0 ||
        giftTrafficTotal != null ||
        giftTrafficRemaining != null ||
        paidTraffic != null
  }
}
