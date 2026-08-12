package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.auth.ApiClientProvider
import cn.edu.ubaa.api.core.ApiClient
import cn.edu.ubaa.model.dto.CardBalanceData

/** 校园一卡通（校园卡）API 服务。 提供校园卡余额查询等功能。 */
interface CardApiBackend {
  /** 查询当前用户的一卡通余额。 */
  suspend fun getBalance(): Result<CardBalanceData>
}

/** 校园一卡通 API 服务入口。 根据当前连接模式自动选择直连、WebVPN 或中继后端。 */
class CardApi(
    private val backendProvider: () -> CardApiBackend = { ConnectionRuntime.apiFactory().cardApi() }
) {
  internal constructor(backend: CardApiBackend) : this({ backend })

  constructor(apiClient: ApiClient) : this({ RelayCardApiBackend(apiClient) })

  private fun currentBackend(): CardApiBackend = backendProvider()

  /**
   * 查询校园卡余额。
   *
   * @return 包含余额与待领取金额的 [Result]。若失败则包含异常信息。
   */
  suspend fun getBalance(): Result<CardBalanceData> {
    return currentBackend().getBalance()
  }
}

internal class RelayCardApiBackend(
    private val apiClient: ApiClient = ApiClientProvider.shared
) : CardApiBackend {
  override suspend fun getBalance(): Result<CardBalanceData> {
    // TODO: 实现 SERVER_RELAY 模式下的一卡通余额查询中继接口
    return Result.failure(
        NotImplementedError("SERVER_RELAY 模式下一卡通余额查询尚未实现")
    )
  }
}
