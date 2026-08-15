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
 * 登录链路（关键：it.buaa.edu.cn/frontend/mail/login 是能触发 CAS 认证的 WAP 入口，
 * Coremail 收件箱 mail.buaa.edu.cn 不是独立登录页）：
 *   访问 it.buaa.edu.cn/frontend/mail/login
 *   → 302 sso.buaa.edu.cn/login?service=<it.buaa 服务地址>
 *   → 已有 SSO 会话则自动通过，否则输账密
 *   → 认证成功 302 回 it.buaa 前端 → 建立 it.buaa 会话 → 前端跳转 mail.buaa.edu.cn Coremail → 收件箱
 *
 * 本实现复用 UBAA 已建立的 SSO 会话 cookie，先在 Ktor 侧预热链路，
 * 再把 buaa 相关各域的会话 cookie 按真实域名注入 WebView，让 WebView 自己走完跳转到收件箱。
 */
object MailPortal {
  // 能触发 CAS 认证的邮箱 WAP 入口（用户确认可用）
  private const val MAIL_ENTRY = "https://it.buaa.edu.cn/frontend/mail/login"

  /** 触发邮箱登录预热：携带已有 SSO 会话访问 WAP 入口，走通认证链并落地 it.buaa 会话 cookie。 */
  suspend fun ensureSession(): Result<Unit> {
    return try {
      val client = LocalUpstreamClientProvider.shared()
      val r = client.get(localUpstreamUrl(MAIL_ENTRY)) {
        header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      }
      platformLog("MAIL", "邮箱WAP入口触达: status=${r.status}")
      if (r.status == HttpStatusCode.Unauthorized || r.status == HttpStatusCode.Forbidden) {
        Result.failure(ApiCallException("邮箱登录需重新认证", r.status, "mail_error"))
      } else {
        Result.success(Unit)
      }
    } catch (e: Exception) {
      platformLog("MAIL", "邮箱登录预热失败: ${e.message}")
      Result.failure(ApiCallException("邮箱登录失败: ${e.message}", HttpStatusCode.BadGateway, "mail_error"))
    }
  }

  /** 兼容旧调用。 */
  fun cookieHeader(): String {
    return domainCookieHeaders().joinToString("; ") { it.second }
  }

  /**
   * 供 WebView 按域注入的 cookie 集合。把所有与 buaa 登录相关的独立域会话 cookie 按真实 domain 拆分：
   *   - sso.buaa.edu.cn ← CASTGC / `_zte_sid_` / `_7da9a` / `insert_cookie` / JSESSIONID（SSO 会话）
   *   - .buaa.edu.cn     ← `_zte_cid_` 等通配父域
   *   - it.buaa.edu.cn   ← it 信息平台会话 cookie
   *   - mail.buaa.edu.cn ← Coremail / Coremail.sid（若已换到）
   *
   * WebView 加载 it/wap 入口后会自己走 CAS 认证链（SSO 已登录则自动放行），最终落到 Coremail 收件箱。
   *
   * @return List of (注入目标 URL, "name=value; name=value")；空列表表示无可注入 cookie。
   */
  fun domainCookieHeaders(): List<Pair<String, String>> {
    val mode = ConnectionRuntime.currentMode()?.takeIf { it != ConnectionMode.SERVER_RELAY } ?: ConnectionMode.DIRECT
    val records = LocalCookieStore.load(mode)
    val sso = mutableListOf<String>()
    val parent = mutableListOf<String>() // .buaa.edu.cn 通配父域
    val itPortal = mutableListOf<String>()
    val mail = mutableListOf<String>()
    for (record in records) {
      val cookie = record.cookie
      val domain = (cookie.domain ?: "").lowercase()
      val name = cookie.name
      val value = cookie.value
      if (name.isBlank() || value.isBlank()) continue
      when {
        // SSO 统一认证域（sso.buaa.edu.cn 及其子域）
        name == "CASTGC" || domain.contains("sso.buaa.edu.cn") || name.startsWith("sso_buaa") ->
            sso += "$name=$value"
        // .buaa.edu.cn 通配父域（_zte_cid_ 等，供所有 buaa 子域使用）
        domain.contains("buaa.edu.cn") && name in listOf("_zte_cid_") ->
            parent += "$name=$value"
        // Coremail 邮箱域（mail.buaa.edu.cn）
        domain.contains("mail.buaa.edu.cn") -> mail += "$name=$value"
        // it 信息平台 / 门户域会话 cookie
        domain.contains("it.buaa.edu.cn") -> itPortal += "$name=$value"
      }
    }
    val result = mutableListOf<Pair<String, String>>()
    if (sso.isNotEmpty()) result += "https://sso.buaa.edu.cn" to sso.distinct().joinToString("; ")
    if (parent.isNotEmpty()) result += "https://buaa.edu.cn" to parent.distinct().joinToString("; ") { "$it; Domain=.buaa.edu.cn" }
    if (itPortal.isNotEmpty()) result += MAIL_ENTRY to itPortal.distinct().joinToString("; ")
    if (mail.isNotEmpty()) result += "https://mail.buaa.edu.cn" to mail.distinct().joinToString("; ")
    return result
  }
}
