# 「严格模式」开启后与现有目标冲突/适配性技术分析报告

> 对象：`HyperOS_FCM_Live`（HyperGreeze / HyperFCMLive）  
> 范围：软件内「更多选项 → 严格模式」开启后，与项目既有目标是否冲突、不适配或不完全适配  
> 依据：`Hooker.kt`、`Prefs.kt`、`MainActivity.kt`、`strings.xml` 帮助文案、`CHANGELOG.md`、`README.md`  
> 结论摘要：**与核心「保 GMS/FCM 链路」目标无冲突；与「目标应用及时收推送」目标为有意取舍；但存在文档口径过强、门控面不对称、以及一处全局旁路导致「未装模块等价」承诺不完全成立的适配缺口。**

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
| 调用点数量 | 4 | 3 |

**要点：白名单本身就已把「唤醒特权」限制在名单内；严格模式是在此基础上，再把另一组「目标应用侧钩子」也收成名单边界。**  
帮助文案「白名单与严格模式只决定……自启动/已停止/2 秒豁免」把两层压成一层，是后文偏差的根源。

### 2.3 调用点矩阵

#### A. 始终生效（不受勾选、不受严格模式）— 服务 G1

| 钩子/行为 | 作用 |
|-----------|------|
| `triggerGMSLimitAction` 恒 false | 关闭 GMS 限制开关 |
| `updateGmsNetStatus` 恒 false | 关闭 GMS 网络限制 |
| 睡眠模式网络白名单塞入 GMS | 整夜不断网 |
| `MILLET_NO_RESTRICT_APP` / `userTable.bgControl` / scenario 8 | GMS 不进限制名单 |
| `AurogonImmobulusMode#isNoRestrictApp` / `isNoRestrictFreezeable` | Greezer 跳过冻结 GMS |
| `initGmsChain` / `updateFrameworkGmsNetStatus` / 重连心跳 | 断链后主动恢复 |
| ListAppsManager 黑名单移除 GMS | GMS 不被拉黑 |
| **`DomesticPolicyManager#deferBroadcast` 对 `CN_DEFER_BROADCAST` + `ACTION_REMOTE_INTENT` 全局放行** | **c2dm 投递不延后（对所有目标包）** |
| `deferBroadcastForMiui` 对 CN 动作放行 | 同上 |

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

### 3.1 G1「保 GMS/FCM 链路」— **无冲突（设计上明确豁免）**

证据：

1. `shouldApply` 末句显式放行 GMS / GMS persistent 进程名（`Hooker.kt:1738-1740`）。
2. 全部 GMS 保护钩子从不读 `sStrictMode` / 名单。
3. `deferBroadcast` 对 `CN_DEFER_BROADCAST` 与 `ACTION_REMOTE_INTENT` **不调用任何谓词**（`Hooker.kt:371-376`）。
4. 帮助文案「无论如何，Google Play 服务本身始终受到保护」与实现一致。

**判定：开启严格模式不会削弱核心修复。** 即使用户勾了很少应用甚至理解偏差，GMS 重连、不断网、不冻结仍完整生效。

### 3.2 G2「目标应用及时收推送」— **有意冲突（产品取舍，非缺陷）**

严格模式开且名单非空时，未勾选应用：

- 失去自启动放行、已停止唤醒、2s 豁免（`shouldWake` 本就不给，与严格开关无关）；
- **额外**失去：c2dm `isAllowBroadcast` 硬放行、`isPushApp` 网络限制豁免、FCM 应用防强停。

用户可预期结果：未勾选应用推送变慢 / 要打开才收，甚至更易被强停或网络限制。  
这与帮助「FAQ 耗电」的省电叙事一致，**属于 G3 对 G2 的主动收敛，不是逻辑冲突**。

但注意：即使未勾选，`deferBroadcast` 仍全局放行 c2dm（见 3.3），因此推送到达速度**略好于**纯系统默认——与文案「与未装模块时的表现一致」不完全相符。

### 3.3 G3「最小干预」— **部分冲突（一处全局旁路）**

`hookDomesticPolicyManager` 注释写明「aligned with strict mode's minimal-intervention philosophy」，但实现上对 **所有包** 的 `ACTION_REMOTE_INTENT` 一律 `deferBroadcast=false`，不看 `shouldWake`/`shouldApply`。

| 场景 | 文案承诺 | 实际行为 |
|------|----------|----------|
| 严格开 + 名单非空 + 未勾选包 | 「还原系统默认判定，与未装模块时一致」 | c2dm 投递**仍不会被 defer**，其余回到默认 |
| 严格关 + 名单非空 + 未勾选包 | 仅「拿不到三项特权」 | 同上，且 `shouldApply` 仍放行 force-stop 防护等 |

**判定：最小干预目标不完全达成；严格模式的「隔离边界」在 defer 层是破的。**  
实现注释表明这是有意为之（保证 FCM 链投递不被延后，服务 G1），属于 **G1 优先于 G3** 的裁决，但文案未同步披露。

### 3.4 G4「文案=行为」— **不完全适配（文档层缺陷）**

`strings.xml:122-125` 要求帮助文案与 Hooker 一致。实际存在三处偏差：

| # | 文案位置 | 文案含义 | 实现事实 | 严重度 |
|---|----------|----------|----------|--------|
| D1 | `help_strict_on_body`：未勾选「还原系统默认判定行为，与未装模块时的表现一致」 | 完全等价未装模块 | `deferBroadcast` 仍全局放行 `ACTION_REMOTE_INTENT` | **高**（能力承诺过强） |
| D2 | `help_power_scope_body`：「白名单与严格模式只决定……自启动/已停止/2 秒豁免」 | 作用面=三项唤醒特权 | `shouldApply` 还管 `isAllowBroadcast`、`isPushApp`、`isForceStopEnable` | **高**（作用面低估） |
| D3 | `help_allowlist_checked_body`：严格模式只是「再改回系统默认判定」 | 与 D1 同义的弱化版 | 同 D1；且未提 force-stop / 网络限制差异 | 中 |

`CHANGELOG 2.3.0`「只要勾选了至少一个应用，模块就**只对勾选的应用生效**」与代码门控一致（空名单仍全放行有帮助页单独说明），但同样未提 defer 旁路。

---

## 四、门控语义对照表（验收用）

设：`S` = 严格模式开关，`L` = 名单，`A` = 任意应用包名，`G` = GMS。

| 行为 | L=∅ | S=0, L∋A | S=0, A∉L | S=1, L∋A | S=1, A∉L |
|------|-----|----------|----------|----------|----------|
| GMS 不冻结/不断网/重连 | ✓ | ✓ | ✓ | ✓ | ✓ |
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
3. 第二行是唯一在「未勾选」列仍为 ✓ 的目标应用侧行为——即 D1 的例外。

---

## 五、不适配 / 不完全适配清单

### C1【不完全适配·行为】defer 层未纳入严格边界

- **位置**：`Hooker.kt` `hookDomesticPolicyManager`（约 359-380 行）
- **问题**：`ACTION_REMOTE_INTENT` 与 `CN_DEFER_BROADCAST` 无条件禁止 defer，未走 `shouldApply`/`shouldWake`。
- **影响**：严格模式无法宣称「未勾选 = 未装模块」；未勾选应用的推送到达路径仍被模块「轻度」放行。
- **与目标关系**：保 G1 的合理副作用；与 G3/D1 不完全适配。
- **建议**（二选一，需产品裁决）：
  - **A. 保 G1，改文案**：把 D1 改为「除推送投递不延后外，其余判定回到系统默认」；
  - **B. 完全隔离**：`ACTION_REMOTE_INTENT` 也加上 `shouldWake`/`shouldApply`，接受未勾选应用可能被 defer 的风险（可能损害部分机型 G2）。

### C2【不完全适配·文档】帮助页低估 `shouldApply` 作用面

- **位置**：`help_power_scope_body`、`help_allowlist_checked_body`
- **问题**：宣称白名单/严格模式「只决定」三项唤醒特权；实际严格模式还收权 force-stop 防护、`isPushApp` 网络限制、`isAllowBroadcast`。
- **影响**：用户/开发者无法从文档推出「开了严格模式后未勾选应用更易被强停/断推送网」。
- **建议**：在「严格模式」小节补一张对照表（即本报告第四节），或把三项扩展为「唤醒特权 + 防强停 + 推送网络限制 + 广播放行」。

### C3【不完全适配·语义】双谓词不对称，文档未建模

- **位置**：`shouldWake`（不读 strict）vs `shouldApply`（读 strict）
- **问题**：名称相近、语义不同；帮助页用「白名单 vs 严格模式」二元描述覆盖不了两层门控。
- **影响**：维护时容易把某钩子挂错谓词（例如误以为「加了 shouldWake 就会被严格模式关掉」）。
- **建议**：在 `Hooker` 顶部 KDoc 画出三层表（A 始终 / B shouldWake / C shouldApply）；或统一命名为 `wakePrivilegeAllowed` / `targetAppInterventionAllowed`。

### C4【边缘·覆盖】`isPushApp` / `isForceStopEnable` 门内仍有附加条件

- `isPushApp`：仅 `SDK_INT >= UPSIDE_DOWN_CAKE`，且调用栈必须命中 `isRestrictNet`；国内策略 `DomesticPolicyManager` 的 `isRestrictNet` **未**做等价 `isPushApp` 豁免（国际策略才有）。
- `isForceStopEnable`：还要求 `declaresFcmComponent`；勾选了但清单无 FCM 四标记的应用仍可能被强停。
- **与严格模式关系**：这些是**既有覆盖缺口**，严格模式开关不会修复也不会恶化门内应用；但会让「只对勾选应用生效」在不同 ROM/应用形态上体感不一致。
- **建议**：帮助 FAQ 注明「防强停仅对声明了 FCM 组件的应用生效」；评估 Domestic 策略侧是否需要对称钩子。

### C5【边缘·配置】空名单 + 严格模式 = 全放行

- 与 `help_strict_unchanged_body` 一致，代码一致，**不是缺陷**。
- 但用户若「开了严格模式却一个不勾」，会得到与关闭严格模式相同的全放行，容易误判模块坏了。
- **建议**：严格模式开启且名单为空时，设置页给出轻提示（「严格模式需至少勾选一个应用才会收权」）。

### C6【一致性·热更新】通路完整，风险低

- `writeStrictMode` 与 `writeAllowlist` 共用 `ACTION_ALLOWLIST_CHANGED`，`loadAllowlistFromRemotePrefs` 整组重读。
- 失败路径有 `pending_push` 上推；广播连发 3 次覆盖 boot 注册窗口。
- **判定：无冲突。** 仅注意 `sStrictMode` 默认 `false`：远程读失败时会退回「不限制」，属于 fail-open（保 G1），与最小干预目标略张力，但可接受。

---

## 六、总判定

| 目标 | 开启严格模式后 | 说明 |
|------|----------------|------|
| G1 保 GMS/FCM 链路 | **不冲突** | 明确豁免，代码与文案一致 |
| G2 目标应用及时推送 | **有意取舍** | 未勾选应用降级，属 G3 设计 |
| G3 最小干预/省电 | **不完全适配** | C1：defer 全局旁路打破「未勾选=未装」 |
| G4 文案=行为 | **不完全适配** | C2/C3：帮助页过强承诺 + 作用面低估 |

**一句话结论：**

> 开启「严格模式」**不会破坏**本项目「修复澎湃谷歌推送重连」这一核心目标（GMS 保护与 c2dm 不延后均不受影响）；它与「所有 FCM 应用都能即时收推送」的宽目标**有意冲突**（省电取舍）。主要问题在于**不完全适配**：严格模式并未、也无法（在现行 G1 优先裁决下）把未勾选应用完全还原为「未装模块」状态，而帮助文案承诺过强且低估了 `shouldApply` 的真实作用面。应优先修文档口径（C1-A + C2），再考虑是否收紧 defer 旁路（C1-B）。

---

## 七、建议落地顺序

1. **P0 文档对齐**（无行为变更）：改 `help_strict_on_body` / `help_power_scope_body` / `help_allowlist_checked_body`，披露 defer 旁路与 force-stop/网络限制作用面；同步 `strings.xml:122-125` 注释。
2. **P1 可发现性**：严格模式开 + 空名单时 UI 提示（C5）。
3. **P2 语义澄清**：Hooker KDoc 三层门控表；考虑重命名谓词（C3）。
4. **P3 行为决策**：产品裁决 C1-B 是否收紧 defer；C4 国内 `isRestrictNet` 对称性单独评估。

---

## 附录：关键代码锚点

| 内容 | 位置 |
|------|------|
| `sStrictMode` 状态 | `Hooker.kt:1631` |
| 与名单一并加载 | `Hooker.kt:1633-1643` |
| `shouldWake` | `Hooker.kt:1725-1728` |
| `shouldApply` | `Hooker.kt:1730-1741` |
| `isAllowBroadcast` + `shouldApply` | `Hooker.kt:264-294` |
| `deferBroadcast` 全局放行 c2dm | `Hooker.kt:359-379` |
| `isPushApp` + `shouldApply` | `Hooker.kt:1983-2020` |
| `isForceStopEnable` + `shouldApply` | `Hooker.kt:2023-2058` |
| `writeStrictMode` | `Prefs.kt:139-162` |
| 帮助文案（行为契约） | `strings.xml:122-144` |
| 严格模式变更记录 | `CHANGELOG.md:67` |
