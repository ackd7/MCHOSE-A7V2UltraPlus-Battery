package top.ackd7.mchose.interop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.SetupApi
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Windows HID 原生接口封装（JNA）。
 *
 * 对应原 C# `Interop/HidNative.cs`：通过 SetupAPI 枚举 HID 设备，
 * 通过 hid.dll 读写 Feature Report，通过 kernel32 做重叠 I/O 读写。
 *
 * 尽量复用 jna-platform 内置的 [SetupApi]、[Kernel32] 接口，
 * 仅 hid.dll 与个别 kernel32 方法（GetOverlappedResult / CancelIoEx）手写声明。
 */
object HidNative {

    private const val HID_GUID = "4D1E55B2-F16F-11CF-88CB-001111000030"

    /** 串行化同一 HID 厂商通道上的读写，避免电量轮询与鼠标配置读写并发串包。 */
    private val ioLock = Any()

    fun <T> withDeviceLock(block: () -> T): T = synchronized(ioLock, block)

    // ---------- 常量 ----------

    private const val FILE_SHARE_RW = WinNT.FILE_SHARE_READ or WinNT.FILE_SHARE_WRITE
    private const val ERROR_IO_PENDING = 997
    private const val DIGCF_PRESENT = 0x00000002
    private const val DIGCF_DEVICEINTERFACE = 0x00000010

    private const val HIDP_STATUS_SUCCESS = 0x00110000

    // ---------- hid.dll 接口（jna-platform 未内置，手写） ----------

    private interface HidDll : Library {
        fun HidD_GetAttributes(HidDeviceObject: WinNT.HANDLE, Attributes: HIDD_ATTRIBUTES): Boolean
        fun HidD_GetPreparsedData(HidDeviceObject: WinNT.HANDLE, PreparsedData: PointerByReference): Boolean
        fun HidD_FreePreparsedData(PreparsedData: Pointer): Boolean
        fun HidP_GetCaps(PreparsedData: Pointer, Capabilities: HIDP_CAPS): Int
        fun HidD_GetProductString(HidDeviceObject: WinNT.HANDLE, Buffer: CharArray, BufferLength: Int): Boolean
        fun HidD_GetManufacturerString(HidDeviceObject: WinNT.HANDLE, Buffer: CharArray, BufferLength: Int): Boolean
        fun HidD_GetFeature(HidDeviceObject: WinNT.HANDLE, ReportBuffer: ByteArray, ReportBufferLength: Int): Boolean
        fun HidD_SetFeature(HidDeviceObject: WinNT.HANDLE, ReportBuffer: ByteArray, ReportBufferLength: Int): Boolean
        fun HidD_SetOutputReport(HidDeviceObject: WinNT.HANDLE, ReportBuffer: ByteArray, ReportBufferLength: Int): Boolean
    }

    // kernel32 补充方法（使用原生 Pointer 签名，避免 JNA 对异步 OVERLAPPED 与缓冲区做临时拷贝/提前释放导致堆破坏）
    private interface Kernel32Extra : Library {
        fun ReadFile(
            hFile: WinNT.HANDLE,
            lpBuffer: Pointer,
            nNumberOfBytesToRead: Int,
            lpNumberOfBytesRead: Pointer?,
            lpOverlapped: Pointer,
        ): Boolean

        fun GetOverlappedResult(
            hFile: WinNT.HANDLE,
            lpOverlapped: Pointer,
            lpNumberOfBytesTransferred: IntByReference,
            bWait: Boolean,
        ): Boolean

        fun CancelIoEx(hFile: WinNT.HANDLE, lpOverlapped: Pointer?): Boolean
    }

    // ---------- 结构体 ----------

    class HIDD_ATTRIBUTES : com.sun.jna.Structure() {
        @JvmField var Size = 0          // ULONG (4 字节)
        @JvmField var VendorID: Short = 0       // USHORT (2 字节)
        @JvmField var ProductID: Short = 0      // USHORT (2 字节)
        @JvmField var VersionNumber: Short = 0  // USHORT (2 字节)

        override fun getFieldOrder(): List<String> = listOf("Size", "VendorID", "ProductID", "VersionNumber")
    }

    class HIDP_CAPS : com.sun.jna.Structure() {
        @JvmField var Usage: Short = 0
        @JvmField var UsagePage: Short = 0
        @JvmField var InputReportByteLength: Short = 0
        @JvmField var OutputReportByteLength: Short = 0
        @JvmField var FeatureReportByteLength: Short = 0
        @JvmField var Reserved = ShortArray(17)
        @JvmField var NumberLinkCollectionNodes: Short = 0
        @JvmField var NumberInputButtonCaps: Short = 0
        @JvmField var NumberInputValueCaps: Short = 0
        @JvmField var NumberInputDataIndices: Short = 0
        @JvmField var NumberOutputButtonCaps: Short = 0
        @JvmField var NumberOutputValueCaps: Short = 0
        @JvmField var NumberOutputDataIndices: Short = 0
        @JvmField var NumberFeatureButtonCaps: Short = 0
        @JvmField var NumberFeatureValueCaps: Short = 0
        @JvmField var NumberFeatureDataIndices: Short = 0

        override fun getFieldOrder(): List<String> = listOf(
            "Usage", "UsagePage", "InputReportByteLength", "OutputReportByteLength",
            "FeatureReportByteLength", "Reserved", "NumberLinkCollectionNodes",
            "NumberInputButtonCaps", "NumberInputValueCaps", "NumberInputDataIndices",
            "NumberOutputButtonCaps", "NumberOutputValueCaps", "NumberOutputDataIndices",
            "NumberFeatureButtonCaps", "NumberFeatureValueCaps", "NumberFeatureDataIndices",
        )
    }

    // ---------- 接口实例 ----------

    private val hid: HidDll = Native.load("hid", HidDll::class.java)
    private val kernel32Extra: Kernel32Extra = Native.load("kernel32", Kernel32Extra::class.java)

    // ---------- 枚举 ----------

    fun enumerate(): List<HidDeviceInfo> {
        val result = ArrayList<HidDeviceInfo>()
        val hidGuid = Guid.GUID.fromString(HID_GUID)

        val hDevInfo = SetupApi.INSTANCE.SetupDiGetClassDevs(hidGuid, null, null, DIGCF_PRESENT or DIGCF_DEVICEINTERFACE)
        if (hDevInfo == WinBase.INVALID_HANDLE_VALUE) return result

        try {
            val did = SetupApi.SP_DEVICE_INTERFACE_DATA()
            // cbSize 已在构造函数中设为 size()

            var idx = 0
            while (SetupApi.INSTANCE.SetupDiEnumDeviceInterfaces(hDevInfo, null, hidGuid, idx, did)) {
                idx++

                val required = IntByReference()
                SetupApi.INSTANCE.SetupDiGetDeviceInterfaceDetail(hDevInfo, did, null, 0, required, null)
                if (required.value == 0) continue

                val size = required.value
                val buf = com.sun.jna.Memory(size.toLong())
                // SP_DEVICE_INTERFACE_DETAIL_DATA 首字段 cbSize
                buf.setInt(0, if (Native.POINTER_SIZE == 8) 8 else 6)
                val required2 = IntByReference()
                if (!SetupApi.INSTANCE.SetupDiGetDeviceInterfaceDetail(hDevInfo, did, buf, size, required2, null)) continue

                // 路径位于偏移 4 之后（cbSize 为 4 字节 DWORD）
                val path = buf.getWideString(4) ?: continue

                val h = Kernel32.INSTANCE.CreateFile(path, 0, FILE_SHARE_RW, null, WinNT.OPEN_EXISTING, 0, null)
                if (h == WinBase.INVALID_HANDLE_VALUE) continue
                try {
                    val attrs = HIDD_ATTRIBUTES()
                    attrs.Size = attrs.size()
                    attrs.write()
                    if (!hid.HidD_GetAttributes(h, attrs)) continue
                    attrs.read()

                    val pp = PointerByReference()
                    val caps = HIDP_CAPS()
                    val gotPreparsed = hid.HidD_GetPreparsedData(h, pp)
                    var ok = false
                    if (gotPreparsed) {
                        try {
                            ok = hid.HidP_GetCaps(pp.value, caps) == HIDP_STATUS_SUCCESS
                            if (ok) caps.read()
                        } finally {
                            pp.value?.let { hid.HidD_FreePreparsedData(it) }
                        }
                    }
                    if (!ok) continue

                    val productName = readString { b -> hid.HidD_GetProductString(h, b, (b.size - 1) * 2) }
                    val manufacturer = readString { b -> hid.HidD_GetManufacturerString(h, b, (b.size - 1) * 2) }

                    result.add(
                        HidDeviceInfo(
                            vendorId = attrs.VendorID.toInt() and 0xFFFF,
                            productId = attrs.ProductID.toInt() and 0xFFFF,
                            usagePage = caps.UsagePage.toInt() and 0xFFFF,
                            usage = caps.Usage.toInt() and 0xFFFF,
                            inputReportLength = caps.InputReportByteLength.toInt() and 0xFFFF,
                            outputReportLength = caps.OutputReportByteLength.toInt() and 0xFFFF,
                            featureReportLength = caps.FeatureReportByteLength.toInt() and 0xFFFF,
                            path = path,
                            productName = productName,
                            manufacturer = manufacturer,
                        )
                    )
                } finally {
                    Kernel32.INSTANCE.CloseHandle(h)
                }
            }
        } finally {
            SetupApi.INSTANCE.SetupDiDestroyDeviceInfoList(hDevInfo)
        }
        return result
    }

    private fun readString(getter: (CharArray) -> Boolean): String {
        val buf = CharArray(256)
        return if (getter(buf)) String(buf).trimEnd('\u0000') else ""
    }

    // ---------- 会话（对应原 HidSession） ----------

    class HidSession(path: String, inputLen: Int, readOnly: Boolean = false) : AutoCloseable {
        private val handle: WinNT.HANDLE
        private val evt: WinNT.HANDLE
        private val inputLen: Int = maxOf(inputLen, 1)
        // 持久化原生堆内存：供异步 OVERLAPPED ReadFile 在整个 I/O 生命周期内固定持有，
        // 彻底杜绝 JNA 传递 ByteArray/Structure 临时拷贝提前释放造成的堆破坏（0xC0000374 / 0xC0000005）
        private val readBuf = com.sun.jna.Memory(this.inputLen.toLong())
        private val ovSize = if (Native.POINTER_SIZE == 8) 32L else 20L
        private val ovEventOffset = if (Native.POINTER_SIZE == 8) 24L else 16L
        private val ovMem = com.sun.jna.Memory(ovSize)
        @Volatile
        private var closed = false
        @Volatile
        var disconnected: Boolean = false
            private set

        init {
            val access = if (readOnly) WinNT.GENERIC_READ else (WinNT.GENERIC_READ or WinNT.GENERIC_WRITE)
            handle = Kernel32.INSTANCE.CreateFile(path, access, FILE_SHARE_RW, null, WinNT.OPEN_EXISTING, WinNT.FILE_FLAG_OVERLAPPED, null)
            if (handle == WinBase.INVALID_HANDLE_VALUE) {
                val err = Native.getLastError()
                readBuf.close()
                ovMem.close()
                throw IllegalStateException("无法打开 HID 设备，错误码 $err")
            }
            val createdEvt = Kernel32.INSTANCE.CreateEvent(null, true, false, null)
            if (createdEvt == null || createdEvt == WinBase.INVALID_HANDLE_VALUE) {
                val err = Native.getLastError()
                Kernel32.INSTANCE.CloseHandle(handle)
                readBuf.close()
                ovMem.close()
                throw IllegalStateException("创建重叠 I/O 事件失败，错误码 $err")
            }
            evt = createdEvt
        }

        fun setFeature(report: ByteArray): Boolean = hid.HidD_SetFeature(handle, report, report.size)
        fun getFeature(report: ByteArray): Boolean = hid.HidD_GetFeature(handle, report, report.size)
        fun setOutputReport(report: ByteArray): Boolean = hid.HidD_SetOutputReport(handle, report, report.size)

        fun read(timeoutMs: Int): ByteArray? {
            if (closed || disconnected) return null
            try {
                Kernel32.INSTANCE.ResetEvent(evt)
                ovMem.clear()
                ovMem.setPointer(ovEventOffset, evt.pointer)

                val transferred = IntByReference(0)
                val ok = kernel32Extra.ReadFile(handle, readBuf, inputLen, null, ovMem)
                if (!ok) {
                    val err = Native.getLastError()
                    if (err != ERROR_IO_PENDING) {
                        disconnected = true
                        return null
                    }
                    if (Kernel32.INSTANCE.WaitForSingleObject(evt, timeoutMs) != 0) {
                        // 超时：先取消异步请求并同步等待 GetOverlappedResult 确认内核不再写入 readBuf/ovMem
                        kernel32Extra.CancelIoEx(handle, ovMem)
                        kernel32Extra.GetOverlappedResult(handle, ovMem, transferred, true)
                        return null
                    }
                }
                if (!kernel32Extra.GetOverlappedResult(handle, ovMem, transferred, true)) {
                    disconnected = true
                    return null
                }
                val n = transferred.value
                if (n <= 0) return null
                return readBuf.getByteArray(0, minOf(n, inputLen))
            } finally {
                java.lang.ref.Reference.reachabilityFence(readBuf)
                java.lang.ref.Reference.reachabilityFence(ovMem)
            }
        }

        fun flush(idleMs: Int) {
            for (i in 0 until 24) {
                if (read(idleMs) == null) break
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            if (handle != WinBase.INVALID_HANDLE_VALUE) Kernel32.INSTANCE.CloseHandle(handle)
            if (evt != WinBase.INVALID_HANDLE_VALUE) Kernel32.INSTANCE.CloseHandle(evt)
            runCatching { ovMem.close() }
            runCatching { readBuf.close() }
        }
    }
}
