package top.ackd7.mchose.services

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
    var lastPath: String = "",
    /** 本次读取失败，显示的是上一次成功读数（设备休眠/离线）。 */
    var stale: Boolean = false,
)
