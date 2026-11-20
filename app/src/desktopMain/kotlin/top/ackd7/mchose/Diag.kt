package top.ackd7.mchose

import top.ackd7.mchose.interop.HidNative
import top.ackd7.mchose.services.MchoseBatteryReader
import top.ackd7.mchose.services.MouseConfigService
import top.ackd7.mchose.services.UpdateChecker

/**
 * 本地诊断入口（不打包进发布产物，仅用于开发调试）。
 * 用法：./gradlew.bat :app:diag
 */
fun main() {
    println("=== 更新检查 ===")
    runCatching {
        val checker = UpdateChecker()
        val asOld = checker.check("2.0.0")
        println("以 2.0.0 为当前版本：latest=${asOld.latestVersion} available=${asOld.available} err=${asOld.error}")
        val asLatest = checker.check(APP_VERSION)
        println("以 $APP_VERSION 为当前版本：latest=${asLatest.latestVersion} available=${asLatest.available} err=${asLatest.error}")
        println("isNewer(v3.0.0, 2.9.9) = ${UpdateChecker.isNewer("v3.0.0", "2.9.9")}")
    }.onFailure { println("更新检查异常: ${it.message}") }

    println("=== Dispatchers.Main 可用性 ===")
    val mainOk = runCatching {
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { }
        }
        true
    }.getOrElse { false }
    println("Dispatchers.Main = ${if (mainOk) "可用" else "缺失（会抛 IllegalStateException）"}")

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
    println("=== 回报率字段探测（写 2000Hz 再看哪个字节变）===")
    runCatching {
        val svc = MouseConfigService()
        val before = svc.read()
        println("before: ok=${before.success} usbRate=${before.usbRateIndex} gRate=${before.wirelessRateIndex} activeDpi=${before.activeDpiIndex}")
        println("before raw: ${before.raw}")
        if (before.success) {
            val (wOk, wErr) = svc.setRate(3) // 3 -> 2000Hz（8K 档位表）
            println("write rate=3 ok=$wOk err=$wErr")
            Thread.sleep(500)
            val after = svc.read()
            println("after : ok=${after.success} usbRate=${after.usbRateIndex} gRate=${after.wirelessRateIndex} activeDpi=${after.activeDpiIndex}")
            println("after  raw: ${after.raw}")
            if (before.usbRateIndex >= 0) {
                val (rOk, _) = svc.setRate(before.usbRateIndex)
                println("restore rate=${before.usbRateIndex} ok=$rOk")
            }
        }
    }.onFailure { println("探测异常: ${it.message}") }

    println()
    println("=== 多命令扫描：找回报率字段（先写 2000Hz=index3）===")
    runCatching {
        val svc = MouseConfigService()
        svc.read()
        svc.setRate(3)
        Thread.sleep(400)
        val dev = top.ackd7.mchose.interop.HidNative.enumerate()
            .firstOrNull { it.vendorId == 0x3837 && it.usagePage == 0xFF01 }
        if (dev == null) {
            println("  未找到 FF01 通道")
        } else {
            top.ackd7.mchose.interop.HidNative.HidSession(dev.path, 65).use { sess ->
                sess.flush(20)
                // bond
                val bond = ByteArray(65) { 0xFF.toByte() }
                bond[0] = 0x11
                bond[1] = (0x03 xor 0xFF).toByte()
                sess.setFeature(bond)
                Thread.sleep(150)
                sess.read(300)
                for (cmd in intArrayOf(0x03, 0x0B, 0x40, 0x41, 0x42, 0x67)) {
                    val f = ByteArray(65) { 0xFF.toByte() }
                    f[0] = 0x11
                    f[1] = (cmd xor 0xFF).toByte()
                    sess.setFeature(f)
                    Thread.sleep(150)
                    val gb = ByteArray(65)
                    gb[0] = 0x11
                    val ok = sess.getFeature(gb)
                    val hex = gb.copyOfRange(0, 20).joinToString(" ") { "%02X".format(it) }
                    println("  cmd=0x%02X ok=%s : %s".format(cmd, ok, hex))
                    sess.read(120)
                }
            }
            // 还原
            svc.setRate(0)
        }
    }.onFailure { println("扫描异常: ${it.message}") }

    println()
    println("=== 尝试读取鼠标配置（DPI / 回报率）===")
    val cfg = MouseConfigService().read()
    println("success=${cfg.success} activeDpiIndex=${cfg.activeDpiIndex} dpi=${cfg.dpiStages.joinToString()}")
    println("raw=${cfg.raw}")
    println("error=${cfg.error}")
}
