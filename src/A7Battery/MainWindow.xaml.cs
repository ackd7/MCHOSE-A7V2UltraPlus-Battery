using System;
using System.Windows;
using System.Windows.Media;
using A7Battery.Services;

namespace A7Battery;

public sealed partial class MainWindow : Window
{
    private readonly AppSettings _settings;
    private readonly BatteryMonitor _monitor;
    private bool _suppressToggle;

    public MainWindow(AppSettings settings, BatteryMonitor monitor)
    {
        _settings = settings;
        _monitor = monitor;
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
        _suppressToggle = false;
        _monitor.SetInterval(_settings.RefreshSeconds);
        HighlightInterval(_settings.RefreshSeconds);
    }

    private void HighlightInterval(int sec)
    {
        foreach (var b in new[] { Btn15, Btn30, Btn90 })
        {
            bool active = int.TryParse(b.Tag?.ToString(), out int v) && v == sec;
            b.Background = active ? Brush("#4CC2FF") : Brush("#F3F5F8");
            b.Foreground = active ? Brushes.White : Brush("#5A6678");
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

    private void Refresh_Click(object sender, RoutedEventArgs e) => _monitor.Refresh();

    private void UpdateUI(BatteryInfo info)
    {
        if (info == null) return;
        if (info.Success && info.Percent >= 0)
        {
            var color = info.Percent >= 50 ? "#4CC2FF" : info.Percent >= 20 ? "#FFC83D" : "#FF5C5C";
            var brush = Brush(color);
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
