package top.ackd7.a7battery.interop

/**
 * HID 设备信息（对应原 C# 的 HidDeviceInfo）。
 */
data class HidDeviceInfo(
    val vendorId: Int,
    val productId: Int,
    val usagePage: Int,
    val usage: Int,
    val inputReportLength: Int,
    val outputReportLength: Int,
    val featureReportLength: Int,
    val path: String,
    val productName: String = "",
    val manufacturer: String = "",
) {
    override fun toString(): String =
        "$vendorId".padStart(4, '0') + ":" + "$productId".padStart(4, '0') +
            " UP=${usagePage.toUShort().toString(16).uppercase()}" +
            " FL=$featureReportLength $productName"
}
