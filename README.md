# A7 Battery

一个基于 **WPF**（.NET 8）的 Windows 托盘小工具，用于查看 **迈从 MCHOSE A7 V2 Ultra+** 无线鼠标的精确电量。

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

从 [Releases](../../releases) 下载 `A7Battery-win-x64.zip`，解压后运行 `A7Battery.exe`。

- 双击运行即显示主窗口；关闭窗口后程序继续在托盘运行，左键托盘图标可再次打开。
- 右键托盘图标 → 「退出」可完全退出程序。
- 无需安装、无需管理员权限、无需厂商驱动、**无需任何额外运行时**。
- 需要 Windows 10 1903+ / Windows 11。发布的 zip 为自包含版本，内含 .NET 8 运行时，解压即用。

## 从源码构建

需要 .NET 8 SDK（无需 Visual Studio、无需 Windows SDK）。

WPF 的 XAML 编译器内置于 .NET SDK，因此普通 `dotnet build` 即可完成全部编译，不依赖 Visual Studio 或任何外部工具，也没有 PRI / WindowsAppSDK 运行时等部署要求。

```powershell
# 调试构建（框架依赖，需本机已装 .NET 8 桌面运行时）
dotnet build src/A7Battery/A7Battery.csproj -c Debug -p:Platform=x64

# 发布构建（自包含 .NET 8 + WPF，可直接分发）
dotnet build src/A7Battery/A7Battery.csproj -c Release -p:Platform=x64
# 产物：src/A7Battery/bin/x64/Release/net8.0-windows/win-x64/
```

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
│   │   ├── UpdateChecker.cs      # GitHub Releases 更新检查
│   │   ├── NativeTrayIcon.cs     # 原生托盘图标（Shell_NotifyIcon）
│   │   ├── BatteryMonitor.cs     # 轮询调度（DispatcherTimer）
│   │   ├── AppSettings.cs        # 设置持久化
│   │   └── Autostart.cs          # 开机自启（注册表）
│   ├── MainWindow.xaml / .cs     # 主界面（WPF XAML）
│   ├── App.cs / Program.cs       # 入口与生命周期
│   └── Assets/app.ico            # 应用图标
├── tools/Probe/                  # HID 探测命令行工具
└── tools/make_icon.py            # 图标生成脚本
```

## 许可

MIT License. 详见 [LICENSE](LICENSE)。

本项目电量协议参考了 [sdparklab/Voltide](https://github.com/sdparklab/Voltide)（MIT License）的逆向成果，DPI / 回报率协议参考了 [icepie/mchose-linux](https://github.com/icepie/mchose-linux) 的逆向文档，特此致谢。
