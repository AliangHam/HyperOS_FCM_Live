# 双报告交叉核验：`strict_mode_analysis.md` × `strict_mode_conflict_analysis.md`

对象：两份针对同一问题（严格模式与现有目标的冲突/适配性）的独立审计
方法：对双方每条**可证伪**的论断回 `Hooker.kt` / `Prefs.kt` / `strings.xml` 复核，不做主观取舍

---

## 0. 结论速览

| | 判定 |
|---|---|
| **核心结论是否一致** | **一致。** 双方都得出"与核心 GMS/FCM 目标无冲突、 `deferBroadcast` 全局放行 c2dm 是主要缺口、帮助文案口径过强" |
| **我的文档的错误** | 0 处硬错；**1 处引用不完整**（已修），导致自己 P1 的严重度被讲轻了 |
| **对方文档的错误** | **2 处**（1 处表格误导、1 处时序归因错误），均已在下节坐实 |
| **对方的论证不完备** | 2 处（G1"无冲突"判定漏检 `shouldWake`；未披露 GMS 护盘的全局副作用） |
| **对方强于我的地方** | 2 处（契约原文引全、 `isPushApp` 的 ROM 分化风险） |
| **双方共同弱点** | 均未做真机验证，两条 P1/C1 的严重度都停在理论层 |

---

## 1. 一致的部分（互为交叉验证，可信度提升）

两条独立路径得出同一结论，这些可以当作已确认事实：

| 事实 | 我 | 对方 |
|---|---|---|
| `DomesticPolicyManager#deferBroadcast` 对 `ACTION_REMOTE_INTENT` 无守门 | P1 | C1 / 3.3 |
| 该处注释自称 aligned with strict mode，判断与实际相反 | P1 | 3.3 |
| `shouldApply` 实际作用域仅 3 处（`isAllowBroadcast` / `isPushApp` / `isForceStopEnable`） | P2-2 | 2.2 / C2 |
| 白名单单独就收窄投递链路，严格开关不改变它 | §2-1 | 2.2 末段 |
| `help_power_scope_body` 低估作用面 | P2-2 | D2 |
| 调用点数 `shouldWake`=4、`shouldApply`=3 | §4 | 2.2 |

复核结果：**全部属实**。`shouldWake` 调用点在 486/535/586/1925，`shouldApply` 在 285/1994/2049。

---

## 2. 对方文档的错误（已回代码坐实）

### E1【表格误导·中等】`deferBroadcastForMiui` 不是第二处 c2dm 旁路

对方 §2.3 A 组表格最末两行：

```
| **`DomesticPolicyManager#deferBroadcast` 对 `CN_DEFER_BROADCAST` + `ACTION_REMOTE_INTENT` 全局放行** | **c2dm 投递不延后（对所有目标包）** |
| `deferBroadcastForMiui` 对 CN 动作放行 | 同上 |
```

**"同上"是错的。** 实际代码（`Hooker.kt:304-309`）：

```kotlin
if ((chain.getArg(0) as? String)?.let { CN_DEFER_BROADCAST.contains(it) } == true) {
    return@intercept false
}
```

判据**只有** `CN_DEFER_BROADCAST`，即 2114-2119 那四个 GMS 内部动作（GCM_RECONNECT / DISCONNECTED / CONNECTED / HEARTBEAT_ALARM），**完全不涉及 `ACTION_REMOTE_INTENT`**。

**后果**：把旁路数量从 1 处渲染成 2 处。这会直接影响：
- C1 的严重度判断（看起来"两道门都在漏水"）
- 修复范围（按他的读法会去动 `hookGreezeManagerService`，实际只需改 `hookDomesticPolicyManager`）

我的 §3 表格第 73 行对这条的判定是正确的（"CN 队列是 GMS 内部动作，符合 C4"）。

### E2【归因错误·严重】fail-open 不是"读失败时退回"，而是必然的开局窗口

对方 C6 写道：

> 仅注意 `sStrictMode` 默认 `false`：远程读**失败**时会退回「不限制」，属于 fail-open（保 G1），与最小干预目标略张力，但可接受

两处不对：

**(a) 触发条件写错。** 初值与赋值同批（1628-1643）：

```kotlin
private var sAllowlist: Set<String> = emptySet()     // 1628
private var sStrictMode = false                      // 1631

private fun loadAllowlistFromRemotePrefs() {
    ...
    sAllowlist = if (set != null) HashSet(set) else HashSet()   // 两者同时赋值
    sStrictMode = prefs.getBoolean(Prefs.KEY_STRICT_MODE, false)
}
```

两个守卫**都是 fail-open**，所以在**首次读取成功之前**就处于全放行态 —— 这是一段**每次装载 / 热重载必然出现的窗口**，不是异常路径。他把它降格成了"读失败时"的异常退回，严重度被系统性低估。

**(b) 只讲了半个机制，而且讲漏了更宽的那一半。** 他只提 `sStrictMode`，没有意识到 `sAllowlist = emptySet()` 才是覆盖面更大的一侧：`shouldWake` 从一开始就有 `allowlist.isEmpty() → true` 分支，因此白名单为空会连带把**全部 4 个 `shouldWake` 调用点**都放行，而 `sStrictMode` 只影响 3 个 `shouldApply` 点。叠加 `ALLOWLIST_STALE_MS=10s` / `ALLOWLIST_RELOAD_MIN_MS=500ms` 后，最坏情况是装配后最早的一次推送走全放行。

据此他的 §4 对照矩阵**缺了一整行**（"读取窗口内的临时全放行"），而这是在真机上最容易观测到的一条。

---

## 3. 对方论证不完备之处（结论方向没错，但不能那样推出）

### E3【遗漏】G1「无冲突」的判定只检查了 `shouldApply`，漏检 `shouldWake`

对方 §3.1 列了 4 条证据判定 G1 无冲突，其中证据 1 是 `shouldApply` 末句的 GMS 分支。**但 `shouldWake` 没有 GMS 分支**：

```kotlin
shouldApply : ... || GMS_PACKAGE_NAME == packageName || GMS_PERSISTENT_PROCESS_NAME == packageName
shouldWake  : allowlist.isEmpty() || allowlist.contains(targetPackage)   // 无 GMS 分支
```

所以他的 §4 矩阵第一行「GMS 不冻结/不断网/重连 ✓ ✓ ✓ ✓ ✓」**不是无条件成立** —— 在 `S=1, L∋A` 且 target 恰为 GMS 时，四个 `shouldWake` 调用点会返回 false，与 C4「GMS 始终受保护」存在一道缝。

公允地说，**他的结论方向仍然正确**，因为命中面很小：`checkApplicationAutoStart`(486) 与 `isRestrictReceiver`(535) 都要求 caller 是 GMS、target 通常不是 GMS 自身；`AMS#broadcastIntent`(1925) 的 target 是被唤起的应用；只有 `isNeedCachedBroadcast`(586) 无 caller 校验。我把它定级为**理论缺口、一行可修**，不是当前可观测问题 —— 但论证上不能从"`shouldApply` 有豁免"跳到"GMS 全路径无冲突"。

### E4【遗漏】GMS 护盘的全局副作用未披露

他框架的 G4 是"文案=行为"，D2 只讲了 `shouldApply` 作用面低估，**没有指出 GMS 保护中有一部分是整机策略表级别的常驻改写**（我的 P4）：

| 动作 | 行号 | 全局副作用 |
|---|---|---|
| 睡眠模式网络白名单注入 GMS | 737-805 | 可能把原本不开的链变成"开链 + 仅 GMS 免掐"，改变其余 UID 夜间策略 |
| `ProcessPolicy#getWhiteList` 追加 GMS | 611-628 | 含 `addIfAbsentInPlace` 原地写回 |
| `ListAppsManager` 黑名单移除 / 数据白名单追加 | 382-458 | 查询前追加，属常驻改写 |

这与 defer 旁路是**同一类问题**：用户以为"严格模式 = 影响只到我勾的几个应用"，实际有一部分是以整机策略表的形式落地、严格模式收窄不了的。

### E5【定性不准】C1-B 被描述为"损害 G2"，实际是向契约靠拢

对方 C1-B 写：`ACTION_REMOTE_INTENT` 也加上谓词，**接受未勾选应用可能被 defer 的风险（可能损害部分机型 G2）**。

这里的定性拧了：契约 `help_allowlist_checked_body` 本来就承诺"未勾选的应用……推送可能到得晚一些，或要应用打开时才送达"。**让未勾选应用被 defer 恰恰是在实现这句承诺**，不是对 G2 的损害。真正的风险是"某些 ROM 的 defer 比原生更激进导致过度延迟"，应表述为**实现风险**而非**目标冲突**。

另外他给 C1-B 的前置条件不够：这条 hook 的**成功路径不打日志**，目前无法区分"命中了"与"从没被调用"。我 §6 把"先补日志 + 真机确认"列为优先级 1，并明确**未确认前不要改** —— 因为当前是偏松，误改成 fail-closed 会让未勾选应用丢推送，风险高于现状。他直接列了 A/B 两个选项，缺这一步。

---

## 4. 对方强于我的地方（已吸收）

### W1【引用不完整·已修】契约原文没有引全 —— 反而弱化了我自己的 P1

他的 D1 完整引用了 `help_strict_on_body`：

> 未勾选的应用会还原系统默认判定行为，**与未装模块时的表现一致**

我的 §1 契约表 C1 只写了"模块只对勾选的应用生效"，**漏掉了后半句**。而这句远强于 `help_allowlist_checked_body` 的"推送可能到得晚一些"——它排除了**任何残余干预**。

未装模块时 c2dm 会被 defer，装了之后不会 —— 这条 hook 直接使"等价未装模块"在投递延迟维度上不成立。已据此修订 P1 的契约偏差段，改为并列两处承诺并指出哪一句更强。

### W2【建议合理·已采纳方向】`isPushApp` 存在 ROM 分化风险

对方 C4 指出两条我没展开的细节，复核**属实**：

- `isPushApp` 需同时满足 `SDK_INT >= UPSIDE_DOWN_CAKE` **且**调用栈命中 `isRestrictNet` 且类名属于 `InternationalPolicyManager`（1993-2000）
- `hookDomesticPolicyManager` **只有** `deferBroadcast`，没有对应的 `isRestrictNet` 豁免 ⇒ 国内策略侧存在对称性缺口

我在 §3 矩阵里只标了"✅ `shouldApply`"，没有展开这层。属"B 门控本身有附加条件"的性质（与 `isForceStopEnable` 需 `declaresFcmComponent` 同类），不影响严格模式的一致性判定，但影响"勾选后体感一致"的范围。

### W3【产品建议】严格模式开 + 名单为空时的 UI 提示

他的 C5：此时行为等同关闭严格模式（与 `help_strict_unchanged_body` 一致，**不是缺陷**），但用户容易误判模块坏了，建议给轻提示。我没写这条，合理，建议纳入。

---

## 5. 双方共同的弱点

**都没有真机验证。** 双方都承认 `deferBroadcast` 成功路径无日志。因此在拿到下面这条之前，P1/C1 的严重度都只能停留在"静态推断"：

```bash
adb shell "setprop persist.log.tag.HyperFCMLive DEBUG"   # 或先补一条 succeed-path log
adb logcat -d | grep -i "defer.*remote_intent"
```

在此之前给 `deferBroadcast` 加守门是**有风险的**：当前行为偏松，一旦误改成 fail-closed，未勾选应用会真的收不到推送，代价比现状更高。

---

## 6. 建议的最终处置顺序（合并两份后）

| 序 | 动作 | 来源 |
|---|---|---|
| 1 | 给 `deferBroadcast` 补成功路径日志，**真机确认是否被 c2dm 命中** | 我 P1 优先级 1 |
| 2 | 修订帮助文案：披露 defer 旁路 + `shouldApply` 真实作用面 + GMS 护盘的全局落地形态 | 双方共识 + 我 P4 |
| 3 | `shouldWake` 补 GMS 分支，与 `shouldApply` 对齐（一行） | 我 P2-1（对方漏） |
| 4 | 装载时同步预读白名单，消除开局 fail-open 窗口 | 我 P3（对方归因错） |
| 5 | 上一步确认命中后，再决定 P1 是否加守门 | 双方共识 |
| 6 | 严格模式开 + 空名单的 UI 轻提示 | 对方 C5 |
| 7 | Hooker KDoc 增补三层门控表；考虑重命名谓词 | 对方 C3 |

**修复范围更正**：只需动 `hookDomesticPolicyManager`(359-380)。`deferBroadcastForMiui`(304-309) **不在范围内** —— 见 E1。

---

## 7. 核验后的额外发现：P5（双方都没有的一条）

上面的对比牵着我顺着对方的 C4（Domestic 侧不对称）往下取证，结果挖出一条**双方报告都没有、且会改变一个共同前提**的事实。

### 7.1 结论

**本机 greeze 实际选用的策略实现是 `DomesticPolicyManager`，而它没有 `isPushApp` 方法 ⇒ 模块的 `InternationalPolicyManager#isPushApp` 钩子在本 ROM 上从不执行。**

### 7.2 证据链（ROM 取证 + 运行时两端闭合）

| # | 证据 | 来源 |
|---|---|---|
| 1 | `PolicyManager` 是接口，`Domestic`/`International` 是两个实现类 | `miui-services.jar` 类声明 |
| 2 | 选择在 `AurogonImmobulusMode#restorePolicyManager`：非强制 CN 时 `PolicyManager.isCnModel()`，命中 CN → `mCurrentCNPolicy = STATE_DOMESTIC` → `mPolicyManager = mDomesticPolicy` | 字节码 `0x170404` 起 |
| 3 | `STATE_DOMESTIC = 1`、`STATE_INTERNATIONAL = 2`（`<clinit>` 中的 `sput`，非编译期常量） | 字节码 |
| 4 | 运行时 `mCurrentCNPolicy: 1`、`mSetForceCnGlobal=false` | `dumpsys greezer` |
| 5 | `DomesticPolicyManager` 方法表**无 `isPushApp`**（只有 `isRestrictNet(I)Z`）；`isPushApp` 仅在 International 侧 | 两类方法清单 |
| 6 | `isPushApp` 的 3 个调用点全部是 `invoke-direct`（类内部自调用），无外部调用方 | 字节码 xref |

### 7.3 对两份报告的影响

| 对象 | 影响 |
|---|---|
| 对方 C4（Domestic 不对称） | 从**理论风险**升级为**本机实际状态**。他说得对，而且比他自己以为的更严重：不是"缺对称钩子"，而是被 hook 的那个类整个不参与运行 |
| 我的 P2-2（作用域不匹配） | 进一步收紧：名义 3 处收权，本机**实际只有 2 处**（`isAllowBroadcast`、`isForceStopEnable`） |
| 我的 §3 矩阵 | 原本 `isPushApp` 那行标 ✅ 是错的，已改为 ⚠️ P5 |
| 双方共同的未知项 | 解释了 `Hooker.kt:2009` 那条一次性 INFO `"isPushApp: caller isRestrictNet matched"` 为何从未出现 —— 此前两人都归因于"成功路径无日志"，真正原因是路径没被执行 |
| 新增功能缺口（超出严格模式范畴） | 本机 `isRestrictNet` 走 Domestic 版本，模块**完全没有覆盖**。`help_power_actions_body` 承诺的一类"推送网络限制豁免"在本 ROM 上并未落地 |

### 7.4 另一组运行时证据（补 P4）

`dumpsys greezer` 同一份输出里还给出了我在 P4 里只能从代码推断的那层：

- Telegram pid 10393（`oom_score_adj=905`、`STAT=S<`）**在 `Frozen processes` 列表内**
- GMS 四个进程 6203 / 11329 / 25558 / 27186 **全部不在冻结列表内**

这组对照同时证实 C4 成立（GMS 未被冻）与"目标应用不受免冻保护"（Telegram 以缓存态被冻）。它也补完了上一轮讨论的链条：**推送拉起 → 缓存进程 → 被 greeze 冻结**。

### 7.5 处置

P5 已并入 `strict_mode_analysis.md` 作为 §4 P5，并新增建议项"评估补 `DomesticPolicyManager#isRestrictNet` 钩子"。性质是**功能补全**而非一致性问题，两份报告都不建议在此刻凭推论直接实现。
