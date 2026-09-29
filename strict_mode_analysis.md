# 严格模式与现有保护目标的一致性分析报告

对象：`HyperFCMLive` v3.2.0.33，`Hooker.kt`（含本次新增的 `AppStandbyController#setUidState` 钩子）
问题：开启应用内「严格模式」后，是否与模块现有目标冲突 / 不适配 / 不完全适配
方法：静态审计（逐 hook 点核对其守门条件）+ 与 help 文案所定义的契约作对比

---

## 0. 摘要

| 结论 | 内容 |
|---|---|
| **是否存在目标冲突** | **否。** 严格模式不存在与模块目标相反的方向：所有守卫都只做"收窄"，没有任何一处因开启严格模式而导致 GMS 失去保护，或让已勾选应用被降级 |
| **是否存在不适配** | **是，1 处（P1）**：`DomesticPolicyManager#deferBroadcast` 对 `ACTION_REMOTE_INTENT` 无条件跳过延迟，既不看白名单也不看严格模式，与帮助页"未勾选的应用推送可能到得晚一些"的契约相反；该处的代码注释还自称"与严格模式的最小干预哲学一致"，判断与实际相反 |
| **是否存在不完全适配** | **是，3 处**：守卫函数之间的 GMS 豁免不对称（P2-1）、严格模式实际作用域远小于开关给人的印象（P2-2）、白名单首读失败时会有一段开局 fail-open 窗口（P3） |
| **是否为设计取舍而非缺陷** | GMS 专属钩子全部无条件生效（含若干具有全局副作用的写入）属于**契约内行为**，但"全局副作用"这一层未在文案中披露（P4） |
| **本机实测额外发现** | **P5（新增）**：`isPushApp` 钩子在本机是**死钩子** —— ROM 取证显示本机 greeze 实际选用的策略实现是 `DomesticPolicyManager`，它**根本没有 `isPushApp` 方法**。因此严格模式名义上的 3 处收权，在本机**实际只生效 2 处** |

一句话：**方向是对的，边界没画干净，而且实际收权面比宣称的还要小。** 严格模式没有把事情做错，但它宣称的边界比实际执行的边界更窄；在本 ROM 上，其中一条边界甚至是空的。

---

## 1. 契约基线：帮助页承诺了什么

审计的判定基准取自 `values/strings.xml`，共 5 条：

| # | 承诺 | 出处 |
|---|---|---|
| C1 | 勾选了至少一个应用 → 模块**只对勾选的应用生效**；未勾选的应用会还原系统默认判定行为，**与未装模块时的表现一致** | `help_strict_on_body` (135) |
| C2 | 未勾选的应用：投递链路上的额外权限拿不到，推送可能晚到或打开才送达；**再打开严格模式**，连其余的判定也改回系统默认 | `help_allowlist_checked_body` (132) |
| C3 | 一个都不勾选 → 全部放行 | `help_allowlist_empty_body` (130) / `help_strict_unchanged_body` (137) |
| C4 | **GMS 与推送重连始终受保护，不依赖勾选，也不受严格模式影响** | `help_power_scope_body` (144) / `help_strict_on_body` (135) |
| C5 | 白名单与严格模式只决定：自启动唤起、「已 stopped」唤醒、约 2 秒省电豁免这三类额外权限 | `help_power_scope_body` (144) |

注意 C2 的表述顺序：白名单单独就已经收窄投递链路，严格模式是在此之上**追加**收窄。这条决定了下一节不少"不一致"其实是**符合契约**的。

## 2. 两个守卫函数的实际语义

```kotlin
// Hooker.kt:1725
private fun shouldWake(targetPackage: String?): Boolean {
    val allowlist = getFcmAllowlist()
    return allowlist.isEmpty() || allowlist.contains(targetPackage)   // 不读 sStrictMode
}

// Hooker.kt:1730
private fun shouldApply(packageName: String?): Boolean {
    if (!sStrictMode) return true                                      // 只在严格模式下才收窄
    val allowlist = getFcmAllowlist()
    if (allowlist.isEmpty()) return true
    return allowlist.contains(packageName) ||
        GMS_PACKAGE_NAME == packageName || GMS_PERSISTENT_PROCESS_NAME == packageName
}
```

三点差异：

1. **`shouldWake` 完全不读 `sStrictMode`** —— 与 C2「白名单单独即收窄投递」**一致**，不是缺陷
2. **`shouldWake` 没有 GMS 豁免，`shouldApply` 有** —— 与 C4 存在潜在冲突（见 P2-1）
3. 两者都是 **fail-open**（白名单为空放行全部）—— 与 C3 一致，但叠加"装载期首读失败"后会在开局放大（见 P3）

## 3. 覆盖矩阵

逐 hook 点核对。判定列含义：✅ 符合契约 / ⚠️ 不适配 / — 与两个开关无关（GMS 侧或全局）

| Hook 点 | 行号 | 守门 | 受严格模式影响 | 判定 |
|---|---|---|---|---|
| `AMS#broadcastIntentWithFeature`（补 stopped 标记 + 2 s 豁免） | 1915–1955 | `shouldWake` | 否（白名单单独生效） | ✅ 符合 C2/C5 |
| `BroadcastQueueModernStubImpl#checkApplicationAutoStart`（自启动） | 475–497 | `shouldWake` | 否 | ✅ 符合 C2/C5 |
| `GreezeManagerService#isRestrictReceiver`（暖/冻 receiver） | 526–550 | `shouldWake` | 否 | ✅ |
| `GreezeManagerService#isNeedCachedBroadcast`（冻结缓存） | 580–598 | `shouldWake` | 否 | ✅ |
| `GreezeManagerService#isAllowBroadcast` | 264–295 | `shouldApply` | **是** | ✅ 符合 C2 后半句 |
| `DomesticPolicyManager#deferBroadcast` | 365–378 | **无** | 否 | ⚠️ **P1** |
| `GreezeManagerService#deferBroadcastForMiui` | 304–309 | 无（仅 CN 队列） | 否 | ✅ CN 队列是 GMS 内部动作，符合 C4 |
| `InternationalPolicyManager#isPushApp` | 1991–2020 | `shouldApply` | **名义是，本机否** | ⚠️ **P5**：该类在本 ROM 上不被实例化，见 §4 P5 |
| `ProcessCleanerBase#isForceStopEnable` | 2038–2058 | `shouldApply` + `declaresFcmComponent` | **是** | ✅ 额外收紧也符合 C5 |
| `AppStandbyController#setUidState`（本次新增） | 1061–1101 | 无（`isGmsUid`） | 否 | ✅ GMS 专属，符合 C4 |
| `NetdExecutor` / `GmsObserver` 系列（PowerKeeper 侧） | 835–1061 | 无 | 否 | ✅ GMS 专属 |
| `MiuiNetworkPolicyManagerService` 睡眠白名单注入 | 737–805 | 无 | 否 | — 但存在全局副作用，**P4** |
| `ProcessPolicy#getWhiteList` | 611–628 | 无 | 否 | ✅ GMS 条目，入参列表为进程全局 |
| `AwareResourceControl#mNoNetworkBlackUids` | 648–699 | 无 | 否 | ✅ GMS 专属，开局构造后清理 |
| `ListAppsManager` 黑名单/数据白名单 | 382–458 | 无 | 否 | ✅ GMS 专属 |
| `GlobalFeatureConfigureHelper#getDozeWhiteListApps` | 1102–1138 | 无 | 否 | ✅ GMS 专属 |
| P1 `NoRestrictList` / P2 `GreezerNoRestrict` / P3 `ScenarioCompiler` | 1154–1599 | 无 | 否 | ✅ 均为 GMS 专属，符合 C4 |

共 6 个 c2dm（`ACTION_REMOTE_INTENT`）处理点，其中 **5 个有守门、1 个裸奔**（`deferBroadcast`）。

## 4. 问题清单

### P1（明确不适配，违反 C2/C5）——`DomesticPolicyManager#deferBroadcast` 无守门

```kotlin
// Hooker.kt:365
val action = chain.getArg(0) as? String
if (action != null &&
    (CN_DEFER_BROADCAST.contains(action) || ACTION_REMOTE_INTENT == action)
) {
    return@intercept false          // 任何 c2dm 都不延迟，不看白名单、不看严格模式
}
```

- **现象**：`CN_DEFER_BROADCAST` 四个动作（GCM_RECONNECT / GCM DISCONNECTED / CONNECTED / HEARTBEAT_ALARM，见 2114–2119）确属 GMS 内部，免延迟符合 C4；但 **`ACTION_REMOTE_INTENT` 是发给目标应用的**，无条件免延迟等于给未勾选应用也发了一张 C2 明确说"拿不到"的票
- **与契约的偏差（两处，其中一句承诺远强于另一句）**：
  - `help_allowlist_checked_body`："未勾选的应用拿不到这几项，推送可能到得晚一些，或要应用打开时才送达"。而"到得晚 / 打开才送达"正是广播被延迟的典型表现 —— 这条 hook 恰恰把该表现也一并消除了
  - `help_strict_on_body`："未勾选的应用会还原系统默认判定行为，**与未装模块时的表现一致**"。这句比上面的更强：**它排除了任何残余干预**，不只是"拿不到那几项特权"。未装模块时 c2dm 是会被 defer 的，装了之后不会 —— 这行 hook 直接使"等价未装模块"在投递延迟这一维度上不成立
- **注释本身是反的**：366–370 行写着 "Everything else follows the normal deferral policy (**aligned with strict mode's minimal-intervention philosophy**)"，但它豁免的部分恰恰违反了最小干预
- **影响面**：偏松弛而非失效，不会丢推送；用户对"我已限制到只保护这几个应用"的预期会被打破，且带上额外耗电
- **建议**：与相邻四个处理点统一，改为 `ACTION_REMOTE_INTENT == action && shouldWake(<目标包名>)`；若该方法拿不到包名（签名只有 `String action`），至少降级为 `shouldApply(<无法判定时按 null 处理>)` 或直接挂 `isRestrictNet` 式的调用栈判定。修之前需先在真机确认这条 defer 是否真的被 c2dm 命中（见 §6）

### P2-1（不完全适配）——`shouldWake` 缺 GMS 豁免，与 `shouldApply` 不对称

```kotlin
shouldApply : allowlist.contains(pkg) || GMS_PACKAGE_NAME == pkg || GMS_PERSISTENT_PROCESS_NAME == pkg
shouldWake  : allowlist.isEmpty() || allowlist.contains(targetPackage)      // 无 GMS 分支
```

- C4 承诺"GMS 与推送重连始终受保护"。当白名单非空且 callee 恰为 GMS 时，`shouldWake` 会返回 false，跳过 thaw / 自启动放行
- **命中面很小**：四处调用里，`checkApplicationAutoStart`、`isRestrictReceiver` 都要求 caller 是 GMS，callee 通常不是 GMS 自身；`isNeedCachedBroadcast` 无 caller 校验，是唯一理论上可能落在 GMS 上的
- 因此定级为**理论缺口**，不构成当前可观测问题；修起来是一行（补两个 GMS 常量判断），建议在 P1 一并处理时顺手对齐

### P2-2（作用域不匹配）——严格模式的实际作用域只有 3 个非投递路径

- `shouldApply` 只被 3 处使用：`isAllowBroadcast`、`isPushApp`、`isForceStopEnable`
- **在本 ROM 上实际只有 2 处**：`isPushApp` 那个类从不实例化（§4 P5），所以本机真实收权面是 `isAllowBroadcast` + `isForceStopEnable` 两条
- 投递主链路（AMS 广播广播方法、自启动、`isRestrictReceiver`、冻结缓存）的收窄由**白名单独立完成**，与开关无关
- 于是出现一个容易误解的状态：**只勾选应用、不开严格模式，投递链路也已经被限到勾选的应用** —— 这点与 C2 文本一致，但与 UI 上"严格模式"作为一个大开关的心理预期不一致；`MainActivity:1189` 附近的注释 "the toggle only decides what the module does for apps that are not checked" 在这三层之外并不成立
- 建议：不必改代码，把帮助页 C2 的因果讲得更直白（"勾选之后，即便不开严格模式，投递也已经只认勾选的应用；严格模式决定的是剩下的判定是否一并还原"）

### P3（时序 fail-open）——白名单加载完成前严格模式不生效

```
sAllowlist 初值 emptySet()                       // Hooker.kt:1628
hookAllowlist() → loadAllowlistFromRemotePrefs() // 1646，**同步**调用一次（不是异步）
getFcmAllowlist(): 若接收器未注册且距上次读取 >= ALLOWLIST_STALE_MS(10 s) → 异步重载后立即返回旧值
loadAllowlistFromRemotePrefs(): sAllowlistReadMs 刷新（**异常路径也会刷新**）；异常仅 log ERROR，不清空
```

**更正（本轮自纠）**：此前把首次加载写成"异步"，**不准确**。`hookAllowlist()`(1645-1648) 在装载时
**同步**调一次 `loadAllowlistFromRemotePrefs()`，之后再异步装接收器。所以：

- 开局 fail-open 窗口**只在同步读取抛异常时**才出现（远程 prefs 尚未就绪），不是常态路径
- 异常时 `sAllowlistReadMs` 仍被刷新(1642) ⇒ 下一次 `getFcmAllowlist()` 要等满 10 s 才触发重试，**失败反而把重试推后**
- 另有一条更常见、且与读取无关的成因：`getStringSet(KEY_ALLOWLIST, emptySet())`(1636) 默认值非空，
  所以 1637 的 `else HashSet()` 是**死分支**；"键从未写入"时会正常读到空集 → 同样全放行（这就是 C3 的空名单语义，不是 bug）

- 影响仍是**短暂且偏松**，不是丢推送；但"装载时同步预读"这条建议**已经存在**，真正该做的是：
  在 `loadAllowlistFromRemotePrefs` 成功/失败各打一条 INFO/ERROR 日志（现在失败才打，成功不打），
  才能在日志里区分"严格模式已生效"与"还没读到名单"；以及异常后不等 10 s 就退避重试

### P4（设计取舍，非缺陷，但文案未披露）——GMS 侧护盘的全局副作用

以下动作对整机的策略表生效，既不看白名单也不受严格模式约束（且**不应该**被它们约束，否则违反 C4）：

| 动作 | 行号 | 全局副作用 |
|---|---|---|
| 睡眠模式网络白名单注入 GMS | 737–805 | 若 ROM 侧睡眠链原本因白名单为空而根本不开链，注入后可能变成"开链 + 仅 GMS 免掐"，其余 UID 夜间联网策略随之改变 |
| `ProcessPolicy#getWhiteList` 追加 GMS | 611–628 | 进程级白名单列表被改写（含原地写回 `addIfAbsentInPlace`） |
| `ListAppsManager` 黑名单移除 / 数据白名单追加 | 382–458 | 构造后清理 + 查询前追加，属常驻改写 |
| P1/P2/P3 免冻结、免限制、场景改写 | 1154–1599 | 让 GMS 永久处于 no-restrict 状态 |

这些都是**刻意为之且符合 C4**，但帮助页只写了"GMS 保护始终开启"，没有说明这些保护中有一部分是**以整机策略表的形式**落地的。若用户期待"严格模式 = 把模块影响收窄到我选的这几个应用"，这部分会落空。建议在 help 的"不受影响的部分"里点明。

#### P4 的真机实证（`dumpsys greezer`，无需等待推送）

| 观测 | 结果 |
|---|---|
| Telegram（上次端到端 FCM 验证用过）pid 10393，`oom_score_adj=905`、`STAT=S<` | **在 `Frozen processes` 列表内** |
| GMS 的 4 个进程 pid 6203 / 11329 / 25558 / 27186 | **全部不在冻结列表内** |

这组对照把 P4 从代码推断升级为运行时事实：

- **C4 成立**：GMS 四个进程都没被 greeze 冻结
- **目标应用不受保护这点也成立**：Telegram 以缓存态（adj 905）被 greeze 冻结 —— 这就是前文"推送后应用进缓存"之后链条的下一环（缓存 → 冻结）。这不是模块没生效，下次推送会重新冷启动；改变它需要在 greeze 侧给非 GMS 应用也加免冻钩子，代价远超严格模式的语义范围

**结论**：模块提供的免冻是 GMS 专属的，这点在方向上正确，但必须在排查"勾选了为什么还是被冻"时讲清楚，否则很容易被误判成模块失效。

### P5（本机实证）——`isPushApp` 钩子在本 ROM 上是从不执行的

这条把 §3 矩阵里原本标 ✅ 的一行推翻了。证据链由 **ROM 取证**（`miui-services.jar`，与本机 services.jar 大小一致，可确认为本机机型）与 **运行时**两端闭合：

| # | 证据 | 出处 |
|---|---|---|
| 1 | `PolicyManager` 是接口（`PUBLIC INTERFACE ABSTRACT`），`DomesticPolicyManager` / `InternationalPolicyManager` 是两个实现 | `classes2.dex` 类声明 |
| 2 | 选择逻辑在 `AurogonImmobulusMode#restorePolicyManager`：`mSetForceCnGlobal` 为假时走 `PolicyManager.isCnModel()`；命中 CN → `mCurrentCNPolicy = STATE_DOMESTIC` → `mPolicyManager = mDomesticPolicy`，否则走 `STATE_INTERNATIONAL` | 字节码偏移 `0x170404` 起 |
| 3 | `STATE_DOMESTIC = 1`、`STATE_INTERNATIONAL = 2` | `<clinit>` 字节码（`sput STATE_INTERNATIONAL` 前一指令为 `const/4 v0, #int 2`） |
| 4 | 运行时 `mCurrentCNPolicy: 1`、`mSetForceCnGlobal=false` | `adb shell dumpsys greezer` |
| 5 | **`DomesticPolicyManager` 没有 `isPushApp` 方法**（方法表内只有 `isRestrictNet(I)Z`）；`isPushApp` 仅存在于 `InternationalPolicyManager` | 两类方法清单对比 |
| 6 | `isPushApp` 的全部 3 个调用点都是 `invoke-direct`，即 `InternationalPolicyManager` **内部自调用**，无外部调用方 | 字节码 xref |

**推论**：本机 `mCurrentCNPolicy=1=STATE_DOMESTIC` ⇒ active 实现是 `DomesticPolicyManager` ⇒ `InternationalPolicyManager` 不被实例化 ⇒ 模块挂在它上面的 `isPushApp` 钩子**方法体永不执行**。

由此产生三层影响：

1. **严格模式在本机的真实收权面只有 2 处**（`isAllowBroadcast`、`isForceStopEnable`），不是 3 处 —— 这是 P2-2 的进一步收紧
2. 顺带解释了一个长期存在的怪象：`Hooker.kt:2009` 那条一次性 INFO `"isPushApp: caller isRestrictNet matched"` **从未在日志里出现过**。此前只能归因于"成功路径无日志"，实际是这条路径整体没有被执行
3. **比严格模式本身更值得处理的功能缺口**：本机 `isRestrictNet` 走的是 Domestic 版本，而模块**没有覆盖它**。帮助页 `help_power_actions_body` 描述的一类"推送网络限制豁免"，在本 ROM 上并未真正落地

第 3 点需要另补 `DomesticPolicyManager#isRestrictNet` 钩子，属**功能补全**而非严格模式一致性问题，本报告不实现，仅登记。

## 5. 确认一致、无需改动的部分

以下判断一度被怀疑有问题，核对后确认**符合契约**，不应被"修复"：

1. **`shouldWake` 不读 `sStrictMode`** —— 与 C2「白名单单独收窄投递」一致
2. **白名单为空即全放行** —— 与 C3 一致，`shouldWake`/`shouldApply` 都显式实现了这一点
3. **`shouldApply` 内的 GMS 分支** —— 与 C4「无论如何 GMS 始终受到保护」一致
4. **`isForceStopEnable` 额外要求 `declaresFcmComponent`** —— 比契约更严，符合 C5 的意图
5. **`deferBroadcastForMiui` 对 CN 队列无守门** —— CN 队列是 GMS 内部重连/心跳动作，属 C4 的"推送重连始终受保护"
6. **UI 列表默认不过滤**（`showFcmSupportedOnly`/`excludeMiPushApps` 初值均为 false）—— 用户能把任意应用加进白名单，不存在"想选却选不到"的覆盖面缺口

## 6. 建议动作

> **2026-09-29 实施状态更新**：优先级 1（补日志）、3（`shouldWake` GMS 分支）、6（帮助页文案）**已实施**
> （`Hooker.kt` + `values/strings.xml`，编译 `EXIT=0`，已 `adb install -r`，热重载日志确认装载成功）。
> 优先级 5（给 `deferBroadcast` 加守门）**仍未做**，且已确认在当前钩点不可实现 —— 见 §9/F1。

| 优先级 | 动作 | 需要动代码？ |
|---|---|---|
| 1 | 真机确认 P1 是否真的被 c2dm 命中：`adb logcat -s LSPosedLogDaemon \| grep -i defer`（当前该 hook 成功路径无日志，建议先补一条） | 是（仅日志） |
| 2 | 评估为 `DomesticPolicyManager#isRestrictNet` 补钩子（P5-3）：本机走的就是 Domestic 分支，这一侧目前**完全没有覆盖** | 是（新钩子） |
| 3 | 给 `shouldWake` 补 GMS 分支，与 `shouldApply` 对齐（P2-1） | 是（一行） |
| 4 | 首读成功/失败各打一条日志（现在只有失败才打），异常后退避重试（P3；"装载时同步预读"代码里**已有**，无需再加） | 是 |
| 5 | P1 确认命中后，把 `deferBroadcast` 加到 `shouldWake` 之下 | 是 |
| 6 | 帮助页文案：说清"白名单单独即收窄投递、严格模式管的是其余判定"（P2-2）；披露 GMS 保护的全局落地形态（P4）；`help_power_actions_body` 中"推送网络限制豁免"在 CN ROM 上不成立，需加限定或进 FAQ（P5） | 文案（中英同步） |

优先级 1 未出结果之前，不建议直接改 P1：当前行为是"偏松"，误改成"偏紧"反而会造成未勾选应用丢推送，风险高于现状。

优先级 2 与 P1 无依赖，可独立评估。它面对的**不是一致性问题而是真实缺失**：本 ROM 上模块自称提供的"推送网络限制豁免"从未生效。补之前同样要先看清 `DomesticPolicyManager#isRestrictNet` 的实际判据 —— 是靠 `isPushApp` 那样的推送类判定，还是别的口径，不能凭接口签名猜语义。

## 7. 本次审计的可复用方法

1. 先取 **契约**：UI 文案/deploy 文档里对开关的承诺，而不是先读代码 —— 否则会把"符合设计"误判成 bug（本次 P2-2、section 5 那几条就是这样排除的）
2. 枚举 **全部处理点**（`grep ACTION_REMOTE_INTENT`），而不是只看守卫函数有几个调用点
3. 对同一语义的多个入口做 **对称性检查**：本次的突破点就是"6 个 c2dm 入口里 5 个有守门、1 个没有"，以及"两个守卫函数的 GMS 豁免不一致"
4. 每个守卫都要问 **fail-open 还是 fail-closed**，再叠加它的加载时序，看开局窗口的行为
5. **代码里有 ≠ 运行时会跑**。守卫挂着不代表那个类被实例化 —— 本次 P5 整条结论就来自顺着"这个类被调用了吗"再追一层：`PolicyManager` 是接口、选择逻辑在 `AurogonImmobulusMode`、常量取自 `<clinit>`、最后拿 `dumpsys greezer` 的运行时值对上号
6. 判断"某 ROM 上哪个实现生效"，ROM 取证 + 运行时 `dumpsys` **两端闭合**才敢下结论。只看一边容易把"存在"当成"生效"

### 本次踩到的取证陷阱（记下来省得再犯）

- **`logcat -G` 不是无损操作**：调整缓冲大小会丢掉原有内容。本次 `adb logcat -G 16M` 之后，缓冲里只剩调整后的一段（10:00–11:22），开机时段全部丢失，导致早期装配日志无法复核。要抓开机记录，先不要动 `-G`，或改用 LSPosed 导出的 `modules_*.log`
- **`head` 会让前面的管道命令提前被 SIGPIPE 终止**：本次 `dexdump -d ... | awk ... | head -70` 里，`head` 到量后 awk 就停止遍历后续输入，于是"扫描完整个 dex 没找到 `isPushApp` 调用点"其实是把不该得出的结论当成结论。去掉 `head` 重跑，那 3 个 `invoke-direct` 调用点立刻出现。**涉及"没找到"的取证，一定不能加 `head` 截断管道。**
- **Git Bash 会把 `/system/...` 当成本地路径**：`adb shell ls /system/framework` 静默变成对本机 `C:\...\Git\...\system\framework` 的 ls，输出为空还以为是目录空。加 `export MSYS_NO_PATHCONV=1` 后再操作的才对。
- **Windows Python 的 `/tmp` 与 Git Bash 的 `/tmp` 不是同一处**（本次 Python 写到 `D:\tmp`，Git Bash 的 `/tmp` 在别处），跨工具传路径时要用绝对路径

---

附（与本报告无关、顺带发现）：当前仓库只有 `values/strings.xml`（中文），**没有 `values-zh-rCN`，也没有英文资源**；`values-night`、`values-v31` 下无 strings。与项目既往"values 英文默认 + values-zh-rCN 成对维护"的约定不符，若后续要补英文需先确认这是有意的裁剪。
