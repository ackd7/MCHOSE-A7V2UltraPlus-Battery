using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;

namespace A7Battery;

public sealed partial class ColorPickerWindow : Window
{
    private bool _suppress;

    public Color ResultColor { get; private set; } = Color.FromRgb(0x67, 0x50, 0xA4);

    public ColorPickerWindow(Color initial)
    {
        InitializeComponent();
        ResultColor = initial;
        _suppress = true;
        RSlider.Value = initial.R;
        GSlider.Value = initial.G;
        BSlider.Value = initial.B;
        HexBox.Text = Services.ThemeService.ToHex(initial);
        _suppress = false;
        UpdatePreview();
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        try { DragMove(); } catch { }
    }

    private void Rgb_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_suppress) return;
        _suppress = true;
        ResultColor = Color.FromRgb((byte)RSlider.Value, (byte)GSlider.Value, (byte)BSlider.Value);
        RValue.Text = ((byte)RSlider.Value).ToString();
        GValue.Text = ((byte)GSlider.Value).ToString();
        BValue.Text = ((byte)BSlider.Value).ToString();
        HexBox.Text = Services.ThemeService.ToHex(ResultColor);
        _suppress = false;
        UpdatePreview();
    }

    private void HexBox_TextChanged(object sender, TextChangedEventArgs e)
    {
        if (_suppress) return;
        var c = Services.ThemeService.ParseHex(HexBox.Text);
        _suppress = true;
        RSlider.Value = c.R;
        GSlider.Value = c.G;
        BSlider.Value = c.B;
        RValue.Text = c.R.ToString();
        GValue.Text = c.G.ToString();
        BValue.Text = c.B.ToString();
        ResultColor = c;
        _suppress = false;
        UpdatePreview();
    }

    private void UpdatePreview()
    {
        Preview.Background = new SolidColorBrush(ResultColor);
        // 根据明度选择预览文字颜色，保证可读
        double luma = (0.299 * ResultColor.R + 0.587 * ResultColor.G + 0.114 * ResultColor.B) / 255.0;
        PreviewHex.Foreground = new SolidColorBrush(luma > 0.55 ? Colors.Black : Colors.White);
        PreviewHex.Text = Services.ThemeService.ToHex(ResultColor);
    }

    private void OkBtn_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = true;
        Close();
    }

    private void CancelBtn_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
        Close();
    }
}
