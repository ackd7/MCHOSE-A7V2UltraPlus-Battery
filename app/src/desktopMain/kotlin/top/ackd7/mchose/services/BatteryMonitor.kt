package top.ackd7.mchose.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 电池轮询（对应原 C# BatteryMonitor），使用协程定时读取。
 * 读取在 IO 线程执行，结果通过回调抛回调用方（UI 层负责切换线程）。
 */
class BatteryMonitor(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val reader = MchoseBatteryReader()
    private var job: Job? = null
    @Volatile
    private var current = BatteryInfo()
    private val busy = AtomicBoolean(false)

    @Volatile
    var intervalSeconds: Int = 30
        private set

    var onReadingChanged: ((BatteryInfo) -> Unit)? = null

    val currentInfo: BatteryInfo get() = current

    fun start(interval: Int) {
        intervalSeconds = interval.coerceIn(5, 900)
        scheduleLoop(immediate = true)
    }

    fun setInterval(seconds: Int) {
        val next = seconds.coerceIn(5, 900)
        if (next == intervalSeconds) return
        intervalSeconds = next
        if (job?.isActive == true) {
            scheduleLoop(immediate = false)
        }
    }

    private fun scheduleLoop(immediate: Boolean) {
        stop()
        job = scope.launch {
            if (immediate) refresh()
            while (isActive) {
                delay(intervalSeconds * 1000L)
                refresh()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** 在后台 IO 线程触发一次立即刷新（供 UI / 托盘事件安全调用，不阻塞界面）。 */
    fun requestRefresh() {
        scope.launch { refresh() }
    }

    fun refresh() {
        if (!busy.compareAndSet(false, true)) return
        try {
            val result = reader.read(current)
            current = result
            onReadingChanged?.invoke(result)
        } catch (ex: Exception) {
            val r = BatteryInfo(error = ex.message ?: "读取异常", updatedAt = System.currentTimeMillis())
            current = r
            onReadingChanged?.invoke(r)
        } finally {
            busy.set(false)
        }
    }
}
