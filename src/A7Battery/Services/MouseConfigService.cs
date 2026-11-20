using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using A7Battery.Interop;

namespace A7Battery.Services
{
    public sealed class MouseConfig
    {
        public bool Success;
        public string Error = "";
        public int[] DpiStages = new int[6];
        public int ActiveDpiIndex = -1;   // 0-based
        public int UsbRateIndex = -1;
        public int WirelessRateIndex = -1;
        public string Raw = "";
    }

    /// <summary>
    /// 迈从 A7 鼠标配置读写（DPI / 回报率）。
    /// 协议来自 M HUB Web 驱动逆向（icepie/mchose-linux 的 PROTOCOL.md），
    /// 与电量读取同属 L7 族 feature report 协议：
    ///   - report 0x11 / 0x12，payload 全部字节 XOR 0xFF，无校验和
    ///   - 写 DPI:     0x11 0x40  usbDpiIndex, gDpiIndex, reserved, dpi0..5(u16 LE), sum
    ///   - 写回报率:   0x11 0x41  usbRate, freeRate
    ///   - 读完整配置: 0x12 0x67  full config（含 6 个 DPI stage 与 index）
    /// 所有命令前先发 0x03 bond 唤醒并消费 interrupt-IN ACK。
    /// </summary>
    public sealed class MouseConfigService
    {
        // 8K 型号回报率档位（与 M HUB 一致）
        public static readonly int[] RateHz = { 125, 500, 1000, 2000, 4000, 8000 };
        public static string RateLabel(int idx) =>
            idx >= 0 && idx < RateHz.Length ? RateHz[idx] + " Hz" : "未知";

        private const ushort Vid = MchoseBatteryReader.MchoseVid;
        private const ushort PageVendor = 0xFF01;
        private const int FeatureLen = 65;
        private const byte RidShort = 0x11;
        private const byte RidLong = 0x12;
        private const byte CmdBond = 0x03;
        private const byte CmdConfigRead = 0x67;
        private const byte CmdDpiWrite = 0x40;
        private const byte CmdRateWrite = 0x41;

        public List<HidDeviceInfo> Discover()
        {
            var found = new List<HidDeviceInfo>();
            var seen = new HashSet<string>();
            foreach (var h in HidNative.Enumerate())
            {
                if (h.VendorId != Vid) continue;
                if (h.UsagePage != PageVendor) continue;
                if (h.FeatureReportLength != FeatureLen) continue;
                if (seen.Add(h.Path)) found.Add(h);
            }
            return found;
        }

        public MouseConfig Read()
        {
            var r = new MouseConfig();
            var devices = Discover();
            if (devices.Count == 0)
            {
                r.Error = "未找到迈从设备 HID 通道（请先连接鼠标并轻动一下）";
                return r;
            }

            Exception last = null;
            foreach (var dev in devices)
            {
                try
                {
                    var cfg = ReadOne(dev);
                    if (cfg.Success) return cfg;
                    last = new Exception(cfg.Error);
                }
                catch (Exception ex) { last = ex; }
            }
            r.Error = last?.Message ?? "读取失败";
            return r;
        }

        private MouseConfig ReadOne(HidDeviceInfo dev)
        {
            var r = new MouseConfig();
            using (var s = new HidSession(dev.Path, Math.Max(dev.InputReportLength, FeatureLen)))
            {
                for (int cycle = 0; cycle < 3; cycle++)
                {
                    if (cycle > 0) Thread.Sleep(180);
                    s.Flush(15);

                    // 1) 唤醒（必须消费 ACK）
                    Send(s, RidShort, CmdBond, null);
                    Thread.Sleep(120);
                    if (!WaitAck(s, CmdBond, 1000))
                    {
                        r.Error = "唤醒无应答，请轻动鼠标后重试";
                        continue;
                    }

                    // 2) 查询完整配置
                    Send(s, RidLong, CmdConfigRead, null);
                    Thread.Sleep(100);
                    WaitAck(s, CmdConfigRead, 200); // 有的固件有 ACK，没有也继续

                    // 3) 回读 feature
                    var payload = ReadFeature(s, RidLong, CmdConfigRead, 6);
                    if (payload == null)
                    {
                        r.Error = "配置读取无应答";
                        continue;
                    }
                    r.Raw = Hex(payload, 24);
                    Parse(payload, r);
                    if (r.DpiStages[0] > 0)
                    {
                        r.Success = true;
                        r.Error = "";
                        return r;
                    }
                    r.Error = "配置数据无效 raw=" + r.Raw;
                }
            }
            return r;
        }

        private static void Parse(byte[] p, MouseConfig r)
        {
            if (p.Length < 18) return;
            // nibble 顺序（低位在前）: usbRate, usbDpi, gRate, gDpi
            int usbRate = p[1] & 0x0F;
            int usbDpi = (p[1] >> 4) & 0x0F;
            int gRate = p[2] & 0x0F;
            int gDpi = (p[2] >> 4) & 0x0F;
            r.UsbRateIndex = ClampIndex(usbRate);
            r.WirelessRateIndex = ClampIndex(gRate);
            r.ActiveDpiIndex = ClampIndex(usbDpi) >= 0 ? usbDpi : (ClampIndex(gDpi) >= 0 ? gDpi : -1);

            // p[3] = reserved（固件会自行翻动，忽略）
            // p[4..15] = dpi0..5，u16 little-endian，原始 DPI 值
            for (int i = 0; i < 6; i++)
            {
                int v = p[4 + i * 2] | (p[5 + i * 2] << 8);
                r.DpiStages[i] = (v > 0 && v <= 50000) ? v : 0;
            }
        }

        private static int ClampIndex(int v) => v >= 0 && v < 6 ? v : -1;

        /// <summary>写入 6 个 DPI 档位并设置当前激活档。</summary>
        public bool SetDpi(int[] stages, int activeIndex, out string error)
        {
            error = "";
            if (stages == null || stages.Length != 6) { error = "需要 6 个 DPI 档位值"; return false; }
            var args = new byte[16]; // usbDpi, gDpi, reserved, dpi0..5(u16), sum
            args[0] = (byte)Math.Clamp(activeIndex, 0, 5);
            args[1] = (byte)Math.Clamp(activeIndex, 0, 5);
            args[2] = 0;
            for (int i = 0; i < 6; i++)
            {
                int v = Math.Clamp(stages[i], 100, 30000);
                args[3 + i * 2] = (byte)(v & 0xFF);
                args[4 + i * 2] = (byte)((v >> 8) & 0xFF);
            }
            args[15] = (byte)stages.Count(v => v > 0);
            return Write(RidShort, CmdDpiWrite, args, out error);
        }

        /// <summary>写入回报率档位（有线与 2.4G 同时设置）。</summary>
        public bool SetRate(int rateIndex, out string error)
        {
            error = "";
            if (rateIndex < 0 || rateIndex >= RateHz.Length) { error = "回报率档位无效"; return false; }
            var args = new byte[] { (byte)rateIndex, (byte)rateIndex }; // usbRate, freeRate
            return Write(RidShort, CmdRateWrite, args, out error);
        }

        private bool Write(byte rid, byte cmd, byte[] args, out string error)
        {
            error = "";
            var devices = Discover();
            if (devices.Count == 0) { error = "未找到迈从设备 HID 通道"; return false; }

            Exception last = null;
            foreach (var dev in devices)
            {
                try
                {
                    using (var s = new HidSession(dev.Path, Math.Max(dev.InputReportLength, FeatureLen)))
                    {
                        s.Flush(15);
                        Send(s, RidShort, CmdBond, null);
                        Thread.Sleep(120);
                        if (!WaitAck(s, CmdBond, 1000))
                        {
                            last = new Exception("唤醒无应答，请轻动鼠标后重试");
                            continue;
                        }

                        Send(s, rid, cmd, args);
                        Thread.Sleep(80);
                        WaitAck(s, cmd, 300); // 尽力消费 ACK（部分固件不发）
                        var echo = ReadFeature(s, rid, cmd, 6);
                        if (echo == null)
                        {
                            last = new Exception("写入后未收到回显确认");
                            continue;
                        }
                        Thread.Sleep(300); // 配置提交需要时间
                        return true;
                    }
                }
                catch (Exception ex) { last = ex; }
            }
            error = last?.Message ?? "写入失败";
            return false;
        }

        // 帧构造：report id 不反转；cmd 与参数全部 XOR 0xFF；padding 用 0xFF（反转的 0）
        private static byte[] BuildFrame(byte rid, byte cmd, byte[] args)
        {
            var buf = new byte[FeatureLen];
            for (int i = 0; i < buf.Length; i++) buf[i] = 0xFF;
            buf[0] = rid;
            buf[1] = (byte)(cmd ^ 0xFF);
            if (args != null)
                for (int i = 0; i < args.Length && 2 + i < buf.Length; i++)
                    buf[2 + i] = (byte)(args[i] ^ 0xFF);
            return buf;
        }

        private static void Send(HidSession s, byte rid, byte cmd, byte[] args)
        {
            s.SetFeature(BuildFrame(rid, cmd, args));
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

        // 回读 feature：XOR bank 与 PLAIN bank 都接受（固件在两个 bank 间交替）
        private static byte[] ReadFeature(HidSession s, byte rid, byte expectCmd, int retries)
        {
            for (int k = 0; k < retries; k++)
            {
                var gb = new byte[FeatureLen];
                gb[0] = rid;
                if (!s.GetFeature(gb)) { Thread.Sleep(60); continue; }
                int echo = gb[1];
                bool xorBank = ((~echo) & 0xFF) == expectCmd;
                bool plainBank = echo == expectCmd;
                if (!xorBank && !plainBank) { Thread.Sleep(60); continue; }
                var p = new byte[FeatureLen - 2];
                for (int j = 0; j < p.Length; j++)
                    p[j] = xorBank ? (byte)(gb[2 + j] ^ 0xFF) : gb[2 + j];
                return p;
            }
            return null;
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
