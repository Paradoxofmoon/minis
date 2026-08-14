package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.auth.ApiCallException
import cn.edu.ubaa.api.auth.toUserFacingApiException
import cn.edu.ubaa.api.auth.userFacingMessageForCode
import cn.edu.ubaa.api.network.platformLog
import cn.edu.ubaa.api.feature.CardApiBackend
import cn.edu.ubaa.api.feature.CardPayWay
import cn.edu.ubaa.api.feature.CardRechargeResult
import cn.edu.ubaa.model.dto.CardBalanceData
import cn.edu.ubaa.model.dto.CardBalanceResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 校园卡充值账单项（BUAA_CAMPUS_CARD_RECHARGE）
private const val RECHARGE_ITEM_ID = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
private const val CAMPUS_CARD_FEE = "campus_card_fee"

internal class LocalCardApiBackend : CardApiBackend {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun getBalance(): Result<CardBalanceData> {
    val studentId = requireStudentId() ?: return Result.failure(localUnauthenticatedApiException())
    return try {
      ensureCcpaySession()
      val response =
          LocalUpstreamClientProvider.shared()
              .get(localUpstreamUrl("https://pass.cc-pay.cn/api/campus_card/balance")) {
                parameter("t", Clock.System.now().toEpochMilliseconds())
                parameter("stuNo", studentId)
                header(HttpHeaders.Accept, "application/json, text/plain, */*")
              }
      parseBalanceResponse(response)
    } catch (e: Exception) {
      Result.failure(e.toUserFacingApiException("一卡通余额查询失败，请稍后重试"))
    }
  }

  override suspend fun getRechargePayWays(): Result<List<CardPayWay>> {
    val studentId = requireStudentId() ?: return Result.failure(localUnauthenticatedApiException())
    return try {
      ensureCcpaySession()
      // 需要先创建一笔交易才能拿到 goodsId 查支付方式；这里返回兜底常用方式，
      // 实际可用性在 beginRecharge 发起支付时校验。
      Result.success(
          listOf(
              CardPayWay(id = "wxpay", name = "wxpay", text = "微信支付", channel = "wxpay"),
              CardPayWay(id = "alipay", name = "alipay", text = "支付宝", channel = "alipay"),
              CardPayWay(id = "ylpay", name = "ylpay", text = "银联", channel = "ylpay"),
          )
      )
    } catch (e: Exception) {
      Result.failure(e.toUserFacingApiException("获取支付方式失败，请稍后重试"))
    }
  }

  override suspend fun beginRecharge(
      amount: String,
      payWayId: String,
  ): Result<CardRechargeResult> {
    val studentId = requireStudentId() ?: return Result.failure(localUnauthenticatedApiException())
    return try {
      ensureCcpaySession()
      platformLog("CR", "会话建立完成")

      // 1. 获取实名信息（学号 + 姓名）
      val (stuNo, realName) = fetchFeeInfo()
      platformLog("CR", "实名信息: $stuNo/$realName")
      if (stuNo.isBlank() || realName.isBlank()) {
        throw ApiCallException("获取校园卡实名信息失败", HttpStatusCode.BadGateway, "card_error")
      }
      // 2. 创建交易订单
      val transactionId = createTransaction(amount, stuNo, realName)
      platformLog("CR", "交易创建完成: $transactionId")      // 3. 发起支付，拿到支付跳转地址
      val payResult = initiatePay(transactionId, payWayId)
      platformLog("CR", "发起支付完成: payUrl=${payResult.payUrl} qrcode=${payResult.payQrCode}")
      Result.success(payResult)
    } catch (e: ApiCallException) {
      platformLog("CR", "充值失败(ApiCall): ${e.message} :: ${e.status}")
      Result.failure(ApiCallException("充值失败: ${e.message}", e.status ?: HttpStatusCode.BadGateway, "card_error"))
    } catch (e: Exception) {
      platformLog("CR", "充值失败: ${e.message} :: ${e::class.simpleName}")
      Result.failure(ApiCallException("充值失败: ${e.message ?: e::class.simpleName}", HttpStatusCode.BadGateway, "card_error"))
    }
  }

  // ===== 私有方法 =====

  private suspend fun requireStudentId(): String? {
    val session = LocalAuthSessionStore.get() ?: return null
    return session.user.schoolid.ifBlank { session.username }.ifBlank { null }
  }

  /** 通过 CAS SSO 跳转建立 pass.cc-pay.cn / mall.cc-pay.cn 会话。 */
  private suspend fun ensureCcpaySession() {
    val client = LocalUpstreamClientProvider.shared()
    // pass 登录（建立 .cc-pay.cn 全局会话）
    val r1 = client.get(localUpstreamUrl("https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fpass.cc-pay.cn%2Flogin")) {
      header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    }
    platformLog("CR", "CAS跳转 pass: status=${r1.status}")
    // mall 登录（充值入口在 mall，需独立建立会话）
    val r2 = client.get(localUpstreamUrl("https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fmall.cc-pay.cn%2Flogin")) {
      header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    }
    platformLog("CR", "CAS跳转 mall: status=${r2.status} url=${r2.call.request.url}")
    // 触达 mall / cashier
    val r3 = client.get(localUpstreamUrl("https://mall.cc-pay.cn/api/address")) {
      header(HttpHeaders.Accept, "application/json")
    }
    platformLog("CR", "mall触达: status=${r3.status} body=${r3.bodyAsText().take(120)}")
    val r4 = client.get(localUpstreamUrl("https://cashier.cc-pay.cn/api/address")) {
      header(HttpHeaders.Accept, "application/json")
    }
    platformLog("CR", "cashier触达: status=${r4.status} body=${r4.bodyAsText().take(120)}")
  }

  /** 获取校园卡充值所需的实名信息。 */
  private suspend fun fetchFeeInfo(): Pair<String, String> {
    val response =
        LocalUpstreamClientProvider.shared().get(
            localUpstreamUrl("https://mall.cc-pay.cn/api/bill/note/feeInfo")
        ) {
          parameter("t", Clock.System.now().toEpochMilliseconds())
          parameter("fromType", CAMPUS_CARD_FEE)
          header(HttpHeaders.Accept, "application/json")
        }
    val body = response.bodyAsText()
    platformLog("CR", "fetchFeeInfo: status=${response.status} body=${body.take(200)}")
    checkCcpaySession(response, body)
    val data = json.parseToJsonElement(body).jsonObject["data"].safeObject()
    val stuNo = data?.get("stuNo")?.jsonPrimitive?.contentOrNull ?: ""
    val realName = data?.get("realName")?.jsonPrimitive?.contentOrNull ?: ""
    return stuNo to realName
  }

  /** 创建支付交易，返回 transactionId。 */
  private suspend fun createTransaction(
      amount: String,
      stuNo: String,
      realName: String,
  ): String {
    val feeInfoJson = json.encodeToString(
        buildJsonObject {
          put("stuNo", JsonPrimitive(stuNo))
          put("realName", JsonPrimitive(realName))
        }
    )
    val payload = json.encodeToString(
        buildJsonObject {
          put("targetId", JsonPrimitive("mall_id"))
          put("targetType", JsonPrimitive("mall"))
          put("money", JsonPrimitive(amount))
          put("itemId", JsonPrimitive(RECHARGE_ITEM_ID))
          put("feeInfo", JsonPrimitive(feeInfoJson))
          put("fromType", JsonPrimitive(CAMPUS_CARD_FEE))
          put("choice", JsonPrimitive(""))
        }
    )
    val response =
        LocalUpstreamClientProvider.shared().post(
            localUpstreamUrl("https://mall.cc-pay.cn/api/payment")
        ) {
          parameter("t", Clock.System.now().toEpochMilliseconds())
          contentType(ContentType.Application.Json)
          setBody(payload)
          header(HttpHeaders.Accept, "application/json, application/json")
          // Referer 需带 name/cardNo/school/money 参数，服务器据此校验
          header(
              HttpHeaders.Referrer,
              "https://mall.cc-pay.cn/entry/card/$RECHARGE_ITEM_ID?name=${realName.encodeURLParameter()}&cardNo=$stuNo&school=buaa&money=$amount"
          )
        }
    val body = response.bodyAsText()
    platformLog("CR", "createTransaction: status=${response.status} body=${body.take(400)}")
    checkCcpaySession(response, body)
    val jsonBody = json.parseToJsonElement(body).jsonObject
    val data = jsonBody["data"].safeObject() ?: jsonBody
    // 若返回业务错误，提取 message
    val msg = jsonBody["message"]?.jsonPrimitive?.contentOrNull
    // transactionId 优先取 data.id / transactionId
    val id = (data["id"] ?: data["transactionId"] ?: data["transaction_id"])?.jsonPrimitive?.contentOrNull.orEmpty()
    if (id.isBlank()) {
      throw ApiCallException(
          "创建充值订单失败${msg?.let { ": $it" } ?: ""}",
          HttpStatusCode.BadGateway,
          "card_error",
      )
    }
    return id
  }

  /** 发起支付，返回支付跳转地址。 */
  private suspend fun initiatePay(transactionId: String, payWayId: String): CardRechargeResult {
    val response =
        LocalUpstreamClientProvider.shared().get(
            localUpstreamUrl("https://cashier.cc-pay.cn/transaction/pay")
        ) {
          parameter("id", transactionId)
          parameter("payWayId", payWayId)
          parameter("phoneNumber", "")
          parameter("ecCode", "")
          header("version", "v2")
          header(HttpHeaders.Accept, "application/json, text/plain, */*")
          header(HttpHeaders.Referrer, "https://cashier.cc-pay.cn/cashier?id=$transactionId")
        }
    val body = response.bodyAsText()
    platformLog("CR", "initiatePay: status=${response.status} body=${body.take(400)}")
    checkCcpaySession(response, body)
    val data = json.parseToJsonElement(body).jsonObject["data"].safeObject() ?: return CardRechargeResult()
    return CardRechargeResult(
        payUrl = data["payUrl"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
        payQrCode = data["payQrCode"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
        payWebForm = data["payWebForm"]?.jsonPrimitive?.contentOrNull,
    )
  }

  private suspend fun checkCcpaySession(response: HttpResponse, body: String) {
    if (response.status == HttpStatusCode.Unauthorized) {
      throw resolveLocalBusinessAuthenticationFailure("card_error")
    }
    if (localIsSsoUrl(response.call.request.url.toString())) {
      throw resolveLocalBusinessAuthenticationFailure("card_error")
    }
    val trimmed = body.trimStart()
    if (trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true) ||
        body.contains("统一身份认证", ignoreCase = true)) {
      throw resolveLocalBusinessAuthenticationFailure("card_error")
    }
  }

  private suspend fun parseBalanceResponse(response: HttpResponse): Result<CardBalanceData> {    val body = response.bodyAsText()
    if (isCardSessionExpired(response, body)) {
      return Result.failure(resolveLocalBusinessAuthenticationFailure("card_error"))
    }
    if (response.status != HttpStatusCode.OK) {
      return Result.failure(
          localBusinessApiException(
              "card_error",
              userFacingMessageForCode("card_error", response.status),
              response.status,
          )
      )
    }

    val payload =
        runCatching { json.decodeFromString<CardBalanceResponse>(body) }.getOrElse {
          return Result.failure(
              localBusinessApiException(
                  "card_error",
                  userFacingMessageForCode(
                      "card_error",
                      HttpStatusCode.InternalServerError,
                  ),
                  HttpStatusCode.InternalServerError,
              )
          )
        }

    if (!payload.success || payload.data == null) {
      return Result.failure(
          ApiCallException(
              message = "一卡通余额查询失败，请稍后重试",
              status = HttpStatusCode.BadGateway,
              code = "card_error",
          )
      )
    }

    return Result.success(payload.data)
  }

  private fun isCardSessionExpired(response: HttpResponse, body: String): Boolean {
    if (response.status == HttpStatusCode.Unauthorized) return true
    if (localIsSsoUrl(response.call.request.url.toString())) return true
    val trimmed = body.trimStart()
    return trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true) ||
        body.contains("input name=\"execution\"") ||
        body.contains("统一身份认证", ignoreCase = true)
  }
}

/** JsonElement 安全转为 JsonObject，非对象（含 null）返回 null。 */
private fun JsonElement?.safeObject(): JsonObject? = this as? JsonObject
