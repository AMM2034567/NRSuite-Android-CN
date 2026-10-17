package com.swp81x.nrsuite.core.flasher

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException

/**
 * [FlasherTransport] backed directly by usb-serial-for-android.
 */
class UsbSerialFlasherTransport(
    private val usbManager: UsbManager,
    private val driver: UsbSerialDriver,
    private val baudRate: Int = 115200,
) : FlasherTransport {

    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null

    override val isOpen: Boolean
        get() = port?.isOpen == true

    override fun open() {
        if (isOpen) return
        val usbDevice = driver.device
        if (!usbManager.hasPermission(usbDevice)) {
            throw IOException("尚未获取 ${usbDevice.deviceName} 的 USB 权限")
        }

        val openedConnection = usbManager.openDevice(usbDevice)
            ?: throw IOException("无法打开 USB 设备 ${usbDevice.deviceName}")
        val openedPort = driver.ports.firstOrNull()
            ?: run {
                openedConnection.close()
                throw IOException("在 ${usbDevice.deviceName} 上未找到串口")
            }

        try {
            openedPort.open(openedConnection)
            openedPort.setParameters(
                baudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE,
            )
        } catch (t: Throwable) {
            runCatching { openedPort.close() }
            runCatching { openedConnection.close() }
            throw if (t is IOException) t else IOException("无法配置串口参数", t)
        }

        connection = openedConnection
        port = openedPort
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val activePort = port ?: throw IOException("串口尚未打开")
        return activePort.read(buffer, timeoutMs)
    }

    override fun write(data: ByteArray, timeoutMs: Int) {
        val activePort = port ?: throw IOException("串口尚未打开")
        activePort.write(data, timeoutMs)
    }

    override fun flushInput() {
        runCatching { port?.purgeHwBuffers(true, false) }
    }

    override fun setDtr(value: Boolean) {
        runCatching { port?.setDTR(value) }
    }

    override fun setRts(value: Boolean) {
        runCatching { port?.setRTS(value) }
    }

    override fun close() {
        runCatching { port?.close() }
        runCatching { connection?.close() }
        port = null
        connection = null
    }
}
