# A7 Battery

一个基于 **Compose Multiplatform (Kotlin) + miuix** 的 Windows 托盘小工具，用于查看 **迈从 MCHOSE A7 V2 Ultra+** 无线鼠标的精确电量。

左键点击任务栏托盘图标即可快速查看电量，支持开机自启、可调刷新间隔，还能直接调整鼠标 DPI 与回报率、自动检查新版本。界面采用 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) 的 MIUI/HyperOS 设计语言（`lightColorScheme` / `darkColorScheme`，主色 `#3482FF`）。

> 本仓库 v2.0 起从 WPF + Material Design 3 迁移至 Compose Desktop + miuix：去除了所有 WPF/MD3 与动态取色代码，仅保留 HID 协议、托盘、设置等核心业务逻辑，并用 Kotlin 重写。

## 功能

- **电量**：直接读取鼠标 HID 厂商协议返回的 0–100% 原始数值（不四舍五入）。
- **三页布局**：首页看电量，第二页调 DPI / 回报率，第三页放设置。
- **mochi 风格界面**：参考 [mochi-compose](https://github.com/feice11/mochi-compose) 的设计规范——iOS 式 inset-grouped 分组卡片、行内发丝分隔线、连续圆角、iOS 风格开关，以及底部悬浮胶囊标签栏（选中指示器弹簧滑动）。
- **无边框窗口**：最小化 / 最大化 / 关闭按钮做在界面内，标题栏可拖动、双击最大化，Windows 11 圆角。
- **托盘右键菜单**：由 Compose 自绘（不是 AWT 原生菜单），中文在任何 JRE 下都不会显示成方块。
- **充电状态**：区分放电中 / 充电中，并在托盘提示中显示。
- **连接方式**：识别 USB 有线 / 2.4G 无线 / 蓝牙。
- **任务栏托盘**：左键打开面板；右键菜单提供打开面板、刷新、退出。
- **托盘电量角标**：直接在托盘图标上绘制百分比数字（按电量高低变色，充电时带闪电）。
- **开机自启**：写入当前用户启动项，无需管理员权限。
- **刷新间隔**：15s / 30s / 90s 可选，设置持久化保存。
- **休眠处理**：鼠标休眠时保留最后一次读数并提示，不会误报 0%。
- **DPI 设置**：6 档 DPI 可视化编辑，选择当前档位一键写入鼠标板载配置；「读取当前」可回读设备现有档位值。
- **回报率设置**：125 / 500 / 1000 / 2000 / 4000 / 8000 Hz 滑条档位，写入后掉电不丢失（蓝牙固定 1K）。
- **自动检查更新**：每天首次启动后台请求 GitHub Releases，发现新版本在界面提示下载（可在设置中关闭）。
- **miuix 风格界面**：直接使用 miuix 的 `MiuixTheme`（`lightColorScheme` / `darkColorScheme`），主色 `#3482FF`、次要文字 `#8C93B0`，跟随系统浅色/深色模式。

## 支持设备

| 设备 | VID | 通道 | 说明 |
| --- | --- | --- | --- |
| 迈从 MCHOSE A7 V2 Ultra+ | `3837` | `FF01` / 65 字节 Feature | 有线 `PID 100B`、2.4G 接收器 `PID 4021`、MagDock `PID 1012` |

协议为实机逆向验证：
- `SET_FEATURE(reportId=0x11, ~cmd, 0xFF...)`，`cmd 0x03` = bond 唤醒，`0x06` = ost 查询
- 每个命令后固件回 interrupt-IN ACK（`e5 03` / `e5 06`），收到 ACK 后 `GET_FEATURE`
- Feature 在两个 bank 间交替（XOR `0xFF` 与明文），解码后 payload：
  - `[0..1]` VID `37 38`，`[8]&7` 连接方式，`[9]` 电量百分比，`[10]` 充电状态

DPI / 回报率写入沿用同一协议族的厂商命令：
- 写 DPI `SET_FEATURE(reportId=0x11, ~0x40, ...)`：当前有线/2.4G 档位 + 6 档原始 DPI（u16 LE）+ 启用档数。
- 写回报率 `SET_FEATURE(reportId=0x11, ~0x41, usbRate, freeRate)`：8K 型号档位 `0→125, 1→500, 2→1000, 3→2000, 4→4000, 5→8000 Hz`。
- 读完整配置 `SET/GET_FEATURE(reportId=0x12, ~0x67)`：解析 6 档 DPI 与档位索引。
- 所有写入前先 bond 唤醒、写入后回读 echo 确认，并留足配置提交时间。

> 协议参考了 [sdparklab/Voltide](https://github.com/sdparklab/Voltide)（MIT）对 MCHOSE A5 / A7 Ultra 的逆向工作，以及 [icepie/mchose-linux](https://github.com/icepie/mchose-linux) 对 M HUB Web 驱动 DPI / 回报率配置的逆向文档（`PROTOCOL.md`），特此致谢。

## 下载与运行

从 [Releases](../../releases) 下载，有两种分发形式：

- **安装向导（推荐）**：`A7Battery-Setup-<版本>.exe` —— 中文向导，按当前用户安装（无需管理员），
  安装时可勾选 **创建桌面快捷方式**、**开机自动启动**；卸载可在「设置 → 应用」里完成，用户配置会保留。
- **MSI 安装包**：`A7Battery-<版本>.msi` —— 系统级 Windows Installer（英文向导），会自动建开始菜单与桌面快捷方式。

- 双击运行即显示主窗口；关闭/最小化窗口后程序继续在托盘运行，左键托盘图标可再次打开。
- 右键托盘图标 → 「退出」可完全退出程序。
- 无需厂商驱动、**无需额外运行时**（Compose Desktop 打包自带 JRE）。
- 需要 Windows 10 1903+ / Windows 11 x64。

## 从源码构建

需要 **JDK 17+**（无需预装 Gradle，`gradlew` 会自动下载 Gradle 9.9.2）。

```powershell
# 构建
.\gradlew.bat :app:build

# 打包桌面分发（MSI + EXE）
.\gradlew.bat :app:packageDistributionForCurrentOS
# 产物：app/build/compose/binaries/
```

也可以使用仓库根目录的 `build.ps1` 一键构建 + 打包。

## 目录结构

```
A7Battery/
├── app/                                   # Compose Desktop 应用
│   ├── build.gradle.kts                   # KMP + Compose Desktop + miuix 依赖
│   └── src/desktopMain/
│       ├── kotlin/top/ackd7/a7battery/
│       │   ├── Main.kt                    # 应用入口 + 托盘/监控编排
│       │   ├── interop/
│       │   │   └── HidNative.kt           # HID 枚举与 Feature 读写（JNA）
│       │   ├── services/
│       │   │   ├── MchoseBatteryReader.kt # 电量协议解析
│       │   │   ├── MouseConfigService.kt  # DPI / 回报率读写协议
│       │   │   ├── BatteryMonitor.kt      # 轮询调度（协程）
│       │   │   ├── NativeTrayIcon.kt      # 原生托盘（Shell_NotifyIcon / JNA）
│       │   │   ├── AppSettings.kt         # 设置持久化（JSON）
│       │   │   ├── Autostart.kt           # 开机自启（注册表 / JNA）
│       │   │   └── UpdateChecker.kt       # GitHub Releases 更新检查
│       │   └── ui/
│       │       ├── Theme.kt               # miuix 主题封装
│       │       └── App.kt                 # 主界面（miuix 组件）
│       └── resources/app.ico              # 应用图标
├── gradle/libs.versions.toml              # 版本目录
├── gradlew / gradlew.bat                  # Gradle Wrapper
├── build.ps1                              # 一键构建脚本
└── LICENSE
```

## 技术栈迁移说明

| 原 WPF 版 (v1.x) | 现 Compose 版 (v2.0) |
| --- | --- |
| .NET 8 + WPF + XAML | Kotlin + Compose Multiplatform |
| Material Design 3 (MaterialDesignInXAML) | miuix（MIUI/HyperOS 设计语言） |
| P/Invoke (hid.dll / setupapi) | JNA (hid.dll / setupapi) |
| DispatcherTimer 轮询 | Kotlin 协程轮询 |
| 自绘窗口 + 系统圆角 | miuix-window |
| 动态取色（Oklab / Material You） | miuix 内建 Monet / 主题 token |

## 许可

MIT License. 详见 [LICENSE](LICENSE)。

本项目电量协议参考了 [sdparklab/Voltide](https://github.com/sdparklab/Voltide)（MIT License）的逆向成果，DPI / 回报率协议参考了 [icepie/mchose-linux](https://github.com/icepie/mchose-linux) 的逆向文档，UI 使用 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)（Apache-2.0），特此致谢。
