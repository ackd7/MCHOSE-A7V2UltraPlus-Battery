package top.ackd7.a7battery.services

import top.ackd7.a7battery.interop.HidDeviceInfo
import top.ackd7.a7battery.interop.HidNative

/**
 * 迈从 A7 V2 Ultra+ 电量读取（本机实机逆向验证，对应原 C# MchoseBatteryReader）。
 *
 * HID 通道：VID 0x3837，UsagePage 0xFF01，65 字节 Feature。
 * 流程（必须严格按此顺序）：
 *   1. SET_FEATURE(reportId=0x11, ~0x03, 0xFF...) → bond 唤醒；固件回 interrupt-IN "e5 03"。
 *   2. SET_FEATURE(reportId=0x11, ~0x06, 0xFF...) → ost 查询；固件回 interrupt-IN "e5 06"。
 *   3. 收到 ACK 后 GET_FEATURE(0x11)，固件在两个 bank 间交替：
 *        XOR bank: 11 F9 ...  → payload = bytes[2..] XOR 0xFF
 *        PLAIN bank:11 06 ... → payload = bytes[2..] 原样
 *   解码后 payload：
 *     [0..1] VID = 37 38 (0x3837)
 *     [2..3] PID
 *     [4..7] 固件版本
 *     [8]    连接位（&7: 0=USB, 1=2.4G, 2=BT）
 *     [9]    电量百分比 0-100
 *     [10]   充电状态（0=放电, 1=充电）
 */
class MchoseBatteryReader {

    private val featureLen = 65
    private val pageVendor = 0xFF01
    private val ridQuery = 0x11.toByte()
    private val cmdBond = 0x03.toByte()
    private val cmdOst = 0x06.toByte()

    fun discover(): List<HidDeviceInfo> {
        val found = ArrayList<HidDeviceInfo>()
        val seen = HashSet<String>()
        for (h in HidNative.enumerate()) {
            if (h.vendorId != MchoseVid) continue
            if (h.usagePage != pageVendor) continue
            if (h.featureReportLength != featureLen) continue
            if (seen.add(h.path)) found.add(h)
        }
        return found
    }

    fun read(accumulate: BatteryInfo? = null): BatteryInfo {
        val result = accumulate ?: BatteryInfo()
        result.updatedAt = System.currentTimeMillis()
        result.success = false

        val devices = discover()
        if (devices.isEmpty()) {
            result.error = "未找到迈从设备的 HID 通道（请确认鼠标已连接，或切换到 2.4G/有线模式）"
            return result
        }

        val sorted = if (result.lastPath.isNotEmpty()) {
            devices.sortedBy { if (it.path.equals(result.lastPath, ignoreCase = true)) 0 else 1 }
        } else devices

        var lastEx: Exception? = null
        for (dev in sorted) {
            try {
                val r = readOne(dev)
                if (r.success) return r
                lastEx = Exception(r.error)
                result.raw = r.raw
            } catch (ex: Exception) {
                lastEx = ex
            }
        }

        result.error = lastEx?.message ?: "读取失败：设备可能处于深度休眠，请轻动鼠标后重试"
        return result
    }

    private fun readOne(dev: HidDeviceInfo): BatteryInfo {
        val r = BatteryInfo(updatedAt = System.currentTimeMillis(), strategy = "ACK")
        HidNative.HidSession(dev.path, maxOf(dev.inputReportLength, featureLen)).use { s ->
            for (cycle in 0 until 3) {
                if (cycle > 0) Thread.sleep(180)

                s.flush(15)

                send(s, cmdBond)
                Thread.sleep(120)
                if (!waitAck(s, cmdBond, 1200)) {
                    r.error = "bond 无应答（设备休眠？轻动鼠标后重试）"
                    continue
                }

                send(s, cmdOst)
                if (!waitAck(s, cmdOst, 1200)) {
                    r.error = "ost 查询无应答（设备休眠？）"
                    continue
                }

                for (k in 0 until 6) {
                    val payload = getPayload(s)
                    if (payload != null) {
                        r.raw = hex(payload, 16)
                        val vid = payload[0].toInt() and 0xFF or ((payload[1].toInt() and 0xFF) shl 8)
                        val flags = payload[8].toInt() and 0xFF
                        val level = payload[9].toInt() and 0xFF
                        val charge = payload[10].toInt() and 0xFF

                        if (vid == MchoseVid && level in 1..100) {
                            r.percent = level
                            r.charging = charge != 0
                            r.mode = when (flags and 7) {
                                0 -> ConnectionMode.Usb
                                1 -> ConnectionMode.Radio24G
                                2 -> ConnectionMode.Bluetooth
                                else -> ConnectionMode.Unknown
                            }
                            r.deviceName = if (dev.productName.isBlank())
                                "MCHOSE ${dev.vendorId.toString(16).uppercase()}:${dev.productId.toString(16).uppercase()}"
                            else dev.productName
                            r.vendorId = dev.vendorId.toString(16).uppercase().padStart(4, '0')
                            r.productId = dev.productId.toString(16).uppercase().padStart(4, '0')
                            r.lastPath = dev.path
                            r.success = true
                            r.error = ""
                            return r
                        }
                    }
                    Thread.sleep(60)
                }
            }
            if (r.error.isBlank()) {
                r.error = if (r.raw.isBlank()) "未读到有效的 feature 报告" else "数据无效 raw=${r.raw}"
            }
        }
        return r
    }

    private fun send(s: HidNative.HidSession, cmd: Byte) {
        val buf = ByteArray(featureLen) { 0xFF.toByte() }
        buf[0] = ridQuery
        buf[1] = (cmd.toInt() xor 0xFF).toByte()
        s.setFeature(buf)
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

    private fun getPayload(s: HidNative.HidSession): ByteArray? {
        val gb = ByteArray(featureLen)
        gb[0] = ridQuery
        if (!s.getFeature(gb)) return null

        val xorBank = ((gb[1].toInt().inv()) and 0xFF) == (cmdOst.toInt() and 0xFF)
        val plainBank = (gb[1].toInt() and 0xFF) == (cmdOst.toInt() and 0xFF)
        if (!xorBank && !plainBank) return null

        val p = ByteArray(featureLen - 2)
        for (j in p.indices) {
            p[j] = if (xorBank) (gb[2 + j].toInt() xor 0xFF).toByte() else gb[2 + j]
        }
        return p
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
        const val MchoseVid: Int = 0x3837
    }
}
