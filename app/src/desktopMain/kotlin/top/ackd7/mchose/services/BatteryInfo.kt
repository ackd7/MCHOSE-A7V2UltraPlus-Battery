package top.ackd7.mchose.services

/**
 * 连接方式。
 */
enum class ConnectionMode { Unknown, Usb, Radio24G, Bluetooth }

/**
 * 充电状态（官方协议 payload[10]：0=放电，1=充电中，2=已充满）。
 */
enum class ChargeState { Discharging, Charging, Full }

/**
 * 电量读数。
 */
data class BatteryInfo(
    var success: Boolean = false,
    /** 展示电量百分比（0–100，充电电压虚高时经平滑处理）。 */
    var percent: Int = -1,
    /** 固件直接上报的原始百分比（0–100），用于透明展示与诊断。 */
    var rawPercent: Int = -1,
    var chargeState: ChargeState = ChargeState.Discharging,
    var mode: ConnectionMode = ConnectionMode.Unknown,
    /** 无线在线标志（官方协议 payload[8] bit3）。 */
    var linked: Boolean = true,
    var deviceName: String = "",
    var vendorId: String = "",
    var productId: String = "",
    var raw: String = "",
    var error: String = "",
    var updatedAt: Long = 0L,
    var lastPath: String = "",
    /** 本次读取失败或设备离线，显示的是上一次成功读数（设备休眠/离线）。 */
    var stale: Boolean = false,
) {
    /** 是否处于通电充电过程（不含已充满）。 */
    val charging: Boolean get() = chargeState == ChargeState.Charging

    /** 是否已充满电（插电且固件上报 chargeStatus == 2）。 */
    val isFull: Boolean get() = chargeState == ChargeState.Full

    /** 展示值是否因充电电压虚高平滑而低于固件原始值。 */
    val smoothed: Boolean get() = success && rawPercent in 0..100 && percent in 0..100 && rawPercent != percent
}
