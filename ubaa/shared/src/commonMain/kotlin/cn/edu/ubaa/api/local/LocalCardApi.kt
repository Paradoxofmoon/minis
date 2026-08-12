package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.auth.ApiCallException
import cn.edu.ubaa.api.auth.toUserFacingApiException
import cn.edu.ubaa.api.auth.userFacingMessageForCode
import cn.edu.ubaa.api.feature.CardApiBackend
import cn.edu.ubaa.model.dto.CardBalanceData
import cn.edu.ubaa.model.dto.CardBalanceResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock
import kotlinx.serialization.json.Json

internal class LocalCardApiBackend : CardApiBackend {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun getBalance(): Result<CardBalanceData> {
    val session =
        LocalAuthSessionStore.get() ?: return Result.failure(localUnauthenticatedApiException())
    val studentId = session.user.schoolid.ifBlank { session.username }
    if (studentId.isBlank()) {
      return Result.failure(localUnauthenticatedApiException())
    }

    return try {
      // 第一步：通过 CAS SSO 跳转获取 pass.cc-pay.cn 会话 Cookie
      LocalUpstreamClientProvider.shared()
          .get(
              localUpstreamUrl(
                  "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fpass.cc-pay.cn%2Flogin"
              )
          ) {
            header(
                HttpHeaders.Accept,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
          }

      // 第二步：调用一卡通余额查询接口
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

  private suspend fun parseBalanceResponse(response: HttpResponse): Result<CardBalanceData> {
    val body = response.bodyAsText()
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
