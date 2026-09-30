# AGENTS.md — HyperOS FCM Live (HyperGreeze)

LSPosed 模块（libxposed API 102）：在 HyperOS 3/4 上放行谷歌推送（FCM）链路的系统级门控，
宿主只有 `system`（system_server）与 `com.miui.powerkeeper`。UI 部分是常规 Android App（Kotlin + 部分 Compose）。

## 目录结构

- `HyperFCMLive/` — 唯一的 app 模块。核心是 `src/main/java/.../Hooker.kt`（约 3000 行，全部系统 hook）；
  `MainActivity.kt` 为白名单设置 UI；`theme/`、`mcu/` 为主题取色；`src/test/` 为纯 JVM 单测。
- `hiddenapi/stubs/` — 隐藏 API 的 compileOnly stubs（不打包进 APK）。需要新的隐藏 API 签名时加在这里。
- 根目录 `strict_mode_*.md`、`overnight_verification_*.md` 是分析记录；`README.md`（工作原理表）、
  `HELP.md`（用户文档，应用内不内置帮助页）、`CHANGELOG.md`（发版说明）。

## 构建与测试

```bash
./gradlew :HyperFCMLive:assembleRelease   # 正式包（R8 混淆开启）
./gradlew :HyperFCMLive:test              # JVM 单测（无仪器测试，无需设备/ADB）
```

- 签名配置在 `local.properties`（已 gitignore）：`storeFile` / `storePassword` / `keyAlias` / `keyPassword`；
  本机密钥在 `/home/liang/#KEYS/`。未配置签名时 release 构建会**直接失败**——必须显式传
  `-PallowDebugSignedRelease` 才允许 debug 密钥签名，这是有意设计（公开 debug 密钥可被仿冒），不要绕过或删除该 guard。
- 只构建 Release 变体（CI 亦然）；Debug 变体无分发价值。CI（`.github/workflows/android.yml`）日常 push 只出产物，
  发版需手动 `workflow_dispatch` 勾选 `publish_release`。

## 修改边界（重要）

- **`Hooker.kt` 的约束不要"现代化"掉**：`XposedModule` 子类保持 public + 无参构造（proguard 依赖）；
  hook 回调跑在 system_server / PowerKeeper 里——绝不向外抛异常、绝不阻塞主线程、绝不引入协程。
  四个 FCM 标记常量与设置列表共享，改动需两端同步。
- `versionCode`（当前 36）/ `versionName` **手动递增**，每版发版时在 `HyperFCMLive/build.gradle` 里改；
  不要用 jgit commitCount 之类自动生成（会让 LSPosed 更新检查把本 fork 当成旧版）。
- `proguard-rules.pro` 的 keep 保持**最小集**：SwipeRefreshLayout 与 Material 的 XML 膨胀类
  （`MaterialSwitch`、`MaterialCardView`、`LoadingIndicator`、`FloatingActionButton`）按类名反射必须 keep；
  Compose 无需 keep（R8 可达性即可，仅隐私页在用）。不要加回 `androidx.compose.**` /
  `com.google.android.material.**` 的全量 `{ *; }` keep：会阻止 R8 收缩，曾把 APK 撑到 14.5MB
  （收紧后 4.5MB，2026-09 真机实测 hook 与界面功能无损）。
- `shrinkResources false` 是有意的（资源收缩会丢仅代码引用的资源，导致 Release-only 崩溃）。
- `minSdk 35`（HyperOS 3 起即 Android 15），arm64-v8a only，Java 21 工具链——低于 API 35 的兼容路径已整体移除，不要加回来。
- 新依赖注意：libxposed 的 `api`/`service` 走 `mavenLocal`（settings.gradle 限定 `io.github.libxposed` 组），
  `hiddenapi:stubs` 与 androidx.annotation 均为 `compileOnly`。

## 约定

- 语言跟文件走：Gradle/代码注释多为英文，README/CHANGELOG/HELP/CI 注释为中文。
- 发版时更新 `CHANGELOG.md`，CI 会用它拼 Release body。
- 桌面快捷入口（`ShortcutPublisher.kt`、`res/xml/shortcuts.xml`）、隐藏图标（`LauncherIcon.kt`）涉及
  Activity 别名切换，改动时注意 Manifest 与 `Prefs` 的一致性。

## 用户目录 skills（导入自 ~/.zcode/skills/）

做对应任务前先读相应 `SKILL.md`，按其知识分片/模板执行：

- `~/.zcode/skills/@user_d8d6a6ab/lsposed-mod-dev/SKILL.md` — **LSPosed 模块开发与逆向**（libxposed API 102、
  Hook 模板、稳定性/回退策略、故障排查卡）。改 `Hooker.kt` 或任何 hook 逻辑时优先参考。
- `~/.zcode/skills/@clawhub_ntriq-gh/android-cli/SKILL.md` — `android` CLI：SDK 管理、建工程、部署、设备交互、环境诊断。
- `~/.zcode/skills/langsearch/SKILL.md` — LangSearch 网页搜索（需要 `LANGSEARCH_API_KEY`）。

## 改动敏感区域前先读

- 动 hook / 省电豁免逻辑 → `README.md` 的「工作原理」门控表 + `strict_mode_*.md` 系列分析。
- 动 UI 文案 / 功能说明 → `HELP.md` 与 `res/values/strings.xml`（多语言在 `values-*`）。
- 动发版 / 版本号 → `CHANGELOG.md`、`HyperFCMLive/build.gradle` 的版本块、release guard 注释。
