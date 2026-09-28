# Changelog

## 3.0.2

- 更新依赖版本号

## 3.0.1

- 给帮助页、开放源代码许可页以及隐私与权限页加上返回按钮 Tooltip

## 3.0.0 **重磅更新**

🚀 推送修复

- 把 GMS 写入 MILLET_NO_RESTRICT_APP 名单，自动回写 userTable.bgControl 为 noRestrict
- 使 Greezer 冻结路径直接跳过 GMS，收到推送广播时主动解冻目标应用，理论上解决了「日志显示已投递、应用却毫无反应」的问题
- 使 c2dm 投递不再被延后，广播也不会被塞进缓存等到下次解锁才回放
- 将 GMS 加入睡眠模式白名单
- 睡眠模式结束后主动让 GMS 重连，不等待下一次心跳
- 扩展恢复流程，覆盖更多失败场景以解决广播投递失败

⚠️理论上做到了对 GMS 广播推送全方位的保护，现阶段可能就只剩高内存负载时清理内存导致 GMS 短时间内掉线，但会立即恢复

✨ 界面优化

- 下拉刷新改用 Material 3 Expressive 加载指示器
- 修复更多选项勾选框配色不跟随动态取色的问题
- 新增隐私说明页
- 更新开源许可页内容
- 优化开源许可页，查看开源项目及组件的展示
- 新增 Shortcut ，桌面长按图标可直达设置、FCM 诊断与帮助
- 支持 Android 12+ 回弹效果

🙏 致谢

本次推送修复重点参考了以下三个项目，在此致谢：

- [dingwen07/hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix)
- [Kr328/HyperOSFCMFix](https://github.com/Kr328/HyperOSFCMFix)
- [ReedGAOOO/FCMGuard-HyperOS](https://github.com/ReedGAOOO/FCMGuard-HyperOS)

## 2.6.0

- 全新桌面图标，支持 Adaptive Icon 与 Themed Icon
- 桌面名称改为 HyperGreeze
- 列表支持拉伸回弹（Stretch overscroll）
- 修复 Android 16 上关闭无障碍时可能崩溃的问题
- 修复小窗/分屏下点「更多选项」菜单位置不正确的问题
- 精简多个入口
- 降低启动与主题切换时异常崩溃的概率

## 2.5.0

- 将原项目绝大部分 Java 代码迁移至 Kotlin，部分界面使用 Jetpack Compose（体积大小变化是正常的）
- 持续优化 Material Design 3 风格
- 初次适配「无障碍」
- 优化 Tooltip 的展示
- 优化按压涟漪效果
- 更新「开放源代码许可」页内容
- 调整「模块状态」页显示
- 关于页标题栏改为「设置」
- 若干闪退与翻译问题修复

## 2.3.0

-  精简「帮助页」文案
- 新增「严格模式」，只要勾选了至少一个应用，模块就**只对勾选的应用生效**
- 完善「模块状态」页下的英文界面翻译
- 删除更多选项多余的振动反馈
- 新增「排除 MiPush 应用」选项
- 修正「显示系统应用」的判定

## 2.1.0

- 修复连续下拉刷新时，较早发起但较慢完成的扫描会用旧结果覆盖新列表的问题
- 新增「模块状态」页
- 加载「电量和性能」代码时缺少 CONTEXT_INCLUDE_CODE
- 补全 4 个此前遗漏的 hook 目标（GreezeManagerService#isAllowBroadcast、GreezeManagerService#getPackageNameFromUid、ActivityManagerService#broadcastIntent*、ActivityManagerService#getRecordForApp*）
- 模块注入后新增一行汇总日志，便于判断模块是否生效
- 回调并完善对FCM应用的判断

## 2.0.1 (versionCode 20)

- 更新 Android Gradle Plugin 至 9.4.1
- 更新 SwipeRefreshLayout 至 1.2.0

## 2.0.0 (versionCode 19)

- 新增**应用内语言切换**（简体中文 / English）
- 新增**主题**切换，包括主题模式、动态颜色、调色风格和颜色规格
- 新增**备份与恢复**
- 新增**帮助页**
- 优化下拉菜单动效
- 修复勾选应用后需手动刷新才生效的问题
- 修复未授权时过早提示无FCM应用的问题
- 加固稳定性与安全性：钩子异常隔离、清理器判定防崩溃、白名单广播改后台线程

## 1.8.0 (versionCode 18)

- 修复 HyperOS 3 上误报的报错
- 修复 HyperOS 3 上 `googleNetworkDisconnect` 从未生效的问题

## 1.7.0 (versionCode 17)

- 新增关于页，完善开放源代码许可清单
- 启动时自动检查更新（每 24 小时一次，亦可手动检查）
- 尝试兼容 HyperOS 3「电量和性能」Hook 策略，参考 [zuohl/HyperOS_FCM_Live](https://github.com/zuohl/HyperOS_FCM_Live)
- 完善应用列表权限获取，参考 [250king/HyperOS_FCM_Live#1](https://github.com/250king/HyperOS_FCM_Live/pull/1)
- 优化更多选项菜单配色
- 桌面图标更名为「FCM 唤醒名单」
- 未找到支持 FCM 的应用时显示 Toast
- 改进项目代码质量

## 1.6.0 (versionCode 16)

- 长按应用卡片进入多选，顶栏批量加入/移出白名单，支持全选/取消全选
- 更多选项新增「展示支持应用」（参考 FCMPushViewer 的 Receiver 检测）；首次启动默认开启，用户更改后持久化
- 长按图标 Tooltip 自定义定位，避免遮挡控件
- 修复多选点击时整屏涟漪异常
- 接入 HyperOS `GET_INSTALLED_APPS` 运行时权限申请（思路参考 [250king/HyperOS_FCM_Live#1](https://github.com/250king/HyperOS_FCM_Live/pull/1)），避免应用列表被过滤得不全

## 1.5.1 (versionCode 15)

###紧急修复，建议更新至本版本
-修复'1.5.0.14'无法隐藏桌面图标的问题
-界面深浅取色跟随系统
