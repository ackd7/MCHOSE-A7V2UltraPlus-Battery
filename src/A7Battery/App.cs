using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Media;
using A7Battery.Services;

namespace A7Battery;

public sealed partial class App : Application
{
    public const string AppVersion = "1.5.0";

    private AppSettings _settings = null!;
    private BatteryMonitor _monitor = null!;
    private MainWindow _window = null!;
    private NativeTrayIcon? _tray;

    internal ThemeService Theme { get; } = new();

    /// <summary>当前取色来源描述（壁纸文件名 / 系统强调色 / 预设）。</summary>
    internal string ThemeSource { get; private set; } = "预设";

    /// <summary>按当前设置应用主题取色（启动与用户在 UI 切换时共用）。</summary>
    internal void ApplyTheme()
    {
        try
        {
            Color seed;
            if (_settings.ThemeMode == ThemeMode.Dynamic)
            {
                var (color, source) = ThemeService.ReadDynamicSeed();
                seed = color;
                ThemeSource = source;
            }
            else
            {
                seed = ThemeService.ParseHex(_settings.StaticSeedColor);
                ThemeSource = "预设 · " + _settings.StaticSeedColor;
            }
            Theme.Apply(seed);
            Log($"主题: mode={_settings.ThemeMode} source={ThemeSource} seed={ThemeService.ToHex(Theme.CurrentSeed)}");
        }
        catch (Exception ex) { Log("主题应用失败: " + ex.Message); }
    }

    internal static void Log(string msg)
    {
        try
        {
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "A7Battery");
            Directory.CreateDirectory(dir);
            var path = Path.Combine(dir, "app.log");
            // 超过 256KB 时截断重写，避免长期运行日志无限增长
            if (File.Exists(path) && new FileInfo(path).Length > 256 * 1024) File.Delete(path);
            File.AppendAllText(path, $"[{DateTime.Now:HH:mm:ss.fff}] {msg}\n");
        }
        catch { }
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        ShutdownMode = ShutdownMode.OnExplicitShutdown;
        Log("=== 启动 ===");

        try
        {
            var args = e.Args;
            _settings = AppSettings.Load();
            Log($"设置: StartMinimized={_settings.StartMinimized}, args=[{string.Join(",", args)}]");

            // 在构造窗口前应用主题取色，保证首屏配色正确
            ApplyTheme();

            _monitor = new BatteryMonitor();
            _window = new MainWindow(_settings, _monitor);
            Log("主窗口已构造");

            // 手动启动永远显示窗口；只有开机自启（--minimized）才缩到托盘。
            // 这样即使旧版残留了 StartMinimized=true，双击运行也能看到界面。
            bool minimize = args.Contains("--minimized");
            if (!minimize)
            {
                _window.ShowWindow();
                Log("窗口已显示");
            }
            else
            {
                Log("按要求最小化到托盘");
            }

            // 托盘失败不影响主程序运行
            try { BuildTray(); Log("托盘已创建"); }
            catch (Exception tex) { Log("托盘失败: " + tex.Message); }

            _monitor.ReadingChanged += (_, info) =>
            {
                Log($"读数: ok={info.Success} pct={info.Percent} err={info.Error}");
                RefreshTrayIcon();
            };
            _monitor.Start(_settings.RefreshSeconds);
            Log("监控已启动");

            // 自动更新检查：开启时每天首次启动检查一次，后台执行不阻塞 UI
            if (_settings.CheckUpdates && DateTime.Now.Date != _settings.LastUpdateCheck.Date)
            {
                _ = Task.Run(async () =>
                {
                    var checker = new UpdateChecker();
                    var info = await checker.CheckAsync(AppVersion);
                    _settings.LastUpdateCheck = DateTime.Now;
                    _settings.Save();
                    Log($"更新检查: available={info.Available} latest={info.LatestVersion} err={info.Error}");
                    _window?.SetUpdateStatus(info);
                });
            }
            else
            {
                _window?.SetUpdateStatus(new UpdateInfo());
            }
        }
        catch (Exception ex)
        {
            Log("FATAL: " + ex);
            MessageBox.Show($"启动失败：{ex.Message}", "A7 电量助手", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    /// <summary>把当前主题主色同步到托盘图标。</summary>
    internal void UpdateTrayAccent()
    {
        if (_tray == null) return;
        try
        {
            if (Resources["A7.Primary"] is System.Windows.Media.SolidColorBrush b)
            {
                var c = b.Color;
                _tray.Accent = System.Drawing.Color.FromArgb(c.R, c.G, c.B);
                RefreshTrayIcon();
            }
        }
        catch { }
    }

    private void BuildTray()
    {
        _tray = new NativeTrayIcon();
        UpdateTrayAccent();
        _tray.OnOpen += () => Dispatcher.Invoke(() => _window.ShowWindow());
        _tray.OnRefresh += () => Dispatcher.Invoke(() => _monitor.Refresh());
        _tray.OnExit += () => Dispatcher.Invoke(ExitApp);
        RefreshTrayIcon();
    }

    public void RefreshTrayIcon()
    {
        if (_tray == null) return;
        var info = _monitor?.Current;
        bool showPercent = _settings?.ShowPercentInTray ?? true;

        if (showPercent && info != null && info.Success && info.Percent >= 0)
            _tray.SetIcon(info.Percent, info.Charging,
                $"迈从 A7 V2 Ultra+  {info.Percent}%  {(info.Charging ? "充电中" : "放电中")}");
        else
            _tray.SetIcon(null, false, "A7 电量助手（设备休眠/未连接）");
    }

    public void ExitApp()
    {
        _monitor?.Stop();
        _tray?.Dispose();
        Shutdown();
    }
}
