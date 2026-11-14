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
- AI 助手：接入 OpenAI 兼容模型，通过AI操作设备
- 诊断日志记录错误信息，便于排查连接和功能异常
- 默认端口、连接超时、命令超时、镜像画质和主题设置

## 如何使用

1. 无线连接：在目标设备开启开发者选项和无线调试，在 sky adb 中输入 IP 和 ADB 端口，或先进行无线调试配对。
2. USB 连接：使用支持数据传输的 OTG 线连接，允许本机访问 USB 设备，并在目标设备确认 USB 调试授权；Fastboot 模式可直接从 USB OTG 列表连接。
3. 连接成功后进入设备详情页，使用应用管理、APK 安装、文件管理、截图、遥控器、屏幕镜像或 Shell 等功能。
4. 常用 WiFi 设备会自动保存到最近设备列表，后续可快速回填连接信息。

屏幕镜像的控制区可以隐藏，输入键盘默认关闭，可通过顶部键盘按钮独立打开和关闭。

## 预览

<div style="display:inline-block">
<img src="docs/img/demo1.jpg" alt="demo1" width="200">
<img src="docs/img/demo2.jpg" alt="demo2" width="200">
<img src="docs/img/demo3.jpg" alt="demo3" width="200">
<img src="docs/img/demo4.jpg" alt="demo3" width="200">
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
- Koog
- enriched-markdown

详细实现、构建验证和维护规范见 [项目文档](docs/README.md)。
