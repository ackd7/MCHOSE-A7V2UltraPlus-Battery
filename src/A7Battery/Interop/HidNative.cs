using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

namespace A7Battery.Interop
{
    public sealed class HidDeviceInfo
    {
        public ushort VendorId;
        public ushort ProductId;
        public ushort UsagePage;
        public ushort Usage;
        public int InputReportLength;
        public int OutputReportLength;
        public int FeatureReportLength;
        public string Path = "";
        public string ProductName = "";
        public string Manufacturer = "";

        public override string ToString() => $"{VendorId:X4}:{ProductId:X4} UP={UsagePage:X4} FL={FeatureReportLength} {ProductName}";
    }

    public static class HidNative
    {
        [StructLayout(LayoutKind.Sequential)]
        public struct SP_DEVICE_INTERFACE_DATA
        {
            public int cbSize;
            public Guid InterfaceClassGuid;
            public int Flags;
            public IntPtr Reserved;
        }

        [StructLayout(LayoutKind.Sequential)]
        public struct HIDD_ATTRIBUTES
        {
            public int Size;
            public ushort VendorID;
            public ushort ProductID;
            public ushort VersionNumber;
        }

        [StructLayout(LayoutKind.Sequential)]
        public struct HIDP_CAPS
        {
            public ushort Usage;
            public ushort UsagePage;
            public ushort InputReportByteLength;
            public ushort OutputReportByteLength;
            public ushort FeatureReportByteLength;
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 17)]
            public ushort[] Reserved;
            public ushort NumberLinkCollectionNodes;
            public ushort NumberInputButtonCaps;
            public ushort NumberInputValueCaps;
            public ushort NumberInputDataIndices;
            public ushort NumberOutputButtonCaps;
            public ushort NumberOutputValueCaps;
            public ushort NumberOutputDataIndices;
            public ushort NumberFeatureButtonCaps;
            public ushort NumberFeatureValueCaps;
            public ushort NumberFeatureDataIndices;
        }

        [StructLayout(LayoutKind.Sequential)]
        public struct OVERLAPPED
        {
            public IntPtr Internal;
            public IntPtr InternalHigh;
            public uint Offset;
            public uint OffsetHigh;
            public IntPtr hEvent;
        }

        public const uint GENERIC_READ = 0x80000000;
        public const uint GENERIC_WRITE = 0x40000000;
        public const uint FILE_SHARE_RW = 0x00000003;
        public const uint OPEN_EXISTING = 3;
        public const uint FILE_FLAG_OVERLAPPED = 0x40000000;
        public const int ERROR_IO_PENDING = 997;
        public const int ERROR_ACCESS_DENIED = 5;
        public const int ERROR_SHARING_VIOLATION = 32;

        [DllImport("hid.dll", SetLastError = true)]
        public static extern bool HidD_GetAttributes(IntPtr h, ref HIDD_ATTRIBUTES a);

        [DllImport("hid.dll", SetLastError = true)]
        public static extern bool HidD_GetPreparsedData(IntPtr h, out IntPtr pp);

        [DllImport("hid.dll", SetLastError = true)]
        public static extern bool HidD_FreePreparsedData(IntPtr p);

        [DllImport("hid.dll", SetLastError = true)]
        public static extern int HidP_GetCaps(IntPtr pp, ref HIDP_CAPS caps);

        [DllImport("hid.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern bool HidD_GetProductString(IntPtr h, StringBuilder sb, int len);

        [DllImport("hid.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern bool HidD_GetManufacturerString(IntPtr h, StringBuilder sb, int len);

        [DllImport("hid.dll", SetLastError = true)]
        public static extern bool HidD_SetOutputReport(IntPtr h, byte[] buf, int len);

        [DllImport("hid.dll", SetLastError = true)]
        public static extern bool HidD_GetFeature(IntPtr h, byte[] buf, int len);

        [DllImport("hid.dll", SetLastError = true)]
        public static extern bool HidD_SetFeature(IntPtr h, byte[] buf, int len);

        [DllImport("setupapi.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr SetupDiGetClassDevs(ref Guid guid, IntPtr enumerator, IntPtr hwndParent, int flags);

        [DllImport("setupapi.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern bool SetupDiEnumDeviceInterfaces(IntPtr h, IntPtr devInfo, ref Guid guid, int index, ref SP_DEVICE_INTERFACE_DATA data);

        [DllImport("setupapi.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern bool SetupDiGetDeviceInterfaceDetail(IntPtr h, ref SP_DEVICE_INTERFACE_DATA data, IntPtr detail, int size, out int required, IntPtr devInfo);

        [DllImport("setupapi.dll", SetLastError = true)]
        public static extern bool SetupDiDestroyDeviceInfoList(IntPtr h);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr CreateFile(string name, uint access, uint share, IntPtr sec, uint disp, uint flags, IntPtr templ);

        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool CloseHandle(IntPtr h);

        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool WriteFile(IntPtr h, byte[] buf, int n, out int written, ref OVERLAPPED ov);

        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool ReadFile(IntPtr h, byte[] buf, int n, out int read, ref OVERLAPPED ov);

        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool GetOverlappedResult(IntPtr h, ref OVERLAPPED ov, out int transferred, bool wait);

        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool CancelIoEx(IntPtr h, ref OVERLAPPED ov);

        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern IntPtr CreateEvent(IntPtr sec, bool manual, bool initial, IntPtr name);

        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern bool ResetEvent(IntPtr h);

        [DllImport("kernel32.dll", SetLastError = true)]
        public static extern uint WaitForSingleObject(IntPtr h, uint ms);

        public static List<HidDeviceInfo> Enumerate()
        {
            var result = new List<HidDeviceInfo>();
            Guid hidGuid = new Guid("4D1E55B2-F16F-11CF-88CB-001111000030");
            IntPtr hDevInfo = SetupDiGetClassDevs(ref hidGuid, IntPtr.Zero, IntPtr.Zero, 0x12);
            if (hDevInfo == new IntPtr(-1)) return result;

            var did = new SP_DEVICE_INTERFACE_DATA();
            did.cbSize = Marshal.SizeOf(typeof(SP_DEVICE_INTERFACE_DATA));
            int idx = 0;

            while (SetupDiEnumDeviceInterfaces(hDevInfo, IntPtr.Zero, ref hidGuid, idx, ref did))
            {
                idx++;
                int req = 0;
                SetupDiGetDeviceInterfaceDetail(hDevInfo, ref did, IntPtr.Zero, 0, out req, IntPtr.Zero);
                if (req == 0) continue;

                IntPtr buf = Marshal.AllocHGlobal(req);
                try
                {
                    Marshal.WriteInt32(buf, 0, IntPtr.Size == 8 ? 8 : 6);
                    int req2 = 0;
                    if (!SetupDiGetDeviceInterfaceDetail(hDevInfo, ref did, buf, req, out req2, IntPtr.Zero)) continue;
                    string path = Marshal.PtrToStringUni(IntPtr.Add(buf, 4));
                    if (string.IsNullOrEmpty(path)) continue;

                    IntPtr h = CreateFile(path, 0, FILE_SHARE_RW, IntPtr.Zero, OPEN_EXISTING, 0, IntPtr.Zero);
                    if (h == new IntPtr(-1)) continue;
                    try
                    {
                        var attrs = new HIDD_ATTRIBUTES();
                        attrs.Size = Marshal.SizeOf(typeof(HIDD_ATTRIBUTES));
                        if (!HidD_GetAttributes(h, ref attrs)) continue;

                        IntPtr pp = IntPtr.Zero;
                        var caps = new HIDP_CAPS { Reserved = new ushort[17] };
                        bool ok = HidD_GetPreparsedData(h, out pp);
                        if (ok) ok = HidP_GetCaps(pp, ref caps) == 0x00110000;
                        if (pp != IntPtr.Zero) HidD_FreePreparsedData(pp);
                        if (!ok) continue;

                        var info = new HidDeviceInfo
                        {
                            VendorId = attrs.VendorID,
                            ProductId = attrs.ProductID,
                            UsagePage = caps.UsagePage,
                            Usage = caps.Usage,
                            InputReportLength = caps.InputReportByteLength,
                            OutputReportLength = caps.OutputReportByteLength,
                            FeatureReportLength = caps.FeatureReportByteLength,
                            Path = path
                        };

                        var sb = new StringBuilder(256);
                        if (HidD_GetProductString(h, sb, 256)) info.ProductName = sb.ToString();
                        var sb2 = new StringBuilder(256);
                        if (HidD_GetManufacturerString(h, sb2, 256)) info.Manufacturer = sb2.ToString();

                        result.Add(info);
                    }
                    finally { CloseHandle(h); }
                }
                finally { Marshal.FreeHGlobal(buf); }
            }

            SetupDiDestroyDeviceInfoList(hDevInfo);
            return result;
        }
    }

    public sealed class HidSession : IDisposable
    {
        private IntPtr _handle;
        private IntPtr _evt;
        private readonly int _inputLen;
        private bool _closed;

        public IntPtr Handle => _handle;
        public bool IsOpen => !_closed && _handle != IntPtr.Zero && _handle != new IntPtr(-1);

        public HidSession(string path, int inputLen, bool readOnly = false)
        {
            _inputLen = Math.Max(inputLen, 1);
            uint access = readOnly ? HidNative.GENERIC_READ : (HidNative.GENERIC_READ | HidNative.GENERIC_WRITE);
            _handle = HidNative.CreateFile(path, access, HidNative.FILE_SHARE_RW, IntPtr.Zero,
                HidNative.OPEN_EXISTING, HidNative.FILE_FLAG_OVERLAPPED, IntPtr.Zero);
            if (!IsOpen)
                throw new InvalidOperationException("无法打开 HID 设备，错误码 " + Marshal.GetLastWin32Error());
            _evt = HidNative.CreateEvent(IntPtr.Zero, true, false, IntPtr.Zero);
        }

        public bool SetFeature(byte[] report) => HidNative.HidD_SetFeature(_handle, report, report.Length);
        public bool GetFeature(byte[] report) => HidNative.HidD_GetFeature(_handle, report, report.Length);
        public bool SetOutputReport(byte[] report) => HidNative.HidD_SetOutputReport(_handle, report, report.Length);

        public byte[] Read(int timeoutMs)
        {
            if (_closed) return null;
            HidNative.ResetEvent(_evt);
            var ov = new HidNative.OVERLAPPED { hEvent = _evt };
            byte[] buf = new byte[_inputLen];
            int read = 0;
            bool ok = HidNative.ReadFile(_handle, buf, _inputLen, out read, ref ov);
            if (!ok)
            {
                int err = Marshal.GetLastWin32Error();
                if (err != HidNative.ERROR_IO_PENDING) return null;
                if (HidNative.WaitForSingleObject(_evt, (uint)timeoutMs) != 0)
                {
                    HidNative.CancelIoEx(_handle, ref ov);
                    HidNative.GetOverlappedResult(_handle, ref ov, out read, true);
                    return null;
                }
                if (!HidNative.GetOverlappedResult(_handle, ref ov, out read, false)) return null;
            }
            if (read <= 0) return null;
            var res = new byte[read];
            Array.Copy(buf, res, res.Length);
            return res;
        }

        public void Flush(int idleMs)
        {
            for (int i = 0; i < 24; i++)
                if (Read(idleMs) == null) break;
        }

        public void Dispose()
        {
            if (_closed) return;
            _closed = true;
            if (_evt != IntPtr.Zero) { HidNative.CloseHandle(_evt); _evt = IntPtr.Zero; }
            if (IsOpen) HidNative.CloseHandle(_handle);
            _handle = IntPtr.Zero;
        }
    }
}
