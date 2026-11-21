package top.ackd7.mchose.services

import top.ackd7.mchose.interop.HidDeviceInfo
import top.ackd7.mchose.interop.HidNative

/**
 * 迈从 A7 V2 Ultra+ 厂商 HID 协议公共常量与工具方法。
 * 由 [MchoseBatteryReader] 与 [MouseConfigService] 共享。
 */
internal object MchoseHidProtocol {

    const val MCHOSE_VID: Int = 0x3837
    const val PAGE_VENDOR: Int = 0xFF01
    const val FEATURE_LEN: Int = 65

    const val RID_SHORT: Byte = 0x11
    const val RID_LONG: Byte = 0x12
    const val CMD_BOND: Byte = 0x03

    /** 枚举匹配 VID 0x3837 / UsagePage 0xFF01 / 65 字节 Feature 的去重 HID 通道。 */
    fun discover(): List<HidDeviceInfo> {
        val found = ArrayList<HidDeviceInfo>()
        val seen = HashSet<String>()
        for (h in HidNative.enumerate()) {
            if (h.vendorId != MCHOSE_VID) continue
            if (h.usagePage != PAGE_VENDOR) continue
            if (h.featureReportLength != FEATURE_LEN) continue
            if (seen.add(h.path)) found.add(h)
        }
        return found
    }

    /**
     * 等待固件回传的 interrupt-IN ACK（解码 XOR 0xFF 后前两字节为 `0xE5, cmd`）。
     */
    fun waitAck(s: HidNative.HidSession, cmd: Byte, totalMs: Int): Boolean {
        val expectCmd = cmd.toInt() and 0xFF
        var waited = 0
        while (waited < totalMs) {
            val p = s.read(80)
            waited += 80
            if (p == null || p.size < 3) continue
            val b1 = (p[1].toInt() xor 0xFF) and 0xFF
            val b2 = (p[2].toInt() xor 0xFF) and 0xFF
            if (b1 == 0xE5 && b2 == expectCmd) return true
        }
        return false
    }

    /** 把字节数组前 [max] 字节格式化为大写十六进制（用于诊断日志）。 */
    fun hex(b: ByteArray?, max: Int): String {
        if (b == null) return ""
        val sb = StringBuilder()
        for (i in 0 until minOf(max, b.size)) {
            if (i > 0) sb.append(' ')
            sb.append(String.format("%02X", b[i].toInt() and 0xFF))
        }
        return sb.toString()
    }
}
