# 「严格模式」开启后与现有目标冲突/适配性技术分析报告

> 对象：`HyperOS_FCM_Live`（HyperGreeze / HyperFCMLive）  
> 范围：软件内「更多选项 → 严格模式」开启后，与项目既有目标是否冲突、不适配或不完全适配  
> 依据：`Hooker.kt`、`Prefs.kt`、`MainActivity.kt`、`strings.xml` 帮助文案、`CHANGELOG.md`、`README.md`  
> 状态：**已按 `strict_mode_cross_review.md` / `strict_mode_analysis.md` 交叉核验修订**（修正 E1 defer 范围、E2 fail-open 归因、G1 论证跳步、C1-B 定性；补 GMS 全局副作用与开局窗口行）  
> 结论摘要：**与核心「保 GMS/FCM 链路」目标无冲突（但 G1 论证需含 `shouldWake` 缺口）；与「目标应用及时收推送」为契约内取舍。`DomesticPolicyManager#deferBroadcast` 对 c2dm 无守门，与帮助「未勾选可能晚到 / 与未装模块一致」明确不符——修复范围仅此一处。另有开局 fail-open 必然窗口与 GMS 护盘的整机策略表副作用两处不完全适配。**

---

## 一、项目既有目标（从实现与文档反推）

| 编号 | 目标 | 来源 | 严格模式是否承诺影响 |
|------|------|------|----------------------|
| **G1** | 保住 GMS/FCM 链路：不断网、不冻结、不延后重连/c2dm 投递，睡眠后主动重连 | README「修复澎湃系统谷歌推送重连」；CHANGELOG 3.0.0；帮助页「省电与冻结」 | **明确承诺不影响** |
| **G2** | 目标应用能真正被推送唤醒：自启动判定、`FLAG_INCLUDE_STOPPED_PACKAGES`、约 2s 省电豁免 | 帮助页「应用介绍 / 白名单机制」 | **由白名单/严格模式共同决定** |
| **G3** | 最小干预 / 省电：只对需要即时推送的应用放权 | 帮助页「严格模式」「FAQ 耗电」；`Hooker.kt:366-370` 注释 | **严格模式就是该目标的开关** |
| **G4** | 用户可预期：帮助文案与 Hook 行为一致 | `strings.xml:122-125` 注释；`HelpActivity.kt` 注释 | 作为一致性验收标准 |

---

## 二、严格模式实现机制

### 2.1 配置通路

- 配置项：`Prefs.KEY_STRICT_MODE = "strict_mode"`，与白名单同在远程偏好组 `GROUP_CONFIG`。
- 写入：`Prefs.writeStrictMode` → 写本地镜像 + 远程 `commit` + 三次 `ACTION_ALLOWLIST_CHANGED` 广播。
- 读取：`Hooker.loadAllowlistFromRemotePrefs` **一次加载整组**（allowlist + strict_mode），广播触发重载。
- 未绑定模块服务时写入本地镜像并打 `strict_mode_pending_push`，下次绑定时上推（与白名单同构）。

**通路本身完整**，与 G1/G2 无结构性冲突；开关变更可热生效，无需重启（与帮助文案一致）。

### 2.2 两套谓词（关键不对称）

```text
shouldWake(pkg)   // 白名单层
  空名单        → true（全放行）
  非空名单      → allowlist.contains(pkg)
  // 无 GMS 豁免分支

shouldApply(pkg)  // 严格模式层
  严格关        → true（全放行）
  严格开且空名单 → true（全放行）
  严格开且非空  → allowlist.contains(pkg) || GMS || GMS persistent
```

| 维度 | `shouldWake` | `shouldApply` |
|------|--------------|---------------|
| 是否读 `sStrictMode` | **否** | **是** |
| 空名单语义 | 全放行 | 全放行 |
| 非空名单语义 | 仅名单 | 严格关=全放行；严格开=名单+GMS |
| GMS 豁免 | **无** | **有** |
| 调用点数量 | 4 | 3 |

**要点：白名单本身就已把「唤醒特权」限制在名单内；严格模式是在此基础上，再把另一组「目标应用侧钩子」也收成名单边界。**  
帮助文案「白名单与严格模式只决定……自启动/已停止/2 秒豁免」把两层压成一层，是后文偏差的根源。

**初值与 fail-open：** `sAllowlist = emptySet()`（1628）、`sStrictMode = false`（1631）在 `loadAllowlistFromRemotePrefs`（1633–1643）成功前一直生效。两个谓词遇空名单/非严格均放行，因此**读取成功之前**就是全放行——见 C6。

### 2.3 调用点矩阵

#### A. 始终生效（不受勾选、不受严格模式）— 服务 G1

| 钩子/行为 | 作用 | 全局副作用 |
|-----------|------|------------|
| `triggerGMSLimitAction` 恒 false | 关闭 GMS 限制开关 | — |
| `updateGmsNetStatus` 恒 false | 关闭 GMS 网络限制 | — |
| 睡眠模式网络白名单塞入 GMS | 整夜不断网 | **可能改变睡眠链是否开链，影响全 UID 夜间联网** |
| `MILLET_NO_RESTRICT_APP` / `userTable.bgControl` / scenario 8 | GMS 不进限制名单 | 改写 Settings / userTable |
| `AurogonImmobulusMode#isNoRestrictApp` / `isNoRestrictFreezeable` | Greezer 跳过冻结 GMS | — |
| `initGmsChain` / `updateFrameworkGmsNetStatus` / 重连心跳 | 断链后主动恢复 | — |
| ListAppsManager 黑名单移除 GMS / 数据白名单追加 | GMS 不被拉黑 | **常驻改写进程级列表** |
| `ProcessPolicy#getWhiteList` 追加 GMS | 进程白名单含 GMS | **原地写回 `addIfAbsentInPlace`** |
| `DomesticPolicyManager#deferBroadcast`：`CN_DEFER_BROADCAST` **或** `ACTION_REMOTE_INTENT` 均不延后 | c2dm 投递不延后（**对所有目标包**） | **见 C1（唯一无守门的 c2dm 点）** |
| `deferBroadcastForMiui`：**仅** `CN_DEFER_BROADCAST` 不延后 | GMS 内部重连/心跳动作 | **不含 `ACTION_REMOTE_INTENT`，与 c2dm 旁路无关** |

> `CN_DEFER_BROADCAST` = `GCM_RECONNECT` / `gcm.DISCONNECTED` / `gcm.CONNECTED` / `HEARTBEAT_ALARM`（2114–2119），确属 GMS 内部动作，免延迟符合 C4/G1。

#### B. `shouldWake` 门控 — 服务 G2

| 钩子 | 行为 |
|------|------|
| `checkApplicationAutoStart` | 自启动判定放行 |
| `GreezeManagerService#isRestrictReceiver` | 不拦收 + `thawUidAsync("bc_action")` |
| `isNeedCachedBroadcast` | 不缓存广播，立即投递 |
| `ActivityManagerService#broadcastIntent*` | 加 `FLAG_INCLUDE_STOPPED_PACKAGES` + 2000ms `GOOGLE_C2DM` 豁免 |

#### C. `shouldApply` 门控 — 严格模式额外收权面

| 钩子 | 行为（在门内时） | 门外时 |
|------|------------------|--------|
| `isAllowBroadcast`（GMS→app `ACTION_REMOTE_INTENT`） | 直接放行 | 走系统默认 |
| `InternationalPolicyManager#isPushApp`（仅 Android 14+ 且调用栈 `isRestrictNet`） | 返回 false，免推送类网络限制 | 系统默认 |
| `ProcessCleanerBase#isForceStopEnable`（且 `declaresFcmComponent`） | 禁止强停 | 系统默认 |

---

## 三、与各目标的冲突判定

### 3.1 G1「保 GMS/FCM 链路」— **结论无冲突（论证需含 `shouldWake`）**

证据：

1. `shouldApply` 末句显式放行 GMS / GMS persistent 进程名（`Hooker.kt:1738-1740`）。
2. 全部 GMS 保护钩子从不读 `sStrictMode` / 名单。
3. `DomesticPolicyManager#deferBroadcast` 对 `CN_DEFER_BROADCAST` 与 `ACTION_REMOTE_INTENT` **不调用任何谓词**（`Hooker.kt:371-376`）；`deferBroadcastForMiui` 同样只对 `CN_DEFER_BROADCAST` 无条件放行（304–309）。
4. 帮助文案「无论如何，Google Play 服务本身始终受到保护」与实现一致。

**论证补全（交叉核验 E3）：** `shouldWake` **没有** GMS 分支。名单非空且 callee 恰为 GMS 时会返回 false，跳过 thaw / 自启动放行。四处调用中 `checkApplicationAutoStart`、`isRestrictReceiver`、AMS 投递均要求 caller 为 GMS、callee 为目标应用，命中面极小；唯一无 caller 校验的是 `isNeedCachedBroadcast`（586），属**理论缺口**。故 G1 结论仍成立，但不能只靠 `shouldApply` 的 GMS 豁免推出，矩阵中 GMS 保护列并非在 `shouldWake` 谓词上无条件成立。

**判定：开启严格模式不会削弱核心修复。** GMS 重连、不断网、不冻结仍完整生效。

### 3.2 G2「目标应用及时收推送」— **契约内取舍（注意 defer 例外）**

严格模式开且名单非空时，未勾选应用：

- 失去自启动放行、已停止唤醒、2s 豁免（`shouldWake` 本就不给，与严格开关无关）；
- **额外**失去：c2dm `isAllowBroadcast` 硬放行、`isPushApp` 网络限制豁免、FCM 应用防强停。

**除 c2dm 不延后外**（见 C1），用户可预期：推送变慢 / 要打开才收，甚至更易被强停或网络限制——这与帮助「未勾选…推送可能到得晚一些」及 FAQ 耗电叙事一致，**属于 G3 对 G2 的主动收敛，不是逻辑冲突**。

但 `deferBroadcast` 仍全局禁止 c2dm 延后，故「到得晚」在投递延迟这一维度**不会**按文案发生，见下节。

### 3.3 G3「最小干预」— **明确不适配（C1，违约而非取舍）**

`hookDomesticPolicyManager` 注释写「aligned with strict mode's minimal-intervention philosophy」，但实现对 **所有包** 的 `ACTION_REMOTE_INTENT` 一律 `deferBroadcast=false`，不看 `shouldWake`/`shouldApply`。

| 场景 | 文案承诺 | 实际行为 |
|------|----------|----------|
| 严格开 + 名单非空 + 未勾选包 | 「推送可能到得晚一些」+「与未装模块时一致」 | c2dm 投递**不会被 defer**（不会因此变晚），其余判定可回默认 |
| 严格关 + 名单非空 + 未勾选包 | 仅「拿不到三项特权」 | 同上；且 `shouldApply` 仍放行防强停等 |

**判定：**

1. 帮助 `help_allowlist_checked_body` 承诺未勾选「推送可能到得晚一些」——「晚到」正是 defer 的典型表现；本 hook 恰好消除了该表现，与契约**方向相反**。
2. `help_strict_on_body`「与未装模块时的表现一致」更强：排除任何残余干预；本 hook 使该承诺在投递延迟维度不成立。
3. 代码注释自称与最小干预哲学一致，**与实现相反**。

**修复范围（交叉核验 E1）：只有 `hookDomesticPolicyManager` 一处。**  
`deferBroadcastForMiui`（304–309）判据**仅** `CN_DEFER_BROADCAST`，**不含** `ACTION_REMOTE_INTENT`，不构成 c2dm 旁路，不要一并改动。

### 3.4 G4「文案=行为」— **不完全适配（文档层缺陷）**

`strings.xml:122-125` 要求帮助文案与 Hooker 一致。实际存在三处偏差：

| # | 文案位置 | 文案含义 | 实现事实 | 严重度 |
|---|----------|----------|----------|--------|
| D1 | `help_strict_on_body`：未勾选「还原系统默认判定行为，与未装模块时的表现一致」 | 完全等价未装模块 | `deferBroadcast` 仍全局放行 `ACTION_REMOTE_INTENT` | **高**（能力承诺过强） |
| D2 | `help_power_scope_body`：「白名单与严格模式只决定……自启动/已停止/2 秒豁免」 | 作用面=三项唤醒特权 | `shouldApply` 还管 `isAllowBroadcast`、`isPushApp`、`isForceStopEnable` | **高**（作用面低估） |
| D3 | `help_allowlist_checked_body`：未勾选「推送可能到得晚一些」 | 期望延迟送达 | c2dm 不会被 defer，「晚到」不因延迟发生 | **高**（与 C1 同源） |

`CHANGELOG 2.3.0`「只要勾选了至少一个应用，模块就**只对勾选的应用生效**」与代码门控一致（空名单仍全放行有帮助页单独说明），但同样未提 defer 旁路。

---

## 四、门控语义对照表（验收用）

设：`S` = 严格模式开关，`L` = 名单，`A` = 任意应用包名，`G` = GMS。

| 行为 | L=∅ | S=0, L∋A | S=0, A∉L | S=1, L∋A | S=1, A∉L |
|------|-----|----------|----------|----------|----------|
| GMS 不冻结/不断网/重连（专用钩子） | ✓ | ✓ | ✓ | ✓ | ✓ |
| c2dm `deferBroadcast` 不延后 | ✓ | ✓ | ✓ | ✓ | **✓（仍旁路）** |
| 自启动 / stopped / 2s 豁免 | ✓ | ✓ | ✗ | ✓ | ✗ |
| `isRestrictReceiver` 解冻+放行 | ✓ | ✓ | ✗ | ✓ | ✗ |
| 不缓存广播立即投递 | ✓ | ✓ | ✗ | ✓ | ✗ |
| `isAllowBroadcast` 放行 c2dm | ✓ | ✓ | ✓ | ✓ | **✗（默认）** |
| `isPushApp` 免网络限制 | ✓ | ✓ | ✓ | ✓ | **✗（默认）** |
| FCM 应用防强停 | ✓ | ✓ | ✓ | ✓ | **✗（默认）** |

**读表结论：**

1. 严格模式的**真实增量**是后三行（force-stop / 网络限制 / isAllowBroadcast）从「全应用」收成「名单+GMS」。
2. 前四项唤醒特权在**非空名单时本来就只给名单**，严格开关并不改变它们。
3. 第二行是唯一在「未勾选」列仍为 ✓ 的目标应用侧行为——即 D1/D3 的例外。
4. 第一行指 **GMS 专用钩子**；`shouldWake` 路径上的 GMS 目标并无豁免（见 3.1），只是命中面极小。

**开局 / 热重载窗口（交叉核验 E2，本表在此之前不适用）：**  
`sAllowlist` 初值 `emptySet()`、`sStrictMode` 初值 `false`，且加载异步。读取成功之前（及 `ALLOWLIST_RELOAD_MIN_MS=500ms` 节流窗口内），等价 **L=∅ 列**：不仅 `shouldApply` 的 3 点全开，**`shouldWake` 的 4 个调用点也全开**——覆盖面更宽，也是真机上最容易观测到的一段。详见 C6。

---

## 五、不适配 / 不完全适配清单

### C1【明确不适配·行为】`deferBroadcast` 无守门，且违反「未勾选会晚到」

- **位置**：`Hooker.kt` `hookDomesticPolicyManager`（约 359–380 行）——**仅此一处**
- **非问题**：`deferBroadcastForMiui`（304–309）只豁免 `CN_DEFER_BROADCAST`（GMS 内部动作），**不涉及** `ACTION_REMOTE_INTENT`，修复时不要动它。
- **问题**：`ACTION_REMOTE_INTENT` 无条件禁止 defer，未走 `shouldApply`/`shouldWake`。
- **与契约**：违反 `help_allowlist_checked_body`（未勾选「可能到得晚一些」）与 `help_strict_on_body`（与未装模块一致）。
- **与目标关系**：保护 FCM 投递不延后是 G1 合理动机，但**当前实现把目标应用侧的延迟承诺也一并消掉**，属违约，不是干净的「G1 优先取舍」。
- **建议**：
  1. **先确认命中**（必做）：成功路径无日志。补一条 INFO（action + 是否走 exempt 分支），真机看 c2dm 是否真的经过此 hook。
  2. **文案对齐（低风险）**：把 D1/D3 改为「除推送投递不延后外，其余判定回到系统默认」。
  3. **行为收紧（需 1 的结果）**：仅对 `ACTION_REMOTE_INTENT` 加 `shouldWake(<目标包>)`；`CN_DEFER_BROADCAST` 保持无条件。  
     风险说明：让未勾选应用被 defer **是在兑现「可能晚到」的契约**，不是「损害 G2」；真实风险是**部分 ROM 的 defer 策略过激**（可能超出「晚到」变成丢推送），属实现风险。在 1 未确认前不要改成 fail-closed，否则可能未勾选应用真丢推送。

### C2【不完全适配·文档】帮助页低估 `shouldApply` 作用面

- **位置**：`help_power_scope_body`、`help_allowlist_checked_body`
- **问题**：宣称白名单/严格模式「只决定」三项唤醒特权；实际严格模式还收权 force-stop 防护、`isPushApp` 网络限制、`isAllowBroadcast`。
- **建议**：在「严格模式」小节补对照表（本报告第四节），或把三项扩展为「唤醒特权 + 防强停 + 推送网络限制 + 广播放行」。

### C3【不完全适配·语义】双谓词不对称，文档未建模

- **位置**：`shouldWake`（不读 strict、无 GMS 豁免）vs `shouldApply`（读 strict、有 GMS 豁免）
- **问题**：名称相近、语义不同；帮助页用「白名单 vs 严格模式」二元描述覆盖不了两层门控；GMS 豁免也不对称（3.1）。
- **建议**：`Hooker` 顶部 KDoc 画三层表；`shouldWake` 补 GMS/persistent 一行与 `shouldApply` 对齐（一行代码，属理论缺口修补）。

### C4【边缘·覆盖】`isPushApp` / `isForceStopEnable` 门内仍有附加条件

- `isPushApp`：仅 `SDK_INT >= UPSIDE_DOWN_CAKE`，且调用栈必须命中 `isRestrictNet`；**国内 `DomesticPolicyManager` 侧无对应豁免**（仅 International 有）——对称性缺口。
- `isForceStopEnable`：还要求 `declaresFcmComponent`；勾选了但清单无 FCM 四标记的应用仍可能被强停。
- **建议**：帮助 FAQ 注明「防强停仅对声明了 FCM 组件的应用生效」；评估 Domestic 侧是否补对称钩子。

### C5【边缘·配置】空名单 + 严格模式 = 全放行

- 与 `help_strict_unchanged_body` 一致，代码一致，**不是缺陷**。
- 用户若「开了严格模式却一个不勾」，会得到与关闭严格模式相同的全放行，容易误判模块坏了。
- **建议**：严格模式开启且名单为空时，设置页轻提示（「严格模式需至少勾选一个应用才会收权」）。

### C6【不完全适配·时序】开局 fail-open 是必然窗口，不是异常退回

- **初值**：`sAllowlist = emptySet()`（1628）、`sStrictMode = false`（1631）。
- **加载**：`loadAllowlistFromRemotePrefs` 异步；`getFcmAllowlist` 在接收器未注册且距上次读取 ≥ `ALLOWLIST_STALE_MS`（10s）时才请求重载，且立即返回旧值；`ALLOWLIST_RELOAD_MIN_MS`（500ms）节流。
- **真实形态**：读取成功之前就在全放行，**每次装配 / 热重载都会出现**，不是「远程读失败才退回」。读失败时保留上次值（不清空）反而是对的。
- **宽度**：空名单使 **`shouldWake` 四点 + `shouldApply` 三点**同时放行，比只看 `sStrictMode`（三点）更宽，也是真机最容易采到的形态。
- **建议**：装载时同步预读一次（或读成功打 INFO，区分「严格模式已生效」vs「尚未读到名单」）。

### C7【设计披露】GMS 护盘会改写整机策略表

以下对整机生效、不受勾选/严格模式约束（**不应**被约束，否则违反 G1），但「严格模式 = 影响收窄到我勾的应用」的预期会落空：

| 动作 | 副作用 |
|------|--------|
| 睡眠网络白名单注入 GMS | 若 ROM 因白名单非空才开睡眠链，注入后可能变成「开链 + 仅 GMS 免掐」，改变全 UID 夜间联网 |
| `ProcessPolicy#getWhiteList` 原地写回 | 进程级白名单被常驻改写 |
| ListAppsManager 黑名单/数据白名单 | 构造后清理 + 查询前追加 |
| P1/P2/P3 免冻结与场景改写 | GMS 长期 no-restrict |

**建议**：帮助「不受影响的部分」点明 GMS 保护含策略表级落地。

### C8【一致性·热更新】通路完整，风险低

- `writeStrictMode` 与 `writeAllowlist` 共用 `ACTION_ALLOWLIST_CHANGED`，整组重读；失败路径有 `pending_push`；广播连发 3 次覆盖 boot 注册窗口。
- **判定：无冲突。** 时序上的 fail-open 见 C6，与「读失败」无关。

---

## 六、总判定

| 目标 | 开启严格模式后 | 说明 |
|------|----------------|------|
| G1 保 GMS/FCM 链路 | **不冲突** | 专用钩子豁免；论证须计入 `shouldWake` 无 GMS 分支（理论缺口） |
| G2 目标应用及时推送 | **契约内取舍** | 未勾选降级；但 c2dm 不延后使「会晚到」不成立（C1） |
| G3 最小干预/省电 | **明确不适配** | C1：`deferBroadcast` 违约；注释与实现相反 |
| G4 文案=行为 | **不完全适配** | C2/C3/D1/D3：过强承诺 + 作用面低估 |

**一句话结论：**

> 开启「严格模式」**不会破坏**「修复澎湃谷歌推送重连」核心目标。主要问题是 **C1**：`hookDomesticPolicyManager#deferBroadcast` 对 c2dm 无条件免延迟——**仅此一处**（`deferBroadcastForMiui` 不涉及 c2dm），与「未勾选可能晚到 / 与未装模块一致」不符。行为是否收紧必须先补日志确认命中；文案应先对齐。另有 C6 开局必然 fail-open 窗口与 C7 策略表级副作用需披露。

---

## 七、建议落地顺序

1. **P0 补日志**：`deferBroadcast` 成功路径打 action + 分支日志，真机确认 c2dm 是否命中（无此步不做 fail-closed）。
2. **P0 文档对齐**（无行为变更）：改 `help_strict_on_body` / `help_allowlist_checked_body` / `help_power_scope_body`，披露 defer 旁路与 `shouldApply` 真实作用面；同步 `strings.xml:122-125`。
3. **P1 时序**：装载同步预读或成功日志（C6）；严格模式开 + 空名单 UI 提示（C5）。
4. **P2 语义**：`shouldWake` 补 GMS 豁免；Hooker KDoc 三层门控表（C3）。
5. **P3 行为**：确认命中后再给 `ACTION_REMOTE_INTENT` 加 `shouldWake`；`CN_DEFER_BROADCAST` 保持原样。评估 Domestic `isPushApp` 对称性（C4）与 C7 披露。

---

## 附录：关键代码锚点

| 内容 | 位置 |
|------|------|
| `sAllowlist` / `sStrictMode` 初值 | `Hooker.kt:1628-1631` |
| 与名单一并加载 | `Hooker.kt:1633-1643` |
| `shouldWake`（无 GMS 豁免） | `Hooker.kt:1725-1728` |
| `shouldApply`（有 GMS 豁免） | `Hooker.kt:1730-1741` |
| `isAllowBroadcast` + `shouldApply` | `Hooker.kt:264-294` |
| `deferBroadcastForMiui`（仅 CN） | `Hooker.kt:304-309` |
| `deferBroadcast`（CN + c2dm，**C1**） | `Hooker.kt:359-379` |
| `isNeedCachedBroadcast` + `shouldWake` | `Hooker.kt:580-598` |
| `isPushApp` + `shouldApply` | `Hooker.kt:1983-2020` |
| `isForceStopEnable` + `shouldApply` | `Hooker.kt:2023-2058` |
| `CN_DEFER_BROADCAST` / `ACTION_REMOTE_INTENT` | `Hooker.kt:2114-2121` |
| 节流 / 陈旧阈值 | `Hooker.kt:2155-2156` |
| `writeStrictMode` | `Prefs.kt:139-162` |
| 帮助文案（行为契约） | `strings.xml:122-144` |
| 严格模式变更记录 | `CHANGELOG.md:67` |
