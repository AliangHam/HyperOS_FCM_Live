# HyperGreeze

去除澎湃系统（HyperOS）对谷歌推送连接相关广播的限制，让 FCM 推送在小米设备上保持可用

当前版本 **3.2.0（33）** · 基于 LSPosed API 102 · GPL-3.0

[使用帮助（HELP.md）](HELP.md) · [更新日志](CHANGELOG.md) · [下载](https://github.com/iamqwert/HyperOS_FCM_Live/releases)

---

## 它做什么

澎湃的省电策略会限制后台应用的广播与网络。谷歌推送（FCM）靠 GMS 发一条广播把应用叫醒，这条广播一旦被延后或拦下，应用就收不到推送

本模块在系统框架层（`system_server` 与 `com.miui.powerkeeper`）改掉这套判定：放行谷歌推送与重连相关的广播，不让 GMS 断网、被冻结或被拉黑；收到推送时按白名单为应用申请约 2 秒的临时省电豁免，并让应用即使在「已停止」状态下也能收到这条广播

它**不做**的事同样重要：不保活、不阻止系统回收应用进程、不修改 GMS 本身。这是刻意的选择 —— 模块的职责是「让推送送达」，不是「让应用常驻」

> 代价：放行推送重连、允许应用被推送唤醒，本身就是耗电的来源。启用后系统耗电可能上升

---

## 功能

| 分类 | 项目 |
|---|---|
| 推送修复 | 放行 c2dm 投递与重连广播、补 `FLAG_INCLUDE_STOPPED_PACKAGES`、过 greeze 自启动与冻结门、投递时约 2 秒省电豁免、c2dm 不延后 |
| GMS 保护 | 写入不限制名单、省电场景改为「无限制」、跳过快速冻结、关闭网络限制、加入睡眠模式白名单、睡眠模式结束后主动重连 |
| 白名单 | 按应用粒度决定谁拿到「被推送唤醒」特权；支持多选批量加入/移出、搜索、全选 |
| 严格模式 | 在白名单之上追加收窄：把「允许推送广播送达」与「防止被强停」也收回到勾选的应用 |
| 列表过滤 | 显示系统应用、展示支持 FCM 的应用、排除 MiPush 应用 |
| 工具 | FCM 诊断（打开 GMS 自带诊断界面）、白名单导出/导入、检查更新 |
| 外观 | 主题模式、动态颜色、调色风格、颜色规格、隐藏桌面图标 |
| 入口 | 桌面长按图标直达设置、帮助、FCM 诊断 |
| 其它 | 隐私与权限说明、开放源代码许可、Material 3 Expressive 下拉刷新、Android 12+ 回弹 |

> 「帮助」入口用浏览器打开在线文档 [HELP.md](HELP.md)，应用内不再内置帮助页 —— 文案只保留一份在仓库里，改完即生效，不必发版

---

## 工作原理

一次推送的链路是：GMS 收到 FCM 消息 → 发出 `com.google.android.c2dm.intent.RECEIVE` 广播 → 目标应用的接收器被执行

澎湃在这条链路上叠加了多层门控，模块按门控逐层处理：

| 环节 | 系统侧门控 | 模块的处理 |
|---|---|---|
| 广播能否送达 | `GreezeManagerService#isAllowBroadcast` | 按白名单（严格模式下）放行 |
| 应用处于「已停止」 | stopped-packages 标记 | 补 `FLAG_INCLUDE_STOPPED_PACKAGES` |
| 自启动判定 | `BroadcastQueueModernStubImpl#checkApplicationAutoStart` | 按白名单放行 |
| 应用已被冻结 | `isRestrictReceiver`（greeze 广播门） | 放行并复现原生解冻 |
| 省电场景 / 待机 | `AppStandbyController#setUidState` | 改写为 allow，保证 GMS 不被限 |
| 冻结策略 | `AurogonImmobulusMode#isNoRestrictApp` 等 | 对 GMS 返回豁免 |
| 广播被延后 | `DomesticPolicyManager#deferBroadcast` | 返回 false，c2dm 不延后 |
| 强停 | `isForceStopEnable` | 严格模式下只对勾选的应用生效 |

被 hook 的宿主只有两个：`system`（系统框架）与 `com.miui.powerkeeper`。主要目标包括 `ActivityManagerService`、`BroadcastQueueModernStubImpl`、`GreezeManagerService`、`AurogonImmobulusMode`、`DomesticPolicyManager` / `InternationalPolicyManager`、`AppStandbyController`、`ProcessPolicy`、`ListAppsManager`、`GmsObserver`、`MiuiNetworkPolicyManagerService` 等

GMS 侧的四项保护（对应 `Hooker` 里的 KDoc 标签）：

- **P1** —— 把 GMS 保持在 `Settings.System.MILLET_NO_RESTRICT_APP` 名单里
- **P2** —— 在 system_server 中为 greeze 冻结路径兜底
- **P3** —— 把 GMS 的编译场景强制为「无限制」（8）而不是 0
- **P4** —— 发现 GMS 已被冻住时，请求 GMS/GSF 重新建立连接并解冻

---

## 安装与启用

### 要求

- 小米 / 红米设备，澎湃（HyperOS）3 或 4
- Android 15 及以上（模块 `minSdk 35`，仅 arm64）
- 已安装 LSPosed（模块要求 API ≥ 101，面向 102）
- 设备上 Google Play 服务可用

### 步骤

1. 从 [Releases](https://github.com/iamqwert/HyperOS_FCM_Live/releases) 下载 APK 并安装
2. 在 LSPosed 的「模块」列表中启用 HyperGreeze
3. 作用域无需手动勾选 —— 模块声明了静态作用域，LSPosed 会自动应用到 `system` 与 `com.miui.powerkeeper`
4. **不需要重启设备**。模块开启 `autoHotReload`，启用或更新后自动热重载
5. 打开模块，在应用列表里勾选需要即时推送的应用

详细的白名单与严格模式说明见 [HELP.md](HELP.md)

---

## 兼容性

两种 PowerKeeper 设计均已适配，模块在运行时按方法是否存在自动探测，无需手动切换：

- **HyperOS 3** —— 旧设计（`initGmsChain` / `updateGmsAlarm` / `updateGmsNetWork` / `updateGoogleReletivesWakelock` 等）
- **HyperOS 4** —— 新设计（`updateFrameworkGmsNetStatus`）

两者 `versionName` 同为 4.2.00，但实际实现完全不同，因此模块不做版本号判断

已验证机型：

| 机型 | 系统 |
|---|---|
| Xiaomi 17 Pro Max（popsicle） | OS3.0.319.0.WPBCNXM / Android 16 |
| Xiaomi（myron） | HyperOS V816 / Android 17（API 37） |

---

## 构建

需要 JDK 21 与 Android SDK

```bash
./gradlew assembleDebug
```

Release 构建默认**拒绝**用公开 debug 密钥签名（防止被同名签名包冒充覆盖）。本机构建需要配置签名：

```properties
# local.properties
storeFile=/path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

未配置签名时可用 `./gradlew assembleRelease -PallowDebugSignedRelease` 产出 debug 签名的包，仅用于本地安装与调试，不要对外分发

对外发布由 GitHub Actions 完成：手动触发 `Android CI`，把 `publish_release` 置为 `true`，发行说明由 `CHANGELOG.md` 按 `^## <version>` 抽取

---

## 隐私

模块只申请三个权限，全部为本地用途：

| 权限 | 用途 |
|---|---|
| `QUERY_ALL_PACKAGES` | 在本地读取已安装应用 |
| `com.android.permission.GET_INSTALLED_APPS` | HyperOS 上获取完整应用列表 |
| `INTERNET` | 仅用于从 GitHub 检查更新 |

不收集、不上传个人数据；白名单与设置仅保存在本机；无统计分析、无广告、无追踪 SDK

---

## 已知边界

- **c2dm 不延后对所有应用一致**，不读白名单也不读严格模式 —— 未勾选应用同样不会被延后
- **严格模式名义收权三处，其中「免网络限制」依赖国际版策略实现**。实测 HyperOS V816 走国内版策略，该项不参与，实际收权两处
- **GMS 保护有一部分以整机策略表的形式写入**（省电场景、进程白名单、不限制名单、睡眠白名单），对整台设备生效，勾选与严格模式都不会把它们收窄
- **目标应用不在免冻集合内**。被推送拉起后进入缓存进程、之后被内存回收或 greeze 冻结都属正常，不影响下一次推送
- **高内存负载时 GMS 仍可能短暂掉线**，但会立即恢复

---

## 致谢

本项目是修改版，参考并致谢以下项目与贡献者：

- [Howard20181/HyperOS_FCM_Live](https://github.com/Howard20181/HyperOS_FCM_Live)
- [billtv/HyperOS_FCM_Live](https://github.com/billtv/HyperOS_FCM_Live)
- [HappyMax0/FCMPushViewer](https://github.com/HappyMax0/FCMPushViewer)
- [dingwen07/hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix)
- [Kr328/HyperOSFCMFix](https://github.com/Kr328/HyperOSFCMFix)
- [ReedGAOOO/FCMGuard-HyperOS](https://github.com/ReedGAOOO/FCMGuard-HyperOS)
- [zuohl/HyperOS_FCM_Live](https://github.com/zuohl/HyperOS_FCM_Live)
- `GET_INSTALLED_APPS` 运行时权限申请思路参考自 250king 的 [PR #1](https://github.com/250king/HyperOS_FCM_Live/pull/1)

---

## 许可

本项目采用 [GNU GPL-3.0](LICENSE) 协议
