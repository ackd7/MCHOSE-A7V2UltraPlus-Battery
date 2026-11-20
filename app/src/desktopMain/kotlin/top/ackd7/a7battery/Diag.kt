package top.ackd7.a7battery

import top.ackd7.a7battery.interop.HidNative
import top.ackd7.a7battery.services.MchoseBatteryReader
import top.ackd7.a7battery.services.MouseConfigService

/**
 * 本地诊断入口（不打包进发布产物，仅用于开发调试）。
 * 用法：./gradlew.bat :app:diag
 */
fun main() {
    println("=== HID 设备枚举 ===")
    val all = HidNative.enumerate()
    println("共 ${all.size} 个 HID 接口")
    for (d in all) {
        val hex = "%04X:%04X".format(d.vendorId, d.productId)
        println("  $hex page=0x%04X usage=0x%04X in=%d out=%d feature=%d name='%s'"
            .format(d.usagePage, d.usage, d.inputReportLength, d.outputReportLength,
                d.featureReportLength, d.productName))
    }

    println()
    println("=== 迈从 0x3837 / FF01 / 65 字节 候选 ===")
    val cands = all.filter { it.vendorId == 0x3837 }
    println("VID=3837 的设备 ${cands.size} 个")
    for (d in cands) {
        println("  PID=%04X page=0x%04X feature=%d".format(d.productId, d.usagePage, d.featureReportLength))
    }

    val matched = MchoseBatteryReader().discover()
    println("MchoseBatteryReader.discover() = ${matched.size} 个")
    matched.forEach { println("  -> ${it.path}") }

    println()
    println("=== 尝试读取电量（现有实现）===")
    val info = MchoseBatteryReader().read()
    println("success=${info.success} percent=${info.percent} charging=${info.charging} mode=${info.mode}")
    println("raw=${info.raw}")
    println("error=${info.error}")

    println()
    println("=== 逐设备原始探测（SetFeature / ACK / GetFeature 细节）===")
    for (d in matched) {
        println("--- ${d.path}")
        runCatching {
            HidNative.HidSession(d.path, maxOf(d.inputReportLength, 65)).use { s ->
                s.flush(20)

                val bond = ByteArray(65) { 0xFF.toByte() }
                bond[0] = 0x11
                bond[1] = (0x03 xor 0xFF).toByte()
                val okBond = s.setFeature(bond)
                println("  SET_FEATURE(bond) = $okBond, lastError=${com.sun.jna.Native.getLastError()}")
                Thread.sleep(120)
                val ackBond = s.read(400)
                println("  ACK(bond) = ${ackBond?.joinToString(" ") { "%02X".format(it) } ?: "null"}")

                val ost = ByteArray(65) { 0xFF.toByte() }
                ost[0] = 0x11
                ost[1] = (0x06 xor 0xFF).toByte()
                val okOst = s.setFeature(ost)
                println("  SET_FEATURE(ost) = $okOst")
                Thread.sleep(120)
                val ackOst = s.read(400)
                println("  ACK(ost) = ${ackOst?.joinToString(" ") { "%02X".format(it) } ?: "null"}")

                for (i in 0 until 4) {
                    val gb = ByteArray(65)
                    gb[0] = 0x11
                    val ok = s.getFeature(gb)
                    println("  GET_FEATURE[$i] = $ok : ${gb.joinToString(" ") { "%02X".format(it) }.take(120)}")
                    Thread.sleep(60)
                }
            }
        }.onFailure { println("  异常: ${it.message}") }
    }

    println()
    println("=== 尝试读取鼠标配置（DPI / 回报率）===")
    val cfg = MouseConfigService().read()
    println("success=${cfg.success} activeDpiIndex=${cfg.activeDpiIndex} dpi=${cfg.dpiStages.joinToString()}")
    println("raw=${cfg.raw}")
    println("error=${cfg.error}")
}
