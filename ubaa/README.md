# UBAA 去统计上报版（自编译分支）

基于 [BUAASubnet/UBAA](https://github.com/BUAASubnet/UBAA)（北航校园客户端，MIT 开源）修改的自用版本。

## 与原版的差异

### 1. 移除登录统计上报
`shared/src/commonMain/kotlin/cn/edu/ubaa/api/auth/LoginStatsReporter.kt` 中 `defaultReporter()` 改为空实现，登录后不再向 UBAA 服务端上报学号和连接模式。

### 2. 新增功能
| 功能 | 实现方式 | 状态 |
|---|---|---|
| 校园卡余额查询 | CAS SSO → pass.cc-pay.cn API | ✅ 可用 |
| 校园网流量查询 | app.buaa.edu.cn HTML 解析 | ⚠️ 解析待修复 |
| 校园网充值 (zfw) | RSA 登录 + WebView 内嵌充值页 | ✅ 可用 |
| 电费充值 (shsd) | WebView 内嵌 + 表号历史自动填充 | ✅ 可用 |

### 3. 架构重构（渐进式）
- **ServiceRegistry** (`shared/.../api/network/ServiceRegistry.kt`)：轻量客户端工厂，给不需要三模式（DIRECT/WEBVPN/SERVER_RELAY）的功能用，内存 Cookie 隔离会话
- **InAppWebView** (`composeApp/.../ui/component/`)：跨平台 WebView 组件（Android 原生 WebView，桌面/iOS 打开浏览器），支持 JS 注入和 Cookie 注入
- 旧功能仍走 ApiFactory，不受影响

### 4. 构建环境适配
- Gradle 分发走腾讯镜像，Maven 依赖走阿里云镜像（`settings.gradle.kts`）
- 需要 JDK 21 和 Android SDK

## 编译

```bash
./gradlew :androidApp:assembleDebug
```

产物：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`

## 已知问题

- 校园网流量页面解析逻辑与实际页面结构不匹配，数据展示不正确（调试信息写入 `cache/ubaa_debug/` 供 adb pull 分析）
- 校园网充值登录后通过 Cookie 注入 WebView 显示已登录充值页（风格与原生 UI 不同，后续可逆向充值 API 改原生界面）
- 启动时 CAS 登录流程较慢

## 调试辅助

- `DebugFileSink`：把调试 HTML/文本写到 app cache 目录 `ubaa_debug/`，`adb pull /data/user/0/cn.edu.ubaa/cache/ubaa_debug/` 即可拉取
- `PlatformLog`：Android 上用 `Log.e`（华为设备会过滤 D 级日志）
