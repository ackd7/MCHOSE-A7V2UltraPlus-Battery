# MCHOSE A7 V2 Ultra+ Battery

<p align="center">
  <b><a href="#简体中文">简体中文</a></b> &nbsp;|&nbsp; <b><a href="#english">English</a></b>
</p>

> 给 **迈从 MCHOSE A7 V2 Ultra+** 无线鼠标用的 Windows 托盘电量与配置工具：直读厂商 HID 协议获取精确电量，支持 DPI / 回报率调节、低电量提醒、深浅色外观与静默开机自启。
>
> A lightweight Windows system tray battery monitor and configuration utility for the **MCHOSE A7 V2 Ultra+** wireless mouse — reads exact battery level directly via the vendor HID protocol, with DPI / polling-rate tuning, low-battery alerts, light/dark themes, and silent autostart.

---

## 简体中文

基于 **Kotlin + Compose Multiplatform** 重写（v2.0 起从 WPF 迁移），界面参考 [mochi-compose](https://github.com/feice11/mochi-compose) 的 iOS 式设计语言：分组卡片、行内发丝分隔线、悬浮胶囊标签栏。安装包自带运行时，双击即用，无需额外安装 .NET 或 JRE。

### 特性

**电量看得准**

- 直接读取 HID 厂商协议返回的 **0–100% 原始电量值**，不四舍五入、不估算
- 区分 **充电中 / 放电中**，识别 **USB 有线 / 2.4G 无线 / 蓝牙**
- 鼠标休眠时固件会返回全 0，程序**保留上一次成功读数与实际更新时间**并标注「休眠中」，不会突然变成 `--`
- 托盘图标直接绘制百分比数字（按电量档位变色，充电时带闪电标志）

**鼠标配置能改**

- **DPI**：六个档位直接列出各自数值，点一行即设为当前档；可选预设或**自定义任意 100–42000**
- **回报率**：125 / 500 / 1000 / 2000 / 4000 / 8000 Hz 可选，写入后掉电不丢
- 启动时**自动读取**当前配置，切页或重开窗口不丢失；HID 读写加互斥锁，避免与电量轮询并发冲突

**用起来省心**

- **托盘常驻**：左键点击托盘图标即可打开/还原主面板，右键菜单提供打开 / 立即刷新 / 退出
- **静默开机自启**：写当前用户启动项（无需管理员权限），开机以 `--minimized` 静默驻留任务栏托盘，不主动弹出主界面
- **低电量提醒**：电量降到阈值（默认 30%，可在 10–50% 间选）时通过托盘气泡提醒一次，电量回升后自动重新待命
- **刷新间隔**：15 / 30 / 90 秒可选，切换后立即生效；手动刷新在后台异步执行不卡界面
- **自动检查更新**：每次启动检查 GitHub Releases，发现新版本在设置里提示（可关闭），设置页可查看**历史版本更新内容**

**界面**

- 横向自适应窗口：宽屏左右两列，窄屏自动回退单列
- **浅色 / 深色 / 跟随系统**三种外观，主窗口与托盘右键菜单同步即时生效
- 三页之间横向滑动切换（末端带轻微回弹）；下拉选项带缩放弹出动画
- 无边框窗口：最小化 / 最大化 / 关闭做在界面里，标题栏可拖动、双击最大化，支持 Windows 11 圆角
- 托盘右键菜单由 Compose 自绘，中文在任何系统环境下都不会出现方块

### 支持设备

| 设备 | VID | HID 通道 | 说明 |
| --- | --- | --- | --- |
| 迈从 MCHOSE A7 V2 Ultra+ | `3837` | `FF01` / 65 字节 Feature | 有线 `PID 100B`、2.4G 接收器 `PID 4021`、MagDock `PID 1012` |

#### 协议（实机逆向验证）

电量（`0x11` 报告族）：

- `SET_FEATURE(reportId=0x11, ~cmd, 0xFF...)`：`0x03` = bond 唤醒，`0x06` = ost 查询
- 每个命令后固件回 interrupt-IN ACK（`e5 03` / `e5 06`），随后轮询 `GET_FEATURE`
- Feature 在两个 bank 间交替（XOR `0xFF` 与明文），解码后 payload：
  `[0..1]` VID、`[8] & 7` 连接方式、`[9]` 电量百分比、`[10]` 充电状态

DPI / 回报率（同一协议族的厂商命令）：

- 写 DPI `SET_FEATURE(0x11, ~0x40, ...)`：有线/2.4G 档位 + 6 档原始 DPI（u16 LE）+ 启用档数
- 写回报率 `SET_FEATURE(0x11, ~0x41, usbRate, freeRate)`：8K 型号 `0→125, 1→500, 2→1000, 3→2000, 4→4000, 5→8000 Hz`
- 读完整配置 `SET/GET_FEATURE(0x12, ~0x67)`：解析 6 档 DPI 与当前档位索引

两个已知限制：

- **回报率不可回读**：`0x12 0x67` 报告中不含回报率字段（实测写入后回读字节不变），界面显示本地记录值
- **读取校验**：休眠或半截数据会返回乱值，程序校验六档全部落在 100–42000 且为 10 的整数倍，否则视为失败重试

> 协议参考了 [sdparklab/Voltide](https://github.com/sdparklab/Voltide)（MIT）对 MCHOSE A5 / A7 Ultra 的逆向工作，以及 [icepie/mchose-linux](https://github.com/icepie/mchose-linux) 的 `PROTOCOL.md`（DPI / 回报率），特此致谢。

### 下载与运行

从 [Releases](../../releases) 下载最新版本：

- **安装向导（推荐）**：`MCHOSE-A7V2UltraPlus-Battery-Setup-<版本>.exe`
  中文向导，按当前用户安装（无需管理员）。安装时可勾选 **创建桌面快捷方式**、**开机自动启动**；卸载可在「设置 → 应用」中完成，用户配置会保留。
- **MSI 安装包**：`MCHOSE-A7V2UltraPlus-Battery-<版本>.msi`
  系统级 Windows Installer（英文向导），自动创建开始菜单与桌面快捷方式。

运行说明：

- 手动双击启动即显示主窗口；点击关闭隐藏到托盘，最小化缩小到任务栏，均可点击托盘小图标再次拉起
- 开机自启时默认静默驻留任务栏托盘，不弹出主窗口
- 右键托盘图标 →「退出」或设置页 →「退出程序」可完全退出
- 无需厂商驱动、**无需额外运行时**（安装包内置 JRE），支持 Windows 10 1903+ / Windows 11 x64

### 从源码构建

需要 **JDK 21**（`gradlew` 会自动下载 Gradle 9.x）。

```powershell
# 编译
.\gradlew.bat :app:compileKotlinDesktop

# 打包 MSI（产物：app/build/compose/binaries/main/msi/）
.\gradlew.bat :app:packageMsi -PappVersion=3.2.3

# 打中文安装向导（需先装 NSIS，产物：dist/）
makensis /DAPP_VERSION=3.2.3 installer/setup.nsi

# 硬件自检（枚举设备、读电量、读配置）
.\gradlew.bat :app:diag
```

### 目录结构

```text
app/src/desktopMain/kotlin/top/ackd7/mchose/
├── Main.kt                     # 入口：窗口、托盘、监控编排、低电量提醒
├── interop/HidNative.kt        # HID 枚举、Feature 读写与并发锁（JNA）
├── services/
│   ├── MchoseBatteryReader.kt  # 电量协议解析
│   ├── MouseConfigService.kt   # DPI / 回报率读写协议
│   ├── BatteryMonitor.kt       # 轮询调度（协程）
│   ├── NativeTrayIcon.kt       # 系统托盘图标与点击事件
│   ├── AppSettings.kt          # 设置持久化（JSON）
│   ├── Autostart.kt            # 开机自启（注册表）
│   └── UpdateChecker.kt        # GitHub Releases 更新检查
└── ui/
    ├── App.kt                  # 三个页面 + 关于弹窗
    ├── Theme.kt                # miuix 主题（浅色/深色/跟随系统）
    └── mochi/                  # 按 mochi-compose 规范移植的组件
        ├── MochiList.kt        # 分组卡片 / 行 / 开关 / 下拉
        └── MochiTabBar.kt      # 悬浮胶囊标签栏
installer/setup.nsi             # NSIS 中文安装向导
CHANGELOG.md                    # 版本更新记录（Release 说明由此生成）
```

### 技术栈

| 环节 | 选择 |
| --- | --- |
| 语言 / UI | Kotlin + Compose Multiplatform |
| 设计语言 | miuix（MIUI/HyperOS 配色与组件）+ mochi-compose 布局规范 |
| HID 访问 | JNA（`hid.dll` / `setupapi` / `kernel32`） |
| 轮询调度 | Kotlin Coroutines |
| 系统托盘 | `java.awt.SystemTray` 自绘图标 + Compose 自绘右键菜单 + Win32 窗口激活 |
| 打包分发 | jpackage（MSI）+ NSIS（中文安装向导） |

---

## English

Rewritten in **Kotlin + Compose Multiplatform** (migrated from WPF in v2.0), inspired by the iOS-style design language of [mochi-compose](https://github.com/feice11/mochi-compose): grouped cards, hairline dividers, and a floating pill tab bar. Installers bundle the JRE runtime — double-click and run, with no .NET or Java installation required.

### Features

**Accurate Battery Monitoring**

- Reads the raw **0–100% battery percentage** directly from the vendor HID protocol (no rounding or estimation)
- Distinguishes **Charging / Discharging** and detects **USB Wired / 2.4G Wireless / Bluetooth**
- Preserves the **last valid reading and timestamp** labeled as *Sleeping* when the mouse enters sleep mode (firmware reports all zeros), instead of dropping to `--`
- Renders the live percentage directly in the system tray icon (color-coded by level, with a lightning bolt while charging)

**Mouse Configuration**

- **DPI**: Displays all 6 DPI stages at a glance; click any row to select the active stage, pick a preset, or enter a **custom value (100–42000)**
- **Polling Rate**: Supports **125 / 500 / 1000 / 2000 / 4000 / 8000 Hz**, persisted in onboard mouse memory
- **Auto-read on startup**: Loads current DPI stages automatically and shares a mutex lock on the HID channel so configuration writes never collide with battery polling

**Everyday Convenience**

- **System Tray Resident**: Left-click the tray icon to open or restore the main window; right-click for Open / Refresh / Exit
- **Silent Autostart**: Registers a per-user Windows Run entry (no admin privileges required) with `--minimized`, starting silently in the system tray without popping up the main window
- **Low-Battery Alert**: Sends a tray notification once when battery drops to or below the threshold (10–50%, default 30%) and automatically re-arms after charging back up
- **Refresh Interval**: Configurable 15 / 30 / 90 s intervals applied immediately; manual refresh runs asynchronously in the background
- **Update Checker**: Checks GitHub Releases on startup (can be toggled off) and includes an in-app changelog viewer

**Interface**

- Responsive horizontal layout: two-column view on wide windows, automatically collapsing to single-column when narrow
- **Light / Dark / Follow System** themes applied instantly to both the main window and the custom tray context menu
- Smooth horizontal page transitions and spring pop-in dropdown menus
- Borderless window with custom title bar controls, drag-to-move, double-click to maximize, and Windows 11 rounded corners

### Supported Device

| Device | VID | HID Channel | Notes |
| --- | --- | --- | --- |
| MCHOSE A7 V2 Ultra+ | `3837` | `FF01` / 65-byte Feature | Wired `PID 100B`, 2.4G Dongle `PID 4021`, MagDock `PID 1012` |

#### HID Protocol (Hardware-Verified)

Battery (`0x11` report family):

- `SET_FEATURE(reportId=0x11, ~cmd, 0xFF...)`: `0x03` = bond wake-up, `0x06` = ost status query
- Firmware replies with interrupt-IN ACKs (`e5 03` / `e5 06`), followed by polling `GET_FEATURE`
- Feature reports alternate between two banks (XOR `0xFF` and plaintext). Decoded payload:
  `[0..1]` VID, `[8] & 7` connection mode, `[9]` battery percentage, `[10]` charging flag

DPI / Polling Rate (same vendor report family):

- Write DPI `SET_FEATURE(0x11, ~0x40, ...)`: wired/2.4G active index + 6 raw DPI values (`u16 LE`) + active stage count
- Write polling rate `SET_FEATURE(0x11, ~0x41, usbRate, freeRate)`: `0→125, 1→500, 2→1000, 3→2000, 4→4000, 5→8000 Hz`
- Read full config `SET/GET_FEATURE(0x12, ~0x67)`: parses the 6 DPI stages and active stage index

Known limitations:

- **Polling rate cannot be read back**: The `0x12 0x67` report on the A7 V2 Ultra+ does not expose the current polling rate, so the UI displays the locally saved value.
- **Read validation**: Sleep/partial frames may contain garbage bytes; all 6 DPI values must fall within `100..42000` and be multiples of 10, otherwise the read is retried.

> Protocol details build upon the reverse-engineering work of [sdparklab/Voltide](https://github.com/sdparklab/Voltide) (MIT) for MCHOSE A5/A7 Ultra and [icepie/mchose-linux](https://github.com/icepie/mchose-linux) (`PROTOCOL.md`).

### Download & Usage

Download from [Releases](../../releases):

- **Setup Wizard (Recommended)**: `MCHOSE-A7V2UltraPlus-Battery-Setup-<version>.exe`
  Per-user NSIS installer (Chinese wizard, no administrator rights needed) with optional desktop shortcut and autostart. Uninstalling via Windows Settings keeps your user config.
- **MSI Package**: `MCHOSE-A7V2UltraPlus-Battery-<version>.msi`
  Standard Windows Installer package with Start Menu and Desktop shortcuts.

Usage notes:

- Double-clicking launches the main window; closing hides it to the system tray, and minimizing sends it to the taskbar — left-click the tray icon anytime to bring it back
- Autostart launches silently into the system tray (`--minimized`) without opening the window
- Right-click the tray icon → **退出 (Exit)** to quit completely
- No vendor driver or external runtime needed (JRE bundled); requires Windows 10 1903+ / Windows 11 x64

### Build from Source

Requires **JDK 21** (`gradlew` downloads Gradle 9.x automatically).

```powershell
# Compile
.\gradlew.bat :app:compileKotlinDesktop

# Build MSI package (output: app/build/compose/binaries/main/msi/)
.\gradlew.bat :app:packageMsi -PappVersion=3.2.3

# Build NSIS Setup installer (requires NSIS, output: dist/)
makensis /DAPP_VERSION=3.2.3 installer/setup.nsi

# Run hardware diagnostics (enumerate HID devices, read battery & config)
.\gradlew.bat :app:diag
```

### Tech Stack

| Layer | Technology |
| --- | --- |
| Language / UI | Kotlin + Compose Multiplatform |
| Design System | miuix (MIUI/HyperOS tokens & components) + mochi-compose layout |
| HID Access | JNA (`hid.dll` / `setupapi` / `kernel32`) |
| Scheduling | Kotlin Coroutines |
| System Tray | `java.awt.SystemTray` dynamic icon + Compose context menu + Win32 window restore |
| Packaging | jpackage (MSI) + NSIS Setup Wizard |

---

## 版本历史 & 许可 / Changelog & License

- 更新日志 / Full changelog: [CHANGELOG.md](CHANGELOG.md)
- 开源协议 / License: MIT License — see [LICENSE](LICENSE)
- 致谢 / Credits: UI powered by [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) (Apache-2.0) and inspired by [mochi-compose](https://github.com/feice11/mochi-compose); HID protocol informed by [sdparklab/Voltide](https://github.com/sdparklab/Voltide) and [icepie/mchose-linux](https://github.com/icepie/mchose-linux).
