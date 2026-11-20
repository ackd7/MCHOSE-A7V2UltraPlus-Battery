package top.ackd7.a7battery.services

import top.ackd7.a7battery.interop.HidDeviceInfo
import top.ackd7.a7battery.interop.HidNative

/**
 * 鼠标配置读取结果（对应原 C# MouseConfig）。
 */
data class MouseConfig(
    var success: Boolean = false,
    var error: String = "",
    var dpiStages: IntArray = IntArray(6),
    var activeDpiIndex: Int = -1,
    var usbRateIndex: Int = -1,
    var wirelessRateIndex: Int = -1,
    var raw: String = "",
)

/**
 * 迈从 A7 鼠标配置读写（DPI / 回报率），对应原 C# MouseConfigService。
 *
 * 协议来自 M HUB Web 驱动逆向（icepie/mchose-linux 的 PROTOCOL.md），
 * 与电量读取同属 L7 族 feature report 协议：
 *   - report 0x11 / 0x12，payload 全部字节 XOR 0xFF，无校验和
 *   - 写 DPI:     0x11 0x40  usbDpiIndex, gDpiIndex, reserved, dpi0..5(u16 LE), sum
 *   - 写回报率:   0x11 0x41  usbRate, freeRate
 *   - 读完整配置: 0x12 0x67  full config（含 6 个 DPI stage 与 index）
 * 所有命令前先发 0x03 bond 唤醒并消费 interrupt-IN ACK。
 */
class MouseConfigService {

    private val featureLen = 65
    private val pageVendor = 0xFF01
    private val ridShort = 0x11.toByte()
    private val ridLong = 0x12.toByte()
    private val cmdBond = 0x03.toByte()
    private val cmdConfigRead = 0x67.toByte()
    private val cmdDpiWrite = 0x40.toByte()
    private val cmdRateWrite = 0x41.toByte()

    fun discover(): List<HidDeviceInfo> {
        val found = ArrayList<HidDeviceInfo>()
        val seen = HashSet<String>()
        for (h in HidNative.enumerate()) {
            if (h.vendorId != MchoseBatteryReader.MchoseVid) continue
            if (h.usagePage != pageVendor) continue
            if (h.featureReportLength != featureLen) continue
            if (seen.add(h.path)) found.add(h)
        }
        return found
    }

    fun read(): MouseConfig {
        val r = MouseConfig()
        val devices = discover()
        if (devices.isEmpty()) {
            r.error = "未找到迈从设备 HID 通道（请先连接鼠标并轻动一下）"
            return r
        }

        var last: Exception? = null
        for (dev in devices) {
            try {
                val cfg = readOne(dev)
                if (cfg.success) return cfg
                last = Exception(cfg.error)
            } catch (ex: Exception) {
                last = ex
            }
        }
        r.error = last?.message ?: "读取失败"
        return r
    }

    private fun readOne(dev: HidDeviceInfo): MouseConfig {
        val r = MouseConfig()
        HidNative.HidSession(dev.path, maxOf(dev.inputReportLength, featureLen)).use { s ->
            for (cycle in 0 until 3) {
                if (cycle > 0) Thread.sleep(180)
                s.flush(15)

                send(s, ridShort, cmdBond, null)
                Thread.sleep(120)
                // bond 的 ACK 尽力消费但不作为硬条件（部分固件不回 ACK）
                waitAck(s, cmdBond, 600)

                send(s, ridLong, cmdConfigRead, null)
                Thread.sleep(100)
                waitAck(s, cmdConfigRead, 200)

                val payload = readFeature(s, ridLong, cmdConfigRead, 8)
                if (payload == null) {
                    r.error = "配置读取无应答"
                    continue
                }
                r.raw = hex(payload, 24)
                parse(payload, r)
                if (r.dpiStages[0] > 0) {
                    r.success = true
                    r.error = ""
                    return r
                }
                r.error = "配置数据无效 raw=" + r.raw
            }
        }
        return r
    }

    private fun parse(p: ByteArray, r: MouseConfig) {
        if (p.size < 18) return
        val usbRate = p[1].toInt() and 0x0F
        val usbDpi = (p[1].toInt() shr 4) and 0x0F
        val gRate = p[2].toInt() and 0x0F
        val gDpi = (p[2].toInt() shr 4) and 0x0F
        r.usbRateIndex = clampIndex(usbRate)
        r.wirelessRateIndex = clampIndex(gRate)
        r.activeDpiIndex = if (clampIndex(usbDpi) >= 0) usbDpi else (if (clampIndex(gDpi) >= 0) gDpi else -1)

        for (i in 0 until 6) {
            val v = (p[4 + i * 2].toInt() and 0xFF) or ((p[5 + i * 2].toInt() and 0xFF) shl 8)
            r.dpiStages[i] = if (v in 1..50000) v else 0
        }
    }

    private fun clampIndex(v: Int): Int = if (v in 0 until 6) v else -1

    /** 写入 6 个 DPI 档位并设置当前激活档。 */
    fun setDpi(stages: IntArray, activeIndex: Int): Pair<Boolean, String> {
        if (stages.size != 6) return false to "需要 6 个 DPI 档位值"
        val args = ByteArray(16)
        args[0] = activeIndex.coerceIn(0, 5).toByte()
        args[1] = activeIndex.coerceIn(0, 5).toByte()
        args[2] = 0
        var active = 0
        for (i in 0 until 6) {
            val v = stages[i].coerceIn(100, 42000)
            args[3 + i * 2] = (v and 0xFF).toByte()
            args[4 + i * 2] = ((v shr 8) and 0xFF).toByte()
            if (v > 0) active++
        }
        args[15] = active.toByte()
        return write(ridShort, cmdDpiWrite, args)
    }

    /** 写入回报率档位（有线与 2.4G 同时设置）。 */
    fun setRate(rateIndex: Int): Pair<Boolean, String> {
        if (rateIndex !in RateHz.indices) return false to "回报率档位无效"
        val args = byteArrayOf(rateIndex.toByte(), rateIndex.toByte())
        return write(ridShort, cmdRateWrite, args)
    }

    private fun write(rid: Byte, cmd: Byte, args: ByteArray?): Pair<Boolean, String> {
        val devices = discover()
        if (devices.isEmpty()) return false to "未找到迈从设备 HID 通道"

        var last: Exception? = null
        for (dev in devices) {
            try {
                HidNative.HidSession(dev.path, maxOf(dev.inputReportLength, featureLen)).use { s ->
                    s.flush(20)
                    send(s, ridShort, cmdBond, null)
                    Thread.sleep(120)
                    // 唤醒 ACK 尽力消费，不作硬条件
                    waitAck(s, cmdBond, 600)

                    send(s, rid, cmd, args)
                    Thread.sleep(80)
                    waitAck(s, cmd, 300)
                    val echo = readFeature(s, rid, cmd, 6)
                    if (echo == null) {
                        last = Exception("写入后未收到回显确认")
                        return@use
                    }
                    Thread.sleep(300)
                    return true to ""
                }
            } catch (ex: Exception) {
                last = ex
            }
        }
        return false to (last?.message ?: "写入失败")
    }

    private fun buildFrame(rid: Byte, cmd: Byte, args: ByteArray?): ByteArray {
        val buf = ByteArray(featureLen) { 0xFF.toByte() }
        buf[0] = rid
        buf[1] = (cmd.toInt() xor 0xFF).toByte()
        if (args != null) {
            for (i in args.indices) {
                if (2 + i >= buf.size) break
                buf[2 + i] = (args[i].toInt() xor 0xFF).toByte()
            }
        }
        return buf
    }

    private fun send(s: HidNative.HidSession, rid: Byte, cmd: Byte, args: ByteArray?) {
        s.setFeature(buildFrame(rid, cmd, args))
    }

    private fun waitAck(s: HidNative.HidSession, cmd: Byte, totalMs: Int): Boolean {
        val target = "e5" + String.format("%02x", cmd.toInt() and 0xFF)
        var waited = 0
        while (waited < totalMs) {
            val p = s.read(80)
            waited += 80
            if (p == null) continue
            val sb = StringBuilder()
            for (i in 1 until p.size) sb.append(String.format("%02x", (p[i].toInt() xor 0xFF) and 0xFF))
            if (sb.toString().startsWith(target)) return true
        }
        return false
    }

    private fun readFeature(s: HidNative.HidSession, rid: Byte, expectCmd: Byte, retries: Int): ByteArray? {
        for (k in 0 until retries) {
            val gb = ByteArray(featureLen)
            gb[0] = rid
            if (!s.getFeature(gb)) {
                Thread.sleep(60)
                continue
            }
            val echo = gb[1].toInt() and 0xFF
            val xorBank = ((echo.inv()) and 0xFF) == (expectCmd.toInt() and 0xFF)
            val plainBank = echo == (expectCmd.toInt() and 0xFF)
            if (!xorBank && !plainBank) {
                Thread.sleep(60)
                continue
            }
            val p = ByteArray(featureLen - 2)
            for (j in p.indices) {
                p[j] = if (xorBank) (gb[2 + j].toInt() xor 0xFF).toByte() else gb[2 + j]
            }
            return p
        }
        return null
    }

    private fun hex(b: ByteArray?, max: Int): String {
        if (b == null) return ""
        val sb = StringBuilder()
        for (i in 0 until minOf(max, b.size)) {
            if (i > 0) sb.append(' ')
            sb.append(String.format("%02X", b[i].toInt() and 0xFF))
        }
        return sb.toString()
    }

    companion object {
        val RateHz = intArrayOf(125, 500, 1000, 2000, 4000, 8000)

        fun rateLabel(idx: Int): String =
            if (idx in RateHz.indices) "${RateHz[idx]} Hz" else "未知"
    }
}
