# sky adb

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
       alt="demo1"
       width="200">
</p>

sky adb 是一款运行在 Android 手机上的 ADB 管理工具，用于通过 WiFi ADB / Wireless Debugging 或 USB OTG 管理手机、平板、电视和盒子。

## 功能特性

- 无线调试配对、连接、断开和最近设备记录
- USB OTG 连接 ADB / Fastboot；Fastboot 支持设备摘要、重启快捷按钮、命令查询和结果复制
- 局域网自动发现和网段扫描，快速发现 adb 设备
- 查看设备基础信息、连接状态和截图
- 应用列表、分类筛选、搜索、启动、停止和卸载
- 本地 APK 安装，在线下载 APK 后安装到目标设备
- 本机用户应用导出并安装到目标设备
- 在线下载文件并推送到目标设备
- 目标设备文件管理、目录浏览、本地文件上传和设备文件下载
- 设备截图预览，需要时再保存到本机
- 目标设备系统日志查看和复制
- 模拟物理按键、虚拟遥控器和屏幕镜像
- Shell 命令执行和输出查看
- AI 助手：接入 OpenAI 兼容模型，流式对话并通过确认后的工具操作设备
- 诊断日志记录错误信息，便于排查连接和功能异常
- 默认端口、连接超时、命令超时、镜像画质和主题设置

## 如何使用

1. 无线连接：在目标设备开启开发者选项和无线调试，在 sky adb 中输入 IP 和 ADB 端口，或先进行无线调试配对。
2. USB 连接：使用支持数据传输的 OTG 线连接，允许本机访问 USB 设备，并在目标设备确认 USB 调试授权；Fastboot 模式可直接从 USB OTG 列表连接。
3. 连接成功后进入设备详情页，使用应用管理、APK 安装、文件管理、截图、遥控器、屏幕镜像或 Shell 等功能。
4. 常用 WiFi 设备会自动保存到最近设备列表，后续可快速回填连接信息。

屏幕镜像的控制区可以隐藏，输入键盘默认关闭，可通过顶部键盘按钮独立打开和关闭。

### AI 助手

在底部打开“AI”，进入右上角模型设置，填写包含 `/v1` 的 API 地址、密钥和模型 ID。可以获取模型列表；服务不提供列表时可手动填写模型 ID。默认使用 Chat Completions，也可切换为服务支持的 Responses 接口。

连接 ADB 设备后，助手可以查询设备信息、列出远端目录、执行经确认的 Shell 命令，以及上传本机文件。助手可以识别所选附件的文件名，读取文本或检查 ZIP 目录与文本条目，也可以在系统权限允许时访问本机共享存储。读取、列出本机内容或上传前会请求确认；设备输出和经确认读取的本机内容会传给所配置的模型服务。对话历史加密保存在本机，并限制保存数量和上下文大小。

连接 New API 等 OpenAI 兼容网关时，API 地址应填写网关的完整 API 前缀（例如 `https://example.com/v1`），并按实际渠道支持情况选择 Chat Completions 或 Responses；模型列表和工具调用取决于网关令牌与上游模型的权限和能力。

## 预览

<div style="display:inline-block">
<img src="docs/img/demo1.jpg" alt="demo1" width="230">
<img src="docs/img/demo2.jpg" alt="demo2" width="230">
<img src="docs/img/demo3.jpg" alt="demo3" width="230">
</div>

---

## 技术栈

- Kotlin
- Jetpack Compose
- Miuix 0.9.4
- Kadb 2.1.5 + kadb-mdns 2.1.4
- scrcpy-server 5.0.1
- Coroutines / Flow
- DataStore
- OkHttp
- Koog（模型接口和工具调用）
- enriched-markdown（流式对话 Markdown 渲染）

详细实现、构建验证和维护规范见 [项目文档](docs/README.md)。
