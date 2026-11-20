using System;
using System.Collections.Generic;
using System.Text;
using System.Threading;
using A7Battery.Interop;

namespace A7Battery.Services
{
    public enum ConnectionMode { Unknown, Usb, Radio24G, Bluetooth }

    public sealed class BatteryInfo
    {
        public bool Success;
        public int Percent = -1;
        public bool Charging;
        public ConnectionMode Mode = ConnectionMode.Unknown;
        public string DeviceName = "";
        public string VendorId = "";
        public string ProductId = "";
        public string Raw = "";
        public string Error = "";
        public DateTime UpdatedAt;
        public string Strategy = "";
        public string LastPath = "";
    }

    /// <summary>
    /// 迈从 A7 V2 Ultra+ 电量读取（本机实机逆向验证）。
    ///
    /// HID 通道：VID 0x3837，UsagePage 0xFF01，65 字节 Feature，PID 0x100B。
    /// 流程（必须严格按此顺序）：
    ///   1. SET_FEATURE(reportId=0x11, ~0x03, 0xFF...) → bond 唤醒；固件回 interrupt-IN "e5 03"。
    ///   2. SET_FEATURE(reportId=0x11, ~0x06, 0xFF...) → ost 查询；固件回 interrupt-IN "e5 06"。
    ///   3. 收到 ACK 后 GET_FEATURE(0x11)，固件在两个 bank 间交替：
    ///        XOR bank: 11 F9 ...  → payload = bytes[2..] XOR 0xFF
    ///        PLAIN bank:11 06 ... → payload = bytes[2..] 原样
    ///   解码后 payload：
    ///     [0..1] VID = 37 38 (0x3837)
    ///     [2..3] PID = 21 40 (0x4021，接收器内部 PID)
    ///     [4..7] 固件版本
    ///     [8]    连接位（&7: 0=USB, 1=2.4G, 2=BT）
    ///     [9]    电量百分比 0-100
    ///     [10]   充电状态（0=放电, 1=充电）
    ///   实机验证：payload = 06 37 38 21 40 05 2E 02 04 09 58 00 ... → 电量 0x58=88%，2.4G。
    /// </summary>
    public sealed class MchoseBatteryReader
    {
        public const ushort MchoseVid = 0x3837;
        private const int FeatureLen = 65;
        private const ushort PageVendor = 0xFF01;
        private const byte RidQuery = 0x11;
        private const byte CmdBond = 0x03;
        private const byte CmdOst = 0x06;

        public List<HidDeviceInfo> Discover()
        {
            var found = new List<HidDeviceInfo>();
            var seen = new HashSet<string>();
            foreach (var h in HidNative.Enumerate())
            {
                if (h.VendorId != MchoseVid) continue;
                if (h.UsagePage != PageVendor) continue;
                if (h.FeatureReportLength != FeatureLen) continue;
                if (seen.Add(h.Path)) found.Add(h);
            }
            return found;
        }

        public BatteryInfo Read(BatteryInfo accumulate = null)
        {
            var result = accumulate ?? new BatteryInfo();
            result.UpdatedAt = DateTime.Now;
            result.Success = false;

            var devices = Discover();
            if (devices.Count == 0)
            {
                result.Error = "未找到迈从设备的 HID 通道（请确认鼠标已连接，或切换到 2.4G/有线模式）";
                return result;
            }

            if (!string.IsNullOrEmpty(result.LastPath))
            {
                devices.Sort((a, b) =>
                    string.Equals(a.Path, result.LastPath, StringComparison.OrdinalIgnoreCase) ? -1 :
                    string.Equals(b.Path, result.LastPath, StringComparison.OrdinalIgnoreCase) ? 1 : 0);
            }

            Exception lastEx = null;
            foreach (var dev in devices)
            {
                try
                {
                    var r = ReadOne(dev);
                    if (r.Success) return r;
                    lastEx = new Exception(r.Error);
                    result.Raw = r.Raw;
                }
                catch (Exception ex) { lastEx = ex; }
            }

            result.Error = lastEx?.Message ?? "读取失败：设备可能处于深度休眠，请轻动鼠标后重试";
            return result;
        }

        private BatteryInfo ReadOne(HidDeviceInfo dev)
        {
            var r = new BatteryInfo { UpdatedAt = DateTime.Now, Strategy = "ACK" };
            using (var s = new HidSession(dev.Path, Math.Max(dev.InputReportLength, FeatureLen)))
            {
                // 鼠标休眠时偶发 ACK 丢失，整个 bond+ost 周期重试 3 轮
                for (int cycle = 0; cycle < 3; cycle++)
                {
                    if (cycle > 0) Thread.Sleep(180);

                    // 清空残留的 interrupt-IN 报告
                    s.Flush(15);

                    // 1) bond 唤醒，必须消费掉 ACK
                    Send(s, CmdBond);
                    Thread.Sleep(120);
                    if (!WaitAck(s, CmdBond, 1200))
                    {
                        r.Error = "bond 无应答（设备休眠？轻动鼠标后重试）";
                        continue;
                    }

                    // 2) ost 查询，必须消费掉 ACK
                    Send(s, CmdOst);
                    if (!WaitAck(s, CmdOst, 1200))
                    {
                        r.Error = "ost 查询无应答（设备休眠？）";
                        continue;
                    }

                    // 3) 连续读取 feature，XOR/PLAIN 两个 bank 都尝试
                    for (int k = 0; k < 6; k++)
                    {
                        var payload = GetPayload(s);
                        if (payload != null)
                        {
                            r.Raw = Hex(payload, 16);
                            int vid = payload[0] | (payload[1] << 8);
                            int flags = payload[8];
                            int level = payload[9];
                            int charge = payload[10];

                            if (vid == MchoseVid && level > 0 && level <= 100)
                            {
                                r.Percent = level;
                                r.Charging = charge != 0;
                                r.Mode = (flags & 7) switch
                                {
                                    0 => ConnectionMode.Usb,
                                    1 => ConnectionMode.Radio24G,
                                    2 => ConnectionMode.Bluetooth,
                                    _ => ConnectionMode.Unknown
                                };
                                r.DeviceName = string.IsNullOrEmpty(dev.ProductName)
                                    ? $"MCHOSE {dev.VendorId:X4}:{dev.ProductId:X4}" : dev.ProductName;
                                r.VendorId = dev.VendorId.ToString("X4");
                                r.ProductId = dev.ProductId.ToString("X4");
                                r.LastPath = dev.Path;
                                r.Success = true;
                                r.Error = "";
                                return r;
                            }
                        }
                        Thread.Sleep(60);
                    }
                }
                if (string.IsNullOrEmpty(r.Error))
                    r.Error = string.IsNullOrEmpty(r.Raw) ? "未读到有效的 feature 报告" : $"数据无效 raw={r.Raw}";
            }
            return r;
        }

        private static void Send(HidSession s, byte cmd)
        {
            var buf = new byte[FeatureLen];
            for (int i = 0; i < buf.Length; i++) buf[i] = 0xFF;
            buf[0] = RidQuery;
            buf[1] = (byte)(cmd ^ 0xFF);
            s.SetFeature(buf);
        }

        private static bool WaitAck(HidSession s, byte cmd, int totalMs)
        {
            string target = "e5" + cmd.ToString("x2");
            int waited = 0;
            while (waited < totalMs)
            {
                var p = s.Read(80);
                waited += 80;
                if (p == null) continue;
                var sb = new StringBuilder();
                for (int i = 1; i < p.Length; i++) sb.Append(((byte)(p[i] ^ 0xFF)).ToString("x2"));
                if (sb.ToString().StartsWith(target, StringComparison.Ordinal)) return true;
            }
            return false;
        }

        private static byte[] GetPayload(HidSession s)
        {
            var gb = new byte[FeatureLen];
            gb[0] = RidQuery;
            if (!s.GetFeature(gb)) return null;

            bool xorBank = ((~gb[1]) & 0xFF) == CmdOst;
            bool plainBank = gb[1] == CmdOst;
            if (!xorBank && !plainBank) return null;

            var p = new byte[FeatureLen - 2];
            for (int j = 0; j < p.Length; j++) p[j] = xorBank ? (byte)(gb[2 + j] ^ 0xFF) : gb[2 + j];
            return p;
        }

        private static string Hex(byte[] b, int max)
        {
            if (b == null) return "";
            var sb = new StringBuilder();
            for (int i = 0; i < max && i < b.Length; i++) sb.Append(b[i].ToString("X2")).Append(' ');
            return sb.ToString().Trim();
        }
    }
}
