package top.ackd7.mchose.services

import top.ackd7.mchose.interop.HidDeviceInfo
import top.ackd7.mchose.interop.HidNative

/**
 * 迈从 A7 V2 Ultra+ 电量读取（实机验证 + 核对官方 M HUB 协议）。
 *
 * HID 通道：VID 0x3837，UsagePage 0xFF01，65 字节 Feature。
 * 流程：
 *   1. SET_FEATURE(reportId=0x11, ~0x03, 0xFF...) → bond 唤醒；尽力消费 interrupt-IN ACK。
 *   2. SET_FEATURE(reportId=0x11, ~0x06, 0xFF...) → ost 查询；轮询 GET_FEATURE（双 bank 交替）。
 *   3. 连续两次一致采样确认（参考 Tensyd/mchose-battery 对 A7 V2 Ultra+ 的防抖策略），
 *      减少刚唤醒时的偶发跳电；超时则回退采用单次有效样本。
 *
 * 解码后 payload（little-endian）：
 *   [0..1] VID = 37 38 (0x3837)
 *   [2..3] 内部 PID
 *   [4..7] 固件版本
 *   [8]    连接位（&7: 0/4=USB, 1=2.4G, 2=BT；&8: 无线在线标志 linked）
 *   [9]    电量百分比 0–100
 *   [10]   充电状态（0=放电, 1=充电中, 2=已充满）
 */
class MchoseBatteryReader {

    private val featureLen = MchoseHidProtocol.FEATURE_LEN
    private val ridQuery = MchoseHidProtocol.RID_SHORT
    private val cmdBond = MchoseHidProtocol.CMD_BOND
    private val cmdOst = 0x06.toByte()

    fun discover(): List<HidDeviceInfo> = MchoseHidProtocol.discover()

    fun read(accumulate: BatteryInfo? = null): BatteryInfo = HidNative.withDeviceLock {
        val preferredPath = accumulate?.lastPath.orEmpty()
        val result = BatteryInfo(
            updatedAt = System.currentTimeMillis(),
            lastPath = preferredPath,
        )

        val devices = discover()
        if (devices.isEmpty()) {
            result.error = "未找到迈从设备的 HID 通道（请确认鼠标已连接，或切换到 2.4G/有线模式）"
            return@withDeviceLock result
        }

        val sorted = if (preferredPath.isNotEmpty()) {
            devices.sortedBy { if (it.path.equals(preferredPath, ignoreCase = true)) 0 else 1 }
        } else devices

        var lastEx: Exception? = null
        for (dev in sorted) {
            try {
                val r = readOne(dev)
                if (r.success) return@withDeviceLock r
                lastEx = Exception(r.error)
                result.raw = r.raw
            } catch (ex: Exception) {
                lastEx = ex
            }
        }

        result.error = lastEx?.message ?: "读取失败：设备可能处于深度休眠，请轻动鼠标后重试"
        result
    }

    private fun readOne(dev: HidDeviceInfo): BatteryInfo {
        val fallback = BatteryInfo(updatedAt = System.currentTimeMillis())
        var firstValid: BatteryInfo? = null

        HidNative.HidSession(dev.path, maxOf(dev.inputReportLength, featureLen)).use { s ->
            for (cycle in 0 until 3) {
                if (cycle > 0) Thread.sleep(150)

                s.flush(20)

                // 1) bond 唤醒（ACK 尽力消费但不作为硬条件）
                send(s, cmdBond)
                Thread.sleep(120)
                waitAck(s, cmdBond, 500)

                // 2) 最多发两轮 ost 查询，连续两次一致采样直接确认（防唤醒首帧抖动）
                for (sampleRound in 0 until 2) {
                    send(s, cmdOst)
                    val sample = pollOstPayload(s, dev, fallback) ?: break
                    val prev = firstValid
                    if (prev != null && prev.rawPercent == sample.rawPercent && prev.chargeState == sample.chargeState) {
                        return sample
                    }
                    firstValid = sample
                    // 若已拿到第一个有效样本，间隔 80ms 再确认一次
                    if (sampleRound == 0) Thread.sleep(80)
                }

                // 若单轮里只成功拿到一个样本，下一轮继续尝试确认；最后一轮直接采用
                if (firstValid != null && cycle >= 1) {
                    return firstValid
                }
            }
        }

        if (firstValid != null) return firstValid

        if (fallback.error.isBlank()) {
            fallback.error = if (fallback.raw.isBlank()) {
                "未读到 feature 报告（设备可能深度休眠，轻动鼠标后重试）"
            } else {
                "设备休眠或数据无效 raw=${fallback.raw}"
            }
        }
        return fallback
    }

    private fun pollOstPayload(
        s: HidNative.HidSession,
        dev: HidDeviceInfo,
        fallback: BatteryInfo,
    ): BatteryInfo? {
        for (k in 0 until 8) {
            Thread.sleep(75)
            waitAck(s, cmdOst, 20)

            val payload = getPayload(s) ?: continue
            fallback.raw = hex(payload, 16)
            val vid = (payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)
            val flags = payload[8].toInt() and 0xFF
            val level = payload[9].toInt() and 0xFF
            val chargeRaw = payload[10].toInt() and 0xFF

            val mode = when (flags and 7) {
                0, 4 -> ConnectionMode.Usb
                1 -> ConnectionMode.Radio24G
                2 -> ConnectionMode.Bluetooth
                else -> ConnectionMode.Unknown
            }
            val linked = (flags and 0x08) != 0

            // 2.4G 模式下若无线在线位 linked == 0，说明鼠标离线/休眠，不采信接收器缓存数据
            if (mode == ConnectionMode.Radio24G && !linked) {
                fallback.error = "鼠标离线或休眠中（linked=0）"
                continue
            }

            if (vid == MchoseVid && level in 1..100) {
                val chargeState = when (chargeRaw) {
                    1 -> ChargeState.Charging
                    2 -> ChargeState.Full
                    else -> ChargeState.Discharging
                }
                return BatteryInfo(
                    success = true,
                    percent = level,
                    rawPercent = level,
                    chargeState = chargeState,
                    mode = mode,
                    linked = linked,
                    deviceName = if (dev.productName.isBlank()) {
                        "MCHOSE ${dev.vendorId.toString(16).uppercase()}:${dev.productId.toString(16).uppercase()}"
                    } else {
                        dev.productName
                    },
                    vendorId = dev.vendorId.toString(16).uppercase().padStart(4, '0'),
                    productId = dev.productId.toString(16).uppercase().padStart(4, '0'),
                    raw = hex(payload, 16),
                    updatedAt = System.currentTimeMillis(),
                    lastPath = dev.path,
                )
            }
        }
        return null
    }

    private fun send(s: HidNative.HidSession, cmd: Byte) {
        val buf = ByteArray(featureLen) { 0xFF.toByte() }
        buf[0] = ridQuery
        buf[1] = (cmd.toInt() xor 0xFF).toByte()
        s.setFeature(buf)
    }

    private fun waitAck(s: HidNative.HidSession, cmd: Byte, totalMs: Int): Boolean =
        MchoseHidProtocol.waitAck(s, cmd, totalMs)

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

    private fun hex(b: ByteArray?, max: Int): String = MchoseHidProtocol.hex(b, max)

    companion object {
        const val MchoseVid: Int = MchoseHidProtocol.MCHOSE_VID
    }
}
