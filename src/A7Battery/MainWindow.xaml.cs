using System;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Media;
using A7Battery.Services;

namespace A7Battery;

public sealed partial class MainWindow : Window
{
    private readonly AppSettings _settings;
    private readonly BatteryMonitor _monitor;
    private readonly MouseConfigService _mouse = new();
    private readonly App _app;
    private bool _suppressToggle;
    private UpdateInfo? _update;

    public MainWindow(AppSettings settings, BatteryMonitor monitor)
    {
        _settings = settings;
        _monitor = monitor;
        _app = (App)Application.Current;
        InitializeComponent();

        // 关闭按钮只隐藏到托盘，不销毁窗口（配合 OnExplicitShutdown）
        Closing += (_, e) => { e.Cancel = true; Hide(); };

        ApplySettings();
        _monitor.ReadingChanged += (_, info) => Dispatcher.Invoke(() => UpdateUI(info));
    }

    private static SolidColorBrush Brush(string hex) =>
        new SolidColorBrush((Color)ColorConverter.ConvertFromString(hex));

    public void ShowWindow()
    {
        Show();
        if (WindowState == WindowState.Minimized) WindowState = WindowState.Normal;
        Activate();
    }

    private void ApplySettings()
    {
        _suppressToggle = true;
        AutoStartSwitch.IsChecked = Autostart.IsEnabled();
        TrayPercentSwitch.IsChecked = _settings.ShowPercentInTray;
        CheckUpdatesSwitch.IsChecked = _settings.CheckUpdates;
        _suppressToggle = false;
        _monitor.SetInterval(_settings.RefreshSeconds);
        HighlightInterval(_settings.RefreshSeconds);

        // 本地记忆的鼠标配置填入输入框
        var dpis = new[] { Dpi0, Dpi1, Dpi2, Dpi3, Dpi4, Dpi5 };
        for (int i = 0; i < 6; i++) dpis[i].Text = _settings.DpiStages[i].ToString();
        ActiveDpiCombo.SelectedIndex = _settings.ActiveDpiIndex;
        RateCombo.SelectedIndex = RateIndexFromHz(_settings.RateHz);
        VersionText.Text = "v" + App.AppVersion;

        InitThemeUI();
    }

    // ---------- 主题取色 ----------

    private static Brush Res(string key) =>
        Application.Current.Resources[key] as Brush ?? Brushes.Transparent;

    private void InitThemeUI()
    {
        // 生成预设色板圆点
        PresetSwatches.Children.Clear();
        foreach (var preset in ThemeService.Presets)
        {
            var color = ThemeService.ParseHex(preset.Hex);
            var btn = new System.Windows.Controls.Button
            {
                Width = 30, Height = 30,
                Margin = new Thickness(0, 0, 8, 8),
                Cursor = System.Windows.Input.Cursors.Hand,
                ToolTip = preset.Name + " " + preset.Hex,
                Tag = preset.Hex,
                Background = new SolidColorBrush(color),
                BorderBrush = Res("A7.OutlineVariant"),
                BorderThickness = new Thickness(1),
                Template = MakeSwatchTemplate()
            };
            btn.Click += PresetColor_Click;
            PresetSwatches.Children.Add(btn);
        }
        RefreshThemeUI();
    }

    private static ControlTemplate MakeSwatchTemplate()
    {
        var template = new ControlTemplate(typeof(System.Windows.Controls.Button));
        var border = new FrameworkElementFactory(typeof(Border), "SwatchBd");
        border.SetBinding(Border.BackgroundProperty, new System.Windows.Data.Binding("Background") { RelativeSource = new System.Windows.Data.RelativeSource(System.Windows.Data.RelativeSourceMode.TemplatedParent) });
        border.SetBinding(Border.BorderBrushProperty, new System.Windows.Data.Binding("BorderBrush") { RelativeSource = new System.Windows.Data.RelativeSource(System.Windows.Data.RelativeSourceMode.TemplatedParent) });
        border.SetBinding(Border.BorderThicknessProperty, new System.Windows.Data.Binding("BorderThickness") { RelativeSource = new System.Windows.Data.RelativeSource(System.Windows.Data.RelativeSourceMode.TemplatedParent) });
        border.SetValue(Border.CornerRadiusProperty, new CornerRadius(15));
        template.VisualTree = border;
        return template;
    }

    /// <summary>根据当前设置刷新模式按钮、面板与调色板预览。</summary>
    private void RefreshThemeUI()
    {
        bool dyn = _settings.ThemeMode == ThemeMode.Dynamic;
        ThemeStaticBtn.Background = dyn ? Brush("#E7E0EC") : Res("A7.Primary");
        ThemeStaticBtn.Foreground = dyn ? Brush("#49454F") : Brushes.White;
        ThemeDynamicBtn.Background = dyn ? Res("A7.Primary") : Brush("#E7E0EC");
        ThemeDynamicBtn.Foreground = dyn ? Brushes.White : Brush("#49454F");
        StaticPalettePanel.Visibility = dyn ? Visibility.Collapsed : Visibility.Visible;
        DynamicPalettePanel.Visibility = dyn ? Visibility.Visible : Visibility.Collapsed;
        ThemeModeLabel.Text = dyn ? "动态 · 跟随系统" : "静态 · 预设配色";

        var seed = _app.Theme.CurrentSeed;
        SwatchPrimary.Background = Res("A7.Primary");
        SwatchSecondary.Background = Res("A7.Secondary");
        SwatchTertiary.Background = Res("A7.Tertiary");
        SwatchContainer.Background = Res("A7.PrimaryContainer");
        SwatchSurface.Background = Res("A7.Surface");
        PaletteHexText.Text = $"种子 {ThemeService.ToHex(seed)} · 主色 {ThemeService.ToHex(((SolidColorBrush)Res("A7.Primary")).Color)}";
        AccentSwatch.Background = new SolidColorBrush(seed);
        AccentHexText.Text = ThemeService.ToHex(seed);
    }

    private void ApplyThemeAndRefresh(Color seed)
    {
        _app.Theme.Apply(seed);
        RefreshThemeUI();
        HighlightInterval(_settings.RefreshSeconds);
        _app.UpdateTrayAccent();
    }

    private void ThemeStatic_Click(object sender, RoutedEventArgs e)
    {
        _settings.ThemeMode = ThemeMode.Static;
        _settings.Save();
        ApplyThemeAndRefresh(ThemeService.ParseHex(_settings.StaticSeedColor));
    }

    private void ThemeDynamic_Click(object sender, RoutedEventArgs e)
    {
        _settings.ThemeMode = ThemeMode.Dynamic;
        _settings.Save();
        ApplyThemeAndRefresh(ThemeService.ReadSystemAccent());
    }

    private void PresetColor_Click(object sender, RoutedEventArgs e)
    {
        if (sender is System.Windows.Controls.Button b && b.Tag is string hex)
        {
            _settings.StaticSeedColor = hex;
            _settings.ThemeMode = ThemeMode.Static;
            _settings.Save();
            ApplyThemeAndRefresh(ThemeService.ParseHex(hex));
        }
    }

    private void RefreshAccent_Click(object sender, RoutedEventArgs e)
    {
        _settings.ThemeMode = ThemeMode.Dynamic;
        _settings.Save();
        ApplyThemeAndRefresh(ThemeService.ReadSystemAccent());
    }

    private static int RateIndexFromHz(int hz)
    {
        int idx = Array.IndexOf(MouseConfigService.RateHz, hz);
        return idx >= 0 ? idx : 2;
    }

    private void HighlightInterval(int sec)
    {
        foreach (var b in new[] { Btn15, Btn30, Btn90 })
        {
            bool active = int.TryParse(b.Tag?.ToString(), out int v) && v == sec;
            b.Background = active ? Res("A7.Primary") : Res("A7.SurfaceVariant");
            b.Foreground = active ? Brushes.White : Res("A7.OnSurfaceVariant");
        }
    }

    private void Interval_Click(object sender, RoutedEventArgs e)
    {
        if (sender is System.Windows.Controls.Button b && int.TryParse(b.Tag?.ToString(), out int sec))
        {
            _settings.RefreshSeconds = sec;
            _settings.Save();
            _monitor.SetInterval(sec);
            HighlightInterval(sec);
        }
    }

    private void AutoStart_Toggled(object sender, RoutedEventArgs e)
    {
        if (_suppressToggle) return;
        _settings.AutoStart = AutoStartSwitch.IsChecked == true;
        Autostart.SetEnabled(_settings.AutoStart);
        _settings.Save();
    }

    private void TrayPercent_Toggled(object sender, RoutedEventArgs e)
    {
        if (_suppressToggle) return;
        _settings.ShowPercentInTray = TrayPercentSwitch.IsChecked == true;
        _settings.Save();
        (Application.Current as App)?.RefreshTrayIcon();
    }

    private void CheckUpdates_Toggled(object sender, RoutedEventArgs e)
    {
        if (_suppressToggle) return;
        _settings.CheckUpdates = CheckUpdatesSwitch.IsChecked == true;
        _settings.Save();
    }

    private void Refresh_Click(object sender, RoutedEventArgs e) => _monitor.Refresh();

    // ---------- 鼠标 DPI / 回报率 ----------

    private void ReadMouse_Click(object sender, RoutedEventArgs e)
    {
        ReadMouseBtn.IsEnabled = false;
        MouseStatusText.Text = "正在读取鼠标配置…";
        Task.Run(() => _mouse.Read()).ContinueWith(t =>
        {
            var cfg = t.Result;
            Dispatcher.Invoke(() =>
            {
                ReadMouseBtn.IsEnabled = true;
                if (!cfg.Success)
                {
                    MouseStatusText.Text = "读取失败：" + cfg.Error;
                    return;
                }
                var dpis = new[] { Dpi0, Dpi1, Dpi2, Dpi3, Dpi4, Dpi5 };
                for (int i = 0; i < 6; i++) dpis[i].Text = cfg.DpiStages[i].ToString();
                if (cfg.ActiveDpiIndex >= 0) ActiveDpiCombo.SelectedIndex = cfg.ActiveDpiIndex;

                int hz = cfg.UsbRateIndex >= 0 && cfg.UsbRateIndex < MouseConfigService.RateHz.Length
                    ? MouseConfigService.RateHz[cfg.UsbRateIndex]
                    : (cfg.WirelessRateIndex >= 0 && cfg.WirelessRateIndex < MouseConfigService.RateHz.Length
                        ? MouseConfigService.RateHz[cfg.WirelessRateIndex] : -1);
                if (hz > 0) RateCombo.SelectedIndex = RateIndexFromHz(hz);

                MouseStatusText.Text = $"已读取 · 当前档位 {cfg.ActiveDpiIndex + 1} · {MouseConfigService.RateLabel(cfg.UsbRateIndex)}"
                    + (string.IsNullOrEmpty(cfg.Raw) ? "" : "  [" + cfg.Raw + "]");
            });
        });
    }

    private int[] CollectDpi(out string error)
    {
        error = "";
        var boxes = new[] { Dpi0, Dpi1, Dpi2, Dpi3, Dpi4, Dpi5 };
        var values = new int[6];
        for (int i = 0; i < 6; i++)
        {
            if (!int.TryParse(boxes[i].Text.Trim(), out values[i]) || values[i] < 100 || values[i] > 30000)
            {
                error = $"档位 {i + 1} 的 DPI 需在 100–30000 之间";
                return Array.Empty<int>();
            }
        }
        return values;
    }

    private void ApplyDpi_Click(object sender, RoutedEventArgs e)
    {
        var values = CollectDpi(out string error);
        if (values.Length == 0) { MouseStatusText.Text = error; return; }
        int active = ActiveDpiCombo.SelectedIndex;
        ApplyDpiBtn.IsEnabled = false;
        MouseStatusText.Text = "正在写入 DPI…";

        Task.Run(() =>
        {
            bool ok = _mouse.SetDpi(values, active, out string err);
            return (ok, err);
        }).ContinueWith(t =>
        {
            Dispatcher.Invoke(() =>
            {
                ApplyDpiBtn.IsEnabled = true;
                var (ok, err) = t.Result;
                if (ok)
                {
                    _settings.DpiStages = values;
                    _settings.ActiveDpiIndex = active;
                    _settings.Save();
                    MouseStatusText.Text = $"DPI 已写入 · 当前档位 {active + 1} = {values[active]}";
                }
                else MouseStatusText.Text = "DPI 写入失败：" + err;
            });
        });
    }

    private void ApplyRate_Click(object sender, RoutedEventArgs e)
    {
        var item = RateCombo.SelectedItem as ComboBoxItem;
        if (item?.Tag == null || !int.TryParse(item.Tag.ToString(), out int hz))
        {
            MouseStatusText.Text = "请选择回报率";
            return;
        }
        int idx = RateIndexFromHz(hz);
        ApplyRateBtn.IsEnabled = false;
        MouseStatusText.Text = "正在写入回报率…";

        Task.Run(() =>
        {
            bool ok = _mouse.SetRate(idx, out string err);
            return (ok, err);
        }).ContinueWith(t =>
        {
            Dispatcher.Invoke(() =>
            {
                ApplyRateBtn.IsEnabled = true;
                var (ok, err) = t.Result;
                if (ok)
                {
                    _settings.RateHz = hz;
                    _settings.Save();
                    MouseStatusText.Text = $"回报率已写入 · {hz} Hz";
                }
                else MouseStatusText.Text = "回报率写入失败：" + err;
            });
        });
    }

    // ---------- 更新 ----------

    /// <summary>由 App 在后台检查完成后调用，把结果显示在底部。</summary>
    public void SetUpdateStatus(UpdateInfo info)
    {
        _update = info;
        Dispatcher.Invoke(() =>
        {
            if (info.Available && !string.IsNullOrEmpty(info.LatestVersion)
                && info.LatestVersion != _settings.SkippedVersion)
            {
                var link = new Hyperlink(new Run("有新版本 " + info.LatestVersion + "，点击查看/下载"))
                {
                    NavigateUri = new Uri(info.HtmlUrl),
                    Foreground = Res("A7.Primary")
                };
                link.RequestNavigate += (_, _) =>
                {
                    try { System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(info.HtmlUrl) { UseShellExecute = true }); }
                    catch { }
                };
                UpdateStatusText.Text = "";
                UpdateStatusText.Inlines.Clear();
                UpdateStatusText.Inlines.Add(link);
            }
            else if (!string.IsNullOrEmpty(info.Error))
            {
                UpdateStatusText.Text = "更新检查失败（不影响使用）";
            }
            else
            {
                UpdateStatusText.Text = "已是最新版本";
            }
        });
    }

    private void UpdateStatus_Click(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (_update == null || string.IsNullOrEmpty(_update.HtmlUrl)) return;
        try { System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(_update.HtmlUrl) { UseShellExecute = true }); }
        catch { }
    }

    private void UpdateUI(BatteryInfo info)
    {
        if (info == null) return;
        if (info.Success && info.Percent >= 0)
        {
            var brush = info.Percent >= 50
                ? Res("A7.Primary")
                : Brush(info.Percent >= 20 ? "#FFC83D" : "#FF5C5C");
            PercentText.Text = info.Percent.ToString();
            PercentText.Foreground = brush;
            BatteryBar.Value = info.Percent;
            BatteryBar.Foreground = brush;
            BatteryFill.Width = 64.0 * info.Percent / 100.0;
            BatteryFill.Background = brush;
            BatteryOutline.BorderBrush = brush;
            BatteryCap.Fill = brush;
            StatusDot.Fill = Brush("#22C55E");
            StatusText.Text = "在线";
            BoltIcon.Visibility = info.Charging ? Visibility.Visible : Visibility.Collapsed;
            ChargeText.Text = info.Charging ? "正在充电" : "放电中";
        }
        else
        {
            PercentText.Text = "--";
            PercentText.Foreground = Brush("#9AA5B4");
            BatteryBar.Value = 0;
            BatteryFill.Width = 0;
            StatusDot.Fill = Brush("#F59E0B");
            StatusText.Text = "离线/休眠";
            BoltIcon.Visibility = Visibility.Collapsed;
            ChargeText.Text = string.IsNullOrEmpty(info.Error) ? "等待读取数据" : info.Error;
        }

        ModeText.Text = info.Mode switch
        {
            ConnectionMode.Usb => "USB 有线",
            ConnectionMode.Radio24G => "2.4G 无线",
            ConnectionMode.Bluetooth => "蓝牙",
            _ => "未知"
        };
        DeviceText.Text = string.IsNullOrEmpty(info.DeviceName) ? "--" : info.DeviceName;
        UpdateText.Text = info.UpdatedAt == default ? "--" : info.UpdatedAt.ToString("HH:mm:ss");
    }
}
