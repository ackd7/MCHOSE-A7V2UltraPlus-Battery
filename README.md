# MCHOSE A7 V2 Ultra+ Battery

> 给 **迈从 MCHOSE A7 V2 Ultra+** 无线鼠标用的 Windows 托盘电量小工具 —— 直接读厂商 HID 协议拿到精确电量，顺手还能调 DPI 和回报率。

基于 **Kotlin + Compose Multiplatform** 重写（v2.0 起从 WPF 迁移），界面参考 [mochi-compose](https://github.com/feice11/mochi-compose) 的 iOS 式设计语言：分组卡片、行内发丝分隔线、悬浮胶囊标签栏。安装包自带运行时，双击即用，无需装 .NET 或 JRE。

---

## 特性

**电量看得准**

- 直接读 HID 厂商协议返回的 **0–100% 原始值**，不做四舍五入、不估算法
- 区分 **充电中 / 放电中**，识别 **USB 有线 / 2.4G 无线 / 蓝牙**
- 鼠标休眠时固件会返回全 0，程序**保留上一次成功读数**并标注「休眠中」，不会突然变成 `--`
- 托盘图标直接绘制百分比数字（按电量档位变色，充电时带闪电）

**鼠标配置能改**

- **DPI**：六个档位一目了然地列出各自数值，点一行即设为当前档；数值可选预设，也可以**自定义任意 100–42000**
- **回报率**：125 / 500 / 1000 / 2000 / 4000 / 8000 Hz 可选，写入掉电不丢
- 启动时**自动读取**当前配置，切页或重开窗口都不会丢

**用起来省心**

- 托盘常驻：左键打开面板，右键菜单提供打开 / 刷新 / 退出
- **低电量提醒**：电量降到阈值（默认 30%，可在 10–50% 之间选）时用托盘气泡提醒一次，回升后自动重新待命
- **开机自启**：写当前用户启动项，不需要管理员权限
- **刷新间隔**：15 / 30 / 90 秒可选
- **自动检查更新**：每次启动检查 GitHub Releases，发现新版本在设置里提示（可关闭）
- 设置里可查看**历史版本更新内容**

**界面**

- 横向窗口、宽屏左右两列、窄屏自动回退单列
- **浅色 / 深色 / 跟随系统**三种外观，切换即时生效
- 三页之间横向滑动切换（末端带轻微回弹）；下拉选项带缩放弹出动画
- 无边框窗口：最小化 / 最大化 / 关闭做在界面里，标题栏可拖动、双击最大化，Windows 11 圆角
- 托盘右键菜单由 Compose 自绘，中文在任何环境下都不会变方块

## 支持设备

| 设备 | VID | 通道 | 说明 |
| --- | --- | --- | --- |
| 迈从 MCHOSE A7 V2 Ultra+ | `3837` | `FF01` / 65 字节 Feature | 有线 `PID 100B`、2.4G 接收器 `PID 4021`、MagDock `PID 1012` |

### 协议（实机逆向验证）

电量（`0x11` 报告族）：

- `SET_FEATURE(reportId=0x11, ~cmd, 0xFF...)`：`0x03` = bond 唤醒，`0x06` = ost 查询
- 每个命令后固件回 interrupt-IN ACK（`e5 03` / `e5 06`），收到 ACK 后 `GET_FEATURE`
- Feature 在两个 bank 间交替（XOR `0xFF` 与明文），解码后 payload：
  `[0..1]` VID、`[8] & 7` 连接方式、`[9]` 电量百分比、`[10]` 充电状态

DPI / 回报率（同一协议族的厂商命令）：

- 写 DPI `SET_FEATURE(0x11, ~0x40, ...)`：有线/2.4G 档位 + 6 档原始 DPI（u16 LE）+ 启用档数
- 写回报率 `SET_FEATURE(0x11, ~0x41, usbRate, freeRate)`：8K 型号 `0→125, 1→500, 2→1000, 3→2000, 4→4000, 5→8000 Hz`
- 读完整配置 `SET/GET_FEATURE(0x12, ~0x67)`：解析 6 档 DPI 与档位索引

两个已知限制：

- **回报率不可回读**：`0x12 0x67` 报告里没有回报率字段（实测写入后回读字节不变），所以界面显示的是本地记录值
- **读取要校验**：休眠或半截数据会返回乱值，程序要求六档全部落在 100–42000 且为 10 的整数倍，否则判为失败重试

> 协议参考了 [sdparklab/Voltide](https://github.com/sdparklab/Voltide)（MIT）对 MCHOSE A5 / A7 Ultra 的逆向工作，以及 [icepie/mchose-linux](https://github.com/icepie/mchose-linux) 的 `PROTOCOL.md`（DPI / 回报率），特此致谢。

## 下载与运行

从 [Releases](../../releases) 下载：

- **安装向导（推荐）**：`MCHOSE-A7V2UltraPlus-Battery-Setup-<版本>.exe`
  中文向导，按当前用户安装（无需管理员）。安装时可勾选 **创建桌面快捷方式**、**开机自动启动**；
  卸载在「设置 → 应用」里完成，用户配置会保留。
- **MSI 安装包**：`A7Battery-<版本>.msi`
  系统级 Windows Installer（英文向导），自动创建开始菜单与桌面快捷方式。

运行说明：

- 双击即显示主窗口；关闭或最小化后继续在托盘运行，左键托盘图标可再次打开
- 右键托盘图标 →「退出」完全退出
- 无需厂商驱动、**无需额外运行时**（安装包自带 JRE）
- 需要 Windows 10 1903+ / Windows 11 x64

## 从源码构建

需要 **JDK 21**（`gradlew` 会自动下载 Gradle 9.x）。

```powershell
# 编译
.\gradlew.bat :app:compileKotlinDesktop

# 打包 MSI（产物：app/build/compose/binaries/main/msi/）
.\gradlew.bat :app:packageMsi -PappVersion=3.1.2

# 打中文安装向导（需先装 NSIS，产物：dist/）
makensis /DAPP_VERSION=3.1.2 installer/setup.nsi

# 硬件自检（枚举设备、读电量、读配置）
.\gradlew.bat :app:diag
```

## 目录结构

```
app/src/desktopMain/kotlin/top/ackd7/a7battery/
├── Main.kt                     # 入口：窗口、托盘、监控编排、低电量提醒
├── interop/HidNative.kt        # HID 枚举与 Feature 读写（JNA）
├── services/
│   ├── MchoseBatteryReader.kt  # 电量协议解析
│   ├── MouseConfigService.kt   # DPI / 回报率读写协议
│   ├── BatteryMonitor.kt       # 轮询调度（协程）
│   ├── NativeTrayIcon.kt       # 原生托盘（Shell_NotifyIcon / JNA）
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

## 版本历史

每个版本的新增与修复见 [CHANGELOG.md](CHANGELOG.md)；应用内「设置 → 关于 → 版本」也能直接查看。

## 技术栈

| 环节 | 选择 |
| --- | --- |
| 语言 / UI | Kotlin + Compose Multiplatform |
| 设计语言 | miuix（MIUI/HyperOS 配色与组件）+ mochi-compose 布局规范 |
| HID 访问 | JNA（hid.dll / setupapi） |
| 轮询 | Kotlin 协程 |
| 托盘 | JNA 调 Shell_NotifyIcon + 自绘图标 |
| 打包 | jpackage（MSI）+ NSIS（中文安装向导） |

## 许可

MIT License，详见 [LICENSE](LICENSE)。

UI 依赖 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)（Apache-2.0），布局与交互规范参考 [mochi-compose](https://github.com/feice11/mochi-compose)；电量与配置协议参考前述两个逆向项目。一并致谢。
