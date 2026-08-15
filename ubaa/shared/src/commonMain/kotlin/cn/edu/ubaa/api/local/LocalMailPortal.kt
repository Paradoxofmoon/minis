package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.ConnectionMode
import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.auth.ApiCallException
import cn.edu.ubaa.api.network.platformLog
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter

/**
 * 北航邮箱（it.buaa.edu.cn / mail.buaa.edu.cn，Coremail）CAS 登录与会话桥接。
 *
 * 邮箱登录走北航统一身份认证（sso.buaa.edu.cn）。复用 UBAA 已建立的 CASTGC 会话，
 * CAS 跳转到 it.buaa.edu.cn 邮箱入口，获取其会话 cookie 供 WebView 注入展示邮箱。
 */
object MailPortal {
  private const val MAIL_SERVICE = "https://it.buaa.edu.cn/frontend/login/index?redirect=https%3A%2F%2Fit.buaa.edu.cn%2Ffrontend%2Fmail%2Flogin"
  private const val MAIL_ENTRY = "https://it.buaa.edu.cn/frontend/mail/login"

  /** 触发 CAS 登录到 it.buaa.edu.cn 邮箱，使 jar 持有其会话 cookie。 */
  suspend fun ensureSession(): Result<Unit> {
    return try {
      val client = LocalUpstreamClientProvider.shared()
      // 带 CASTGC 访问 SSO login?service=<it 邮箱>，SSO 自动 302 到邮箱入口并种下会话 cookie
      val encodedService = MAIL_SERVICE.encodeURLParameter()
      val loginUrl = "https://sso.buaa.edu.cn/login?service=$encodedService"
      val r = client.get(localUpstreamUrl(loginUrl)) {
        header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      }
      platformLog("MAIL", "CAS跳转邮箱: status=${r.status}")
      // 触达信息平台邮箱入口，落地会话 cookie
      val r2 = client.get(localUpstreamUrl(MAIL_ENTRY)) {
        header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      }
      platformLog("MAIL", "it邮箱入口触达: status=${r2.status}")
      if (r.status == HttpStatusCode.Unauthorized || r2.status == HttpStatusCode.Unauthorized) {
        Result.failure(ApiCallException("邮箱登录需重新认证", HttpStatusCode.Unauthorized, "mail_error"))
      } else {
        Result.success(Unit)
      }
    } catch (e: Exception) {
      platformLog("MAIL", "邮箱CAS登录失败: ${e.message}")
      Result.failure(ApiCallException("邮箱登录失败: ${e.message}", HttpStatusCode.BadGateway, "mail_error"))
    }
  }

  /** 提取 it.buaa.edu.cn / mail.buaa.edu.cn / SSO 相关 cookie（供 WebView 注入）。 */
  fun cookieHeader(): String {
    val mode = ConnectionRuntime.currentMode()?.takeIf { it != ConnectionMode.SERVER_RELAY } ?: ConnectionMode.DIRECT
    val records = LocalCookieStore.load(mode)
    val parts = mutableListOf<String>()
    for (record in records) {
      val cookie = record.cookie
      val domain = (cookie.domain ?: "").lowercase()
      val isPortal = domain.endsWith("it.buaa.edu.cn") || domain.endsWith("mail.buaa.edu.cn") || domain == "buaa.edu.cn"
      val isSso = cookie.name == "CASTGC" || cookie.name.startsWith("sso_buaa") || domain.endsWith("sso.buaa.edu.cn")
      if (isPortal || isSso) {
        val name = cookie.name
        val value = cookie.value
        if (name.isNotBlank() && value.isNotBlank()) parts += "$name=$value"
      }
    }
    return parts.distinct().joinToString("; ")
  }
}
