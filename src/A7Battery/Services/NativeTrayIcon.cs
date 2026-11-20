using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;
using System.Threading;

namespace A7Battery.Services
{
    /// <summary>
    /// 原生 Win32 托盘图标（Shell_NotifyIcon），不依赖第三方控件。
    /// 支持动态绘制电量百分比，左键回调、右键菜单回调。
    /// </summary>
    public sealed class NativeTrayIcon : IDisposable
    {
        private const uint WM_APP = 0x8000;
        private const int WM_LBUTTONUP = 0x0202;
        private const int WM_RBUTTONUP = 0x0205;
        private const int WM_DESTROY = 0x0002;
        private const uint NIM_ADD = 0x00000000;
        private const uint NIM_MODIFY = 0x00000001;
        private const uint NIM_DELETE = 0x00000002;
        private const uint NIF_MESSAGE = 0x00000001;
        private const uint NIF_ICON = 0x00000002;
        private const uint NIF_TIP = 0x00000004;
        private const uint TPM_LEFTALIGN = 0x0000;
        private const uint TPM_RETURNCMD = 0x0100;

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct NOTIFYICONDATA
        {
            public uint cbSize;
            public IntPtr hWnd;
            public uint uID;
            public uint uFlags;
            public uint uCallbackMessage;
            public IntPtr hIcon;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
            public string szTip;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr GetModuleHandle(string moduleName);

        [DllImport("shell32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool Shell_NotifyIcon(uint dwMessage, ref NOTIFYICONDATA lpData);

        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr CreateWindowEx(uint exStyle, string className, string windowName, uint style,
            int x, int y, int w, int h, IntPtr parent, IntPtr menu, IntPtr instance, IntPtr param);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern IntPtr DefWindowProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool DestroyWindow(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern IntPtr CreateIconFromResourceEx(byte[] bits, uint size, bool isIcon, uint version, int cx, int cy, uint flags);

        [DllImport("user32.dll")]
        private static extern bool DestroyIcon(IntPtr hIcon);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern IntPtr CreatePopupMenu();

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        private static extern bool AppendMenu(IntPtr hMenu, uint flags, uint id, string text);

        [DllImport("user32.dll")]
        private static extern uint TrackPopupMenu(IntPtr hMenu, uint flags, int x, int y, int reserved, IntPtr hwnd, IntPtr rect);

        [DllImport("user32.dll")]
        private static extern bool DestroyMenu(IntPtr hMenu);

        [DllImport("user32.dll")]
        private static extern bool GetCursorPos(out POINT pt);

        [DllImport("user32.dll")]
        private static extern bool SetForegroundWindow(IntPtr hWnd);

        [DllImport("user32.dll")]
        private static extern short LoWord(IntPtr lParam);

        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern ushort RegisterClass(ref WNDCLASS wc);

        [StructLayout(LayoutKind.Sequential)]
        private struct POINT { public int X; public int Y; }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct WNDCLASS
        {
            public uint style;
            public IntPtr lpfnWndProc;
            public int cbClsExtra;
            public int cbWndExtra;
            public IntPtr hInstance;
            public IntPtr hIcon;
            public IntPtr hCursor;
            public IntPtr hbrBackground;
            public string lpszMenuName;
            public string lpszClassName;
        }

        private delegate IntPtr WndProcDelegate(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

        private readonly IntPtr _hwnd;
        private readonly WndProcDelegate _wndProc; // keep alive
        private NOTIFYICONDATA _nid;
        private IntPtr _currentIcon = IntPtr.Zero;
        private bool _disposed;

        public event Action OnOpen;
        public event Action OnRefresh;
        public event Action OnToggleAutoStart;
        public event Action OnExit;
        public event Action OnTrayClick;

        public NativeTrayIcon()
        {
            _wndProc = WndProc;
            var wc = new WNDCLASS
            {
                lpfnWndProc = Marshal.GetFunctionPointerForDelegate(_wndProc),
                lpszClassName = "A7BatteryTrayClass",
                hInstance = GetModuleHandle(null)
            };
            RegisterClass(ref wc);

            _hwnd = CreateWindowEx(0, "A7BatteryTrayClass", "A7Tray", 0, 0, 0, 0, 0,
                IntPtr.Zero, IntPtr.Zero, GetModuleHandle(null), IntPtr.Zero);

            _nid = new NOTIFYICONDATA
            {
                cbSize = (uint)Marshal.SizeOf(typeof(NOTIFYICONDATA)),
                hWnd = _hwnd,
                uID = 1,
                uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP,
                uCallbackMessage = WM_APP + 1,
                szTip = "A7 电量助手"
            };
        }

        private IntPtr WndProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam)
        {
            if (msg == WM_APP + 1)
            {
                switch ((uint)lParam)
                {
                    case WM_LBUTTONUP:
                        OnOpen?.Invoke();
                        OnTrayClick?.Invoke();
                        break;
                    case WM_RBUTTONUP:
                        ShowMenu();
                        break;
                }
                return IntPtr.Zero;
            }
            if (msg == WM_DESTROY) return IntPtr.Zero;
            return DefWindowProc(hWnd, msg, wParam, lParam);
        }

        private void ShowMenu()
        {
            var menu = CreatePopupMenu();
            AppendMenu(menu, 0, 1, "打开面板");
            AppendMenu(menu, 0, 2, "立即刷新");
            AppendMenu(menu, 0x0008 /* MF_DISABLED */, 3, "---");
            AppendMenu(menu, 0, 4, "退出");
            GetCursorPos(out POINT pt);
            SetForegroundWindow(_hwnd);
            uint cmd = TrackPopupMenu(menu, TPM_LEFTALIGN | TPM_RETURNCMD, pt.X, pt.Y, 0, _hwnd, IntPtr.Zero);
            DestroyMenu(menu);
            switch (cmd)
            {
                case 1: OnOpen?.Invoke(); break;
                case 2: OnRefresh?.Invoke(); break;
                case 4: OnExit?.Invoke(); break;
            }
        }

        public void SetIcon(int? percent, bool charging, string tip)
        {
            var icon = BuildIcon(percent, charging);
            _nid.hIcon = icon;
            _nid.szTip = tip ?? "A7 电量助手";
            bool ok = (_nid.uFlags & NIF_TIP) == 0
                ? Shell_NotifyIcon(_currentIcon == IntPtr.Zero ? NIM_ADD : NIM_MODIFY, ref _nid)
                : false;
            if (_currentIcon == IntPtr.Zero)
                Shell_NotifyIcon(NIM_ADD, ref _nid);
            else
                Shell_NotifyIcon(NIM_MODIFY, ref _nid);

            if (_currentIcon != IntPtr.Zero) DestroyIcon(_currentIcon);
            _currentIcon = icon;
        }

        private static IntPtr BuildIcon(int? percent, bool charging)
        {
            using var bmp = new Bitmap(32, 32);
            using (var g = Graphics.FromImage(bmp))
            {
                g.Clear(Color.Transparent);
                g.SmoothingMode = SmoothingMode.AntiAlias;
                Color color = percent.HasValue
                    ? (percent.Value >= 50 ? Color.FromArgb(76, 194, 255) : percent.Value >= 20 ? Color.FromArgb(255, 200, 61) : Color.FromArgb(255, 92, 92))
                    : Color.FromArgb(120, 140, 160);
                using var brush = new SolidBrush(color);
                if (percent.HasValue)
                {
                    // 绘制圆角背景 + 百分比文字
                    g.FillRoundedRectangle(brush, new Rectangle(1, 5, 30, 22), 5);
                    using var font = new Font("Segoe UI", 13f, FontStyle.Bold, GraphicsUnit.Point);
                    using var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
                    g.DrawString(percent.Value.ToString(), font, Brushes.White, new RectangleF(1, 5, 30, 22), sf);
                    if (charging)
                    {
                        using var bolt = new Font("Segoe UI Symbol", 9f, FontStyle.Bold);
                        g.DrawString("⚡", bolt, Brushes.Yellow, 22, 6);
                    }
                }
                else
                {
                    g.FillRoundedRectangle(brush, new Rectangle(1, 5, 30, 22), 5);
                    using var font = new Font("Segoe UI", 10f, FontStyle.Bold);
                    using var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
                    g.DrawString("A7", font, Brushes.White, new RectangleF(1, 5, 30, 22), sf);
                }
            }
            return bmp.GetHicon();
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
            Shell_NotifyIcon(NIM_DELETE, ref _nid);
            if (_currentIcon != IntPtr.Zero) DestroyIcon(_currentIcon);
            DestroyWindow(_hwnd);
        }
    }

    internal static class GraphicsExtensions
    {
        public static void FillRoundedRectangle(this Graphics g, Brush brush, Rectangle rect, int radius)
        {
            using var path = new System.Drawing.Drawing2D.GraphicsPath();
            int r = Math.Min(radius, Math.Min(rect.Width / 2, rect.Height / 2));
            path.AddArc(rect.X, rect.Y, r * 2, r * 2, 180, 90);
            path.AddArc(rect.Right - r * 2, rect.Y, r * 2, r * 2, 270, 90);
            path.AddArc(rect.Right - r * 2, rect.Bottom - r * 2, r * 2, r * 2, 0, 90);
            path.AddArc(rect.X, rect.Bottom - r * 2, r * 2, r * 2, 90, 90);
            path.CloseFigure();
            g.FillPath(brush, path);
        }
    }
}
