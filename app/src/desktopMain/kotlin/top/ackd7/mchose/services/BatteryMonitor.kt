package top.ackd7.mchose.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.ackd7.mchose.interop.HidNative
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 电池轮询与事件监听调度器（协程）。
 *
 * 融合多开源项目实机策略：
 * 1. 定时通过 [MchoseBatteryReader] 轮询 `0x11 0x06`（双采样防抖 + 在线位校验）；
 * 2. 后台只读监听固件主动推送的 `Report 0x13`（子类型 `0xE2` / `0x1D`），
 *    在插拔充电线或电量变动时秒级触发刷新，无需干等轮询周期；
 * 3. 对充电瞬间因锂电池端电压抬升导致的电量暴涨（如 87% -> 100%）做平滑限速，
 *    避免虚假满电，待固件真正上报已充满（chargeState == Full）时自然到达 100%。
 */
class BatteryMonitor(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val reader = MchoseBatteryReader()
    private var pollJob: Job? = null
    private var pushJob: Job? = null
    @Volatile
    private var current = BatteryInfo()
    private val busy = AtomicBoolean(false)

    // 充电电压虚高平滑器状态（仅在 refresh() 串行路径访问）
    private var lastDisplayedPercent: Int = -1
    private var lastChargeState: ChargeState = ChargeState.Discharging
    private var lastStepTimeMs: Long = 0L

    // 主动推送监听器状态（仅在 pushJob 串行路径访问）
    private var lastPushSignature: Int = -1
    private var lastPushTriggerMs: Long = 0L

    @Volatile
    var intervalSeconds: Int = 30
        private set

    var onReadingChanged: ((BatteryInfo) -> Unit)? = null

    val currentInfo: BatteryInfo get() = current

    fun start(interval: Int) {
        intervalSeconds = interval.coerceIn(5, 900)
        scheduleLoop(immediate = true)
        startPushListener()
    }

    fun setInterval(seconds: Int) {
        val next = seconds.coerceIn(5, 900)
        if (next == intervalSeconds) return
        intervalSeconds = next
        if (pollJob?.isActive == true) {
            scheduleLoop(immediate = false)
        }
    }

    private fun scheduleLoop(immediate: Boolean) {
        pollJob?.cancel()
        pollJob = scope.launch {
            if (immediate) refresh()
            while (isActive) {
                delay(intervalSeconds * 1000L)
                refresh()
            }
        }
    }

    /**
     * 后台只读监听固件主动推送的 `Report 0x13`（参考 LORQAQ/mchose-tray）。
     * 仅使用只读句柄监听 Interrupt-IN，绝不抢占 Feature 写入锁；
     * 检测到充电状态、电量变化或设备热插拔重连时立即触发一次标准刷新，
     * 并自动过滤与当前读数已一致的 nudge 回显，避免无效重复轮询。
     */
    private fun startPushListener() {
        pushJob?.cancel()
        pushJob = scope.launch {
            while (isActive) {
                val devices = runCatching { reader.discover() }.getOrDefault(emptyList())
                val preferred = current.lastPath
                val dev = devices.firstOrNull {
                    preferred.isNotBlank() && it.path.equals(preferred, ignoreCase = true)
                } ?: devices.firstOrNull()

                if (dev == null) {
                    delay(5000L)
                    continue
                }

                var disconnected = false
                try {
                    HidNative.HidSession(dev.path, maxOf(dev.inputReportLength, MchoseHidProtocol.FEATURE_LEN), readOnly = true).use { s ->
                        while (isActive) {
                            // 若轮询已切换到新通道（如从 2.4G 接收器插线切为 USB 有线），退出旧句柄重连新通道
                            val activePath = current.lastPath
                            if (activePath.isNotBlank() && !activePath.equals(dev.path, ignoreCase = true)) {
                                break
                            }
                            val p = s.read(500)
                            if (s.disconnected) {
                                disconnected = true
                                break
                            }
                            if (p == null || p.size < 6 || p[0] != 0x13.toByte()) continue
                            val subType = (p[1].toInt() xor 0xFF) and 0xFF
                            if (subType != 0xE2 && subType != 0x1D) continue
                            val chargeByte = (p[4].toInt() xor 0xFF) and 0xFF
                            val levelByte = (p[5].toInt() xor 0xFF) and 0xFF
                            if (levelByte !in 1..100) continue

                            val cur = current
                            val curChargeCode = when (cur.chargeState) {
                                ChargeState.Charging -> 1
                                ChargeState.Full -> 2
                                ChargeState.Discharging -> 0
                            }
                            val alreadySynced = cur.success && !cur.stale &&
                                cur.rawPercent == levelByte && curChargeCode == chargeByte
                            val sig = (chargeByte shl 8) or levelByte
                            val now = System.currentTimeMillis()

                            if (!alreadySynced &&
                                (sig != lastPushSignature || cur.stale || !cur.success) &&
                                (now - lastPushTriggerMs >= PUSH_COOLDOWN_MS)
                            ) {
                                lastPushSignature = sig
                                lastPushTriggerMs = now
                                requestRefresh()
                            } else {
                                lastPushSignature = sig
                            }
                        }
                    }
                } catch (_: Exception) {
                    delay(3000L)
                }

                if (disconnected && isActive) {
                    // 设备拔插或通道失效：稍作等待后触发一次刷新并重新发现活跃通道
                    lastPushSignature = -1
                    delay(1500L)
                    requestRefresh()
                }
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        pushJob?.cancel()
        pushJob = null
    }

    /** 在后台 IO 线程触发一次立即刷新（供 UI / 托盘事件安全调用，不阻塞界面）。 */
    fun requestRefresh() {
        scope.launch { refresh() }
    }

    fun refresh() {
        if (!busy.compareAndSet(false, true)) return
        try {
            val rawResult = reader.read(current)
            val processed = if (rawResult.success) applyChargeSmoothing(rawResult) else rawResult
            current = processed
            onReadingChanged?.invoke(processed)
        } catch (ex: Exception) {
            val r = BatteryInfo(error = ex.message ?: "读取异常", updatedAt = System.currentTimeMillis())
            current = r
            onReadingChanged?.invoke(r)
        } finally {
            busy.set(false)
        }
    }

    /**
     * 抑制插电瞬间锂电池端电压抬升导致的电量暴涨（例如 87% -> 100%）。
     *
     * 规则：
     * - 仅在「充电中（chargeState == Charging）」且之前已有有效基线读数时生效；
     * - 首次从非充电状态进入充电时重置计时起点，绝不把插电前的静置/休眠时长计为充电时长；
     * - 充电中若原始读数单次跃升超过 2%，按物理充电速率上限（每 60 秒最多 +1%）平滑爬升，
     *   同时在 `rawPercent` 中保留固件原始值供 UI 透明展示；
     * - 若硬件明确上报「已充满（chargeState == Full）」，立刻准确显示 100%；
     * - 正常放电时如实显示固件原始放电电量。
     */
    internal fun applyChargeSmoothing(reading: BatteryInfo): BatteryInfo {
        val now = reading.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val rawPct = reading.rawPercent.coerceIn(0, 100)
        val prevPct = lastDisplayedPercent
        val enteringCharging = lastChargeState != ChargeState.Charging

        val displayPct = when {
            prevPct < 0 -> {
                lastStepTimeMs = now
                rawPct
            }
            reading.chargeState == ChargeState.Full -> {
                // 硬件确认已充满，直接显示 100%
                lastStepTimeMs = now
                100
            }
            reading.chargeState == ChargeState.Charging -> {
                if (rawPct > prevPct + 2) {
                    if (enteringCharging) {
                        // 刚插上充电线瞬间：不计入插电前的静置/休眠时间，保持当前基线电量起平滑
                        lastStepTimeMs = now
                        prevPct
                    } else {
                        val elapsedMs = (now - lastStepTimeMs).coerceAtLeast(0L)
                        val allowedStep = (elapsedMs / CHARGE_STEP_MS).toInt()
                        if (allowedStep > 0) {
                            val next = (prevPct + allowedStep).coerceAtMost(rawPct)
                            val usedSteps = next - prevPct
                            lastStepTimeMs = if (next >= rawPct) now else (lastStepTimeMs + usedSteps * CHARGE_STEP_MS).coerceAtMost(now)
                            next
                        } else {
                            prevPct
                        }
                    }
                } else if (rawPct < prevPct) {
                    // 充电过程中电压小幅波动不倒退
                    if (enteringCharging) lastStepTimeMs = now
                    prevPct
                } else {
                    if (enteringCharging || rawPct != prevPct) lastStepTimeMs = now
                    rawPct
                }
            }
            else -> {
                // 放电状态：如实显示固件真实放电电量
                lastStepTimeMs = now
                rawPct
            }
        }

        lastDisplayedPercent = displayPct
        lastChargeState = reading.chargeState
        return reading.copy(percent = displayPct, rawPercent = rawPct)
    }

    companion object {
        /** 充电电压虚高平滑速率：每 60 秒最多上升 1%（87% → 100% 约 13 分钟，贴合锂电恒压段）。 */
        private const val CHARGE_STEP_MS = 60_000L

        /** Report 0x13 推送触发刷新的最小间隔，防止连续推送风暴。 */
        private const val PUSH_COOLDOWN_MS = 2_500L
    }
}
