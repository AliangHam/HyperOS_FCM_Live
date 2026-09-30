# 整宿验证：2026-09-30 开机 00:24:00 → 09:47:41

数据源：`D:\Dev\modules_2026-09-30T00_23_56.238196.log`（LSPosed `modules_*.log`，25852 行，2.2 MiB）。
本模块相关 260 行，其中 200 行为 `checkWakePath` 心跳、10 行为拒绝记录、50 行为挂载与事件。
判定只依据该日志；**未做 adb 侧复核**（分析时设备未连接）。

## 1. 结论速览

| 项 | 结果 | 证据 |
|---|---|---|
| 挂载稳定性 | 一次性挂载，整夜无热重载 / 无重启 | `HyperFCMLive active …` 只出现 1 次（system_server 28 钩 / 0 absent；powerkeeper 15 钩 / 10 absent） |
| 冻结相关防御位 | **零触发** | 无 `isRestrictNet: kept GMS`、无 `udpPackageRestrict: skipped UDP filter for GMS` |
| Alarm 门 | ROM 全程放行，无一次拒绝改写 | 仅 `checkAlarmIsAllowedSend: GMS alarm allowed by ROM (creatorUid=10133)`（00:24:48） |
| powerkeeper userTable | `noRestrict` 保持整夜 | 00:24:36 `miuiAuto → noRestrict`；01:26:28 复查仍 `noRestrict`；此后无改写 |
| Gate-W（`checkWakePath`，3.7） | **对 GMS 无拒绝证据** | 9h23m：reached=9951 / denied=22；**09:30 前 denied 恒为 0** |
| 睡眠模式链 | **整夜零回调，无法证实** | 无 `Sleep mode entering: kept GMS…`、无 `Sleep mode exited: …nudging GMS` |

## 2. Gate-W 数据（3.7 的降级条件）

```
窗口      00:24:08 → 09:47:41（9h23m）
reached   9951
denied    22
分界      09:30:38 之前 denied 恒为 0；22 次拒绝全部发生在 09:30:38 之后（醒后开始用机）
归因      可归因的前 10 条（探针只记前 10 次）全部 callerPkg=com.coolapk.market
          11–22 次 caller 未记录；同期心跳 caller = hypercomm / notes / aicr / htmlviewer /
          home / milink / mi.health / mi_connect_service 等，无 GMS
GMS       callerPkg=com.google.android.gms 出现 57 次（约每 10 分钟命中一次采样），全部放行；
          最后一次 GMS 采样 09:29:30，当时 denied 仍为 0
```

按 3.7 原定判据，严格表述是：**拒绝机制本身是活的（22 次），但没有一条能归因到 GMS**。
⇒ 行为钩仍然不落地；是否整行结案取决于能否接受"11–22 次 caller 未记录"这一段盲区，
保守做法是按"对 GMS 阴性"留观察位 + 不写收益，而不是写成"整条链从不拒绝"。

## 3. 睡眠模式这条链：日志里没有任何痕迹（不得过度解读）

模块两处睡眠相关回调各自只在被调用时才打日志：

- `MiuiNetworkPolicyManagerService#setSleepModeWhitelistUidRules()` → `Sleep mode entering: kept GMS (uid …) on the network whitelist`
- `MiuiNetworkPolicyManagerService#enableSleepModeChain(false)` → `Sleep mode exited: network restored, nudging GMS to reconnect`

**整份日志两条都没有** ⇒ 这两个方法整夜一次都没被调用。由此：

1. 可以证明的是"整夜没有坏事发生"，**不能**证明"睡眠断网链被白名单注入挡住了"；
2. 07:00 前后的"正常重新拉起来"**不是**我们的 nudge 造成的（nudge 未触发），是 GMS 自行恢复；
3. `NetdExecutor#execute` 对 `enablemiuistandby enable` 是**静默 skip、不打日志**，
   所以"断网链被静默挡掉"在日志里既不能证实也不能排除。

A3 只证明了 framework 侧 `ConnectivityManager#updateSleepModeUidRule` / `#enableSleepModeChain`
这两个 API 存在，**不等于** service 侧会被调用。这个边界此前没写清，本轮补上。

## 4. 本轮发现的两个坑（已写入 `MEMORY.md`）

- **冷启动探针假阴性**：`mMessageApp probe: size=0, containsGms=false`（00:24:00）。
  开机挂载那一刻 `AurogonImmobulusMode.<clinit>` 还没跑，静态名单尚未 `sput`；
  此前读到的 544 / 576 是热重载（类已初始化）时取的。⇒ 该探针在冷启动下不可信。
- **探针只记前 10 次拒绝**：`checkWakePath` 的 DENIED 日志上限 10 条，
  第 11 次起的 caller 在日志里不可恢复 —— 长窗观测会留下归因盲区。

## 5. 补充：ADB 复核 + ROM 取证（2026-09-30 上午，设备 a6dc236e 已连接）

用户更正：入睡约 01:00，进入睡眠模式要 30–60 分钟（≈01:30–02:00）；退出在 **07:00–08:00 之间任意时刻**，
不是整点。ROM 侧写死：23:00 起开始"观望"，07:00 后结束观望。

**① 睡眠模式这条链的完整调用路径（ROM 取证，`miui-services` + PowerKeeper dex）**

```
PowerKeeper  com.miui.powerkeeper.statemachine.PhoneSleepModeController
               .broadcastSleepState(int state, String, String)
             → 广播 com.miui.powerkeeper_sleep_changed（extra: state）
                 └─ system_server  MiuiNetworkPolicyManagerService$45.onReceive
                      state==1 → setSleepModeWhitelistUidRules() → enableSleepModeChain(bool)
                      state!=1 → clearSleepModeWhitelistUidRules() → enableSleepModeChain(false)
                      ACTION_SCREEN_ON → clearSleepMode… → enableSleepModeChain(false)
                 └─ MiuiNetworkPolicyManagerService$46（ContentObserver，看 sleepModeEnabled()）
                      变真 → registerSleepModeReceiver（日志 "Sleep mode receiver register"）
                      变假 → unregister + clearSleepMode… + enableSleepModeChain(false)
```

调用点数（精确匹配，含 `-$$Nest$m` 合成访问器）：`setSleepModeWhitelistUidRules` **1 个**（$45 内）、
`enableSleepModeChain` **4 个**（$45×3 + $46×1）。⇒ 模块钩的正是这条链上唯一的实现体，挂载位置没问题。

**② 为什么日志里查不到"睡眠模式进入过"的其他证据**

- `PhoneSleepModeController.hasTimeToSleep()` 的日志（`power.sleep` / "hasTimeToSleep curHour=…"）
  被一个静态 DEBUG 布尔包着，**release 版不打** ⇒ logcat 里查不到 ≠ 没进入。
- logcat 主缓冲已被 09:00 之后的 5.6 万行冲掉，夜间只剩不到 40 行 ⇒ **夜间取证只能靠 modules 日志**。
- 「睡眠开关在私有存储、免 root 读不到」——**这条已定位（2026-09-30 二轮取证解除）**：
  开关不在 Settings 三个命名空间里，而在 PowerKeeper 自己的 ContentProvider：
  `content://com.miui.powerkeeper.configure/SimpleSettings/misc`（`SimpleSettings$Misc.<clinit>`），
  后端是 `user_configure.db` / `cloud_configure.db` 的 `misc` 表。三个 Settings 命名空间里
  唯一可见的 `cloud_sleepmode_networkpolicy_enabled=on` 是**另一套**（system_server 侧的云控键），
  与 PowerKeeper 的 `sleep_mode_*` 不是一回事。详见第 7 节。
- `adb root` 不可用（production build），`su` 不可达 ⇒ 免 root 读不到
  `/data/data/com.miui.powerkeeper`；Provider 本身要求 `miui.permission.powerkeeper.HIDDEN_MODE_PROVIDER`
  （signature 级，adb uid 2000 实测被拒）⇒ 需设备端 root 终端，命令见第 7 节。

**③ 实测现状（10:15 左右，睡眠模式已结束）**

```
dumpsys netpolicy   mSleepModeWhitelistUids:0      ← 白名单空（睡眠结束后被 clear，符合预期）
                    UID=10133 … effective=NONE     ← GMS 当前未被任何策略阻塞
settings system     cloud_sleepmode_networkpolicy_enabled=on
                    MILLET_NO_RESTRICT_APP=…,com.google.android.gms,…   ← 模块写入的免限名单含 GMS
dumpsys deviceidle  com.google.android.gms ×3      ← doze 白名单内
dumpsys greezer     MilletEnable=true；[com.google.android.gms] uid=10133
```

**④ 结论：昨夜"睡眠模式"这一段不能证实，且大概率没进这条链**

能确证的只有"整夜没有任何坏事"；不能确证"睡眠断网链被白名单挡住"。
最可能的解释（按可能性排序）：睡眠模式开关/条件未满足 ⇒ `PhoneSleepModeController` 没发
`state=1` 的广播 ⇒ `$45` 没跑 ⇒ 两个回调都没触发；或者进入了但走的是另一条链
（`AppStandbyController` → `NetdExecutor` 的 `enablemiuistandby`，那条是**静默 skip**）。

**⑤ 对"模块本该在退出睡眠时把 GMS 拉醒"的解释**

那个 nudge 只挂在 `enableSleepModeChain(false)` 上（代码位置 `Hooker.kt:1204-1213`）。
昨夜这条回调一次没被触发 ⇒ nudge 没跑。

**⚠️ 初次结论被撤回**：此前据此写的"这次不需要它"是**过度推断**，站不住。一个晚上的阴性
只能证明「本轮未观测到触发」，不能证明「nudge 无用」。三条反证：

1. **连"睡眠模式是否进入过"都没证实**（见 ④）。若 `$45` 从未收到 `state=1`，那么整条链
   一次都没执行，nudge 的**触发面 = 0 样本**，不是 0 需求。用 0 样本否定兜底逻辑是循环论证。
2. **nudge 的定位本就不是"睡眠退出专用"**：它是「怀疑长连接已僵死时的补救」。睡眠断网只是
   已知的一种诱因；MCS 僵死还可以来自网络切换、GMS 被杀后重建、Nat/防火墙老化等。
   **当前只挂了睡眠退出这一个触发点，是覆盖面不足，不是需求不存在。**
3. **代价不对等**：nudge 触发的代价是在 FCM 诊断里多一条 Close（可观测、可解释）；
   不触发的代价是长连接僵死期间推送静默丢失（不可观测、用户侧表现为"收不到"）。
   在触发面未验证前，不能因为"这次没坏事"就判定它冗余。

可成立的表述只有一句：**nudge 在本轮 9h23m 窗口内触发次数 = 0，其触发面与有效性尚未验证。**

要把它从"未验证"变成"已验证"，只需补一次真实进入/退出睡眠的观测（第 7 节的命令 + 静默路径补日志）。

## 6. 待用户确认 / 下一步

1. 「01:00–07:00 睡眠模式」的依据来源（设置里的定时？MIUI 默认？）—— 日志侧没有对应痕迹，
   需 adb 侧核对：`dumpsys netpolicy`（看 `mSleepModeWhitelistUids`）、`settings list system | grep -i sleep/millet`。
2. 「计时已走过一个多小时」具体指哪个计时（FCM 诊断的连接时长？其他？）——
   若指 MCS 连接时长，则可与"整夜无 nudge ⇒ 不应有我们造成的 Close"互相印证。
3. 是否把 3.7 按"对 GMS 阴性"更新进 `residual_hook_targets_action_list.md`（措辞需按第 2 节的保守口径）。
4. 是否给 `mMessageApp` 探针加"类已初始化"校验或延后重试（消除冷启动假阴性）。

---

## 7. 睡眠开关在哪（二轮取证，2026-09-30）

### 7.1 存储机制（ROM 取证，`pk/OS4_dis.txt`）

开关**不在** Settings 的 system/global/secure 里，之前查不到是正常的。真实位置：

```
com.miui.powerkeeper.provider.SimpleSettings$Misc.<clinit>
    CONTENT_URI = content://com.miui.powerkeeper.configure/SimpleSettings/misc
    sNameValueCache = new NameValueCache(uri, GET_misc, PUT_misc, Del_misc)

后端（com.miui.powerkeeper.provider.*DatabaseHelper）：
    UserDatabaseHelper  -> user_configure.db    表 misc   （用户侧：sleep_mode_user …）
    CloudDatabaseHelper -> cloud_configure.db   表 misc   （云控侧：sleep_mode_cloud …）
```

Provider 声明的权限是 `miui.permission.powerkeeper.HIDDEN_MODE_PROVIDER`
（signature 级；免 root 实测 `Error while accessing provider … Permission Denial`，uid 2000 被拒）。

### 7.2 键名全表（常量均取自 `<clinit>` 的 `const-string`，非猜测）

| 键 | 含义 | 读到的值说明 |
|---|---|---|
| `sleep_mode_user` | **用户开关**，`Utils.isSleepModeEnabled()` 判它 == `on` | 这是总开关 |
| `sleep_mode_cloud` | 云控开关 | |
| `deep_sleep_mode_cloud` | 深度睡眠云控 | |
| `key_sleep_state` | **状态 JSON**：`state` / `previous` / `restore` / `oldFeatureStatus` / `previousNotification` | `state` 即当前睡眠状态机位置 |
| `key_settings_sleep_mode` | 设置项镜像 | |
| `sleep_mode_network_white_apps` | **睡眠模式网络白名单**（应用级） | 判 GMS 是否已在其中，很关键 |
| `sleep_reboot_time` / `sleep_reboot` | 上次睡眠时间 | |
| `userCloseSleepModeByUIButton` | 用户从 UI 关过 | |
| `sleep_mode_cloud_params` / `_params2` | 云控参数（含时间窗） | |

时间窗**不是硬编码**：`PhoneSleepModeController.hasTimeToSleep()` 读
`mSleepState.BEGIN_TIME` / `END_TIME`（由 `SleepState.resetThreshold()` 从 `sleep_mode_cloud`
与 `power.sleep` 恢复），判定区间为 `BEGIN_TIME <= h <= END_TIME-1`。
⇒ 23:00–07:00 是**云控下发**的默认值，可被改写。

`hasTimeToSleep()` 的日志被 `DBG_SLEEP` 静态布尔包着，release 不打 ⇒ logcat 查不到不能作为判据。

### 7.3 供设备端 root 终端执行的命令（无双引号、无 adb shell 前缀）

**① 定位数据库文件**

```
ls -l /data/user/0/com.miui.powerkeeper/databases/
ls -l /data/user_de/0/com.miui.powerkeeper/databases/
```

**② 首选：走 Provider 逐键读（root 下若放行，输出最干净）**

```
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg sleep_mode_user
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg sleep_mode_cloud
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg key_sleep_state
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg key_settings_sleep_mode
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg sleep_mode_network_white_apps
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg sleep_reboot_time
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg userCloseSleepModeByUIButton
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg deep_sleep_mode_cloud
```

若回 `Permission Denial`（signature 权限对 root 也可能不放行），转 ③。

**③ 兜底 A：直接抠 DB 明文（设备无 `/system/bin/sqlite3`，实测缺失）**

```
cat /data/user/0/com.miui.powerkeeper/databases/user_configure.db | tr -c '[:print:]\n' '\n' | grep -a -E 'sleep|key_' | sort -u
cat /data/user/0/com.miui.powerkeeper/databases/cloud_configure.db | tr -c '[:print:]\n' '\n' | grep -a -E 'sleep|key_' | sort -u
```

**④ 兜底 B（推荐，最可靠）：拷出来交给电脑解析**

```
cp /data/user/0/com.miui.powerkeeper/databases/user_configure.db /sdcard/Download/user_configure.db
cp /data/user/0/com.miui.powerkeeper/databases/cloud_configure.db /sdcard/Download/cloud_configure.db
chmod 644 /sdcard/Download/user_configure.db
chmod 644 /sdcard/Download/cloud_configure.db
```

拷完通知一声，用 `adb pull /sdcard/Download/user_configure.db` 拉回本机后用 Python `sqlite3` 读，
`misc` 表名值一目了然（`sleep_mode_network_white_apps` 里有没有 GMS 也能一次看清）。

**⑤ 辅助一手状态**

```
dumpsys netpolicy | grep -iE 'sleep|whitelist'
getprop | grep -i sleep
```

**⑥ 日间手动推进状态机（仅当确实想今天验证）**

```
am broadcast -a com.miui.powerkeeper.check_stationary
```

`PhoneSleepModeController$CheckStationaryReceiver` 动态注册了
`com.miui.powerkeeper.screen_off_timeout` 与 `com.miui.powerkeeper.check_stationary` 两个 action。
但**大概率无效**：`hasTimeToSleep()` 会先用 `BEGIN_TIME/END_TIME` 挡一道，当前 10 点不在
23:00–07:00 区间内 ⇒ 广播收到也会直接返回。要真在白天跑通，得先让云控/本地的时间窗覆盖当前
小时，那需要写 `PUT_misc`，权限同样是 signature 级。

⇒ **结论：日间无法完整复现睡眠链，真实进入/退出只能等今晚。**

---

## 8. 一手数据（2026-09-30 10:36，设备端 root 读 PowerKeeper `misc` 表）

来源：用户在设备 root 终端执行 `content call --uri …/SimpleSettings/misc --method GET_misc`
**成功了**（signature 权限对 root 放行），并把 `user_configure.db` / `cloud_configure.db` 导出到
`D:\Dev\电量与性能OS4数据库`。DB 在 `/data/user/0/`（CE），**不在 `/data/user_de/0/`（DE 下为空，已核实）**。

### 8.1 睡眠模式相关键值

| 键 | 值 | 判读 |
|---|---|---|
| `sleep_mode_user` | **`null`**（键不存在） | 用户从未写过。`Utils.isSleepModeEnabled()` 是 `getString(key, "on").equals("on")` ⇒ **缺省即视为开启** |
| `sleep_mode_cloud` | `true` | 云控开关开 |
| `sleep_mode_cloud_params` | `10` | |
| `sleep_mode_cloud_params2` | **`3,480,10,23,7`** | 逗号分隔 5 段，末两段 = 23 / 7 |
| `key_settings_sleep_mode` | `1` | |
| `key_sleep_state` | `{"state":2,"oldFeatureStatus":"sleep","previous":0,"restore":0,"previousNotification":0}` | 见 8.3 |
| `sleep_mode_network_white_apps` | 9 个包名，**不含 GMS** | 见 8.2，**最关键** |
| `wifi_ssid_when_sleep` | `益华便利店5G` | 可当"是否进入过睡眠"的锚点 |
| `func_deep_sleep_check` | `true` | |

**时间窗落定**：`SleepState.resetThreshold()` 里硬默认值就是 `BEGIN_TIME=23` / `END_TIME=7`
（`const/16 v2, #int 23` / `const/4 v3, #int 7`），云控 `sleep_mode_cloud_params2` 的末两段同样是
23 / 7。**两条路都指向 23:00–07:00**（`hasTimeToSleep()` 判定区间 `BEGIN <= h <= END-1`）。
⇒ 用户描述的窗口与 ROM 配置一致，昨夜 01:30–02:00 入睡确实在窗内（时间窗不是变量）。

### 8.2 ❗ GMS 不在睡眠网络白名单里 —— 白名单注入有价值

```
sleep_mode_network_white_apps =
  com.xiaomi.xmsf, com.miui.securitycore,
  com.google.android.networkstack, com.google.android.networkstack.permissionconfig,
  com.google.android.cellbroadcastservice, com.google.android.networkstack.tethering,
  com.android.networkstack, com.android.networkstack.tethering,
  com.android.networkstack.permissionconfig
```

9 项全是网络基础设施，**`com.google.android.gms` 不在其中**。

同时 **`gms_control=true` + `gms_control_params` 含 GMS**（20 个包的 GMS 全家桶名单）是**另一套**
机制，别混为一谈。而 `userTable` 里 `com.google.android.gms = noRestrict`（全表仅 11 个 noRestrict，
取自 kh 模块写入）—— **但 noRestrict 管的是常规后台限制，与"睡眠期间是否放行网络"是两个维度**，
前者不能替代后者。

⇒ **`addGmsToSleepModeWhitelist` 的注入不是冗余，是唯一能让 GMS 在睡眠期拿到网络的位置。**
（前提仍是链真的跑；见 8.3。）

### 8.3 `key_sleep_state` 现状 —— 倾向"昨晚没走完整往返"，但不能单独定案

```
state=2  oldFeatureStatus="sleep"  previous=0  restore=0  previousNotification=0
```

- `readFromDb` 里 `oldFeatureStatus` 的**缺省值是 `"enhance"`**（`optString(key,"enhance")`），
  现在却是 `"sleep"` ⇒ 至少发生过一次 enhance→sleep 的跃迁，但时间点未知。
- `previous` / `restore` 是位图，记录"进入睡眠前被改过的 feature"和"待恢复项"。
  两者都是 0：**倾向昨晚没做过一次完整的 apply→restore 往返**；但也可能是退出后已清零，
  ⇒ 单独看这一条不能下结论。
- `state=2` 的枚举语义尚未从字节码锁定（代码被混淆，`broadcastSleepState` 的实参追溯不到常量名）。
  已知 `$45.onReceive` 里 **`state==1` 才走"进入"分支**（`setSleepModeWhitelistUidRules` +
  `enableSleepModeChain(true)`）。

### 8.4 今晚就能定案的观测协议（睡前 / 醒后各一次，同一批命令）

```
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg key_sleep_state
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg wifi_ssid_when_sleep
content call --uri content://com.miui.powerkeeper.configure/SimpleSettings/misc --method GET_misc --arg sleep_reboot_time
ls -l /data/user/0/com.miui.powerkeeper/databases/user_configure.db
```

判定表：

| 观测 | 若为真 ⇒ |
|---|---|
| `wifi_ssid_when_sleep` 变成家里的 WiFi | **确实进入过睡眠模式**（进入时写入当时 SSID） |
| `key_sleep_state.previous` 或 `restore` 变成非 0 | 发生过 apply→restore 往返 |
| `state` 在睡前/醒后两次取值不同（如 2↔1） | 状态机真的动过 |
| `user_configure.db` 的 mtime 落在 23:00–07:00 | 夜间发生过 `writeToDb` |

四条全阴，才能说"睡眠链没跑"；任一条为真，就得回头查为什么 `$45` 没让我们的钩子命中。
（当前 mtime = `2026-09-30 09:11`，`cloud_configure.db` = `00:24` 开机时刻。）

### 8.5 附带发现

## 9. `sleep_mode_network_white_apps` 不需要 hook（两套名单无数据流）

用户问：GMS 不在 `sleep_mode_network_white_apps` 里，是不是也得改？**不用。** 取证结论如下。

### 9.1 它们根本不是同一条链

| | PowerKeeper 侧 | system_server 侧 |
|---|---|---|
| 载体 | `sleep_mode_network_white_apps`（**包名**，云控下发） | `mSleepModeWhitelistUids`（**uid 集合**） |
| 进程 | `com.miui.powerkeeper` | `system_server` |
| 类 | `PhoneSleepModeController` | `MiuiNetworkPolicyManagerService` |
| **两者之间** | **没有任何数据流** | ← |

PowerKeeper 侧那份名单落到字段 `PhoneSleepModeController.mNetworkAllowApps`
（`initCloudConfig()` 读入），**全部读取点只有 7 处**，业务用途只有两个：

- `getCloudAppsNetWhiteListOfEarthquakeWarn()` —— **地震预警**的网络白名单
- `$BugAppsClearSwitchCloudObserver` —— 睡眠清后台时的豁免（仅出现在日志串 `", mNetworkAllowApps is "`）

⇒ 它既不参与 uid 级网络放行，也不是 `mSleepModeWhitelistUids` 的来源。

### 9.2 system_server 侧那个集合，ROM 自己从来不填

```
registerPowerKeeperSleep():
    mSleepModeWhitelistUids = new HashSet<>()      // ← 唯一的 iput 初始化，空集合
    registerSleepModeEnabledObserver()

$44.run():                                          // ← 唯一的 Set.add()
    for (UserInfo user : mUserManager.getUsers())
        mSleepModeWhitelistUids.add(UserHandle.getUid(user.id, appId))
        // 注意：这个 Runnable 完全不使用 onSleepModeWhitelistChange 的 boolean added 参数

setSleepModeWhitelistUidRules():
    if (!mSleepModeWhitelistUids.isEmpty())
        for (uid : mSleepModeWhitelistUids) updateSleepModeUidRule(uid, true)
        // 只遍历，不清空、不重建、不从 PowerKeeper 拉取
```

`onSleepModeWhitelistChange(int appId, boolean added)` 在 system_server 侧只有 1 个调用者
（`MiuiNetworkManager.onSleepModeWhitelistChange`），而 PowerKeeper 侧唯一的触发者是
**`PhoneSleepModeController.whitelistCloudAppsForEarthquakeWarning()`（地震预警）**。

⇒ **常规路径下 `mSleepModeWhitelistUids` 恒为空集**（与实测 `dumpsys netpolicy` 的
`mSleepModeWhitelistUids:0` 完全吻合）⇒ 没有我们的钩子时，`setSleepModeWhitelistUidRules()`
就是一个**空操作**。

### 9.3 由此得到两个修正

**① 我们的钩子不是"补一个漏掉的 GMS"，而是"构造一条 ROM 本来不会有的放行规则"。**
`addGmsToSleepModeWhitelist` 把一个恒定为空的集合填上 GMS uid，使接下来的遍历真的调用
`updateSleepModeUidRule(10133, true)` —— 一条显式的 uid 级放行。ROM 那份包名名单里有没有 GMS，
对这条链**毫无影响**，所以不需要 hook PowerKeeper。

**② 顺带解释了昨晚为什么干净。** 睡眠期间真正会断网的是另一条：
`AppStandbyController → NetdExecutor.enableFirewallStandbyChain() → dnsproxyd enablemiuistandby`
—— 它是**全局 standby 链，不按 uid 豁免**，而且**已被 `NetdExecutor.execute` 钩子拦掉**。
`mSleepModeWhitelistUids` 那条线本来就是空的（不存在 ROM 侧的 uid 级放行需求），
与 E7-3「GMS 零命中」的结论互相印证。

⇒ §8.2 里"GMS 不在白名单 ⇒ 注入有价值"的表述要收窄一句：**价值成立，但来源是系统本来不给任何
app 放行，而不是"单独漏了 GMS"。** 这条不影响挂钩的必要性，只影响对机制的理解。

## 10. 已落地：4 条静默出口补日志（只加日志，不动行为）

§9 的推演全部基于静态取证，而昨晚的日志里「回调没跑」和「回调跑了但集合为空」长得一模一样。
为让今晚一份 `modules_*.log` 就能定案，`Hooker.kt` 的 4 个静默出口已各补一条日志。
**没有任何行为分支改动**：不改返回值、不改参数、不新增触发点。

| # | 位置 | 原文 | 新增日志（级别） |
|---|---|---|---|
| 1 | `hookSleepModeNetworkPolicy` 内 `enableSleepModeChain` 回调，`enabling==true` 分支 | 完全无日志 | `Sleep mode entering: chain enabled, whitelist size <N>`（INFO） |
| 2 | `addGmsToSleepModeWhitelist`：`raw !is MutableCollection` | 静默 `return` | `Sleep mode entering: whitelist field is <class>, not a mutable collection, GMS not added`（WARN） |
| 3 | `addGmsToSleepModeWhitelist`：`gmsUid() == null` | 静默 `return` | `Sleep mode entering: GMS uid unresolved, GMS not added`（WARN） |
| 4 | `addGmsToSleepModeWhitelist`：`whitelist.add(uid)` 返回 false | 静默忽略 | `Sleep mode entering: GMS (uid 10133) already whitelisted, size <N>`（INFO） |

附带新增只读辅助 `sleepModeWhitelistSize(Field, Any?)`：反射读集合大小，
`try/catch` 全包、永不抛出（钩子跑在 system_server 里，回调不允许抛异常）。

### 10.1 今晚日志的判定表

`Sleep mode entering: chain enabled, whitelist size N` 这一条是**分水岭**——它出现即证明
`enableSleepModeChain(true)` 被调用过（睡眠链真的跑了）。

| 观测到的日志组合 | 判定 |
|---|---|
| **无任何** `Sleep mode entering` 行 | 睡眠链整夜未进入（`$45` 广播未发，或时间窗/静止判定未满足） |
| size **0**，且**无** `kept GMS` 行 | 进入过，但 `setSleepModeWhitelistUidRules()` 没有在链开启前被调用 ⇒ 注入未生效，需查调用顺序 |
| size **1**（或 ≥1）且 `kept GMS (uid 10133)` | **注入成功**，uid 级放行已下发 |
| `already whitelisted, size N` ≥1 | 集合非空 ⇒ ROM 侧确实填过（推翻 §9 的"恒为空"），或上一次进入的残留未清 |
| `not a mutable collection` / `GMS uid unresolved` | 字段类型或 uid 解析异常 ⇒ 注入失效，需改实现 |

注：第 4 条 `already whitelisted` 若出现且 `size 1`，与 §9 的静态结论冲突，
**以运行时为准**（静态取证只证明常规路径，不能排除云控/地震预警等特殊路径写入）。

### 10.2 装机状态（2026-09-30 11:26）

- 产物 `HyperFCMLive-3.4.0.34-debug.apk`，`assembleDebug` 构建成功（10m54s），`adb install -r` 成功。
- 热重载于 11:26:28 完成，日志确认 `Sleep-mode network whitelist hooked: GMS will stay online overnight`
  已在列，其余钩子与 absent 项与此前一致（无新增缺失）。
- **白天看不到新日志是正常的**：这 4 条全部挂在睡眠链回调上，只有进入睡眠模式才会打印。

### 10.3 附带观测（与本次改动无关，但修正既有认知）

本次热重载读到 `mMessageApp probe: size=64, containsGms=false`。
此前记录过 `size=0`（开机冷启动）与 `size=544 / 576`（热重载），本轮出现 **64**
⇒ **该名单的 size 随云控与加载阶段浮动，不是稳定值**，判据只能用 `containsGms`（恒 false）。
