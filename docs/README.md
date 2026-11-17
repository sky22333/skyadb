# sky adb 项目文档

本文档基于当前源码实际状态整理，用于说明 sky adb 的功能范围、项目结构、开发规范、维护规范和性能要求。后续开发应在功能或工程结构变化后同步更新本文档。

## 项目定位

sky adb 是一款运行在 Android 手机上的支持简体中文和英文的 ADB 管理工具。它通过 WiFi ADB / Wireless Debugging 或 USB OTG 连接并管理目标 Android 设备，适用于手机、平板、电视、盒子等设备的日常维护。

项目采用单 Android App 模块。

## 当前功能

- 无线调试配对：输入配对 IPv4/IPv6 地址、配对端口、配对码后调用 Kadb Pairing。
- 手动连接设备：输入目标设备 IPv4/IPv6 地址和 ADB 端口；IPv6 显式端口使用 `[地址]:端口`，link-local 地址可带 `%接口名`。
- USB OTG：识别 ADB / Fastboot 接口，授权后连接；Fastboot 设备页提供镜像刷写、临时启动、固件 ZIP 浏览和槽位管理；命令页提供设备摘要、命令查询和结果复制。
- 最近设备：保存 WiFi 设备记录，支持点击回填 IP 和端口；USB 总线地址仅用于当前插入，不持久化。
- 设备详情：点击设备名称查看 Android 信息或 Fastboot 摘要；点击连接状态选择重启到系统、Bootloader、Recovery 或 fastbootd。
- 应用管理：通过目标设备探针获取真实应用名称和图标，支持用户应用、系统应用分类、搜索、启动、停止和卸载；图标按需加载。
- APK 安装：选择本机 APK 并安装到已连接设备。
- 在线下载：从 HTTP / HTTPS 链接下载 APK 或普通文件，支持进度、取消、下载后安装或推送。
- 文件管理：支持本机与目标设备双栏目录浏览、上传、下载、新建文件夹、重命名和删除文件。
- 本机应用安装到目标设备：列出本机用户应用，支持单 APK 应用导出并安装到目标设备。
- 截图：调用目标设备 `screencap`，拉取截图到本机缓存并预览，需要时再由用户选择保存位置。
- 系统日志：读取目标设备最近系统日志，支持复制日志内容，便于排查目标设备问题。
- 虚拟遥控器：采用实体遥控器式布局，提供电源、唤醒、息屏、方向与确认、返回、主页、菜单、音量、静音和媒体控制共 17 个按键。默认字号下紧凑手机也可一屏操作；横屏自动切换双区布局，所有按键触控区域至少 48dp。状态提示固定占位，错误提示可点击查看完整原因，反馈不会推移按键。
- 屏幕镜像：基于官方 `scrcpy-server-v5.0.1`、Kadb `localabstract` 流、`SurfaceView` 和 `MediaCodec` 实现画面预览与远程触控；默认 H.264，可在设置中切换 H.265；支持音频转发与中文输入；镜像页支持控制区显示/隐藏、实时码率/解码帧率/丢帧/流水线延迟统计，输入键盘默认关闭，可独立显示和关闭；关闭输入区时收起软键盘并释放焦点。画面按远端视频比例居中显示。
- 命令页：ADB 模式执行 Shell 命令，Fastboot 模式发送协议命令；支持粘贴带 `adb` / `adb shell` / `fastboot` 前缀的输入，展示输出和执行状态。
- 局域网发现：优先通过 Android NSD / mDNS 自动发现 ADB 服务，支持 `_adb._tcp.`、`_adb-tls-pairing._tcp.`、`_adb-tls-connect._tcp.`；自动发现无结果时，保留 TCP 网段扫描和 ADB 协议轻握手确认作为兜底。
- AI 助手：配置 OpenAI 兼容接口、密钥和模型，支持获取模型列表、Chat Completions / Responses、流式对话、历史记录和 Markdown 渲染。支持选择文件供工具读取或传输，并向支持多模态的模型发送图片。
- AI 工具：支持已连接或最近设备、多设备 ADB 会话、设备 Shell、界面读取、授权的本机文件与跨设备文件操作、APK 提取和 HTTP / HTTPS 文件下载；可配置工具执行确认或自动执行。
- MCP：配置 Streamable HTTP 服务，发现并调用远端工具，支持 Bearer Token 和连接测试。
- 诊断日志：记录进程内错误和异常，支持复制排查。
- 设置：支持默认端口、连接超时、命令超时、扫描网段、屏幕镜像画质、屏幕镜像编码器、主题模式配置。

## 技术栈

依赖版本统一维护在 `gradle/libs.versions.toml`，Gradle 版本和校验值维护在 `gradle/wrapper/gradle-wrapper.properties`。

| 组件 | 当前版本 |
| --- | --- |
| Gradle Wrapper / Android Gradle Plugin | 9.8.1 / 9.4.1 |
| Kotlin | 2.4.21 |
| Compose BOM / Miuix | 2026.09.00 / 0.9.4 |
| Activity / AppCompat | 1.13.0 / 1.8.0 |
| Lifecycle / Miuix Navigation | 2.11.0 / 0.9.4 |
| Coroutines / DataStore Preferences | 1.11.0 / 1.2.1 |
| Kadb / kadb-mdns | 2.1.5 / 2.1.4 |
| scrcpy-server | 5.0.1 |
| OkHttp / Okio | 5.5.0 / 3.18.2 |
| fastboot-kt / Timber | 1.0.3（本地模块） / 5.0.1 |
| Koog OpenAI / MCP | 1.3.0 / 1.3.0-beta |
| Ktor OkHttp / enriched-markdown | 3.3.3 / 0.3.0 |
| JUnit / AndroidX Test Runner / AndroidX Test JUnit | 4.13.2 / 1.7.0 / 1.3.0 |

`kadb-mdns` 独立于核心库指定版本，以各模块实际发布的制品为准。

## 运行范围

- 运行 sky adb 的设备：最低 Android 7.0，API 24。
- 编译目标：compileSdk 37，targetSdk 37。
- Android 17 局域网：targetSdk 37 需声明并运行时申请 `ACCESS_LOCAL_NETWORK`，用于 mDNS 发现、网段扫描和 WiFi ADB 连接。
- 被连接设备：支持 IPv4/IPv6 ADB over TCP、Wireless Debugging，或已开启 USB 调试并完成授权。Android 11+ 推荐使用无线调试配对；传统 WiFi ADB 设备通常使用 `5555` 端口。IPv6 局域网自动发现仍以 mDNS 为准，网段扫描只处理 IPv4。
- 屏幕镜像目标设备：以目标设备是否支持 scrcpy server、所选视频编码器和 ADB socket 转发为准；默认画质使用均衡模式（1280p/60fps/6Mbps），默认编码器为 H.264，高负载或弱网设备可在设置中切换为流畅模式（1024p/30fps/2Mbps）。

## 项目结构

```text
skyadb/
├── .github/workflows/
│   ├── android-checks.yml       # push / PR：单元测试、Lint、Debug 构建
│   └── android-ci.yml           # 手动正式发布：测试、Lint、签名构建、Release
├── app/
│   ├── build.gradle.kts         # SDK、版本、签名、依赖和资源收缩
│   ├── proguard-rules.pro       # Release 混淆配置
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/scrcpy/   # 官方 scrcpy server
│       │   ├── java/com/sky22333/skyadb/
│       │   │   ├── adb/         # Kadb 身份、连接、命令、传输和 Fastboot
│       │   │   ├── ai/          # 模型、会话、工具、文件和 MCP
│       │   │   ├── apps/        # 目标设备应用名称、图标与分类信息
│       │   │   ├── data/        # DataStore 设置和最近 WiFi 设备
│       │   │   ├── diagnostics/ # 有界进程内错误日志
│       │   │   ├── discovery/   # kadb-mdns 适配、网段扫描和协议探测
│       │   │   ├── download/    # OkHttp 下载与取消
│       │   │   ├── files/       # 本机文件、缓存和 SAF 复制
│       │   │   ├── i18n/        # 应用语言与资源访问
│       │   │   ├── localapps/   # 本机应用读取、图标和 APK 导出
│       │   │   ├── model/       # 数据、结果与操作状态模型
│       │   │   ├── repository/  # ADB 仓库接口与连接状态整合
│       │   │   ├── scrcpy/      # 会话、协议、异步音视频解码和触控
│       │   │   ├── ui/          # Compose 页面、ViewModel、组件与主题
│       │   │   ├── usb/         # OTG 发现、授权和插拔事件
│       │   │   └── validation/  # 网络、URL、路径和文件名校验
│       │   └── res/             # 中英文资源、图标、主题与平台配置
│       ├── probe/               # 目标设备应用信息探针源码，构建时生成 DEX
│       ├── test/                # JVM 单元测试
│       └── androidTest/         # USB、镜像、解码和键盘真机测试
├── docs/README.md               # 当前实现与开发维护规范
├── docs/img/                    # README 使用的预览图片
├── gradle/libs.versions.toml    # 依赖版本
├── gradle/wrapper/              # 固定版本与 SHA-256 的 Gradle Wrapper
├── build.gradle.kts
├── settings.gradle.kts
└── README.md                    # 产品介绍和使用方式
```

## 架构说明

当前架构采用单 Android App 模块，保持实现直接、边界清晰。

- UI 层：Compose 页面只负责展示状态和派发用户事件。
- ViewModel 层：持有页面状态，调用仓库、下载、文件、扫描等服务。
- Repository 层：面向 UI 提供稳定的 ADB 操作接口，隐藏 Kadb 细节。
- KadbManager：集中封装真实 ADB 能力，避免页面层直接拼接和调用底层 API。
- DataStore：保存设置和最近设备，所有读取以 Flow 形式向上游暴露。
- AI：`AiViewModel` 管理会话、流式响应和工具调用；`AiConfigStore` 加密保存配置与历史，模型客户端按需创建；`AgentSessions` 管理额外 ADB 会话，MCP 连接在使用后关闭。
- Apps：应用信息探针在目标设备读取名称和图标，页面按需请求图标并使用有界缓存。
- Discovery：自动发现和扫描逻辑独立于 UI。mDNS 由 `kadb-mdns` 管理 Android NSD 发现、解析和关闭，发现页离开时停止；配对查找连接端口使用独立短时会话，IPv6 主机按规范化地址比较。TCP 扫描支持并发限制、超时、取消和进度节流，仅覆盖 IPv4 网段。
- Download：下载逻辑独立于页面，取消时关闭 OkHttp Call；文件名经过校验，下载落入独立缓存子目录，进度更新节流。
- Diagnostics：错误日志仅保存在进程内，最多 500 条；Debug 构建同时将失败原因写入 logcat，不做永久存储。
- Scrcpy：屏幕镜像逻辑独立于页面，使用官方 server、MediaCodec 硬解码和控制协议封装；WiFi 优先建立专用 ADB transport，失败时回退主连接，USB OTG 复用主连接。视频编码器、画质档和性能统计集中管理。
- Validation：输入校验集中维护，避免每个页面重复实现 IPv4/IPv6、端口、URL、路径判断；endpoint 显示统一使用 IPv6 方括号格式。

## 连接、传输与镜像实现

- 应用探针由 `app/src/probe/java` 构建为 DEX；进入应用管理时推送探针和 scrcpy server 至目标设备 `/data/local/tmp/skyadb-app-probe.dex` 与 `/data/local/tmp/skyadb-app-probe.jar`，通过 `app_process` 运行。退出时关闭进程并尝试清理，固定文件名可在下次启动时覆盖；图标缓存上限为 4 MiB。
- USB ADB 由 `KadbUsb.isAdbDevice()` 识别、`KadbUsb.create()` 建立连接。USB 镜像与其他操作复用该连接；WiFi 镜像尝试新建相同身份认证的专用 TCP ADB transport，设备不接受第二连接时自动回退共享连接。
- 连接、断开、设备信息刷新和最近设备状态合并由仓库串行管理；授权等待受超时与取消控制，失败或取消会关闭待连接实例。
- ADB 身份由 `AdbIdentityManager` 配置持久化存储，密钥在 IO 操作时按需加载。
- 远端目录使用 `openSync().list()`，由 Kadb 协商 Sync v1/v2。文件下载写入目标同目录临时文件，成功后原子替换；失败保留已有文件。
- Fastboot 使用本地 fastboot-kt 模块和 USB bulk 传输；会话内串行执行命令，协议异常或取消时关闭连接。镜像从文件流式读取，超过设备下载限制时转换或拆分为 Sparse 分片。
- Fastboot 输出缓存上限为 64 KiB，超出时标记截断并继续排空响应，保留终态。UI 在命令结束后一次更新输出，显示区域限制高度并支持双向滚动；命令结果和设备摘要均可复制。
- 进入 Fastboot 命令页时读取产品型号、序列号、Bootloader 版本、当前槽位和 `is-userspace`，并支持手动刷新；不轮询或持久化摘要。不支持的变量显示“设备未提供”，不根据 `secure` 猜测解锁状态。
- Fastboot 快捷按钮支持重启到系统、Bootloader、Recovery 和 fastbootd，实际可用性取决于设备。仅收到成功终态后显示指令已确认并清理当前连接；设备重新枚举后可从首页或 AI 工具重新连接，不自动重放指令。
- 单 Activity 应用入口使用 `singleTask`，USB 重新接入通过 `onNewIntent` 刷新设备列表，保留当前命令页和导航状态，避免模式切换堆叠 Activity。
- 命令输入由 `CommandInputParser` 统一解析，只去除开头完整的客户端名称（支持 `.exe`），ADB Shell 正文的参数、引号和操作符保留。前缀与当前连接模式不一致时拒绝执行，不自动切换设备。
- Fastboot 命令页使用对应标题和示例。`fastboot getvar product`、`fastboot erase cache`、`fastboot set_active b` 分别转换为 `getvar:product`、`erase:cache`、`set_active:b`；支持 `fastboot reboot` 及带重启目标的写法，原始协议和 OEM 命令可直接输入。
- `devices` 属于主机枚举操作，提示用户到首页查看设备；桌面选设备参数、交互式 Shell 参数和未实现的客户端文件传输流程在发送前提示。命令页不提供完整桌面 adb / fastboot 客户端兼容层；设备页和 AI 均可使用本地镜像执行 Fastboot 刷写。
- `ScrcpySession` 管理官方 server 及视频、音频、控制流；镜像退出只关闭自己的流和专用 ADB 连接，不关闭共享的 Kadb 连接。
- scrcpy 5.0.1 服务端通过 `LocalServerSocket` 输出已编码流，客户端经 ADB `localabstract` 读取；ADB 没有 UDP 转发服务，因此不改 server 时不能把该链路替换成 UDP。当前优化集中在 H.264/H.265 编码、画质档、Surface 解码、积压丢帧、关键帧恢复和专用 ADB 连接。
- `MediaCodecPump` 在独立 HandlerThread 上串行管理解码器，输出不依赖下一次网络输入。普通输入采用反压，视频增量帧使用非阻塞提交；解码器积压时丢弃当前非关键帧并等待关键帧，通过 `RESET_VIDEO` 限流请求恢复，避免延迟持续累积。支持设备声明的低延迟解码；音频按实际输出格式创建 AudioTrack，解码 PCM 直接通过 ByteBuffer 写入。
- `ScrcpySession` 每秒汇总传输类型、独立/共享连接、实际解码器、接收/解码帧率、码率、待解码队列深度、累计丢帧、关键帧请求和相对流水线延迟增长，镜像页在控制区可见时显示紧凑统计条。
- Surface 重建时保留 scrcpy 会话，重建视频解码器并发送 `RESET_VIDEO`，等待关键帧恢复画面；旧 Surface 的迟到回调与新解码器隔离。
- 镜像启停串行化，延迟清理不关闭后续启动的新会话。触摸 MOVE 按指针合并，保留按下、抬起和其他控制指令的顺序。
- scrcpy 资产为 `app/src/main/assets/scrcpy/scrcpy-server-v5.0.1`，SHA-256 为 `764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`；更换资产时同步更新 `ScrcpyConstants` 并核验协议。

## AI 功能与边界

- 模型接入使用 Koog 的 OpenAI 兼容客户端，支持自定义 Base URL、API Key、模型列表、Chat Completions 和 Responses；模型、服务端对工具调用及图片的支持情况取决于提供商。
- 历史对话和草稿加密保存在本机；主动退出后再次进入默认新对话，历史仍可访问。后台返回保留当前会话。
- 图片通过支持多模态的模型接口发送；普通附件由 AI 工具按用户授权读取或传输，文件内容不会因为选择附件就全部自动发送给模型。
- AI 能发现并复用已连接设备或最近的 WiFi 设备，也可管理其他 ADB 会话。AI 可发现并连接 USB ADB 和 Fastboot 设备、查询和执行协议命令，从本地文件、附件或授权目录流式刷写、暂存或启动镜像，并可在设备支持时读取暂存数据或分区。固件 ZIP 支持列出并提取条目到应用下载目录；设备重启后重新发现并连接。本机 Shell 受应用自身 UID 和 Android 权限约束；授权目录使用系统文件选择器，不依赖 Root 或 Shizuku。
- MCP 通过 Streamable HTTP 发现工具。工具执行可选择逐次确认或自动执行；AI 下载复用现有下载服务，提供进度、取消及文件导出。
- 当前没有内置 AI 联网搜索或网页读取；设备连接、模型服务和 MCP 服务可用性由实际环境决定。

## UI 与交互规范

- 界面文案使用资源提供简体中文和英文，品牌名统一写作 `sky adb`；提示使用当前应用语言，精简重复说明，失败时展示具体原因和必要的下一步操作。
- 优先使用 Miuix 默认主题、组件和图标；默认效果不符合语义、布局或交互需求时，只做必要的少量调整，保持整体扁平、清爽且有设计感。
- 同类页面统一边距、圆角、控件高度和视觉节奏；标题、图标、文字、选择器和操作按钮应按布局规则对齐，避免局部拥挤或大面积无意义留白。
- 设置项保持紧凑、平齐，不按用途增加额外分组；文案长度和条目间距应协调。避免不必要的卡片嵌套、灰色底色和过深阴影，检查圆角容器是否裁剪内容，点击反馈与控件形状一致。
- 图标应准确表达实际功能，同类操作使用一致的语义和样式。纯图标按钮保留无障碍描述和足够的触控区域；不常用操作可适当收敛，不能因此降低可发现性。
- 页面切换优先采用现有导航组件和轻量动画，可按实际效果调整速度；正向与返回的动画应自然一致。键盘弹出、收起及系统栏过渡不应造成遮挡、跳动或多层画面叠加。
- AI 对话输入区保持轻量、悬浮且与页面协调，兼容长文本、换行、附件和停止生成；设置弹窗与键盘不应让底层输入框产生重叠或意外聚焦。后台返回保留当前对话、草稿和位置，主动退出后的历史仍可访问。
- 屏幕镜像以画面为主，控制区和输入键盘可独立显示、隐藏；画质在设置页配置。耗时操作展示状态和结果，高风险操作保留必要的确认。
- 按内容规模选择普通布局或惰性列表；避免在页面绘制和滚动中执行阻塞操作，性能和动画流畅度以真机测量为准。

## 性能要求

- 所有 ADB、下载、文件、扫描、本机应用读取等阻塞任务必须放到 `Dispatchers.IO` 执行；仅使用 ViewModel 协程不代表已离开主线程。
- 下载进度和扫描进度必须节流，避免高频状态更新造成 Compose 频繁重组。
- 局域网扫描默认并发为 48，上限为 64，默认超时为 600 ms。
- mDNS 自动发现只在用户进入局域网发现页时启动，离开发现页必须停止，不做后台常驻监听。
- 默认扫描 `/24` 网段，最多手动配置 6 个网段，避免过大范围导致耗电、卡顿和误扫。
- 扫描只在用户主动触发时执行，不做后台常驻扫描。
- 下载文件和导出的 APK 放在缓存目录，导出前清理旧 APK，避免缓存无限膨胀。
- 大列表优先使用惰性布局，列表项保持稳定 key；小型固定内容按实际需要选择布局。
- ViewModel 中的 Flow 收集和协程任务要跟随 ViewModel 生命周期释放，避免 Activity 泄漏。
- 不在 Compose 页面中直接持有 `Context` 长生命周期引用；需要上下文的服务使用 `applicationContext`。
- 状态模型要小而稳定，避免把大文件内容、图片 Bitmap 或长输出反复塞入全局状态。
- 屏幕镜像默认使用均衡画质，用户可切换流畅、均衡、高清三档；高清模式不作为默认值。流畅、均衡、高清均优先保证 60fps 观看流畅度，并可通过码率/分辨率取舍适配设备能力。
- 屏幕镜像默认 H.264，用户可切换 H.265；H.265 仅在目标设备编码器和本机解码器均支持时使用，切换后下一会话生效。
- 屏幕镜像使用 `SurfaceView` 按远端视频比例居中显示，触摸坐标必须与实际画面区域一致。
- 屏幕镜像必须维持有界积压；视频积压时丢弃过期增量帧并请求关键帧，不能让延迟随时间增长。
- 屏幕镜像的触摸、按键、文本发送和 socket 释放必须在 IO 线程执行，不能阻塞主线程。
- 屏幕镜像不因短暂切后台或 Surface 重建而拆除会话；回前台通过重建解码器并发送 `RESET_VIDEO` 恢复画面。用户关闭页面、离开镜像页或视频流异常时释放会话。

## 开发规范

- 开发前先阅读当前源码、已有 docs、相关依赖实际 API 和官方文档。
- 不凭经验猜测 Kadb、Compose、DataStore、OkHttp、Android 平台 API 行为。
- 每次改动保持小范围、可解释、可验证，不做无关重构。
- 所有新增页面和功能必须有加载、空状态、错误态、执行中、成功态的处理。
- 业务逻辑优先放在 ViewModel、Repository、Manager 或 Validator 中，不塞进 Composable。
- 不重复造同类输入校验、状态展示、结果封装。
- 新增 ADB 命令时必须封装在 KadbManager 或 Repository 层，页面只调用语义化方法。
- 新增设置项必须进入 `AppSettingsStore`，并在设置页保持布局紧凑统一。
- 新增持久化数据按当前版本设计，保持必要的读取和错误处理；无需增加尚未发布版本的升级兼容代码。
- 新增网络能力必须考虑超时、取消、错误提示、缓存清理和进度节流。
- 新增 UI 优先使用 Miuix 组件、默认主题和图标，并复用现有尺寸规范；必要的定制应保持整体一致。
- 新增镜像参数必须通过 `MirrorQualityPreset`、`MirrorVideoCodec` 或 `ScrcpyOptions` 集中管理，不能在 UI 或启动命令中散落硬编码。

## 验收与维护

- 保持修改范围清晰，优先复用现有组件；替换实现时删除旧代码、旧状态和无用资源。
- 功能应具备必要的执行状态、结果、错误提示、取消或资源释放路径。高风险操作须清楚展示目标及操作内容。
- 测试覆盖有意义的关键路径，不增加重复测试或仅为假设场景堆砌兜底。无需保留未发布版本的数据迁移和升级兼容代码。
- 耗时任务不得阻塞主线程，协程、连接、文件流和缓存应按生命周期释放或限制大小。
- 功能、依赖、协议、CI 或工程结构变化时同步更新文档；不要提交缓存、构建产物、签名证书或临时分析文件。
- 文本默认 UTF-8、LF；Kotlin 缩进 4 空格，Markdown、YAML、JSON 和 XML 缩进 2 空格；Windows 脚本可使用 CRLF。
- 正式发布使用 GitHub Actions 和 PKCS#12 签名，所需 Secrets 为 `SIGNING_KEY_BASE64`、`KEY_ALIAS`、`KEY_STORE_PASSWORD`、`KEY_PASSWORD`。

## 构建与验证

- 使用项目 Gradle Wrapper，安装 Android SDK Platform 37 和 Build Tools 37.0.0；编译和 CI 使用 JDK 21 或更高版本。
- `local.properties` 配置本机 `sdk.dir`，或通过 `ANDROID_HOME` 指向 Android SDK。
- Windows 执行以下命令；Linux / macOS 改用 `./gradlew` 调用相同任务。

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleDebug assembleRelease
.\gradlew.bat connectedDebugAndroidTest
git diff --check
```

单元测试覆盖输入校验、错误处理、协议编码、状态模型、AI 工具及历史，以及 Fastboot 多包响应、总超时、取消、输出上限和异常关闭。真机测试覆盖单帧解码输出、镜像键盘开关、USB ADB 并发与文件往返、镜像 Surface 重建，以及 Fastboot 摘要、多行响应、失败后的查询和界面复制。USB 测试要求本机作为 OTG 主机连接对应模式下已授权的目标；缺少目标时跳过对应测试。

`FastbootUiTest` 默认只查询和复制结果。显式传入 instrumentation 参数 `fastbootReboot=bootloader` 或 `fastbootReboot=system` 时额外验证对应重启按钮及会话清理，测试结束后目标设备停留在所选模式。额外传入 `fastbootPrepare=true` 可从已授权的 USB ADB 目标重启到 Bootloader，再执行本轮测试；未显式指定时不会切换目标模式。

Debug 使用本地调试签名；未配置发布签名时，本地 Release APK 为未签名产物。Release 使用 R8 混淆和资源收缩。

## 发布规范

- 当前本地默认版本号：`1.0.0`，`versionCode` 为 `10000`。
- 正式发布由 GitHub Actions 手动触发。
- 正式发布输入 `v主版本.次版本.修订版本`，CI 会写入 APK 的 `versionName` 和正式版 `versionCode`。
- Release notes 根据上一个 `v*` tag 到当前提交之间的 commit message 自动生成。
- Release APK 由 CI 使用 PKCS#12 证书签名后发布到 GitHub Releases。

## 当前风险与注意事项

- 依赖统一由 `AppServices` 提供，服务使用应用上下文。
- mDNS 自动发现依赖局域网 multicast / DNS-SD 支持，部分路由器、访客网络、热点隔离、跨网段场景可能无法发现服务；此时用户可继续使用网段扫描或在设置中手动配置网段。
- 局域网扫描依赖网络拓扑，手机热点、旁路由、访客网络、跨网段场景需要用户在设置中手动配置网段。
- 本机应用安装到目标设备当前只支持用户应用的单 APK 导出；Split APK / App Bundle 不在当前范围。
- Shell 是单条命令执行，不是交互式终端。Fastboot 不支持完整桌面客户端流程。
- 应用探针依赖目标设备 ADB shell 和 Android 系统 API；目标环境不兼容或连接异常时不能保证获取所有名称和图标。
- AI 图片理解、工具调用及 MCP 功能取决于所选模型、服务端及用户授予的权限；本机 Shell 不具备系统或 Root 权限。
- 设备断线后的主动心跳和自动重连当前未作为常驻功能实现，避免增加耗电和后台复杂度。
