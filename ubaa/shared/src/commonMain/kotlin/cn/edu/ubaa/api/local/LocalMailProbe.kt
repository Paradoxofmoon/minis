package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.ConnectionMode
import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.network.platformLog
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText

/**
 * 邮箱会话探测（阶段0，独立文件，只读复用原有 shared client 与 cookie 存储）。
 *
 * 目标：验证能否用 UBAA 既有 SSO 会话，通过 Ktor(纯HTTP，不需渲染)换到 Coremail.sid，
 * 从而支撑"原生UI抓Coremail JSON接口"方案。探测结果返回给 UI 直接展示，免去抓 logcat。
 */
object MailProbe {
  private const val MAIL_ENTRY = "https://mail.buaa.edu.cn/coremail/XT/index.jsp"

  /**
   * 执行探测：访问 Coremail 收件箱入口，检查是否换到 Coremail.sid，并统计拿到的 buaa/coremail cookie。
   * @return 人类可读的探测报告（供 UI 展示）。
   */
  suspend fun probe(): String {
    val sb = StringBuilder()
    try {
      val mode = ConnectionRuntime.currentMode()?.takeIf { it != ConnectionMode.SERVER_RELAY }
          ?: ConnectionMode.DIRECT
      // 只读复用：共享 cookie jar（含 UBAA SSO 会话）
      val client = LocalUpstreamClientProvider.shared()
      sb.appendLine("mode=$mode")

      // 1) 访问 Coremail 收件箱入口
      val r = client.get(localUpstreamUrl(MAIL_ENTRY)) {
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      }
      sb.appendLine("coremail_index status=${r.status.value}")
      platformLog("MAILPROBE", "coremail index status=${r.status.value}")

      // 2) 检查 cookie jar 里现在有什么 buaa 相关 cookie（含 Coremail.sid 是否已拿到）
      val records = LocalCookieStore.load(mode)
      val names = mutableListOf<String>()
      var hasCoremailSid = false
      var coremailSid = ""
      var hasZteCid = false
      var hasSso = false
      for (rec in records) {
        val c = rec.cookie
        val domain = (c.domain ?: "").lowercase()
        names += "${c.name}[${domain}]"
        if (c.name == "Coremail.sid") { hasCoremailSid = true; coremailSid = c.value }
        if (c.name == "_zte_cid_") hasZteCid = true
        if (c.name == "CASTGC" || c.name == "_zte_sid_" || domain.contains("sso.buaa")) hasSso = true
      }
      sb.appendLine("cookie count=${records.size}")
      sb.appendLine("hasSSO=$hasSso  hasZteCid=$hasZteCid")
      sb.append("hasCoremailSid=$hasCoremailSid")
      if (hasCoremailSid) sb.append("  value=${coremailSid.take(12)}...")
      sb.appendLine()
      sb.appendLine("cookieNames=" + names.distinct().joinToString(",").take(600))

      // 3) 若拿到 sid，再试拉一页收件箱(验证接口可调)
      if (hasCoremailSid) {
        try {
          val listUrl = "$MAIL_ENTRY".replace("/index.jsp", "") +
              "/s/json?sid=${coremailSid}&func=mbox%3AlistMessages"
          val lr = client.post(localUpstreamUrl(listUrl)) {
            header("Content-Type", "text/x-json; tz=\"Asia/Shanghai\"")
            setBody("""{"start":0,"limit":5,"mode":"count","order":"receivedDate","desc":true,"returnTotal":true,"returnTag":false,"summaryWindowSize":5,"fid":1,"mboxa":"","topFirst":true}""")
          }
          sb.appendLine("listMessages status=${lr.status.value} body=${lr.bodyAsText().take(300)}")
        } catch (pe: Exception) {
          sb.appendLine("listMessages err: ${pe.message}")
        }
      }
    } catch (e: Exception) {
      sb.appendLine("EXCEPTION: ${e.message}")
      platformLog("MAILPROBE", "probe exception ${e.message}")
    }
    return sb.toString()
  }
}
