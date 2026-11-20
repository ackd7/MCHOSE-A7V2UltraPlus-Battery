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
    private int _activeDpiIndex = 2;
    private int _selectedRateIndex = 2;
    private bool _styleComboInit;
    private int _lastPercent = -1;

    private TextBox[] DpiBoxes => new[] { Dpi0, Dpi1, Dpi2, Dpi3, Dpi4, Dpi5 };
    private Border[] DpiChips => new[] { DpiChip0, DpiChip1, DpiChip2, DpiChip3, DpiChip4, DpiChip5 };

    public MainWindow(AppSettings settings, BatteryMonitor monitor)
    {
        _settings = settings;
        _monitor = monitor;
        _app = (App)Application.Current;
        InitializeComponent();

        // 系统级圆角（不透明窗口，4K 下文字清晰；替代 AllowsTransparency 位图缩放）
        SourceInitialized += (_, _) => WindowRounding.Apply(this);

        // 关闭窗口（含自绘 X 与 Alt+F4）只隐藏到托盘，后台监控继续运行
        Closing += (_, e) => { e.Cancel = true; Hide(); };
        // 最大化图标随窗口状态切换（圆角由系统 DWM 处理）
        StateChanged += (_, _) =>
        {
            bool max = WindowState == WindowState.Maximized;
            MaximizeIcon.Kind = max
                ? MaterialDesignThemes.Wpf.PackIconKind.WindowRestore
                : MaterialDesignThemes.Wpf.PackIconKind.WindowMaximize;
        };

        ApplySettings();
        _monitor.ReadingChanged += (_, info) => Dispatcher.Invoke(() => UpdateUI(info));

        // 卡片依次淡入（MD3 Expressive 入场动效）
        Loaded += (_, _) => UiAnimations.StaggerFadeIn(
            BatteryCard, DeviceCard, SettingsCard, MouseCard, ThemeCard);
    }

    private static SolidColorBrush Brush(string hex) =>
        new SolidColorBrush((Color)ColorConverter.ConvertFromString(hex));

    public void ShowWindow()
    {
        Show();
        if (WindowState == WindowState.Minimized) WindowState = WindowState.Normal;
        Activate();
    }

    // ---------- 自绘标题栏 ----------

    private void TitleBar_MouseLeftButtonDown(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2) { ToggleMaximize(); return; }
        try { DragMove(); } catch { }
    }

    private void MinimizeBtn_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void MaximizeBtn_Click(object sender, RoutedEventArgs e) => ToggleMaximize();

    private void ToggleMaximize()
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
        MaximizeIcon.Kind = WindowState == WindowState.Maximized
            ? MaterialDesignThemes.Wpf.PackIconKind.WindowRestore
            : MaterialDesignThemes.Wpf.PackIconKind.WindowMaximize;
    }

    // 关闭按钮只隐藏窗口，后台监控与托盘继续运行；完全退出请用托盘右键「退出」
    private void CloseBtn_Click(object sender, RoutedEventArgs e) => Hide();

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
        for (int i = 0; i < 6; i++) DpiBoxes[i].Text = _settings.DpiStages[i].ToString();
        _activeDpiIndex = _settings.ActiveDpiIndex;
        _selectedRateIndex = RateIndexFromHz(_settings.RateHz);
        _styleComboInit = true;
        PaletteStyleCombo.SelectedIndex = (int)_settings.PaletteStyle;
        _styleComboInit = false;
        HighlightDpiStages();
        HighlightRate();
        UpdateActiveStageText();
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

        // 自选颜色色块
        var gradient = new LinearGradientBrush(
            new GradientStopCollection
            {
                new GradientStop(Color.FromRgb(0xE5, 0x40, 0x5D), 0.0),
                new GradientStop(Color.FromRgb(0xFF, 0xB3, 0x00), 0.33),
                new GradientStop(Color.FromRgb(0x21, 0xB6, 0x6C), 0.66),
                new GradientStop(Color.FromRgb(0x42, 0xA5, 0xF5), 1.0)
            }, new Point(0, 0), new Point(1, 1));
        var custom = new System.Windows.Controls.Button
        {
            Width = 30, Height = 30,
            Margin = new Thickness(0, 0, 8, 8),
            Cursor = System.Windows.Input.Cursors.Hand,
            ToolTip = "自选颜色（RGB / HEX）",
            Content = "+",
            FontSize = 17, FontWeight = FontWeights.SemiBold,
            Foreground = Brushes.White,
            Background = gradient,
            BorderBrush = Res("A7.OutlineVariant"),
            BorderThickness = new Thickness(1),
            Template = MakeSwatchTemplate()
        };
        custom.Click += CustomColor_Click;
        PresetSwatches.Children.Add(custom);

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
        AccentHexText.Text = ThemeService.ToHex(seed) + " · " + _app.ThemeSource;
    }

    private void ApplyThemeAndRefresh(Color seed)
    {
        _app.Theme.Apply(seed);
        RefreshThemeUI();
        RefreshDynamicHighlights();
        _app.UpdateTrayAccent();
    }

    /// <summary>主题色变化后刷新所有依赖动态画刷的高亮状态。</summary>
    private void RefreshDynamicHighlights()
    {
        HighlightInterval(_settings.RefreshSeconds);
        HighlightDpiStages();
        HighlightRate();
    }

    private void ThemeStatic_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button b) UiAnimations.PressBounce(b);
        _settings.ThemeMode = ThemeMode.Static;
        _settings.Save();
        ApplyThemeAndRefresh(ThemeService.ParseHex(_settings.StaticSeedColor));
        UiAnimations.GentlePulse(SwatchPrimary);
    }

    private void ThemeDynamic_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button b) UiAnimations.PressBounce(b);
        _settings.ThemeMode = ThemeMode.Dynamic;
        _settings.Save();
        _app.ApplyTheme();
        AfterThemeApplied();
        UiAnimations.GentlePulse(SwatchPrimary);
    }

    private void PresetColor_Click(object sender, RoutedEventArgs e)
    {
        if (sender is System.Windows.Controls.Button b && b.Tag is string hex)
        {
            UiAnimations.PressBounce(b);
            _settings.StaticSeedColor = hex;
            _settings.ThemeMode = ThemeMode.Static;
            _settings.Save();
            ApplyThemeAndRefresh(ThemeService.ParseHex(hex));
            UiAnimations.GentlePulse(SwatchPrimary);
        }
    }

    private void PaletteStyleCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_styleComboInit || PaletteStyleCombo.SelectedIndex < 0) return;
        _settings.PaletteStyle = (PaletteStyle)PaletteStyleCombo.SelectedIndex;
        _settings.Save();
        _app.ApplyTheme();
        AfterThemeApplied();
    }

    private void CustomColor_Click(object sender, RoutedEventArgs e)
    {
        var picker = new ColorPickerWindow(ThemeService.ParseHex(_settings.StaticSeedColor))
        {
            Owner = this
        };
        if (picker.ShowDialog() == true)
        {
            string hex = ThemeService.ToHex(picker.ResultColor);
            _settings.StaticSeedColor = hex;
            _settings.ThemeMode = ThemeMode.Static;
            _settings.Save();
            ApplyThemeAndRefresh(picker.ResultColor);
        }
    }

    private void RefreshAccent_Click(object sender, RoutedEventArgs e)
    {
        _settings.ThemeMode = ThemeMode.Dynamic;
        _settings.Save();
        _app.ApplyTheme();
        AfterThemeApplied();
    }

    /// <summary>主题已由 App 应用（含取色来源），统一刷新 UI、间隔高亮与托盘。</summary>
    private void AfterThemeApplied()
    {
        RefreshThemeUI();
        RefreshDynamicHighlights();
        _app.UpdateTrayAccent();
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

    private void Refresh_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button b) UiAnimations.PressBounce(b);
        _monitor.Refresh();
    }

    // ---------- 鼠标 DPI / 回报率 ----------

    private void HighlightDpiStages()
    {
        for (int i = 0; i < 6; i++)
        {
            bool active = i == _activeDpiIndex;
            DpiChips[i].Background = active ? Res("A7.PrimaryContainer") : Res("A7.SurfaceContainer");
            DpiChips[i].BorderBrush = active ? Res("A7.Primary") : Brushes.Transparent;
            DpiChips[i].BorderThickness = active ? new Thickness(1.5) : new Thickness(0);
        }
        UpdateActiveStageText();
    }

    private void HighlightRate()
    {
        double val = Math.Clamp(_selectedRateIndex, 0, 5);
        if (Math.Abs(RateSlider.Value - val) > 0.01) RateSlider.Value = val;
        UpdateRateText();
    }

    private void UpdateRateText()
    {
        int idx = Math.Clamp((int)Math.Round(RateSlider.Value), 0, MouseConfigService.RateHz.Length - 1);
        RateValueText.Text = MouseConfigService.RateHz[idx] + " Hz";
    }

    private void RateSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        _selectedRateIndex = Math.Clamp((int)Math.Round(e.NewValue), 0, 5);
        UpdateRateText();
    }

    private void UpdateActiveStageText()
    {
        string val = DpiBoxes[_activeDpiIndex].Text.Trim();
        ActiveStageText.Text = $"档位 {_activeDpiIndex + 1} · {val} DPI";
    }

    private void DpiStage_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button b && int.TryParse(b.Tag?.ToString(), out int idx) && idx >= 0 && idx < 6)
        {
            _activeDpiIndex = idx;
            HighlightDpiStages();
        }
    }

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
                for (int i = 0; i < 6; i++) DpiBoxes[i].Text = cfg.DpiStages[i].ToString();
                if (cfg.ActiveDpiIndex >= 0 && cfg.ActiveDpiIndex < 6) _activeDpiIndex = cfg.ActiveDpiIndex;
                HighlightDpiStages();

                int hz = cfg.UsbRateIndex >= 0 && cfg.UsbRateIndex < MouseConfigService.RateHz.Length
                    ? MouseConfigService.RateHz[cfg.UsbRateIndex]
                    : (cfg.WirelessRateIndex >= 0 && cfg.WirelessRateIndex < MouseConfigService.RateHz.Length
                        ? MouseConfigService.RateHz[cfg.WirelessRateIndex] : -1);
                if (hz > 0) { _selectedRateIndex = RateIndexFromHz(hz); HighlightRate(); }

                MouseStatusText.Text = $"已读取 · 当前档位 {cfg.ActiveDpiIndex + 1} · {MouseConfigService.RateLabel(cfg.UsbRateIndex)}"
                    + (string.IsNullOrEmpty(cfg.Raw) ? "" : "  [" + cfg.Raw + "]");
            });
        });
    }

    private int[] CollectDpi(out string error)
    {
        error = "";
        var values = new int[6];
        for (int i = 0; i < 6; i++)
        {
            if (!int.TryParse(DpiBoxes[i].Text.Trim(), out values[i]) || values[i] < 100 || values[i] > 30000)
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
        int active = _activeDpiIndex;
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
                    UpdateActiveStageText();
                    MouseStatusText.Text = $"DPI 已写入 · 当前档位 {active + 1} = {values[active]}";
                }
                else MouseStatusText.Text = "DPI 写入失败：" + err;
            });
        });
    }

    private void ApplyRate_Click(object sender, RoutedEventArgs e)
    {
        int idx = _selectedRateIndex;
        int hz = idx >= 0 && idx < MouseConfigService.RateHz.Length ? MouseConfigService.RateHz[idx] : 1000;
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
            bool changed = _lastPercent >= 0 && _lastPercent != info.Percent;
            PercentText.Text = info.Percent.ToString();
            _lastPercent = info.Percent;
            if (changed) UiAnimations.GentlePulse(PercentText);
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
