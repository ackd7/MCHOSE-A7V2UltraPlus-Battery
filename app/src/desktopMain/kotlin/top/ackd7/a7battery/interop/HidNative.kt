package top.ackd7.a7battery.interop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.Collections

/**
 * Windows HID 原生接口封装（JNA）。
 *
 * 对应原 C# `Interop/HidNative.cs`：通过 SetupAPI 枚举 HID 设备，
 * 通过 hid.dll 读写 Feature Report，通过 kernel32 做重叠 I/O 读写。
 */
object HidNative {

    private const val HID_GUID = "4D1E55B2-F16F-11CF-88CB-001111000030"

    // ---------- 常量 ----------

    private const val GENERIC_READ = 0x80000000L
    private const val GENERIC_WRITE = 0x40000000L
    private const val FILE_SHARE_RW = 0x00000003
    private const val OPEN_EXISTING = 3
    private const val FILE_FLAG_OVERLAPPED = 0x40000000
    private const val ERROR_IO_PENDING = 997
    private const val DIGCF_PRESENT = 0x00000002
    private const val DIGCF_DEVICEINTERFACE = 0x00000010

    private const val HIDP_STATUS_SUCCESS = 0x00110000

    // ---------- 结构体 ----------

    @Structure.FieldOrder("cbSize", "InterfaceClassGuid", "Flags", "Reserved")
    class SP_DEVICE_INTERFACE_DATA : Structure() {
        @JvmField var cbSize = 0
        @JvmField var InterfaceClassGuid: Guid.ByValue = Guid.ByValue()
        @JvmField var Flags = 0
        @JvmField var Reserved: Pointer? = null
    }

    @Structure.FieldOrder("Size", "VendorID", "ProductID", "VersionNumber")
    class HIDD_ATTRIBUTES : Structure() {
        @JvmField var Size = 0
        @JvmField var VendorID = 0
        @JvmField var ProductID = 0
        @JvmField var VersionNumber = 0
    }

    @Structure.FieldOrder(
        "Usage", "UsagePage", "InputReportByteLength", "OutputReportByteLength",
        "FeatureReportByteLength", "Reserved", "NumberLinkCollectionNodes",
        "NumberInputButtonCaps", "NumberInputValueCaps", "NumberInputDataIndices",
        "NumberOutputButtonCaps", "NumberOutputValueCaps", "NumberOutputDataIndices",
        "NumberFeatureButtonCaps", "NumberFeatureValueCaps", "NumberFeatureDataIndices",
    )
    class HIDP_CAPS : Structure() {
        @JvmField var Usage = 0
        @JvmField var UsagePage = 0
        @JvmField var InputReportByteLength = 0
        @JvmField var OutputReportByteLength = 0
        @JvmField var FeatureReportByteLength = 0
        @JvmField var Reserved = ShortArray(17)
        @JvmField var NumberLinkCollectionNodes = 0
        @JvmField var NumberInputButtonCaps = 0
        @JvmField var NumberInputValueCaps = 0
        @JvmField var NumberInputDataIndices = 0
        @JvmField var NumberOutputButtonCaps = 0
        @JvmField var NumberOutputValueCaps = 0
        @JvmField var NumberOutputDataIndices = 0
        @JvmField var NumberFeatureButtonCaps = 0
        @JvmField var NumberFeatureValueCaps = 0
        @JvmField var NumberFeatureDataIndices = 0
    }

    // ---------- JNA 接口 ----------

    private interface HidDll : Library {
        fun HidD_GetAttributes(HidDeviceObject: WinNT.HANDLE, Attributes: HIDD_ATTRIBUTES): Boolean
        fun HidD_GetPreparsedData(HidDeviceObject: WinNT.HANDLE, PreparsedData: PointerByReference): Boolean
        fun HidD_FreePreparsedData(PreparsedData: Pointer): Boolean
        fun HidP_GetCaps(PreparsedData: Pointer, Capabilities: HIDP_CAPS): Int
        fun HidD_GetProductString(HidDeviceObject: WinNT.HANDLE, Buffer: CharArray, BufferLength: Int): Boolean
        fun HidD_GetManufacturerString(HidDeviceObject: WinNT.HANDLE, Buffer: CharArray, BufferLength: Int): Boolean
        fun HidD_SetOutputReport(HidDeviceObject: WinNT.HANDLE, ReportBuffer: ByteArray, ReportBufferLength: Int): Boolean
        fun HidD_GetFeature(HidDeviceObject: WinNT.HANDLE, ReportBuffer: ByteArray, ReportBufferLength: Int): Boolean
        fun HidD_SetFeature(HidDeviceObject: WinNT.HANDLE, ReportBuffer: ByteArray, ReportBufferLength: Int): Boolean
    }

    private interface SetupApi : Library {
        fun SetupDiGetClassDevs(
            ClassGuid: Guid.ByReference?,
            Enumerator: Pointer?,
            hwndParent: WinDef.HWND?,
            Flags: Int,
        ): WinNT.HANDLE

        fun SetupDiEnumDeviceInterfaces(
            DeviceInfoSet: WinNT.HANDLE,
            DeviceInfoData: Pointer?,
            InterfaceClassGuid: Guid.ByReference,
            MemberIndex: Int,
            DeviceInterfaceData: SP_DEVICE_INTERFACE_DATA,
        ): Boolean

        fun SetupDiGetDeviceInterfaceDetail(
            DeviceInfoSet: WinNT.HANDLE,
            DeviceInterfaceData: SP_DEVICE_INTERFACE_DATA,
            DeviceInterfaceDetailData: Pointer?,
            DeviceInterfaceDetailDataSize: Int,
            RequiredSize: IntByReference,
            DeviceInfoData: Pointer?,
        ): Boolean

        fun SetupDiDestroyDeviceInfoList(DeviceInfoSet: WinNT.HANDLE): Boolean
    }

    private interface Kernel32 : Library {
        fun CreateFile(
            lpFileName: String,
            dwDesiredAccess: Int,
            dwShareMode: Int,
            lpSecurityAttributes: Pointer?,
            dwCreationDisposition: Int,
            dwFlagsAndAttributes: Int,
            hTemplateFile: WinNT.HANDLE?,
        ): WinNT.HANDLE

        fun CloseHandle(hObject: WinNT.HANDLE): Boolean
        fun WriteFile(hFile: WinNT.HANDLE, lpBuffer: ByteArray, nNumberOfBytesToWrite: Int, lpNumberOfBytesWritten: IntByReference, lpOverlapped: WinBase.OVERLAPPED?): Boolean
        fun ReadFile(hFile: WinNT.HANDLE, lpBuffer: ByteArray, nNumberOfBytesToRead: Int, lpNumberOfBytesRead: IntByReference, lpOverlapped: WinBase.OVERLAPPED?): Boolean
        fun GetOverlappedResult(hFile: WinNT.HANDLE, lpOverlapped: WinBase.OVERLAPPED, lpNumberOfBytesTransferred: IntByReference, bWait: Boolean): Boolean
        fun CancelIoEx(hFile: WinNT.HANDLE, lpOverlapped: WinBase.OVERLAPPED?): Boolean
        fun CreateEvent(lpEventAttributes: Pointer?, bManualReset: Boolean, bInitialState: Boolean, lpName: String?): WinNT.HANDLE
        fun ResetEvent(hEvent: WinNT.HANDLE): Boolean
        fun WaitForSingleObject(hHandle: WinNT.HANDLE, dwMilliseconds: Int): Int
    }

    private val hid: HidDll = Native.load("hid", HidDll::class.java)
    private val setupapi: SetupApi = Native.load("setupapi", SetupApi::class.java)
    private val kernel32: Kernel32 = Native.load("kernel32", Kernel32::class.java)

    // ---------- 枚举 ----------

    fun enumerate(): List<HidDeviceInfo> {
        val result = ArrayList<HidDeviceInfo>()
        val hidGuid = Guid.GUID.fromString(HID_GUID)
        val guidRef = Guid.ByReference(hidGuid)

        val hDevInfo = setupapi.SetupDiGetClassDevs(guidRef, null, null, DIGCF_PRESENT or DIGCF_DEVICEINTERFACE)
        if (hDevInfo == WinBase.INVALID_HANDLE_VALUE) return result

        try {
            val did = SP_DEVICE_INTERFACE_DATA()
            did.cbSize = did.size()
            did.write()

            var idx = 0
            while (setupapi.SetupDiEnumDeviceInterfaces(hDevInfo, null, guidRef, idx, did)) {
                did.read()
                idx++

                val required = IntByReference()
                setupapi.SetupDiGetDeviceInterfaceDetail(hDevInfo, did, null, 0, required, null)
                if (required.value == 0) continue

                val size = required.value
                val buf = com.sun.jna.Memory(size.toLong())
                // cbSize 字段（SP_DEVICE_INTERFACE_DETAIL_DATA 首字段）
                buf.setInt(0, if (Native.POINTER_SIZE == 8) 8 else 6)
                val required2 = IntByReference()
                if (!setupapi.SetupDiGetDeviceInterfaceDetail(hDevInfo, did, buf, size, required2, null)) continue

                // 路径位于偏移 4 之后（cbSize 字段为 4 字节）
                val path = buf.getWideString(4) ?: continue

                val h = kernel32.CreateFile(path, 0, FILE_SHARE_RW, null, OPEN_EXISTING, 0, null)
                if (h == WinBase.INVALID_HANDLE_VALUE) continue
                try {
                    val attrs = HIDD_ATTRIBUTES()
                    attrs.Size = attrs.size()
                    attrs.write()
                    if (!hid.HidD_GetAttributes(h, attrs)) continue
                    attrs.read()

                    val pp = PointerByReference()
                    val caps = HIDP_CAPS()
                    caps.write()
                    var ok = hid.HidD_GetPreparsedData(h, pp)
                    if (ok) ok = hid.HidP_GetCaps(pp.value, caps) == HIDP_STATUS_SUCCESS
                    caps.read()
                    val preparsed = pp.value
                    if (preparsed != null) hid.HidD_FreePreparsedData(preparsed)
                    if (!ok) continue

                    val productName = readString(h) { b -> hid.HidD_GetProductString(h, b, b.size) }
                    val manufacturer = readString(h) { b -> hid.HidD_GetManufacturerString(h, b, b.size) }

                    result.add(
                        HidDeviceInfo(
                            vendorId = attrs.VendorID and 0xFFFF,
                            productId = attrs.ProductID and 0xFFFF,
                            usagePage = caps.UsagePage and 0xFFFF,
                            usage = caps.Usage and 0xFFFF,
                            inputReportLength = caps.InputReportByteLength and 0xFFFF,
                            outputReportLength = caps.OutputReportByteLength and 0xFFFF,
                            featureReportLength = caps.FeatureReportByteLength and 0xFFFF,
                            path = path,
                            productName = productName,
                            manufacturer = manufacturer,
                        )
                    )
                } finally {
                    kernel32.CloseHandle(h)
                }
            }
        } finally {
            setupapi.SetupDiDestroyDeviceInfoList(hDevInfo)
        }
        return result
    }

    private fun readString(h: WinNT.HANDLE, getter: (CharArray) -> Boolean): String {
        val buf = CharArray(256)
        return if (getter(buf)) String(buf).trimEnd('\u0000') else ""
    }

    // ---------- 会话（对应原 HidSession） ----------

    class HidSession(path: String, inputLen: Int, readOnly: Boolean = false) : AutoCloseable {
        private val handle: WinNT.HANDLE
        private val evt: WinNT.HANDLE
        private val inputLen: Int = maxOf(inputLen, 1)
        private var closed = false

        init {
            val access = if (readOnly) GENERIC_READ.toInt() else (GENERIC_READ or GENERIC_WRITE).toInt()
            handle = kernel32.CreateFile(path, access, FILE_SHARE_RW, null, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, null)
            if (handle == WinBase.INVALID_HANDLE_VALUE) {
                throw IllegalStateException("无法打开 HID 设备，错误码 " + Native.getLastError())
            }
            evt = kernel32.CreateEvent(null, true, false, null)
        }

        fun setFeature(report: ByteArray): Boolean = hid.HidD_SetFeature(handle, report, report.size)
        fun getFeature(report: ByteArray): Boolean = hid.HidD_GetFeature(handle, report, report.size)
        fun setOutputReport(report: ByteArray): Boolean = hid.HidD_SetOutputReport(handle, report, report.size)

        fun read(timeoutMs: Int): ByteArray? {
            if (closed) return null
            kernel32.ResetEvent(evt)
            val ov = WinBase.OVERLAPPED()
            ov.hEvent = evt
            val buf = ByteArray(inputLen)
            val read = IntByReference()
            val ok = kernel32.ReadFile(handle, buf, inputLen, read, ov)
            if (!ok) {
                val err = Native.getLastError()
                if (err != ERROR_IO_PENDING) return null
                if (kernel32.WaitForSingleObject(evt, timeoutMs) != 0) {
                    kernel32.CancelIoEx(handle, ov)
                    kernel32.GetOverlappedResult(handle, ov, IntByReference(), true)
                    return null
                }
                if (!kernel32.GetOverlappedResult(handle, ov, read, false)) return null
            }
            if (read.value <= 0) return null
            return buf.copyOf(read.value)
        }

        fun flush(idleMs: Int) {
            for (i in 0 until 24) {
                if (read(idleMs) == null) break
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            if (evt != null && evt != WinBase.INVALID_HANDLE_VALUE) kernel32.CloseHandle(evt)
            if (handle != null && handle != WinBase.INVALID_HANDLE_VALUE) kernel32.CloseHandle(handle)
        }
    }
}
