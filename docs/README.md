# sky adb 项目文档

本文档基于当前源码实际状态整理，用于说明 sky adb 的功能范围、项目结构、开发规范、维护规范和性能要求。后续开发应在功能或工程结构变化后同步更新本文档。

## 项目定位

sky adb 是一款运行在 Android 手机上的全中文 ADB 管理工具。它通过 WiFi ADB / Wireless Debugging 连接并管理目标 Android 设备，适用于手机、平板、电视、盒子等设备的日常维护。

当前交付目标是 Android App，未来仅做少量架构预留，方便后续评估 Windows 端支持，不在当前版本中引入复杂跨平台工程。

## 当前功能

- 无线调试配对：输入配对 IP、配对端口、配对码后调用 Kadb Pairing。
- 手动连接设备：输入目标设备 IP 和 ADB 端口。
- USB OTG：识别 ADB / Fastboot 接口，授权后连接；Fastboot 命令可在 Shell 页执行。
- 最近设备：连接成功后保存设备记录，支持点击回填 IP 和端口。
- 设备详情：读取品牌、型号、Android 版本、SDK、ABI、分辨率、电池信息。
- 应用管理：读取目标设备应用列表，支持用户应用、系统应用分类，支持搜索、启动、停止、卸载。
- APK 安装：选择本机 APK 并安装到已连接设备。
- 在线下载：从 HTTP / HTTPS 链接下载 APK 或普通文件，支持进度、取消、下载后安装或推送。
- 文件管理：支持浏览目标设备目录、上传本机文件、下载设备文件、新建文件夹和删除文件。
- 本机应用安装到目标设备：列出本机用户应用，支持单 APK 应用导出并安装到目标设备。
- 截图：调用目标设备 `screencap`，拉取截图到本机缓存并预览，需要时再由用户选择保存位置。
- 系统日志：读取目标设备最近系统日志，支持复制日志内容，便于排查目标设备问题。
- 虚拟遥控器：模拟返回、主页、最近任务、方向键、确认、音量、静音、媒体控制等常用按键。
- 屏幕镜像：基于官方 `scrcpy-server-v4.1`、Kadb `localabstract` 流、`SurfaceView` 和 `MediaCodec` 实现画面预览与远程触控；支持音频转发与中文输入；镜像页支持控制区显示/隐藏，画面按远端视频比例居中显示。
- Shell：执行单条 Shell 命令并展示输出、错误输出和退出码。
- 局域网发现：优先通过 Android NSD / mDNS 自动发现 ADB 服务，支持 `_adb._tcp.`、`_adb-tls-pairing._tcp.`、`_adb-tls-connect._tcp.`；自动发现无结果时，保留 TCP 网段扫描和 ADB 协议轻握手确认作为兜底。
- 诊断日志：仅记录错误和异常信息，覆盖 WiFi ADB、配对、应用、文件、截图、系统日志、遥控器、屏幕镜像等功能，支持复制排查。
- 设置：支持默认端口、连接超时、命令超时、扫描网段、屏幕镜像画质、主题模式配置。

## 技术栈

- Kotlin
- Android Gradle Plugin 9.2.0
- Kotlin 2.3.20
- Jetpack Compose + Material 3
- Navigation Compose
- Lifecycle ViewModel + StateFlow
- Kotlin Coroutines / Flow
- DataStore Preferences
- Kadb 2.1.3
- kadb 2.1.3（mDNS 使用平台 NsdManager）
- scrcpy-server 4.1
- OkHttp 5.3.2
- Timber
- JUnit

## 运行范围

- 运行 sky adb 的设备：最低 Android 7.0，API 24。
- 编译目标：compileSdk 37，targetSdk 37。
- Android 17 局域网：targetSdk 37 需声明并运行时申请 `ACCESS_LOCAL_NETWORK`，用于 mDNS 发现、网段扫描和 WiFi ADB 连接。
- 被连接设备：以目标设备是否支持 ADB over TCP / Wireless Debugging 为准。Android 11+ 推荐使用无线调试配对；传统 WiFi ADB 设备通常使用 `5555` 端口。
- 屏幕镜像目标设备：以目标设备是否支持 scrcpy server、视频编码和 ADB socket 转发为准；默认画质使用均衡模式，高负载设备可在设置中切换为流畅模式。

## 项目结构

```text
skyadb/
├── .github/
│   └── workflows/
│       └── android-ci.yml                 # GitHub Actions：测试、lint、签名构建、发布 Release APK
├── app/
│   ├── build.gradle.kts                   # Android App 配置、版本号、签名、依赖
│   └── src/main/
│       ├── AndroidManifest.xml            # 权限、应用入口、图标、应用名
│       ├── assets/scrcpy/scrcpy-server-v4.1 # 官方 scrcpy server 资产
│       ├── java/com/sky22333/skyadb/
│       │   ├── AdbManagerApplication.kt   # Application 初始化入口
│       │   ├── AppServices.kt             # 当前实际使用的轻量服务容器
│       │   ├── MainActivity.kt            # Compose Activity 入口
│       │   ├── adb/
│       │   │   ├── AdbIdentityManager.kt  # KadbCert 持久化身份配置
│       │   │   ├── FastbootOtgManager.kt  # USB OTG Fastboot
│       │   │   └── KadbManager.kt         # Kadb 真实能力封装：连接、配对、Shell、安装、传输、截图
│       │   ├── data/
│       │   │   ├── AppSettingsStore.kt    # DataStore 设置：端口、超时、网段、镜像画质、主题
│       │   │   └── RecentDeviceStore.kt   # DataStore 最近设备持久化
│       │   ├── diagnostics/
│       │   │   ├── DiagnosticFormatter.kt # 诊断日志格式化和复制文本
│       │   │   ├── DiagnosticLog.kt       # 诊断日志模型和模块定义
│       │   │   ├── DiagnosticLogger.kt    # 进程内错误日志收集，限制最大条数
│       │   │   └── DiagnosticResultExtensions.kt # 统一结果失败日志扩展
│       │   ├── discovery/
│       │   │   ├── AdbMdnsDiscovery.kt   # mDNS UI 模型与接口（中文文案）
│       │   │   ├── AndroidAdbMdnsDiscovery.kt # 平台 NsdManager，仅在发现页前台运行
│       │   │   ├── AdbProtocolProbe.kt    # ADB 协议轻握手确认
│       │   │   ├── AdbScanModels.kt       # 扫描结果、进度、状态模型
│       │   │   ├── LanAdbScanner.kt       # 并发局域网扫描、进度节流、协程取消
│       │   │   ├── NetworkInfoProvider.kt # 获取本机网络地址并推导默认 /24 网段
│       │   │   └── ScanRangeParser.kt     # 手动网段解析、校验、私网范围限制
│       │   ├── download/
│       │   │   ├── DownloadTask.kt        # 下载状态模型
│       │   │   └── NetworkDownloadManager.kt # OkHttp 下载、文件名解析、取消、进度节流
│       │   ├── files/
│       │   │   └── LocalFileManager.kt    # 本地缓存文件和 SAF Uri 文件复制
│       │   ├── localapps/
│       │   │   ├── LocalAppExporter.kt    # 本机用户应用筛选、单 APK 导出、缓存清理
│       │   │   └── LocalInstalledApp.kt   # 本机应用模型
│       │   ├── model/
│       │   │   ├── AdbDevice.kt           # 设备模型、连接状态、设备类型
│       │   │   ├── AdbOperationResult.kt  # 统一成功/失败结果模型
│       │   │   ├── AppInfo.kt             # 目标设备应用模型
│       │   │   ├── DeviceInfo.kt          # 设备详情模型
│       │   │   └── OperationStatus.kt     # UI 操作状态模型
│       │   ├── repository/
│       │   │   └── AdbRepository.kt       # ViewModel 面向的 ADB 仓库层，整合 Kadb 和持久化
│       │   ├── scrcpy/
│       │   │   ├── MirrorCoordinateMapper.kt # 镜像触控坐标映射
│       │   │   ├── MirrorQualityPreset.kt # 镜像画质预设和 scrcpy 参数
│       │   │   ├── ScrcpyConstants.kt     # scrcpy server 路径、版本和默认参数
│       │   │   ├── ScrcpyControlClient.kt # scrcpy 控制协议发送：触摸、按键、文本
│       │   │   ├── ScrcpyProtocol.kt      # scrcpy v4 视频包头和控制包编码
│       │   │   ├── ScrcpyRepository.kt    # 镜像会话仓库，连接 UI 与底层 session
│       │   │   ├── ScrcpyServerManager.kt # push 并启动 scrcpy-server-v4.1
│       │   │   ├── ScrcpySession.kt       # scrcpy video/control socket 和生命周期
│       │   │   └── ScrcpyVideoDecoder.kt  # MediaCodec 解码到 Surface
│       │   ├── ui/
│       │   │   ├── AdbManagerApp.kt       # 导航、底部栏、页面切换动画
│       │   │   ├── apps/                  # 目标设备应用管理页和 ViewModel
│       │   │   ├── components/            # 状态徽章、空状态、分区标题等复用组件
│       │   │   ├── device/                # 设备详情和快捷操作入口
│       │   │   ├── discovery/             # 局域网发现页：mDNS 自动发现和 TCP 网段扫描
│       │   │   ├── download/              # 在线下载并安装/推送
│       │   │   ├── files/                 # 目标设备文件管理、上传、下载、新建和删除
│       │   │   ├── home/                  # 首页连接、最近设备、扫描和配对入口
│       │   │   ├── install/               # 本机 APK 安装到目标设备
│       │   │   ├── localapps/             # 本机用户应用导出并安装
│       │   │   ├── logs/                  # 目标设备系统日志查看和复制
│       │   │   ├── mirror/                # 屏幕镜像、远程触控和文本发送
│       │   │   ├── pairing/               # 无线调试配对
│       │   │   ├── remote/                # 虚拟遥控器和物理按键模拟
│       │   │   ├── screenshot/            # 截图执行与预览
│       │   │   ├── settings/              # 设置页
│       │   │   ├── shell/                 # Shell 命令页
│       │   │   └── theme/                 # 颜色、字号、间距、圆角、主题
│       │   └── validation/
│       │       ├── DevicePathValidator.kt # 设备路径校验
│       │       ├── DownloadInputValidator.kt # 下载链接和文件名校验
│       │       └── NetworkInputValidator.kt  # IP 和端口校验
│       └── res/
│           ├── mipmap-*/ic_launcher.png   # 应用图标
│           └── values/
│               ├── strings.xml            # 应用名和基础字符串
│               └── styles.xml             # Android 主题桥接
├── docs/
│   ├── README.md                          # 当前项目总文档
│   └── img/                               # 预览图片
├── gradle/
│   └── libs.versions.toml                 # 依赖版本集中管理
├── .editorconfig                          # 编辑器编码、缩进、换行规范
├── .gitattributes                         # Git 文件编码和换行转换规则
├── .gitignore                             # 忽略构建产物和签名证书
├── build.gradle.kts                       # 根工程 Gradle 配置
├── settings.gradle.kts                    # 仓库源和模块声明
└── README.md                              # 面向用户的简洁产品介绍
```

## 架构说明

当前架构采用单 Android App 模块，保持实现直接、边界清晰。

- UI 层：Compose 页面只负责展示状态和派发用户事件。
- ViewModel 层：持有页面状态，调用仓库、下载、文件、扫描等服务。
- Repository 层：面向 UI 提供稳定的 ADB 操作接口，隐藏 Kadb 细节。
- KadbManager：集中封装真实 ADB 能力，避免页面层直接拼接和调用底层 API。
- DataStore：保存设置和最近设备，所有读取以 Flow 形式向上游暴露。
- Discovery：自动发现和扫描逻辑独立于 UI。mDNS 基于平台 `NsdManager` 仅在发现页前台运行，TCP 扫描支持并发限制、超时、取消和进度节流。
- Download：下载逻辑独立于页面，支持取消、缓存目录、进度节流和文件名推断。
- Diagnostics：错误日志仅保存在进程内，用于定位连接、ADB 操作和功能异常，不做永久存储。
- Scrcpy：屏幕镜像逻辑独立于页面，使用单独 KADB 流式连接、官方 server、MediaCodec 硬解码和控制协议封装。
- Validation：输入校验集中维护，避免每个页面重复实现 IP、端口、URL、路径判断。

## UI 与交互规范

- App 内所有可见文案必须使用简体中文，品牌名统一写作 `sky adb`。
- 使用 Material 3 作为基础，但要保持现代、扁平、克制、工具型产品风格。
- 页面不能有粗糙默认感，按钮、卡片、输入框、状态徽章、列表间距必须统一。
- 首页、设置页、设备页、Shell 页等长内容页面使用 `LazyColumn`，避免滚动不跟手。
- 页面切换使用轻量 fade + slide，动画时长控制在 120 到 180 ms。
- 列表项要尽量轻量，避免在列表滚动时做阻塞 IO、ADB 调用或大图加载。
- 耗时操作必须展示状态，失败必须给出中文原因和下一步建议。
- 危险操作，例如卸载应用，应保留确认流程。
- 设置页面需要紧凑、对齐、统一，不使用明显大于其他页面的输入控件。
- 屏幕镜像页以画面为主，不显示常驻设备信息；顶部只保留关闭和控制区开关，底部控制区可隐藏。
- 屏幕镜像画质配置放在设置页，不放在镜像页，避免遮挡画面。

## 性能要求

- 所有 ADB、下载、文件、扫描、本机应用读取等耗时任务必须放到 `Dispatchers.IO` 或 ViewModel 协程中执行，不能阻塞主线程。
- 下载进度和扫描进度必须节流，避免高频状态更新造成 Compose 频繁重组。
- 局域网扫描必须限制并发，当前扫描默认并发不超过 64，默认超时为 600 ms。
- mDNS 自动发现只在用户进入局域网发现页时启动，离开发现页必须停止，不做后台常驻监听。
- 默认扫描 `/24` 网段，最多手动配置 6 个网段，避免过大范围导致耗电、卡顿和误扫。
- 扫描只在用户主动触发时执行，不做后台常驻扫描。
- 下载文件和导出的 APK 放在缓存目录，导出前清理旧 APK，避免缓存无限膨胀。
- 大列表必须优先使用 `LazyColumn`，列表项保持稳定 key。
- ViewModel 中的 Flow 收集和协程任务要跟随 ViewModel 生命周期释放，避免 Activity 泄漏。
- 不在 Compose 页面中直接持有 `Context` 长生命周期引用；需要上下文的服务使用 `applicationContext`。
- 状态模型要小而稳定，避免把大文件内容、图片 Bitmap 或长输出反复塞入全局状态。
- 屏幕镜像默认使用均衡画质，用户可切换流畅、均衡、高清三档；高清模式不作为默认值。
- 屏幕镜像使用 `SurfaceView` 按远端视频比例居中显示，触摸坐标必须与实际画面区域一致。
- 屏幕镜像的触摸、按键、文本发送和 socket 释放必须在 IO 线程执行，不能阻塞主线程。
- 屏幕镜像不因短暂切后台或 Surface 重建而拆除会话；回前台通过换绑 Surface 并 `RESET_VIDEO` 恢复画面。用户关闭页面、离开镜像页或视频流异常时释放会话。

## 开发规范

- 开发前先阅读当前源码、已有 docs、相关依赖实际 API 和官方文档。
- 不凭经验猜测 Kadb、Compose、DataStore、OkHttp、Android 平台 API 行为。
- 每次改动保持小范围、可解释、可验证，不做无关重构。
- 所有新增页面和功能必须有加载、空状态、错误态、执行中、成功态的处理。
- 业务逻辑优先放在 ViewModel、Repository、Manager 或 Validator 中，不塞进 Composable。
- 不重复造同类输入校验、状态展示、结果封装。
- 新增 ADB 命令时必须封装在 KadbManager 或 Repository 层，页面只调用语义化方法。
- 新增设置项必须进入 `AppSettingsStore`，并在设置页保持布局紧凑统一。
- 新增持久化数据要考虑字段升级、默认值和读取失败兜底。
- 新增网络能力必须考虑超时、取消、错误提示、缓存清理和进度节流。
- 新增 UI 要遵循现有 `AppDimens`、主题颜色、圆角和 Material Icons 风格。
- 新增镜像参数必须通过 `MirrorQualityPreset` 或 `ScrcpyOptions` 集中管理，不能在 UI 或启动命令中散落硬编码。

## 验收标准

- 功能必须生产可用，不以实验、演示或临时代码为交付目标。
- 实现必须直击需求根因，优先复用现有项目结构和已有组件，不堆砌兜底逻辑。
- 代码量保持必要且最少，不新增重复抽象、重复状态、重复入口或重复校验。
- 引入新实现时必须同步删除被替代的旧实现、旧文案、旧状态和无用代码，不保留死代码。
- 业务逻辑必须闭环：有入口、有执行中状态、有成功反馈、有失败原因、有释放资源或取消路径。
- 失败信息必须尽量反映真实原因，并进入诊断日志；不能只给泛化错误。
- 所有耗时、网络、ADB、文件、解码、扫描和 socket 操作不得阻塞主线程。
- 资源必须按生命周期释放，避免后台常驻、协程泄漏、流未关闭、缓存无限增长和重复扫描。
- 默认行为必须兼顾省电、低占用和兼容性；高性能或高画质能力必须由用户主动选择。
- UI 必须与现有风格一致，布局紧凑、对齐、少遮挡，不增加无必要的弹窗、按钮和层级。
- 交互必须形成闭环：用户知道当前状态、能取消或返回、失败后知道下一步。
- 新增设置项必须保存默认值、异常值回退和读取失败兜底，并保持设置页整齐统一。
- 新增功能必须覆盖关键路径测试；测试要少而准确，优先覆盖解析、映射、参数、错误回退等纯逻辑。
- 不使用已知即将弃用或已弃用的 API；遇到平台、依赖或第三方协议不确定时必须查阅最新官方文档或当前源码。
- 不提交临时计划文档、缓存文件、构建产物、签名证书、APK、AAB 或仅用于本地分析的第三方源码。
- 改动完成后至少执行静态检查，确认 `git diff --check` 通过；本地无法编译时必须说明，并由 CI 验证构建、测试和 lint。

## 维护规范

- 每次改动保持小范围、可审查、可验证，不新增临时计划文档。
- 依赖升级、CI 调整、Kadb API 行为变化要同步更新本文档。
- 用户明确暂停的功能不要在计划里写成待立即开发项。
- GitHub Actions 构建失败时，先根据日志定位具体任务，再修复 workflow 或源码。
- Release 构建需要仓库 Secrets：`SIGNING_KEY_BASE64`、`KEY_ALIAS`、`KEY_STORE_PASSWORD`、`KEY_PASSWORD`。
- 签名证书必须使用 PKCS#12 格式，不允许提交到仓库。
- 提交前执行静态检查，至少确认 `git diff --check` 通过。
- 当前本地没有完整 Android 构建环境时，以静态检查为准，真实编译由 GitHub Actions 验证。

## 编码与换行规范

- 文本文件默认 UTF-8。
- 默认 LF 换行。
- Markdown、YAML、JSON、XML 使用 2 空格缩进。
- Kotlin 使用 4 空格缩进。
- Windows 脚本文件允许 CRLF。
- 二进制文件、图片、APK、签名证书按 `.gitattributes` 标记处理。
- 签名证书、构建产物、APK、AAB 不提交到仓库。

## 发布规范

- 当前本地默认版本号：`1.0.0`，`versionCode` 为 `10000`。
- 正式发布和测试版发布均由 GitHub Actions 手动触发。
- 正式发布输入 `v主版本.次版本.修订版本`，CI 会写入 APK 的 `versionName` 和正式版 `versionCode`。
- 测试版发布输入基础版本标签，CI 会自动追加 `-beta`，写入测试版 `versionName`，并使用低于同版本正式版的 `versionCode`。
- Release notes 根据上一个 `v*` tag 到当前提交之间的 commit message 自动生成。
- Release APK 由 CI 使用 PKCS#12 证书签名后发布到 GitHub Releases。

## 当前风险与注意事项

- 当前依赖管理统一由 `AppServices` 提供，不保留未使用的 DI 框架；未来如需 Desktop 或多实现架构，再单独评估轻量依赖图或 DI。
- 屏幕镜像依赖 `app/src/main/assets/scrcpy/scrcpy-server-v4.1`，更新 server 版本时必须同步更新 `ScrcpyConstants` 和相关协议适配。
- 屏幕镜像会话与 Surface 解耦：`surfaceDestroyed` 只暂停渲染，不拆除 scrcpy；新 Surface 通过 `MediaCodec.setOutputSurface` 与 `RESET_VIDEO` 恢复。视频解码在配置后等待关键帧再上屏。
- mDNS 自动发现依赖局域网 multicast / DNS-SD 支持，部分路由器、访客网络、热点隔离、跨网段场景可能无法发现服务；此时用户可继续使用网段扫描或在设置中手动配置网段。
- 局域网扫描依赖网络拓扑，手机热点、旁路由、访客网络、跨网段场景需要用户在设置中手动配置网段。
- 本机应用安装到目标设备当前只支持用户应用的单 APK 导出；Split APK / App Bundle 不在当前范围。
- Shell 是单条命令执行，不是交互式终端。
- 设备断线后的主动心跳和自动重连当前未作为常驻功能实现，避免增加耗电和后台复杂度。
