package top.ackd7.mchose

import top.ackd7.mchose.interop.HidNative
import top.ackd7.mchose.services.MchoseBatteryReader
import top.ackd7.mchose.services.MouseConfigService
import top.ackd7.mchose.services.UpdateChecker

/**
 * 本地只读诊断入口（不打包进发布产物，不修改鼠标任何设置）。
 * 用法：./gradlew.bat :app:diag
 */
fun main() {
    println("=== 更新检查 ===")
    runCatching {
        val checker = UpdateChecker()
        val info = checker.check(APP_VERSION)
        println("当前=$APP_VERSION latest=${info.latestVersion} available=${info.available} err=${info.error}")
    }.onFailure { println("更新检查异常: ${it.message}") }

    println("=== Dispatchers.Main 可用性 ===")
    val mainOk = runCatching {
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { }
        }
        true
    }.getOrElse { false }
    println("Dispatchers.Main = ${if (mainOk) "可用" else "缺失"}")

    println("=== HID 设备枚举 ===")
    val all = HidNative.enumerate()
    println("共 ${all.size} 个 HID 接口")
    for (d in all.filter { it.vendorId == MchoseBatteryReader.MchoseVid }) {
        println(
            "  %04X:%04X page=0x%04X usage=0x%04X in=%d out=%d feature=%d name='%s'"
                .format(
                    d.vendorId, d.productId, d.usagePage, d.usage,
                    d.inputReportLength, d.outputReportLength, d.featureReportLength, d.productName,
                ),
        )
    }

    val matched = MchoseBatteryReader().discover()
    println("匹配厂商通道 ${matched.size} 个:")
    matched.forEach { println("  -> ${it.path}") }

    println()
    println("=== 读取电量 ===")
    val info = MchoseBatteryReader().read()
    println("success=${info.success} percent=${info.percent} rawPercent=${info.rawPercent} chargeState=${info.chargeState} linked=${info.linked} mode=${info.mode}")
    println("raw=${info.raw}")
    println("error=${info.error}")

    println()
    println("=== 读取鼠标配置（只读）===")
    val cfg = MouseConfigService().read()
    println(
        "success=${cfg.success} activeDpiIndex=${cfg.activeDpiIndexFor(info.mode)} " +
            "activeRate=${MouseConfigService.rateLabel(cfg.activeRateIndex(info.mode))} " +
            "(wireless=${MouseConfigService.rateLabel(cfg.wirelessRateIndex)}, usb=${MouseConfigService.rateLabel(cfg.usbRateIndex)}) " +
            "dpi=${cfg.dpiStages.joinToString()}",
    )
    println("raw=${cfg.raw}")
    println("error=${cfg.error}")
}
