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
        public PaletteStyle CurrentStyle { get; private set; } = PaletteStyle.TonalSpot;

        /// <summary>应用一套种子色，生成并写入全部画刷。</summary>
        public void Apply(Color seed, PaletteStyle style = PaletteStyle.TonalSpot)
        {
            CurrentSeed = seed;
            CurrentStyle = style;

            var (_, seedChroma, seedHue) = Oklab.FromColor(seed);
            // 保证种子彩度不至于过低（灰种子也能生成有区分度的色板）
            double baseC = Math.Max(seedChroma, 0.045);

            // 参考 MaterialKolor / material-color-utilities 的各 PaletteStyle 派生规则
            double secHueShift, secCScale, terHueShift, terCScale;
            switch (style)
            {
                case PaletteStyle.Vibrant:
                    secHueShift = 0; secCScale = 0.65; terHueShift = 240; terCScale = 0.55; break;
                case PaletteStyle.Expressive:
                    secHueShift = 120; secCScale = 0.45; terHueShift = 240; terCScale = 0.45; break;
                case PaletteStyle.Neutral:
                    secHueShift = 0; secCScale = 0.18; terHueShift = 0; terCScale = 0.28; break;
                case PaletteStyle.Monochrome:
                    secHueShift = 0; secCScale = 0.0; terHueShift = 0; terCScale = 0.0; break;
                default: // TonalSpot
                    secHueShift = 0; secCScale = 0.40; terHueShift = 60; terCScale = 0.67; break;
            }

            var primary = new TonalPalette(seedHue, baseC);
            var secondary = new TonalPalette(seedHue + secHueShift, baseC * secCScale);
            var tertiary = new TonalPalette(seedHue + terHueShift, baseC * terCScale);
            var neutral = new TonalPalette(seedHue, baseC * 0.08);
            var neutralVariant = new TonalPalette(seedHue, baseC * 0.16);
            var error = new TonalPalette(25, 0.13);

            // 1) 更新 MaterialDesignThemes 内置控件画刷
            try
            {
                Theme theme = _palette.GetTheme();
                theme.PrimaryLight = new ColorPair(primary.Tone(80));
                theme.PrimaryMid = new ColorPair(primary.Tone(40));
                theme.PrimaryDark = new ColorPair(primary.Tone(25));
                theme.SecondaryLight = new ColorPair(secondary.Tone(80));
                theme.SecondaryMid = new ColorPair(secondary.Tone(40));
                theme.SecondaryDark = new ColorPair(secondary.Tone(25));
                _palette.SetTheme(theme);
            }
            catch { /* 换肤失败不应影响主程序 */ }

            // 2) 写入自定义 MD3 语义画刷
            var r = Application.Current.Resources;
            // 主色系跟随种子色（保持取色功能），但规整到 miuix 的明度层级
            Set(r, "A7.Primary", primary.Tone(40));
            Set(r, "A7.OnPrimary", primary.Tone(100));
            Set(r, "A7.PrimaryContainer", primary.Tone(90));
            Set(r, "A7.OnPrimaryContainer", primary.Tone(10));

            // miuix 骨架：次要与表面固定为其中性色阶，不随种子色漂移——这是 miuix 视觉的统一感来源
            Set(r, "A7.Secondary", Color.FromRgb(0xE6, 0xE6, 0xE6));
            Set(r, "A7.OnSecondary", Color.FromRgb(0x30, 0x30, 0x30));
            Set(r, "A7.SecondaryContainer", Color.FromRgb(0xF0, 0xF0, 0xF0));
            Set(r, "A7.OnSecondaryContainer", Color.FromRgb(0xA9, 0xA9, 0xA9));
            Set(r, "A7.Tertiary", Color.FromRgb(0xEA, 0xF2, 0xFF));
            Set(r, "A7.OnTertiary", primary.Tone(40));
            Set(r, "A7.TertiaryContainer", Color.FromRgb(0xEA, 0xF2, 0xFF));

            Set(r, "A7.Background", Colors.White);
            Set(r, "A7.OnBackground", Colors.Black);
            Set(r, "A7.Surface", Color.FromRgb(0xF7, 0xF7, 0xF7));
            Set(r, "A7.OnSurface", Colors.Black);
            Set(r, "A7.SurfaceVariant", Colors.White);
            Set(r, "A7.OnSurfaceVariant", Color.FromRgb(0x8C, 0x93, 0xB0)); // miuix 标志性蓝调灰
            Set(r, "A7.SurfaceContainer", Colors.White);
            Set(r, "A7.SurfaceContainerLow", Color.FromRgb(0xF7, 0xF7, 0xF7));
            Set(r, "A7.SurfaceContainerHigh", Color.FromRgb(0xE8, 0xE8, 0xE8));
            Set(r, "A7.Divider", Color.FromRgb(0xE0, 0xE0, 0xE0));
            Set(r, "A7.Outline", Color.FromRgb(0xD9, 0xD9, 0xD9));
            Set(r, "A7.OutlineVariant", Color.FromRgb(0xE8, 0xE8, 0xE8));
            Set(r, "A7.Error", Color.FromRgb(0xE9, 0x46, 0x34));
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

        /// <summary>写入画刷。每次替换为新实例，避免冻结画刷无法修改的问题；主题的柔和不强调由 UI 层动画负责。</summary>
        private static void Set(ResourceDictionary dict, string key, Color color)
        {
            dict[key] = new SolidColorBrush(color);
        }
    }
}
