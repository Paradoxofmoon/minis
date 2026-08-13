package cn.edu.ubaa.ui.screens.electricity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.api.storage.MeterNumberStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 电费购电 UI 状态。 */
data class ElectricityUiState(
    // ---- 电表查询 tab ----
    val isLoadingTree: Boolean = false,
    val meters: List<ElectricityMeter> = emptyList(),
    val campuses: List<String> = emptyList(),
    val buildings: List<String> = emptyList(),
    val floors: List<String> = emptyList(),
    val rooms: List<String> = emptyList(),
    val meterOptions: List<ElectricityMeter> = emptyList(),
    val selectedCampus: String? = null,
    val selectedBuilding: String? = null,
    val selectedFloor: String? = null,
    val selectedRoom: String? = null,
    val selectedMeter: ElectricityMeter? = null,
    // ---- 电费缴费 tab ----
    val meterNumber: String = "",
    val meterHistory: List<String> = emptyList(),
    val isLoadingMeter: Boolean = false,
    val meterInfo: ElectricityMeterInfo? = null,
    val power: String = "",
    val computedPower: Int? = null,
    val computedMoney: Double? = null,
    val isSubmitting: Boolean = false,
    val payUrl: String? = null,
    // ---- 公共 ----
    val error: String? = null,
) {
  val hasPendingOrder: Boolean get() = meterInfo?.payUrl != null
}

/** 电费购电 ViewModel。直连 shsd.buaa.edu.cn，不依赖 shared ApiFactory。 */
class ElectricityViewModel(
    private val api: ElectricityApi = ElectricityApi(),
) : ViewModel() {
  private val _state = MutableStateFlow(ElectricityUiState())
  val state: StateFlow<ElectricityUiState> = _state.asStateFlow()

  init {
    _state.value = _state.value.copy(meterHistory = MeterNumberStore.getAll())
    loadMeterTree()
  }

  fun clearError() {
    _state.value = _state.value.copy(error = null)
  }

  // ===== 查询 tab =====

  fun loadMeterTree() {
    _state.value = _state.value.copy(isLoadingTree = true, error = null)
    viewModelScope.launch {
      runCatching { api.fetchMeterTree() }
          .onSuccess { meters ->
            val campuses = meters.map { it.campus }.distinct().sorted()
            _state.value =
                _state.value.copy(
                    isLoadingTree = false,
                    meters = meters,
                    campuses = campuses,
                )
          }
          .onFailure { e ->
            _state.value =
                _state.value.copy(
                    isLoadingTree = false,
                    error = e.message ?: "用电查询数据加载失败",
                )
          }
    }
  }

  fun onCampusSelect(campus: String) {
    val buildings = _state.value.meters.filter { it.campus == campus }.map { it.building }.distinct().sorted()
    _state.value =
        _state.value.copy(
            selectedCampus = campus,
            buildings = buildings,
            floors = emptyList(),
            rooms = emptyList(),
            meterOptions = emptyList(),
            selectedBuilding = null,
            selectedFloor = null,
            selectedRoom = null,
            selectedMeter = null,
        )
  }

  fun onBuildingSelect(building: String) {
    val s = _state.value
    val floors =
        s.meters.filter { it.campus == s.selectedCampus && it.building == building }
            .map { it.floor }.distinct().sorted()
    _state.value =
        s.copy(
            selectedBuilding = building,
            floors = floors,
            rooms = emptyList(),
            meterOptions = emptyList(),
            selectedFloor = null,
            selectedRoom = null,
            selectedMeter = null,
        )
  }

  fun onFloorSelect(floor: String) {
    val s = _state.value
    val rooms =
        s.meters.filter {
              it.campus == s.selectedCampus && it.building == s.selectedBuilding && it.floor == floor
            }
            .map { it.room }.distinct().sorted()
    _state.value =
        s.copy(
            selectedFloor = floor,
            rooms = rooms,
            meterOptions = emptyList(),
            selectedRoom = null,
            selectedMeter = null,
        )
  }

  fun onRoomSelect(room: String) {
    val s = _state.value
    val meterOptions =
        s.meters.filter {
              it.campus == s.selectedCampus &&
                  it.building == s.selectedBuilding &&
                  it.floor == s.selectedFloor &&
                  it.room == room
            }
    _state.value = s.copy(selectedRoom = room, meterOptions = meterOptions, selectedMeter = null)
  }

  fun onMeterSelect(meter: ElectricityMeter) {
    _state.value = _state.value.copy(selectedMeter = meter)
  }

  /** 把查询到电表的 identityNo 填入缴费 tab 并自动查询。 */
  fun useSelectedMeterForPay() {
    val identityNo = _state.value.selectedMeter?.identityNo ?: return
    onMeterNumberChange(identityNo)
    queryMeter()
  }

  // ===== 缴费 tab =====

  fun onMeterNumberChange(value: String) {
    _state.value =
        _state.value.copy(
            meterNumber = value,
            meterInfo = null,
            payUrl = null,
            power = "",
            computedPower = null,
            computedMoney = null,
        )
  }

  fun onHistorySelect(num: String) {
    onMeterNumberChange(num)
    queryMeter()
  }

  fun onHistoryRemove(num: String) {
    MeterNumberStore.remove(num)
    _state.value = _state.value.copy(meterHistory = MeterNumberStore.getAll())
  }

  /** 查询电表信息（余额、电价、倍率）。 */
  fun queryMeter() {
    val number = _state.value.meterNumber.trim()
    if (number.isBlank()) {
      _state.value = _state.value.copy(error = "请输入购电表号")
      return
    }
    _state.value = _state.value.copy(isLoadingMeter = true, error = null, meterInfo = null)
    viewModelScope.launch {
      runCatching { api.fetchMeterInfo(number) }
          .onSuccess { info ->
            MeterNumberStore.add(number)
            _state.value =
                _state.value.copy(
                    isLoadingMeter = false,
                    meterInfo = info,
                    meterHistory = MeterNumberStore.getAll(),
                )
          }
          .onFailure { e ->
            _state.value =
                _state.value.copy(isLoadingMeter = false, error = e.message ?: "查询电表失败")
          }
    }
  }

  fun onPowerChange(value: String) {
    val info = _state.value.meterInfo
    if (info == null) {
      _state.value = _state.value.copy(power = value)
      return
    }
    val pwr = value.toIntOrNull()
    if (pwr == null) {
      _state.value =
          _state.value.copy(power = value, computedPower = null, computedMoney = null)
      return
    }
    compute(pwr)
  }

  private fun compute(requestedPower: Int) {
    val info = _state.value.meterInfo ?: return
    val ct = if (info.ct > 0) info.ct else 1
    val writePower = requestedPower / ct
    val actualPower = writePower * ct
    val money = actualPower * info.price
    _state.value =
        _state.value.copy(
            power = actualPower.toString(),
            computedPower = writePower,
            computedMoney = money,
        )
  }

  /** 确认支付：创建订单，返回跳转地址。 */
  fun submitPay() {
    val s = _state.value
    val info = s.meterInfo ?: return
    val writePower = s.computedPower ?: s.power.toIntOrNull()
    if (writePower == null || writePower < 1) {
      _state.value = s.copy(error = "购电量必须是大于 0 的整数")
      return
    }

    _state.value = s.copy(isSubmitting = true, error = null)
    viewModelScope.launch {
      runCatching { api.submitPay(info.id, writePower) }
          .onSuccess { result ->
            when (result) {
              is ElectricityPayResult.Success ->
                  _state.value =
                      _state.value.copy(isSubmitting = false, payUrl = result.payUrl)
              is ElectricityPayResult.Failure ->
                  _state.value = _state.value.copy(isSubmitting = false, error = result.message)
            }
          }
          .onFailure { e ->
            _state.value =
                _state.value.copy(isSubmitting = false, error = e.message ?: "下单失败，请稍后重试")
          }
    }
  }

  /** 继续支付未完成订单。 */
  fun continuePendingPay() {
    val url = _state.value.meterInfo?.payUrl
    if (url != null) {
      _state.value = _state.value.copy(payUrl = url)
    }
  }

  /** 取消未完成订单。 */
  fun cancelPendingPay() {
    val info = _state.value.meterInfo ?: return
    val serial = info.serial ?: return
    viewModelScope.launch {
      runCatching { api.cancelPay(info.id, serial) }
          .onSuccess { _ ->
            _state.value = _state.value.copy(meterInfo = null)
            queryMeter()
          }
          .onFailure { e ->
            _state.value = _state.value.copy(error = e.message ?: "取消订单失败")
          }
    }
  }

  /** 支付完成 / 返回后刷新。 */
  fun dismissPayUrl() {
    _state.value = _state.value.copy(payUrl = null)
    queryMeter()
  }

  override fun onCleared() {
    api.close()
  }
}
