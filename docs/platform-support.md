# 平台支持与安装范围

| 平台 | 本轮可交付内容 | 限制与验证状态 |
| --- | --- | --- |
| Android 5.0+ 电视、手机、平板 | 完整题库、学习规则、本地服务及权限条件下的整机管控 | TV、mobile Debug 编译通过；Android TV 模拟器回归通过，厂商固件需真机复测。 |
| MAXHUB Android 大屏 | 使用现有 Android 包；带遥控器用 TV 包，触屏用 mobile 包 | 需确认 Android 版本、允许安装 APK，安装方法与普通 Android 相同；没有 MAXHUB 实机验证。 |
| MAXHUB Windows / OPS 电脑 | 浏览器练习页 | Windows 不能直接安装 Android APK；不提供 Windows 整机管控。 |
| Android 4.0–4.4（API 14–20） | 独立 `legacy` 离线练习 APK | 支持本地题库、单选/多选/判断/填空、年级、练习记录；没有现代版整机管控、后台服务及家长局域网 API。编译通过，尚无 Android 4 实机或模拟器验证。 |
| iPad / iPhone Safari | 浏览器本地练习页；家长管理页可管理 Android 设备 | 不能限制其他 iOS 应用。没有声称实现 iOS Screen Time / Family Controls 原生管控。 |

## MAXHUB

先在大屏「设置 → 关于」确认操作系统。有些大屏同时提供 Android 与可插拔 Windows OPS，需在对应系统中使用相应内容。

Android 5.0+：下载对应 APK，通过 U 盘/文件管理器打开，按系统要求允许安装；首次启动设置管理密码。权限页面是否支持无障碍、悬浮窗、使用情况访问和设备管理员，取决于大屏固件及机构的设备管理策略。不能打开权限时先导出诊断，不通过修改系统文件或刷机解决。

若无法安装，记录型号、Android 版本、APK 包型和安装器的原始错误。Issue #107 只有设备名称，缺少这些信息，因此当前不能把“有 Android 安装包”等同于“MAXHUB 已真机适配”。

## Android 4 练习版

独立包名 `com.studyontv.legacy`，与现代版数据分开；minSdk 14、targetSdk 34。构建：

```sh
gradle -p app/native :legacy:assembleDebug
```

产物：`app/native/legacy/build/outputs/apk/debug/legacy-debug.apk`。

练习资源内置在 APK，安装后可离线打开。旧 WebView 的文件选择兼容性不同，可直接粘贴题库 JSON。历史记录与题库由当前 WebView 的本地存储保存，请导出备份。该包是旧系统可安装的学习版本，不是把现代版 minSdk 降低后宣称其全部管控能力都可用。

## iPad 与浏览器练习

在同一局域网访问运行现代 Android 版的设备：

```text
http://<Android设备IP>:8099/practice
```

也可以把 `app/web/practice.html`、`practice-sw.js`、`practice.appcache`、`assets/css/practice.css`、`assets/js/practice.js` 放到自己的静态站点。现代浏览器在 HTTPS 或 localhost 下支持 service worker 离线缓存；普通 HTTP 局域网的现代浏览器不保证离线访问。旧浏览器提供 AppCache 清单，但是否生效取决于浏览器支持。

页面无需家长管理令牌，不连接题库写入/设备控制 API。先从家长端导出题库，在浏览器练习页导入或粘贴；题库与记录保存在当前浏览器，互不影响。可添加到 iPad 主屏幕，独立练习。

iOS 的整机时间控制需要独立原生工程、Family Controls / Managed Settings / Device Activity 能力，以及 Apple 开发者签名和相应授权。当前环境没有这些签名/能力条件，本轮未实现或验证 iPad 的跨应用拦截。网页、PWA 或 Android APK 都不能替代该系统能力。
