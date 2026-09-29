# 双报告交叉核验：`strict_mode_analysis.md` × `strict_mode_conflict_analysis.md`

对象：两份针对同一问题（严格模式与现有目标的冲突/适配性）的独立审计
方法：对双方每条**可证伪**的论断回 `Hooker.kt` / `Prefs.kt` / `strings.xml` 复核，不做主观取舍

---

## 0. 结论速览

| | 判定 |
|---|---|
| **核心结论是否一致** | **一致。** 双方都得出"与核心 GMS/FCM 目标无冲突、 `deferBroadcast` 全局放行 c2dm 是主要缺口、帮助文案口径过强" |
| **我的文档的错误** | 0 处硬错；**1 处引用不完整**（已修），导致自己 P1 的严重度被讲轻了 |
| **对方文档的错误** | **2 处**（1 处表格误导、1 处时序归因错误），均已在下节坐实；另见 **§9 第二轮复审**（再 +2 处严重，其中 1 处是修复方案不可实现） |
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

---

## 8. 对 `strict_mode_analysis.md` 的三处复审意见（第二轮）

> 背景：P5 并入后，再通读 `strict_mode_analysis.md` 全文并与 `strings.xml` / `Hooker.kt` 对照。主体（P1 主结论、P3 时序、P5 死钩子取证）成立；下列 3 处需改，其中 1 处若照做会引入回归。

### R1【严重·错误修复方案】`shouldApply(null)` 会误伤已勾选应用

**位置**：`strict_mode_analysis.md` P1 建议段

> 若该方法拿不到包名（签名只有 `String action`），至少降级为 `shouldApply(<无法判定时按 null 处理>)`

**坐实**（`Hooker.kt:1730-1741`）：

```kotlin
// shouldApply(null) 在「严格开 + 名单非空」时：
allowlist.contains(null)  // false
GMS_PACKAGE_NAME == null  // false
→ return false            // 对一切目标都不豁免 defer
```

`deferBroadcast(String action)`（`Hooker.kt:362-363`）**只有 action、没有目标包名**。主建议 `shouldWake(<目标包名>)` 在现有签名下**不可实现**；若退回 `shouldApply(null)`，严格模式下 **c2dm 会被全部 defer，包括已勾选应用** —— 把「未勾选收权」做成「全员收权」，直接砸 G2/契约 C2 的正向部分。

**处置**：删除或明确标注该降级路径为 **禁止项**。仅存的可行方向：

1. 换钩子 / 调用栈拿到 Intent 与 targetPackage（信息量对齐 `isRestrictReceiver`）；
2. **只改文案、不动行为**；
3. 先补成功路径日志，确认 c2dm 是否真的进入该 hook，再谈守门。

### R2【中等·契约用法自相矛盾】C5 被双向误用

**契约 C5**（`help_power_scope_body:144`）：

> 白名单与严格模式**只决定**：自启动唤起、「已停止」唤醒、约 2 秒省电豁免

| 文中判断 | 问题 | 应改为 |
|---|---|---|
| P1 标题「违反 C2/**C5**」 | defer **不受**白名单/严格模式管辖，字面上反而**容纳于** C5 的「只决定这三项」。真正违约的是 **C2**（未勾选「可能到得晚一些」）与 **C1**（与未装模块一致） | P1 只打 **C2/C1** |
| 矩阵/§5：`isForceStopEnable`「✅ 额外收紧也**符合 C5**」 | 防强停是第 **4** 项，C5 根本未列。不能一边把 C5 当基线，一边说超纲行为「符合 C5」 | 标「契约外附加、偏紧无害」**或**承认 **C5/help 低估 `shouldApply` 作用面**（= conflict_analysis 的 D2），作文案缺陷单列 |

同理，`isAllowBroadcast` / `isPushApp` 被 `shouldApply` 控制，也超出 C5 三项 —— 这是 **help 不完整**，不是「都符合 C5」。

### R3【中等·误引文案】`help_power_actions_body` ≠ 目标应用「推送网络限制豁免」

**位置**：P5 影响 ③ 及 §6 优先级 6；本文件 §7.3 末行曾同样表述，一并更正。

**原文（`strings.xml:142`）**：

> 关闭**针对 GMS 的**限制开关和**网络限制**；放行推送重连与投递广播

指的是 **GMS** 的网络限制（`triggerGMSLimitAction` / `updateGmsNetStatus`，始终生效且有效），与 `InternationalPolicyManager#isPushApp`（目标应用免「推送类」网络限制）**不是同一承诺**。

帮助页对**目标应用**只写了 C5 三项，**从未承诺**「推送网络限制豁免」。因此：

| 断言 | 判定 |
|---|---|
| P5 本体：`isPushApp` 在 CN ROM 是死钩子 | **成立**（取证链完整，保留） |
| 「`help_power_actions_body` 的推送网络限制豁免未落地」 | **误引**，应删除 |
| Domestic `isRestrictNet` 无对应豁免 | **成立**，但性质是 **代码有、文案未承诺** 的功能缺口（功能补全），不是「帮助承诺未兑现」 |

**处置**：P5 影响 ③ 改为「`shouldApply` 名义 3 收权中的 `isPushApp` 在本机无效；帮助未承诺此项，无需为『承诺』改文案；若要补 Domestic `isRestrictNet`，属功能补全」。优先级表中「`help_power_actions_body` 需加限定」一条改为「无需改该句；若实现 Domestic 豁免后再决定是否写入帮助」。

### 8.1 其余表述瑕疵（不阻塞）

1. 「6 个 c2dm 点，5 个有守门」——数量对，但应注明 5 个门 = **1×`shouldApply` + 4×`shouldWake`**，语义不同。
2. P3 标题只提「严格模式」，正文实为**空名单令两套谓词共 7 个调用点全开**，标题宜放宽。
3. P1「给未勾选也发了拿不到的票」——defer 旁路是「不会晚到」，不是「拿到三项特权」，避免与 C2 因果缠绕。

### 8.2 本轮结论

| 项 | 处置 |
|---|---|
| R1 | **必改**（否则修复方案会引入回归） |
| R2 | **必改**（契约标签错误会误导文案修改范围） |
| R3 | **必改**（避免改错 `help_power_actions_body`） |
| P1/P3/P5 主体 | 保留，可信 |

`strict_mode_conflict_analysis.md` 已含 D2/C1 表述与「禁止 `shouldApply(null)`」方向，可作 R2/R1 的对照稿。

---

## 9. 第二轮复审：`strict_mode_conflict_analysis.md` 修订稿仍有的问题

> 背景：对方已按首轮核验修订（E1 defer 范围、E2 fail-open 归因、G1 论证跳步、C1-B 定性都改对了）。
> 本轮只针对**修订稿剩下的、且可回代码/ROM 证伪**的部分。
> 方法同上：只挑可证伪论断，不挑行文风格。

### 9.0 速览

| 编号 | 位置 | 问题 | 级别 |
|---|---|---|---|
| **F1** | C1 建议 3 / §7 第 5 步 | 要求给 `ACTION_REMOTE_INTENT` 加 `shouldWake(<目标包名>)`，**但当前钩点拿不到包名** —— 修复方案不可实现 | **严重** |
| **F2** | §四 对照表 `isPushApp` 行 / §3.2 / 读表结论 1 | 未按 P5 更新：该行在本机**恒不执行**，✓/✗ 两态都不出现 | **严重** |
| F3 | §2.2「shouldApply 调用点 = 3」 | 名义 3，本机实际 **2**（`isPushApp` 是死钩子） | 中等 |
| F4 | §四 矩阵的 ✓ 列 | 未带附加门槛，与 C4 自相矛盾（防强停需 `declaresFcmComponent` 等） | 中等 |
| F5 | C7 表格「P1/P2/P3 免冻结与场景改写」 | `P1–P4` 是 **Hooker KDoc 内部编号**，报告未声明，与 C1–C8 编号碰撞 | 轻 |
| F6 | §2.3 A 组表格 | 把「对所有目标包免 defer」归进「始终生效 — 服务 G1」分组，分类位置替 C1 开脱 | 轻 |
| F7 | C6 | 「读失败保留上次值」只对异常路径成立；另有两条更常见的空名单成因未写 | 轻 |

---

### F1【严重·修复方案不可实现】`deferBroadcast` 钩点没有目标包名

对方 C1 建议 3 与 §7 第 5 步都写着：

> 仅对 `ACTION_REMOTE_INTENT` 加 `shouldWake(<目标包名>)` …… 确认命中后再给 `ACTION_REMOTE_INTENT` 加 `shouldWake`

**这在当前钩点上做不到，双向坐实：**

```kotlin
// Hooker.kt:362-363 —— 只声明了一个 String 参数
val deferBroadcastMethod = DomesticPolicyManagerClass.getDeclaredMethod(
    "deferBroadcast", String::class.java
)
// 365-377：intercept 里只有 chain.getArg(0) as? String 作为 action，
// 没有第二个参数，也没有 thisObject 上的 uid/Intent 字段可用
```

ROM 侧同样确认（本机 `miui-services.jar`）：

| 类 | 方法 | 说明 |
|---|---|---|
| `DomesticPolicyManager` | `deferBroadcast (Ljava/lang/String;)Z` | **唯一重载**，无带包名/uid 的变体 |
| `PolicyManager`（接口） | `deferBroadcast (Ljava/lang/String;)Z` | 同一签名，接口未声明更宽的重载 |

⇒ 该 hook 的信息量只有 action 字符串，`shouldWake(pkg)` 的入参无从取得。若照建议硬写，只有两条路，第一条正是我 §8-R1 已列为**禁止项**的 `shouldApply(null)`（严格开+名单非空时对**一切**目标返回 false ⇒ c2dm 全员被 defer，包括已勾选应用，直接砸掉 G2 的正向部分）。

**可行方向（按可行性排序）：**

1. **换钩到信息量足够的同族方法**。`DomesticPolicyManager#needCacheBroadcast (Ljava/lang/String;II)Z` 带两个 int（疑似 uid / pid），可复用模块已有的 `getPackageNameFromUid` 反查包名 —— 这正是 `isAllowBroadcast` 钩子（264-294）已经在用的手法。**参数语义需字节码确认，列为待验**，但它说明"要拿到包名就得换钩点"，不是加一行谓词的事。
2. **只改文案、不动行为**（零风险，可立即做）。
3. 先补成功路径日志确认 c2dm 是否真进入 `deferBroadcast` —— 若根本不命中，F1/F2 讨论的修复就没有对象。

> 值得记一笔：投递"延迟"维度上模块**已有**一处守门 —— `GreezeManagerService#isNeedCachedBroadcast`（580-598，`shouldWake`）；`deferBroadcast` 是**另一条**延迟路径且裸奔。所以这是"同一维度的两道门只守了一道"的不对称，而不是"延迟维度整体无守门"。

### F2【严重·矩阵失效】`isPushApp` 行在本机恒不执行，修订稿未同步

P5（§7）取证链给出：本机 `mCurrentCNPolicy=1` ⇒ active 实现是 `DomesticPolicyManager`。补两条本轮新坐实的证据：

- `PolicyManager` 接口的方法表里**没有** `isPushApp` ⇒ 不存在 `invoke-interface` 调用路径，该方法只在 `InternationalPolicyManager` 内部以 `invoke-direct` 自调用（3 处）
- `DomesticPolicyManager`（29 个方法）既无 `isPushApp`，也无 `updatePushApps` / `isDeviceIdleOrGoogleOrPushApp`

因此对方报告中三处仍按"`isPushApp` 会执行"来描述的地方**在本机都不成立**：

| 位置 | 现行表述 | 应改为 |
|---|---|---|
| §四 对照表 `isPushApp 免网络限制` 行 | `✓ ✓ ✓ ✓ ✗（默认）` | **本机恒为「系统默认」**，与勾选状态无关，✓/✗ 两态都不出现 |
| §3.2「额外失去：……`isPushApp` 网络限制豁免」 | 列为未勾选应用的损失项 | 本机**本来就没有**这项特权，勾选与否都不存在 |
| §四 读表结论 1「真实增量是后三行」 | 三行 | 本机**两行**（`isAllowBroadcast`、`isForceStopEnable`） |

C4 的现有表述「国内 `DomesticPolicyManager` 侧无对应豁免 —— 对称性缺口；建议评估补对称钩子」方向正确，但定性偏轻：不是"缺一个对称钩子"，而是**被 hook 的那个类在本机从不实例化**。相差一个量级 —— 前者是"补一个钩子"，后者是"这段门控在本机是空的"。

### F3【中等·数字需加限定】`shouldApply` 调用点名义 3、本机 2

§2.2 对照表「调用点数量 shouldApply = 3」本身没错（285 / 1994 / 2049），但读表结论 1 把它当作三个**生效**的收权点。按 P5，`isPushApp`(1994) 那个调用点所在方法永不执行 ⇒ 本机生效的是 2 处。建议在数字旁加"(本机 2)"或脚注，否则 §四"增量三行"与 §2.2"调用点 3"会各自成立却拼不出同一张图。

### F4【中等·矩阵与 C4 自相矛盾】✓ 只是名义态，还带附加门槛

§四 矩阵的 ✓ 列被写成"勾选 ⇒ 生效"，但 C4 自己列了附加条件，表格没有体现：

| 行 | 附加门槛 | 后果 |
|---|---|---|
| `FCM 应用防强停` | 还需 `declaresFcmComponent`（2023-2058） | 勾选了但没声明 FCM 四标记的应用，**即使 `S=1,L∋A` 仍是 ✗** |
| `isPushApp 免网络限制` | 还需 `SDK_INT >= 34` 且调用栈命中 `isRestrictNet` | 同上，另叠加 F2（本机恒不执行） |

矩阵是"验收用"的，读者照它验证会得出"勾选了就该防强停"的预期，然后误判成 bug。建议把这两行的 ✓ 改成 `✓*` 并在表下注明门槛。

### F5【轻·编号碰撞】C7 的「P1/P2/P3」是 Hooker KDoc 内部编号，报告未声明

回代码核对后确认它**有出处**，不是笔误 —— `Hooker.kt` 的 KDoc 里就有一组 `P1–P4` 标签：

| 标签 | 行号 | 内容 |
|---|---|---|
| P1 | 1141 | keep GMS in `Settings.System.MILLET_NO_RESTRICT_APP` |
| P2 | 1483 | Greezer freeze-path safety net in system_server |
| P3 | 1419 | force GMS's compiled scenario to 8（noRestrict）instead of 0 |
| P4 | 1362 | ask GMS/GSF to re-establish its FCM connection and un-freeze |

但报告正文从头到尾只定义了 `C1–C8`，突然出现 `P1/P2/P3` 无法溯源；再叠加本文（§2/§8 用 P1–P5，指**我**这份 analysis 的问题分级），同一种记号有三种含义。建议改写为「KDoc P1/P2/P3（Hooker.kt:1141/1483/1419）」。

### F6【轻·分类位置替 C1 开脱】免 defer 被列进「始终生效 — 服务 G1」

§2.3 A 组标题是「始终生效（不受勾选、不受严格模式）— 服务 G1」，而 `ACTION_REMOTE_INTENT` 免 defer 就放在这一组里。

问题在于：这条动作的受益方是**目标应用**，不是 GMS 自身链路。把它归入 "服务 G1 / 始终生效"，在结构上就等于提前替它做了"属于 G1 优先取舍"的定性 —— 而 C1 正文的结论恰恰是**这不是取舍、是违约**。分组与结论互相打架。建议单列一组「G1 与 G2 的交界（本应按包名守门）」，把这一行移进去。

### F7【轻·C6 补两条】空名单不只来自"读失败"

C6 写「读失败时保留上次值（不清空）反而是对的」—— 对**异常路径**成立（1634-1641 抛出时 `sAllowlist` 不动）。但另两条更常见的成因没写：

1. **读成功也可能是空集**：`getStringSet(KEY_ALLOWLIST, emptySet())`（1636）的默认值非空 ⇒ 1637 的 `else HashSet()` 是**死分支**；"远程 prefs 可读但键从未写入"时会正常读到空集 → 同样全放行。这不是异常，也不是"保留上次值"。
2. **异常也会刷新陈旧计时**：`sAllowlistReadMs = SystemClock.uptimeMillis()`（1642）在 `catch` 之后无条件执行 ⇒ 读取失败后，下次 `getFcmAllowlist()` 看到的 `sinceLastRead` 被重置，要再等满 `ALLOWLIST_STALE_MS`（10s）才会触发重载 —— 失败反而把重试推后了。

两条都使"开局窗口"比 C6 描述得更宽/更久，方向与他的修订一致，属补强。

---

### 9.5 本轮复核通过、无需再改的部分

| 论断 | 复核结果 |
|---|---|
| §3.1 证据 2「全部 GMS 保护钩子从不读 `sStrictMode` / 名单」 | **成立**。`sAllowlist` 只被 1637（写）与 1722（`getFcmAllowlist` 返回）触碰；`getFcmAllowlist()` 的调用点只有 1726（`shouldWake`）与 1734（`shouldApply`）两处 |
| §2.1「未绑定模块服务时写本地镜像 + `strict_mode_pending_push`，下次绑定上推」 | **成立**。`Prefs.kt:145-158` 两条分支分别置位/清位 |
| C8「`writeStrictMode` 与 `writeAllowlist` 共用广播、连发 3 次」 | **成立**（首轮已核） |
| C1「修复范围只有 `hookDomesticPolicyManager` 一处」 | **成立**。`deferBroadcastForMiui`(301-312) 判据仅 `CN_DEFER_BROADCAST` |
| C5「空名单 + 严格 = 全放行，与 `help_strict_unchanged_body` 一致，不是缺陷」 | **成立** |
| 首轮 E1/E2 的修订 | **已正确吸收**，修订稿相应段落与代码一致 |

### 9.6 本轮结论

| 项 | 处置 |
|---|---|
| F1 | **必改**（建议 3 与 §7 第 5 步不可实现；须改为"换钩 / 只改文案 / 先补日志"三选一） |
| F2 | **必改**（否则 §四 矩阵会给出本机错误的验收预期） |
| F3 / F4 | 建议改（数字加本机限定；✓ 加附加门槛注） |
| F5 / F6 / F7 | 可选（可追溯性与表述精度） |
| C1 主结论、§3.3 违约定性、C6 归因、C7 披露 | **保留，可信** |

一句话：**修订稿把首轮的事实错误都改对了，剩下的两处严重问题都属于"P5 之后没同步"和"修复方案没验签名"** —— 都不是判断错，是没跟上取证。
