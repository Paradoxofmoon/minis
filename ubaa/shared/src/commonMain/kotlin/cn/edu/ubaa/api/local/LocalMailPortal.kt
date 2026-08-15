package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.ConnectionMode
import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.auth.ApiCallException
import cn.edu.ubaa.api.network.platformLog
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter

/**
 * 北航邮箱（mail.buaa.edu.cn，Coremail）CAS 登录与会话桥接。
 *
 * 邮箱系统是 Coremail（mail.buaa.edu.cn），通过北航统一身份认证(sso.buaa.edu.cn)登录。
 * 复用 UBAA 已建立的 SSO 会话 cookie（_zte_sid_/_7da9a/insert_cookie/_zte_cid_ 等），
 * 先在 Ktor 侧访问 Coremail 收件箱入口触发校验、换取 Coremail.sid 会话 cookie，
 * 再把这些会话 cookie 按真实域名注入 WebView，使 WebView 加载收件箱即已登录。
 */
object MailPortal {
  // Coremail 收件箱入口（真实邮箱域，非 WAP 版）
  private const val MAIL_ENTRY = "https://mail.buaa.edu.cn/coremail/XT/index.jsp"

  /**
   * 触发 Coremail 邮箱登录：携带已有 SSO 会话访问收件箱入口，
   * 使 shared client 的 cookie jar 持有 Coremail.sid 等邮箱会话 cookie。
   */
  suspend fun ensureSession(): Result<Unit> {
    return try {
      val client = LocalUpstreamClientProvider.shared()
      // 带 SSO 会话访问 Coremail 收件箱，Coremail 校验后种 sid / 重定向到收件箱
      val r = client.get(localUpstreamUrl(MAIL_ENTRY)) {
        header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      }
      platformLog("MAIL", "Coremail收件箱触达: status=${r.status}")
      if (r.status == HttpStatusCode.Unauthorized || r.status == HttpStatusCode.Forbidden) {
        Result.failure(ApiCallException("邮箱登录需重新认证", r.status, "mail_error"))
      } else {
        Result.success(Unit)
      }
    } catch (e: Exception) {
      platformLog("MAIL", "邮箱CAS登录失败: ${e.message}")
      Result.failure(ApiCallException("邮箱登录失败: ${e.message}", HttpStatusCode.BadGateway, "mail_error"))
    }
  }

  /**
   * 构建 WebView 收件箱 URL。Coremail 的收件箱地址带 sid 参数（index.jsp?sid=<值>），
   * 该 sid 与 Coremail.sid cookie 相同。从已持有的 cookie 中提取并拼到 URL，
   * 使 WebView 直接命中已验证的收件箱会话；若无 sid 则回退到不带参数入口。
   */
  fun buildMailUrl(): String {
    val mode = ConnectionRuntime.currentMode()?.takeIf { it != ConnectionMode.SERVER_RELAY } ?: ConnectionMode.DIRECT
    val records = LocalCookieStore.load(mode)
    for (record in records) {
      val cookie = record.cookie
      if (cookie.name == "Coremail.sid" && cookie.value.isNotBlank()) {
        return "$MAIL_ENTRY?sid=${cookie.value.encodeURLParameter()}"
      }
    }
    return MAIL_ENTRY
  }

  /**
   * 提取邮箱相关 cookie（兼容旧调用，单串拼接）。
   */
  fun cookieHeader(): String {
    return domainCookieHeaders().joinToString("; ") { it.second }
  }

  /**
   * 供 WebView 按域注入的 cookie 集合。
   *
   * Coremail 邮箱涉及多个独立域名，必须按 cookie 的真实 domain 分别注入：
   *   - sso.buaa.edu.cn ← CASTGC / _zte_sid_ / _7da9a / insert_cookie / JSESSIONID（SSO 会话）
   *   - .buaa.edu.cn     ← _zte_cid_（通配父域，SSO 与邮箱共用）
   *   - mail.buaa.edu.cn ← Coremail / Coremail.sid（Coremail 邮箱会话）
   *
   * @return List of (注入目标 URL, "name=value; name=value")；空列表表示无可注入 cookie。
   */
  fun domainCookieHeaders(): List<Pair<String, String>> {
    val mode = ConnectionRuntime.currentMode()?.takeIf { it != ConnectionMode.SERVER_RELAY } ?: ConnectionMode.DIRECT
    val records = LocalCookieStore.load(mode)
    val sso = mutableListOf<String>()
    val port = mutableListOf<String>() // .buaa.edu.cn 通配
    val mail = mutableListOf<String>()
    for (record in records) {
      val cookie = record.cookie
      val domain = (cookie.domain ?: "").lowercase()
      val name = cookie.name
      val value = cookie.value
      if (name.isBlank() || value.isBlank()) continue
      when {
        // SSO 统一认证域
        name == "CASTGC" || name.startsWith("sso_buaa") || domain.endsWith("sso.buaa.edu.cn") -> {
          sso += "$name=$value"
        }
        // .buaa.edu.cn 通配父域（_zte_cid_）
        domain == ".buaa.edu.cn" -> {
          port += "$name=$value"
        }
        // Coremail 邮箱域
        domain.endsWith("mail.buaa.edu.cn") -> {
          mail += "$name=$value"
        }
      }
    }
    val result = mutableListOf<Pair<String, String>>()
    if (sso.isNotEmpty()) result += "https://sso.buaa.edu.cn" to sso.distinct().joinToString("; ")
    // _zte_cid_ 是 .buaa.edu.cn 通配父域 cookie；显式带 Domain 属性确保能发给 mail/sso 等所有子域
    if (port.isNotEmpty()) result += "https://buaa.edu.cn" to port.distinct().joinToString("; ") { "$it; Domain=.buaa.edu.cn" }
    if (mail.isNotEmpty()) result += MAIL_ENTRY to mail.distinct().joinToString("; ")
    return result
  }
}
