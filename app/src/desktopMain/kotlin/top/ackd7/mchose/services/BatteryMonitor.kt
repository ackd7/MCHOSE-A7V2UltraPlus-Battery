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
    private var lastStepTimeMs: Long = 0L
    private var lastPushSignature: Int = -1

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
     * 检测到充电状态或电量变化推送时立即触发一次标准刷新。
     */
    private fun startPushListener() {
        pushJob?.cancel()
        pushJob = scope.launch {
            while (isActive) {
                val dev = runCatching { reader.discover().firstOrNull() }.getOrNull()
                if (dev == null) {
                    delay(5000L)
                    continue
                }
                try {
                    HidNative.HidSession(dev.path, maxOf(dev.inputReportLength, MchoseHidProtocol.FEATURE_LEN), readOnly = true).use { s ->
                        while (isActive) {
                            val p = s.read(500) ?: continue
                            if (p.size < 6 || p[0] != 0x13.toByte()) continue
                            val subType = (p[1].toInt() xor 0xFF) and 0xFF
                            if (subType != 0xE2 && subType != 0x1D) continue
                            val chargeByte = (p[4].toInt() xor 0xFF) and 0xFF
                            val levelByte = (p[5].toInt() xor 0xFF) and 0xFF
                            if (levelByte !in 1..100) continue
                            val sig = (chargeByte shl 8) or levelByte
                            if (sig != lastPushSignature) {
                                lastPushSignature = sig
                                requestRefresh()
                            }
                        }
                    }
                } catch (_: Exception) {
                    delay(3000L)
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
     * - 正常放电或硬件明确上报「已充满（chargeState == Full）」时直接采用原始读数；
     * - 充电中若原始读数单次跃升超过 3%，按物理充电速率上限（约每 30 秒最多 +1%，每次至少 +1%）
     *   平滑爬升，且在未收到 Full 标志前最高封顶到 99%，同时在 `rawPercent` 中保留原始值供 UI 透明展示。
     */
    internal fun applyChargeSmoothing(reading: BatteryInfo): BatteryInfo {
        val now = reading.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val rawPct = reading.rawPercent.coerceIn(0, 100)
        val prevPct = lastDisplayedPercent

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
                if (rawPct > prevPct + 3) {
                    // 按真实经过时间平滑爬升（约每 45 秒最多 +1%），且未报 Full 前最高封顶 99%
                    val elapsedSec = ((now - lastStepTimeMs).coerceAtLeast(0L) / 1000L).toInt()
                    val allowedStep = elapsedSec / 45
                    val cap = if (rawPct >= 100) 99 else rawPct
                    if (allowedStep > 0) {
                        val next = (prevPct + allowedStep).coerceAtMost(cap)
                        if (next != prevPct) lastStepTimeMs = now
                        next
                    } else {
                        prevPct
                    }
                } else if (rawPct < prevPct) {
                    // 充电过程中电压小幅波动不倒退
                    prevPct
                } else {
                    if (rawPct != prevPct) lastStepTimeMs = now
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
        return reading.copy(percent = displayPct, rawPercent = rawPct)
    }
}
