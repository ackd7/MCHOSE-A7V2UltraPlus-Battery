using System;
using System.Collections.Generic;
using System.Windows;
using System.Windows.Media;
using MaterialDesignColors;
using MaterialDesignThemes.Wpf;
using Microsoft.Win32;

namespace A7Battery.Services
{
    public enum ThemeMode
    {
        Static = 0,   // 静态取色：使用预设的种子色
        Dynamic = 1   // 动态取色：跟随 Windows 系统强调色（Win11 本身从壁纸提取）
    }

    public sealed class ThemePreset
    {
        public string Name { get; init; } = "";
        public string Hex { get; init; } = "";
    }

    /// <summary>
    /// Material You 风格取色服务。
    /// 从一个种子色生成整套 MD3 语义色板（ Primary / Container / Surface / Outline 等），
    /// 写入 Application.Resources 供 XAML 使用，同时通过 PaletteHelper 更新内置控件配色。
    ///
    /// - 静态取色：使用预设种子色（紫/蓝/青/绿/橙/粉…）。
    /// - 动态取色：读取 Windows 系统强调色（DWM ColorizationColor / WindowGlassColor），
    ///   Win10/11 的强调色在开启「自动从壁纸取色」时即来自当前壁纸，等价于 dots-hyprland 的 wallpaper→palette。
    /// </summary>
    public sealed class ThemeService
    {
        public static readonly IReadOnlyList<ThemePreset> Presets = new[]
        {
            new ThemePreset { Name = "紫罗兰", Hex = "#6750A4" },
            new ThemePreset { Name = "海洋蓝", Hex = "#1565C0" },
            new ThemePreset { Name = "青碧", Hex = "#00838F" },
            new ThemePreset { Name = "森林绿", Hex = "#2E7D32" },
            new ThemePreset { Name = "琥珀橙", Hex = "#EF6C00" },
            new ThemePreset { Name = "玫红", Hex = "#C2185B" },
            new ThemePreset { Name = "靛青", Hex = "#3F51B5" },
            new ThemePreset { Name = "石墨", Hex = "#49454F" }
        };

        private readonly PaletteHelper _palette = new();

        /// <summary>当前实际使用的种子色（供 UI 显示）。</summary>
        public Color CurrentSeed { get; private set; } = Color.FromRgb(0x67, 0x50, 0xA4);

        /// <summary>应用一套种子色，生成并写入全部画刷。</summary>
        public void Apply(Color seed)
        {
            CurrentSeed = seed;

            RgbToHsl(seed, out double h, out double s, out _);

            // 主色：把种子色规整到 MD3 常用的中等明度（tone≈40）
            Color primary = Tone(h, s, 40);
            Color secondary = Tone(h, Math.Max(0.18, s * 0.45), 40);
            Color tertiary = Tone(h + 60, Math.Max(0.25, s * 0.7), 40);

            // 1) 更新 MaterialDesignThemes 内置控件画刷
            try
            {
                Theme theme = _palette.GetTheme();
                theme.PrimaryLight = new ColorPair(Tone(h, s, 80));
                theme.PrimaryMid = new ColorPair(primary);
                theme.PrimaryDark = new ColorPair(Tone(h, s, 25));
                theme.SecondaryLight = new ColorPair(Tone(h, Math.Max(0.18, s * 0.45), 80));
                theme.SecondaryMid = new ColorPair(secondary);
                theme.SecondaryDark = new ColorPair(Tone(h, Math.Max(0.18, s * 0.45), 25));
                _palette.SetTheme(theme);
            }
            catch { /* 换肤失败不应影响主程序 */ }

            // 2) 写入自定义 MD3 语义画刷（供 MainWindow.xaml 使用）
            var r = Application.Current.Resources;
            Set(r, "A7.Primary", primary);
            Set(r, "A7.OnPrimary", Tone(h, s, 100));
            Set(r, "A7.PrimaryContainer", Tone(h, s, 90));
            Set(r, "A7.OnPrimaryContainer", Tone(h, s, 10));

            Set(r, "A7.Secondary", secondary);
            Set(r, "A7.OnSecondary", Tone(h, Math.Max(0.18, s * 0.45), 100));
            Set(r, "A7.SecondaryContainer", Tone(h, Math.Max(0.18, s * 0.45), 90));
            Set(r, "A7.OnSecondaryContainer", Tone(h, Math.Max(0.18, s * 0.45), 10));

            Set(r, "A7.Tertiary", tertiary);
            Set(r, "A7.OnTertiary", Tone(h + 60, Math.Max(0.25, s * 0.7), 100));

            // 表面与背景：低彩度的中性色（MD3 的 surface 带一点主色相）
            Set(r, "A7.Background", Tone(h, 0.04, 98));
            Set(r, "A7.OnBackground", Tone(h, 0.04, 10));
            Set(r, "A7.Surface", Tone(h, 0.04, 98));
            Set(r, "A7.OnSurface", Tone(h, 0.04, 10));
            Set(r, "A7.SurfaceVariant", Tone(h, Math.Max(0.08, s * 0.18), 90));
            Set(r, "A7.OnSurfaceVariant", Tone(h, 0.10, 40));
            Set(r, "A7.SurfaceContainer", Tone(h, 0.05, 94));
            Set(r, "A7.SurfaceContainerLow", Tone(h, 0.04, 96));
            Set(r, "A7.SurfaceContainerHigh", Tone(h, 0.05, 92));
            Set(r, "A7.Divider", Tone(h, 0.06, 80));
            Set(r, "A7.Outline", Tone(h, 0.08, 50));
            Set(r, "A7.OutlineVariant", Tone(h, 0.06, 80));
        }

        /// <summary>
        /// 动态取色：优先从当前壁纸提取主色（Material You 的 wallpaper→palette），
        /// 失败则回退 Windows 系统强调色。返回实际种子色与来源描述。
        /// </summary>
        public static (Color Color, string Source) ReadDynamicSeed()
        {
            try
            {
                var wp = new WallpaperColorService().Extract();
                if (wp != null)
                    return (wp.Color, "壁纸 · " + (string.IsNullOrEmpty(wp.FileName) ? "当前桌面" : wp.FileName));
            }
            catch { }
            return (ReadSystemAccent(), "系统强调色");
        }

        /// <summary>读取 Windows 系统强调色作为动态种子色（回退路径）。</summary>
        public static Color ReadSystemAccent()
        {
            // 优先：WPF 暴露的 DWM 玻璃色（即强调色）
            try
            {
                var c = SystemParameters.WindowGlassColor;
                if (c.A > 0 && !(c.R == 0 && c.G == 0 && c.B == 0))
                    return Color.FromRgb(c.R, c.G, c.B);
            }
            catch { }

            // 回退：注册表 DWM\AccentColor（ABGR 或 ARGB）
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\DWM");
                object? val = key?.GetValue("AccentColor") ?? key?.GetValue("ColorizationColor");
                if (val is int v)
                {
                    int a = (v >> 24) & 0xFF, bb = (v >> 16) & 0xFF, g = (v >> 8) & 0xFF, r = v & 0xFF;
                    if (a != 0) return Color.FromRgb((byte)r, (byte)g, (byte)bb);
                }
            }
            catch { }

            return Color.FromRgb(0x67, 0x50, 0xA4);
        }

        public static Color ParseHex(string? hex)
        {
            try
            {
                if (string.IsNullOrWhiteSpace(hex)) return Color.FromRgb(0x67, 0x50, 0xA4);
                string s = hex.Trim().TrimStart('#');
                if (s.Length == 6) return Color.FromRgb(
                    Convert.ToByte(s.Substring(0, 2), 16),
                    Convert.ToByte(s.Substring(2, 2), 16),
                    Convert.ToByte(s.Substring(4, 2), 16));
            }
            catch { }
            return Color.FromRgb(0x67, 0x50, 0xA4);
        }

        public static string ToHex(Color c) => $"#{c.R:X2}{c.G:X2}{c.B:X2}";

        private static void Set(ResourceDictionary dict, string key, Color color)
        {
            dict[key] = new SolidColorBrush(color);
        }

        /// <summary>
        /// 按 HSL 生成一个「tone」色阶（0=黑, 100=白）。
        /// 明度随 tone 线性变化，彩度在两端收敛——近似 Material 的 tonal palette，
        /// 保证任意种子色都能得到协调的一套色阶，且完全 self-contained。
        /// </summary>
        private static Color Tone(double hue, double saturation, double tone)
        {
            hue = ((hue % 360) + 360) % 360;
            saturation = Math.Clamp(saturation, 0.0, 1.0);
            double t = Math.Clamp(tone, 0.0, 100.0) / 100.0;

            // 饱和度在极暗/极亮处收敛，中间最强
            double sat = saturation * (1.0 - Math.Abs(t - 0.4) * 0.75);
            sat = Math.Clamp(sat, 0.0, 1.0);

            return HslToRgb(hue, sat, t);
        }

        private static void RgbToHsl(Color c, out double h, out double s, out double l)
        {
            double r = c.R / 255.0, g = c.G / 255.0, b = c.B / 255.0;
            double max = Math.Max(r, Math.Max(g, b));
            double min = Math.Min(r, Math.Min(g, b));
            double d = max - min;
            l = (max + min) / 2.0;
            if (d < 1e-6) { h = 0; s = 0; return; }
            s = d / (1 - Math.Abs(2 * l - 1));
            if (max == r) h = 60 * (((g - b) / d) % 6);
            else if (max == g) h = 60 * ((b - r) / d + 2);
            else h = 60 * ((r - g) / d + 4);
            if (h < 0) h += 360;
        }

        private static Color HslToRgb(double h, double s, double l)
        {
            double c = (1 - Math.Abs(2 * l - 1)) * s;
            double x = c * (1 - Math.Abs((h / 60.0) % 2 - 1));
            double m = l - c / 2;
            double r, g, b;
            int sector = (int)(h / 60) % 6;
            switch (sector)
            {
                case 0: r = c; g = x; b = 0; break;
                case 1: r = x; g = c; b = 0; break;
                case 2: r = 0; g = c; b = x; break;
                case 3: r = 0; g = x; b = c; break;
                case 4: r = x; g = 0; b = c; break;
                default: r = c; g = 0; b = x; break;
            }
            return Color.FromRgb(
                (byte)Math.Round((r + m) * 255),
                (byte)Math.Round((g + m) * 255),
                (byte)Math.Round((b + m) * 255));
        }
    }
}
