package top.ackd7.mchose.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 电池轮询（对应原 C# BatteryMonitor），使用协程定时读取。
 * 读取在 IO 线程执行，结果通过回调抛回调用方（UI 层负责切换线程）。
 */
class BatteryMonitor(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val reader = MchoseBatteryReader()
    private var job: Job? = null
    private var current = BatteryInfo()
    private var busy = false

    @Volatile
    var intervalSeconds: Int = 30
        private set

    var onReadingChanged: ((BatteryInfo) -> Unit)? = null

    val currentInfo: BatteryInfo get() = current

    fun start(interval: Int) {
        intervalSeconds = interval.coerceIn(5, 900)
        stop()
        job = scope.launch {
            refresh()
            while (isActive) {
                delay(intervalSeconds * 1000L)
                refresh()
            }
        }
    }

    fun setInterval(seconds: Int) {
        intervalSeconds = seconds.coerceIn(5, 900)
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun refresh() {
        if (busy) return
        busy = true
        try {
            val result = reader.read(current)
            current = when {
                result.success -> result
                current.success -> {
                    current.updatedAt = result.updatedAt
                    current.error = result.error
                    current
                }
                else -> result
            }
            onReadingChanged?.invoke(current)
        } catch (ex: Exception) {
            val r = BatteryInfo(error = ex.message ?: "读取异常", updatedAt = System.currentTimeMillis())
            current = r
            onReadingChanged?.invoke(r)
        } finally {
            busy = false
        }
    }
}
