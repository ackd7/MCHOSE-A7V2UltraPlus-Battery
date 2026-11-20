package top.ackd7.a7battery.services

/**
 * 连接方式（对应原 C# ConnectionMode）。
 */
enum class ConnectionMode { Unknown, Usb, Radio24G, Bluetooth }

/**
 * 电量读数（对应原 C# BatteryInfo）。
 */
data class BatteryInfo(
    var success: Boolean = false,
    var percent: Int = -1,
    var charging: Boolean = false,
    var mode: ConnectionMode = ConnectionMode.Unknown,
    var deviceName: String = "",
    var vendorId: String = "",
    var productId: String = "",
    var raw: String = "",
    var error: String = "",
    var updatedAt: Long = 0L,
    var strategy: String = "",
    var lastPath: String = "",
)
