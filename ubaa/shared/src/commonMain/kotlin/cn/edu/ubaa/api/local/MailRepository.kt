package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.ConnectionMode
import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.auth.ApiCallException
import cn.edu.ubaa.api.network.platformLog
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Coremail 一封邮件的摘要字段（对齐 mbox:listMessages 返回 var[]）。 */
@Serializable
data class CoremailMessage(
    val id: String = "",
    val fid: Int = 0,
    val size: Long = 0,
    val from: String = "",
    val to: String = "",
    val subject: String = "(无主题)",
    @SerialName("sentDate") val sentDate: String = "",
    @SerialName("receivedDate") val receivedDate: String = "",
    val summary: String = "",
    val sender: String = "",
    val priority: Int = 3,
    @SerialName("hasAttachment") val hasAttachment: Boolean = false,
    @SerialName("attachmentnum") val attachmentNum: Int = 0,
)

/** listMessages 顶层响应。 */
@Serializable
data class CoremailBoxResponse(
    val code: String = "",
    val desc: String? = null,
    @SerialName("var") val items: List<CoremailMessage> = emptyList(),
    @SerialName("returnTotal") val total: Long = 0,
)

/** listMessages 返回的分页结果。 */
data class CoremailPage(
    val items: List<CoremailMessage> = emptyList(),
    val total: Long = 0,
    val hasMore: Boolean = false,
)

/**
 * 北航 Coremail 邮箱数据仓库（新增独立文件，只读复用 shared client 与 cookie 存储）。
 *
 * 通过 Ktor(纯HTTP)调 Coremail JSON 接口，彻底绕开 WebView 渲染问题。
 * 会话基于 UBAA 既有 SSO 会话换取 Coremail.sid（每个请求轮换，需现场取）。
 */
object MailRepository {
  private const val INDEX_URL = "https://mail.buaa.edu.cn/coremail/XT/index.jsp"
  private const val JSON_BASE = "https://mail.buaa.edu.cn/coremail/s/json"
  private const val DESKTOP_UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
  private val json = Json { ignoreUnknownKeys = true }

  private fun currentMode(): ConnectionMode =
      ConnectionRuntime.currentMode()?.takeIf { it != ConnectionMode.SERVER_RELAY }
          ?: ConnectionMode.DIRECT

  /** 从 cookie jar 读取当前 Coremail.sid 值（若已持有）。 */
  fun currentSid(): String? {
    val records = LocalCookieStore.load(currentMode())
    return records.firstOrNull { it.cookie.name == "Coremail.sid" && it.cookie.value.isNotBlank() }
        ?.cookie?.value
  }

  /**
   * 确保持有有效 Coremail.sid：带既有 SSO 会话访问收件箱入口，换取/刷新 sid。
   * @return 当前有效的 sid；非空表示会话可用。
   */
  suspend fun ensureSession(): Result<String> {
    return try {
      val client = LocalUpstreamClientProvider.shared()
      // 访问收件箱入口触发 Coremail 校验，种/刷新 Coremail.sid（即使返回500也可能已种 sid）
      client.get(localUpstreamUrl(INDEX_URL)) {
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      }
      client.get(localUpstreamUrl(INDEX_URL)) {
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      }
      val sid = currentSid()
      if (sid.isNullOrBlank()) {
        Result.failure(ApiCallException("未获取到邮箱会话(sid)，请重新登录", HttpStatusCode.Unauthorized, "mail_error"))
      } else {
        Result.success(sid)
      }
    } catch (e: Exception) {
      platformLog("MAIL", "邮箱会话获取失败: ${e.message}")
      Result.failure(ApiCallException("邮箱会话获取失败: ${e.message}", HttpStatusCode.BadGateway, "mail_error"))
    }
  }

  /**
   * 拉取文件夹(fid)的邮件列表。收件箱 fid=1。
   * @param start 起始偏移(分页)。
   * @param limit 每页条数。
   */
  suspend fun listMessages(start: Int = 0, limit: Int = 20, fid: Int = 1): Result<CoremailPage> {
    return ensureSession().fold(
        onSuccess = { sid ->
          val url = "$JSON_BASE?sid=$sid&func=mbox%3AlistMessages"
          val referer = "$INDEX_URL?sid=$sid"
          val body =
              """{"start":$start,"limit":$limit,"mode":"count","order":"receivedDate","desc":true,"returnTotal":true,"returnTag":false,"summaryWindowSize":$limit,"fid":$fid,"mboxa":"","topFirst":true}"""
          try {
            val client = LocalUpstreamClientProvider.shared()
            val resp = client.post(localUpstreamUrl(url)) {
              header("Accept", "text/x-json")
              header("Content-Type", "text/x-json; tz=\"Asia/Shanghai\"")
              header("X-Requested-With", "XMLHttpRequest")
              header("Referer", referer)
              header("Origin", "https://mail.buaa.edu.cn")
              header("User-Agent", DESKTOP_UA)
              setBody(body)
            }
            val text = resp.bodyAsText()
            if (resp.status != HttpStatusCode.OK) {
              Result.failure(ApiCallException("拉取邮件失败: HTTP ${resp.status}", resp.status, "mail_error"))
            } else {
              val parsed = json.decodeFromString<CoremailBoxResponse>(text)
              if (parsed.code == "S_OK") {
                val hasMore = start + parsed.items.size < parsed.total
                Result.success(CoremailPage(parsed.items, parsed.total, hasMore))
              } else {
                Result.failure(ApiCallException("Coremail 返回错误: ${parsed.desc ?: parsed.code}", resp.status, "mail_error"))
              }
            }
          } catch (e: Exception) {
            platformLog("MAIL", "listMessages 异常: ${e.message}")
            Result.failure(ApiCallException("拉取邮件异常: ${e.message}", HttpStatusCode.BadGateway, "mail_error"))
          }
        },
        onFailure = { Result.failure(it) },
    )
  }

  /** 解析 Coremail 发件人字段（"名字" <邮箱> 或 邮箱）→ 展示名。 */
  fun displayFrom(raw: String): String {
    if (raw.isBlank()) return "未知"
    // 优先取引号里的姓名
    val q1 = raw.indexOf('"')
    val q2 = raw.lastIndexOf('"')
    if (q1 >= 0 && q2 > q1) {
      val name = raw.substring(q1 + 1, q2).trim()
      if (name.isNotBlank()) return name
    }
    // 其次取 <邮箱>
    val lt = raw.lastIndexOf('<')
    val gt = raw.indexOf('>', if (lt >= 0) lt else 0)
    if (lt >= 0 && gt > lt) {
      val mail = raw.substring(lt + 1, gt).trim()
      if (mail.isNotBlank()) return mail.substringBefore('@').ifBlank { mail }
    }
    // 兜底
    return raw.trim().substringBefore('@').ifBlank { raw }
  }

  /** 时间展示：sentDate "2026-08-12 16:34:27" → 简短 "08-12 16:34"。 */
  fun displayTime(sentDate: String): String {
    if (sentDate.isBlank()) return ""
    val mmdd = sentDate.substringAfter("-").substringBefore(" ")
    val hhmm = sentDate.substringAfter(" ").take(5)
    return "$mmdd $hhmm"
  }
}
