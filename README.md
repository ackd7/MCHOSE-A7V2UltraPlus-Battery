# A7 Battery

一个基于 **WPF**（.NET 8）的 Windows 托盘小工具，采用 **Material Design 3** 界面，用于查看 **迈从 MCHOSE A7 V2 Ultra+** 无线鼠标的精确电量。

左键点击任务栏托盘图标即可快速查看电量，支持开机自启、可调刷新间隔，还能直接调整鼠标 DPI 与回报率、自动检查新版本。

## 功能

- **精确电量**：直接读取鼠标 HID 厂商协议返回的 0–100% 原始数值（不四舍五入）。
- **充电状态**：区分放电中 / 充电中，并在托盘提示中显示。
- **连接方式**：识别 USB 有线 / 2.4G 无线 / 蓝牙。
- **任务栏托盘**：左键打开面板；右键菜单提供打开面板、刷新、退出。
- **托盘电量角标**：直接在托盘图标上绘制百分比数字（按电量高低变色，充电时带闪电）。
- **开机自启**：写入当前用户启动项，无需管理员权限。
- **刷新间隔**：15s / 30s / 90s 可选，设置持久化保存。
- **休眠处理**：鼠标休眠时保留最后一次读数并提示，不会误报 0%。
- **DPI 设置**：6 档 DPI 可视化编辑，选择当前档位一键写入鼠标板载配置；「读取当前」可回读设备现有档位值。
- **回报率设置**：125 / 500 / 1000 / 2000 / 4000 / 8000 Hz 可选，写入后掉电不丢失（蓝牙固定 1K）。
- **自动检查更新**：每天首次启动后台请求 GitHub Releases，发现新版本在窗口底部提示下载（可在设置中关闭）。

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

- **安装包（推荐）**：`A7Battery-Setup-v1.2.0.exe` —— 双击安装，写入卸载注册表（在「设置 → 应用」/ 控制面板「程序和功能」中可正常卸载），可选创建桌面快捷方式与开机自启。安装到 `Program Files\A7Battery`，需要管理员权限。
- **便携解压版**：`A7Battery-win-x64.zip` —— 解压后运行 `A7Battery.exe`，免安装、免管理员权限。

- 双击运行即显示主窗口；关闭窗口后程序继续在托盘运行，左键托盘图标可再次打开。
- 右键托盘图标 → 「退出」可完全退出程序。
- 无需厂商驱动、**无需任何额外运行时**（自包含 .NET 8 + WPF + Material Design 3）。
- 需要 Windows 10 1903+ / Windows 11 x64。

## 界面

采用 Material Design 3（通过 [MaterialDesignInXAML](https://github.com/MaterialDesignInXAML/MaterialDesignInXamlToolkit) 5.x 的 MD3 主题），卡片 / 开关 / 按钮 / 进度条均为 MD3 风格。电量数字按高低变色（高=主题主色、中=琥珀、低=红），充电时显示闪电图标。

### 动态 / 静态取色（Material You）

参考 [end-4/dots-hyprland](https://github.com/end-4/dots-hyprland) 的 Material You 配色理念，界面提供两种取色模式：

- **静态取色**：从 8 套预设种子色（紫罗兰、海洋蓝、青碧、森林绿、琥珀橙、玫红、靛青、石墨）中选择，配色持久保存。
- **动态取色（壁纸取色）**：直接读取当前桌面壁纸图像，采样并按色相分桶加权提取代表性主色作为种子色（Wallpaper Engine 等动态壁纸同样适用）。换壁纸后点「重新取色」即可让界面跟随，正是 dots-hyprland 的 wallpaper → palette 流程。若壁纸过于暗淡/单调无法提取，会回退到 Windows 系统强调色。

两种模式都会从一个种子色生成整套 MD3 语义色板（Primary / Secondary / Tertiary / Container / Surface / Outline …），实时应用到窗口、内置控件与托盘图标。取色与色板生成都是 self-contained 实现（System.Drawing 采样 + HSL tonal 算法），无需额外依赖，切换即时生效。

## 从源码构建

需要 .NET 8 SDK（无需 Visual Studio、无需 Windows SDK）。

WPF 的 XAML 编译器内置于 .NET SDK，因此普通 `dotnet build` 即可完成全部编译，不依赖 Visual Studio 或任何外部工具，也没有 PRI / WindowsAppSDK 运行时等部署要求。

```powershell
# 调试构建（框架依赖，需本机已装 .NET 8 桌面运行时）
dotnet build src/A7Battery/A7Battery.csproj -c Debug -p:Platform=x64

# 发布构建（自包含 .NET 8 + WPF + MD3，可直接分发）
dotnet build src/A7Battery/A7Battery.csproj -c Release -p:Platform=x64
# 产物：src/A7Battery/bin/x64/Release/net8.0-windows/win-x64/
```

## 制作安装包

使用 [Inno Setup 6](https://jrsoftware.org/isinfo.php) 生成带卸载注册表的安装程序：

```powershell
# 先构建 Release，再编译安装脚本
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" installer\A7Battery.iss
# 产物：dist\installer\A7Battery-Setup-v1.2.0.exe
```

安装脚本位于 `installer/A7Battery.iss`，包含：
- 卸载注册表项（HKLM Uninstall，可在「程序和功能」中卸载）
- 开始菜单快捷方式 + 卸载入口；可选桌面快捷方式
- 可选开机自启（写 HKCU `...\Run`，与程序内设置一致）
- 安装前自动结束正在运行的旧实例

## 电量探测工具

`tools/Probe` 是命令行工具，用于验证协议和查看原始 HID 报告：

```powershell
dotnet run --project tools/Probe/Probe.csproj
```

## 目录结构

```
A7Battery/
├── src/A7Battery/                # WPF 应用（.NET 8）
│   ├── Interop/HidNative.cs      # HID 设备枚举与 Feature 读写（P/Invoke）
│   ├── Services/
│   │   ├── MchoseBatteryReader.cs # 电量协议解析
│   │   ├── MouseConfigService.cs # DPI / 回报率读写协议
│   │   ├── ThemeService.cs       # Material You 动态/静态取色（色板生成）
│   │   ├── WallpaperColorService.cs # 壁纸主色提取
│   │   ├── UpdateChecker.cs      # GitHub Releases 更新检查
│   │   ├── NativeTrayIcon.cs     # 原生托盘图标（Shell_NotifyIcon）
│   │   ├── BatteryMonitor.cs     # 轮询调度（DispatcherTimer）
│   │   ├── AppSettings.cs        # 设置持久化
│   │   └── Autostart.cs          # 开机自启（注册表）
│   ├── MainWindow.xaml / .cs     # 主界面（WPF XAML，Material Design 3）
│   ├── App.xaml / App.cs         # 全局 MD3 资源与生命周期入口
│   └── Assets/app.ico            # 应用图标
├── installer/A7Battery.iss       # Inno Setup 安装包脚本（卸载注册表）
├── tools/Probe/                  # HID 探测命令行工具
└── tools/make_icon.py            # 图标生成脚本
```

## 许可

MIT License. 详见 [LICENSE](LICENSE)。

本项目电量协议参考了 [sdparklab/Voltide](https://github.com/sdparklab/Voltide)（MIT License）的逆向成果，DPI / 回报率协议参考了 [icepie/mchose-linux](https://github.com/icepie/mchose-linux) 的逆向文档，特此致谢。
