using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using Microsoft.Win32;

namespace A7Battery.Services
{
    /// <summary>
    /// 从当前桌面壁纸提取代表性主色（Material You 的 wallpaper → seed 步骤）。
    ///
    /// 流程：
    ///   1. 定位壁纸文件：注册表 HKCU\Control Panel\Desktop\Wallpaper，回退 Themes\TranscodedWallpaper。
    ///   2. 缩略采样，跳过接近黑/白/灰的像素。
    ///   3. 按色相分桶统计，权重 = 饱和度 × 明度适中度 × 出现频率，选出最有代表性的色相。
    ///   4. 在该色相的样本里取平均色并适度提饱和，得到最终种子色。
    ///
    /// Wallpaper Engine 等动态壁纸会把当前帧写到 Override 文件，因此同样适用。
    /// </summary>
    public sealed class WallpaperColorService
    {
        public sealed class WallpaperSeed
        {
            public System.Windows.Media.Color Color;
            public string Source = "";
            public string FileName = "";
        }

        /// <summary>读取当前壁纸并提取主色；失败返回 null。</summary>
        public WallpaperSeed? Extract()
        {
            string? path = FindWallpaperPath();
            if (string.IsNullOrEmpty(path) || !File.Exists(path)) return null;

            var swatch = ExtractFromFile(path!);
            if (swatch == null) return null;
            return new WallpaperSeed
            {
                Color = swatch.Value,
                Source = path!,
                FileName = Path.GetFileName(path)
            };
        }

        private static string? FindWallpaperPath()
        {
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(@"Control Panel\Desktop");
                var p = key?.GetValue("Wallpaper") as string;
                if (!string.IsNullOrWhiteSpace(p) && File.Exists(p)) return p;
            }
            catch { }

            try
            {
                string transcoded = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                    @"Microsoft\Windows\Themes\TranscodedWallpaper");
                if (File.Exists(transcoded)) return transcoded;
            }
            catch { }
            return null;
        }

        private static System.Windows.Media.Color? ExtractFromFile(string path)
        {
            Bitmap? bmp = null;
            try
            {
                // 以只读方式打开，避免占用导致 Wallpaper Engine 无法更新
                using var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                using var img = Image.FromStream(fs);
                int w = Math.Min(img.Width, 160);
                int h = Math.Min(img.Height, 100);
                if (w < 2 || h < 2) return null;
                bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb);
                using (var gr = Graphics.FromImage(bmp))
                {
                    gr.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
                    gr.DrawImage(img, 0, 0, w, h);
                }

                // 色相分桶（24 桶，每桶 15°），累计权重与 RGB
                var buckets = new Bucket[24];
                for (int y = 0; y < h; y += 1)
                {
                    for (int x = 0; x < w; x += 1)
                    {
                        var c = bmp.GetPixel(x, y);
                        double hue = c.GetHue();
                        double sat = c.GetSaturation();
                        double bri = c.GetBrightness();

                        // 过滤近黑白灰：无色相或明度极端的像素
                        if (sat < 0.12 || bri < 0.10 || bri > 0.93) continue;

                        int bucket = (int)(hue / 15.0) % 24;
                        // 明度适中（0.35~0.7）权重更高，饱和度高权重更高
                        double mid = 1.0 - Math.Abs(bri - 0.52) * 1.4;
                        if (mid < 0.05) mid = 0.05;
                        double weight = (0.25 + sat) * mid;

                        var b = buckets[bucket] ??= new Bucket();
                        b.Weight += weight;
                        b.R += c.R * weight;
                        b.G += c.G * weight;
                        b.B += c.B * weight;
                        b.SatSum += sat * weight;
                    }
                }

                Bucket? best = null;
                foreach (var b in buckets)
                    if (b != null && (best == null || b.Weight > best.Weight)) best = b;
                if (best == null || best.Weight < 1e-6) return null;

                byte r = (byte)Math.Clamp(best.R / best.Weight, 0, 255);
                byte g = (byte)Math.Clamp(best.G / best.Weight, 0, 255);
                byte bl = (byte)Math.Clamp(best.B / best.Weight, 0, 255);

                // 适度提高饱和度，让种子色更鲜明（向该色相的纯色方向拉伸）
                double avgSat = best.SatSum / best.Weight;
                double boost = Math.Clamp(0.35 + avgSat * 0.6, 0.35, 0.85);
                var boosted = AdjustSaturation(System.Windows.Media.Color.FromRgb(r, g, bl), boost);
                return boosted;
            }
            catch
            {
                return null;
            }
            finally
            {
                bmp?.Dispose();
            }
        }

        private static System.Windows.Media.Color AdjustSaturation(System.Windows.Media.Color c, double target)
        {
            RgbToHsl(c, out double h, out _, out double l);
            return HslToRgb(h, target, Math.Clamp(l, 0.25, 0.75));
        }

        private static void RgbToHsl(System.Windows.Media.Color c, out double h, out double s, out double l)
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

        private static System.Windows.Media.Color HslToRgb(double h, double s, double l)
        {
            h = ((h % 360) + 360) % 360;
            double ch = (1 - Math.Abs(2 * l - 1)) * s;
            double x = ch * (1 - Math.Abs((h / 60.0) % 2 - 1));
            double m = l - ch / 2;
            double r, g, b;
            switch ((int)(h / 60) % 6)
            {
                case 0: r = ch; g = x; b = 0; break;
                case 1: r = x; g = ch; b = 0; break;
                case 2: r = 0; g = ch; b = x; break;
                case 3: r = 0; g = x; b = ch; break;
                case 4: r = x; g = 0; b = ch; break;
                default: r = ch; g = 0; b = x; break;
            }
            return System.Windows.Media.Color.FromRgb(
                (byte)Math.Round((r + m) * 255),
                (byte)Math.Round((g + m) * 255),
                (byte)Math.Round((b + m) * 255));
        }

        private sealed class Bucket
        {
            public double Weight;
            public double R;
            public double G;
            public double B;
            public double SatSum;
        }
    }
}
