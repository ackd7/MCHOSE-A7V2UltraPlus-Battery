using System;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace A7Battery
{
    /// <summary>
    /// Windows 11 原生窗口圆角（DWMWA_WINDOW_CORNER_ROUNDING）。
    /// 用系统级圆角代替 WPF 的 AllowsTransparency 透明窗口方案——
    /// 透明窗口在 4K 高 DPI 下会被 DWM 位图缩放导致文字发虚，而原生圆角不影响渲染清晰度。
    /// </summary>
    public static class WindowRounding
    {
        private const int DWMWA_WINDOW_CORNER_ROUNDING = 33;
        private const int CornerRound = 2;   // DWM_WINDOW_CORNER_PREFERENCE: 0=默认 1=无 2=圆角 3=小圆角

        [DllImport("dwmapi.dll", PreserveSig = true)]
        private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

        /// <summary>在窗口 ContentRendered / SourceInitialized 后调用，启用系统圆角。</summary>
        public static void Apply(Window window)
        {
            try
            {
                var hwnd = new WindowInteropHelper(window).Handle;
                if (hwnd == IntPtr.Zero) return;
                int pref = CornerRound;
                DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_ROUNDING, ref pref, sizeof(int));
            }
            catch { /* 老版本 Windows 不支持则忽略，退化为直角，不影响功能 */ }
        }
    }
}
