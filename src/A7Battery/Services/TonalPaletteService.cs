using System;
using System.Windows.Media;

namespace A7Battery.Services
{
    /// <summary>
    /// Material You 配色风格（参考 MaterialKolor / material-color-utilities 的 PaletteStyle）。
    /// 决定从种子色派生 secondary / tertiary / neutral 时的色相偏移与彩度缩放。
    /// </summary>
    public enum PaletteStyle
    {
        TonalSpot = 0,   // 默认，柔和协调
        Vibrant = 1,     // 鲜艳
        Expressive = 2,  // 表现力强，色相偏移更大
        Neutral = 3,     // 中性，低彩度
        Monochrome = 4   // 单色，无彩度
    }

    /// <summary>
    /// 基于 Oklab 感知均匀色彩空间的 Tonal Palette。
    ///
    /// Material You 的 tonal palette 本质是「固定色相与彩度、只改变感知明度」的一组色阶（tone 0-100）。
    /// Oklab 是感知均匀色彩空间，生成的色阶比 HSL 均匀得多，效果接近 Google 的 HCT/CAM16 方案，
    /// 但算法简洁、完全 self-contained，无需任何额外依赖。
    /// </summary>
    public sealed class TonalPalette
    {
        private readonly double _hue;      // Oklab 色相角（度）
        private readonly double _chroma;   // Oklab 彩度

        public double Hue => _hue;
        public double Chroma => _chroma;

        public TonalPalette(Color seed)
        {
            var (_, c, h) = Oklab.FromColor(seed);
            _chroma = c;
            _hue = h;
        }

        public TonalPalette(double hue, double chroma)
        {
            _hue = hue;
            _chroma = chroma;
        }

        /// <summary>取指定 tone（0=黑,100=白）的颜色，tone 即 Oklab 的感知明度 L×100。</summary>
        public Color Tone(double tone)
        {
            double l = Math.Clamp(tone, 0, 100) / 100.0;
            // 极高/极低明度处收敛彩度，避免溢色（接近 Material 的行为）
            double c = _chroma * (1.0 - Math.Pow(Math.Abs(l - 0.5) * 2, 3) * 0.55);
            return Oklab.ToColor(l, c, _hue);
        }
    }

    /// <summary>sRGB ↔ Oklab 感知色彩空间转换（Björn Ottosson 的标准算法）。</summary>
    public static class Oklab
    {
        // sRGB(linear) -> LMS
        private const double M1_00 = 0.4122214708, M1_01 = 0.5363325363, M1_02 = 0.0514459929;
        private const double M1_10 = 0.2119034982, M1_11 = 0.6806995451, M1_12 = 0.1073969566;
        private const double M1_20 = 0.0883024619, M1_21 = 0.2817188376, M1_22 = 0.6299787005;
        // LMS' -> Oklab
        private const double M2_00 = 0.2104542553, M2_01 = 0.7936177850, M2_02 = -0.0040720468;
        private const double M2_10 = 1.9779984951, M2_11 = -2.4285922050, M2_12 = 0.4505937099;
        private const double M2_20 = 0.0259040371, M2_21 = 0.7827717662, M2_22 = -0.8086757660;
        // Oklab -> LMS'
        private const double I2_00 = 1.0, I2_01 = 0.3963377774, I2_02 = 0.2158037573;
        private const double I2_10 = 1.0, I2_11 = -0.1055613458, I2_12 = -0.0638541728;
        private const double I2_20 = 1.0, I2_21 = -0.0894841775, I2_22 = -1.2914855480;
        // LMS -> sRGB(linear)
        private const double I1_00 = 4.0767416621, I1_01 = -3.3077115913, I1_02 = 0.2309699292;
        private const double I1_10 = -1.2684380046, I1_11 = 2.6097574011, I1_12 = -0.3413193965;
        private const double I1_20 = -0.0041960863, I1_21 = -0.7034186147, I1_22 = 1.7076147010;

        /// <summary>返回 (L, C, h)，L∈[0,1]，h 为度。</summary>
        public static (double L, double C, double H) FromColor(Color color)
        {
            double r = SrgbToLinear(color.R / 255.0);
            double g = SrgbToLinear(color.G / 255.0);
            double b = SrgbToLinear(color.B / 255.0);

            double l_ = M1_00 * r + M1_01 * g + M1_02 * b;
            double m_ = M1_10 * r + M1_11 * g + M1_12 * b;
            double s_ = M1_20 * r + M1_21 * g + M1_22 * b;
            double lc = Math.Cbrt(l_), mc = Math.Cbrt(m_), sc = Math.Cbrt(s_);

            double L = M2_00 * lc + M2_01 * mc + M2_02 * sc;
            double a = M2_10 * lc + M2_11 * mc + M2_12 * sc;
            double bb = M2_20 * lc + M2_21 * mc + M2_22 * sc;
            double C = Math.Sqrt(a * a + bb * bb);
            double h = Math.Atan2(bb, a) * 180.0 / Math.PI;
            if (h < 0) h += 360;
            return (L, C, h);
        }

        /// <summary>由 (L, C, h) 构造 sRGB 颜色，自动 clamp 到合法色域。</summary>
        public static Color ToColor(double L, double C, double hDeg)
        {
            L = Math.Clamp(L, 0, 1);
            double rad = hDeg * Math.PI / 180.0;
            double a = C * Math.Cos(rad);
            double bb = C * Math.Sin(rad);

            double l_ = I2_00 * L + I2_01 * a + I2_02 * bb;
            double m_ = I2_10 * L + I2_11 * a + I2_12 * bb;
            double s_ = I2_20 * L + I2_21 * a + I2_22 * bb;
            double l3 = l_ * l_ * l_, m3 = m_ * m_ * m_, s3 = s_ * s_ * s_;

            double r = I1_00 * l3 + I1_01 * m3 + I1_02 * s3;
            double g = I1_10 * l3 + I1_11 * m3 + I1_12 * s3;
            double bl = I1_20 * l3 + I1_21 * m3 + I1_22 * s3;

            return Color.FromRgb(
                (byte)Math.Round(Math.Clamp(LinearToSrgb(r), 0, 1) * 255),
                (byte)Math.Round(Math.Clamp(LinearToSrgb(g), 0, 1) * 255),
                (byte)Math.Round(Math.Clamp(LinearToSrgb(bl), 0, 1) * 255));
        }

        private static double SrgbToLinear(double c) =>
            c <= 0.04045 ? c / 12.92 : Math.Pow((c + 0.055) / 1.055, 2.4);

        private static double LinearToSrgb(double c) =>
            c <= 0.0031308 ? c * 12.92 : 1.055 * Math.Pow(c, 1.0 / 2.4) - 0.055;
    }
}
