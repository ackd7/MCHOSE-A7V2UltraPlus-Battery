package top.ackd7.mchose.services

import top.ackd7.mchose.APP_ID
import top.ackd7.mchose.appLog
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * 单实例锁与唤醒通道。
 *
 * 通过系统内核维护的排他文件锁（%LOCALAPPDATA%\APP_ID\instance.lock）保证同一时间
 * 只运行一个实例（进程异常退出时内核自动释放锁，不会残留死锁）。
 * 主实例在 127.0.0.1 随机端口监听唤醒信号；二次启动若检测到已有实例在运行，
 * 则通知旧实例拉起主窗口并直接退出。
 */
class SingleInstance private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
    private val server: ServerSocket?,
) : AutoCloseable {

    @Volatile
    private var onWake: (() -> Unit)? = null

    fun setWakeHandler(handler: () -> Unit) {
        onWake = handler
    }

    override fun close() {
        runCatching { server?.close() }
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        private fun appDir(): File {
            val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            return File(base, APP_ID).apply { mkdirs() }
        }

        private fun lockFile(): File = File(appDir(), "instance.lock")
        private fun portFile(): File = File(appDir(), "instance.port")

        /**
         * 尝试获取单实例锁。
         * @param notifyIfRunning 已存在运行中实例时是否发送唤醒指令（非静默启动传 true）。
         * @return 拿到锁返回 [SingleInstance]；已有实例在运行返回 null。
         */
        fun tryAcquire(notifyIfRunning: Boolean): SingleInstance? {
            val channel = try {
                FileChannel.open(
                    lockFile().toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )
            } catch (e: Exception) {
                appLog("打开单实例锁文件失败: ${e.message}")
                return null
            }

            val lock = try {
                channel.tryLock()
            } catch (_: Exception) {
                null
            }

            if (lock == null) {
                runCatching { channel.close() }
                if (notifyIfRunning) notifyExistingInstance()
                return null
            }

            val server = runCatching {
                ServerSocket(0, 4, InetAddress.getLoopbackAddress()).also { ss ->
                    portFile().writeText(ss.localPort.toString())
                }
            }.getOrNull()

            val instance = SingleInstance(channel, lock, server)
            if (server != null) {
                thread(isDaemon = true, name = "single-instance-wake") {
                    while (!server.isClosed) {
                        try {
                            server.accept().use { client ->
                                client.soTimeout = 500
                                val cmd = client.getInputStream().bufferedReader().readLine()?.trim()
                                if (cmd == "SHOW") {
                                    appLog("收到二次启动唤醒信号")
                                    instance.onWake?.invoke()
                                }
                            }
                        } catch (_: Exception) {
                            if (server.isClosed) break
                        }
                    }
                }
            }
            return instance
        }

        private fun notifyExistingInstance() {
            runCatching {
                val port = portFile().takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
                    ?: return
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500)
                    socket.getOutputStream().write("SHOW\n".toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                }
            }
        }
    }
}
