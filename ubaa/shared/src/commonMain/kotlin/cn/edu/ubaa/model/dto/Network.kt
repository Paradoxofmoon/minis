package cn.edu.ubaa.model.dto

import kotlinx.serialization.Serializable

/**
 * 校园网流量数据。
 *
 * @property freeTrafficTotal 免费流量总额（GB）。
 * @property freeTrafficRemaining 免费流量剩余（GB）。
 * @property giftTrafficTotal 赠送流量总额（GB），不存在时为 null。
 * @property giftTrafficRemaining 赠送流量剩余（GB），不存在时为 null。
 * @property paidTraffic 计费流量（GB），不存在时为 null。
 */
@Serializable
data class TrafficData(
    val freeTrafficTotal: Double = 0.0,
    val freeTrafficRemaining: Double = 0.0,
    val giftTrafficTotal: Double? = null,
    val giftTrafficRemaining: Double? = null,
    val paidTraffic: Double? = null
)
