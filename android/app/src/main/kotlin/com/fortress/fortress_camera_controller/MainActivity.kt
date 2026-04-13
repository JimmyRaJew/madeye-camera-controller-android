package com.fortress.fortress_camera_controller

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    private val usbChannel = "fortress_camera_controller/usb"
    private val transportChannel = "fortress_camera_controller/transport"
    private val usbPermissionAction = "fortress_camera_controller.USB_PERMISSION"
    private val rndisChannel = "fortress_camera_controller/rndis"
    private var pendingUsbPermissionResult: MethodChannel.Result? = null
    private var pendingUsbPermissionDeviceName: String? = null

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != usbPermissionAction) {
                return
            }
            val deviceName = intent.getStringExtra("deviceName")
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val result = pendingUsbPermissionResult
            if (result != null && deviceName == pendingUsbPermissionDeviceName) {
                if (granted) {
                    result.success("Permission granted for $deviceName")
                } else {
                    result.error("usb_permission_denied", "USB permission denied for $deviceName", null)
                }
                pendingUsbPermissionResult = null
                pendingUsbPermissionDeviceName = null
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        ContextCompat.registerReceiver(
            this,
            usbPermissionReceiver,
            IntentFilter(usbPermissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, usbChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "listUsbDevices" -> result.success(listUsbDevices())
                "requestUsbPermission" -> requestUsbPermission(call.argument<String>("deviceName"), result)
                else -> result.notImplemented()
            }
        }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, transportChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "listNetworks" -> result.success(listNetworks())
                else -> result.notImplemented()
            }
        }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, rndisChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "probeRndis" -> probeRndis(call.argument<String>("deviceName"), result)
                else -> result.notImplemented()
            }
        }
    }

    override fun onDestroy() {
        runCatching {
            unregisterReceiver(usbPermissionReceiver)
        }
        pendingUsbPermissionResult = null
        pendingUsbPermissionDeviceName = null
        super.onDestroy()
    }

    private fun listUsbDevices(): List<Map<String, Any?>> {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.map { device ->
            mapOf(
                "name" to device.deviceName,
                "vendorId" to device.vendorId,
                "productId" to device.productId,
                "deviceClass" to device.deviceClass,
                "deviceSubclass" to device.deviceSubclass,
                "deviceProtocol" to device.deviceProtocol,
                "manufacturerName" to device.manufacturerName,
                "productName" to device.productName,
                "version" to device.version,
                "hasPermission" to usbManager.hasPermission(device),
                "interfaces" to List(device.interfaceCount) { index ->
                    val usbInterface = device.getInterface(index)
                    mapOf(
                        "id" to usbInterface.id,
                        "interfaceClass" to usbInterface.interfaceClass,
                        "interfaceSubclass" to usbInterface.interfaceSubclass,
                        "interfaceProtocol" to usbInterface.interfaceProtocol,
                        "endpoints" to List(usbInterface.endpointCount) { endpointIndex ->
                            val endpoint = usbInterface.getEndpoint(endpointIndex)
                            mapOf(
                                "address" to endpoint.address,
                                "attributes" to endpoint.attributes,
                                "maxPacketSize" to endpoint.maxPacketSize,
                                "interval" to endpoint.interval,
                            )
                        },
                    )
                },
            )
        }
    }

    private fun requestUsbPermission(deviceName: String?, result: MethodChannel.Result) {
        if (deviceName.isNullOrBlank()) {
            result.error("usb_permission_missing", "Missing USB device name", null)
            return
        }
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList[deviceName]
        if (device == null) {
            result.error("usb_permission_missing_device", "USB device not found: $deviceName", null)
            return
        }
        if (usbManager.hasPermission(device)) {
            result.success("Permission already granted for $deviceName")
            return
        }
        if (pendingUsbPermissionResult != null) {
            result.error("usb_permission_pending", "A USB permission request is already in progress", null)
            return
        }

        pendingUsbPermissionResult = result
        pendingUsbPermissionDeviceName = deviceName
        val intent = Intent(usbPermissionAction).apply {
            setPackage(packageName)
            putExtra("deviceName", deviceName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
        usbManager.requestPermission(device, pendingIntent)
    }

    private fun listNetworks(): List<Map<String, Any?>> {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.allNetworks.mapNotNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            val props = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
            mapOf(
                "interfaceName" to (props.interfaceName ?: "-"),
                "networkName" to network.toString(),
                "isVpn" to caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                "isCellular" to caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                "isWifi" to caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                "isEthernet" to caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                "isUsb" to props.interfaceName?.startsWith("usb", ignoreCase = true),
                "addresses" to props.linkAddresses.map { it.address.hostAddress ?: "-" },
                "routes" to props.routes.map { route ->
                    "${route.destination} via ${route.gateway?.hostAddress ?: "-"}"
                },
                "dnsServers" to props.dnsServers.map { it.hostAddress ?: "-" },
            )
        }
    }

    private fun probeRndis(deviceName: String?, result: MethodChannel.Result) {
        if (deviceName.isNullOrBlank()) {
            result.error("rndis_missing", "Missing USB device name", null)
            return
        }
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList[deviceName]
        if (device == null) {
            result.error("rndis_missing_device", "USB device not found: $deviceName", null)
            return
        }
        if (!usbManager.hasPermission(device)) {
            result.error("rndis_no_permission", "USB permission has not been granted for $deviceName", null)
            return
        }

        val commInterface = findRndisCommunicationInterface(device)
        val dataInterface = findRndisDataInterface(device)
        if (commInterface == null || dataInterface == null) {
            result.error("rndis_missing_interfaces", "Could not locate RNDIS communication/data interfaces", null)
            return
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            result.error("rndis_open_failed", "Unable to open USB device", null)
            return
        }

        try {
            if (!connection.claimInterface(commInterface, true)) {
                result.error("rndis_claim_failed", "Unable to claim communication interface ${commInterface.id}", null)
                return
            }
            if (!connection.claimInterface(dataInterface, true)) {
                result.error("rndis_claim_failed", "Unable to claim data interface ${dataInterface.id}", null)
                return
            }

            val interruptEndpoint = findInterruptInEndpoint(commInterface)
            val bulkIn = findBulkInEndpoint(dataInterface)
            val bulkOut = findBulkOutEndpoint(dataInterface)
            if (interruptEndpoint == null || bulkIn == null || bulkOut == null) {
                result.error("rndis_missing_endpoints", "Could not locate RNDIS endpoints", null)
                return
            }

            val initializeRequestId = 1
            val initializeRequest = buildRndisInitializeMessage(initializeRequestId, 16384)
            val initTransferred = sendRndisControlMessage(connection, commInterface.id, initializeRequest)
            if (initTransferred < 0) {
                result.error("rndis_init_send_failed", "Failed to send RNDIS initialize message", null)
                return
            }

            waitForRndiSNotification(connection, interruptEndpoint)
            val initResponseBytes = readRndisResponse(connection, commInterface.id)
            val initResponse = parseRndisInitializeComplete(initResponseBytes)

            val supportedOids = queryRndisBytes(connection, commInterface.id, 0x00010101, 256)
            val currentAddress = queryRndisBytes(connection, commInterface.id, 0x01010102, 16)
            val maxFrameSize = queryRndisBytes(connection, commInterface.id, 0x00010106, 8)
            val linkStatus = queryRndisBytes(connection, commInterface.id, 0x00010114, 8)

            val packetFilter = 0x0000000f
            val setFilterResponse = setRndisValue(connection, commInterface.id, 0x0001010e, writeInt32LE(packetFilter))
            val setFilterOk = parseRndisSetComplete(setFilterResponse)

            result.success(
                mapOf(
                    "deviceName" to device.deviceName,
                    "communicationInterface" to commInterface.id,
                    "dataInterface" to dataInterface.id,
                    "interruptEndpoint" to interruptEndpoint.address,
                    "bulkInEndpoint" to bulkIn.address,
                    "bulkOutEndpoint" to bulkOut.address,
                    "initialized" to initResponse.success,
                    "medium" to initResponse.medium,
                    "maxTransferSize" to initResponse.maxTransferSize,
                    "packetAlignmentFactor" to initResponse.packetAlignmentFactor,
                    "currentAddress" to bytesToHex(currentAddress),
                    "supportedOids" to bytesToHex(supportedOids),
                    "maxFrameSize" to bytesToHex(maxFrameSize),
                    "linkStatus" to bytesToHex(linkStatus),
                    "packetFilterSet" to setFilterOk,
                )
            )
        } catch (error: Exception) {
            result.error("rndis_probe_failed", error.message, null)
        } finally {
            runCatching { connection.close() }
        }
    }

    private data class RndisInitializeResult(
        val success: Boolean,
        val medium: Int,
        val maxTransferSize: Int,
        val packetAlignmentFactor: Int,
    )

    private fun findRndisCommunicationInterface(device: UsbDevice): android.hardware.usb.UsbInterface? {
        for (index in 0 until device.interfaceCount) {
            val intf = device.getInterface(index)
            val hasInterrupt = findInterruptInEndpoint(intf) != null
            if (intf.interfaceClass == UsbConstants.USB_CLASS_COMM && hasInterrupt) {
                return intf
            }
        }
        return null
    }

    private fun findRndisDataInterface(device: UsbDevice): android.hardware.usb.UsbInterface? {
        for (index in 0 until device.interfaceCount) {
            val intf = device.getInterface(index)
            val hasBulkIn = findBulkInEndpoint(intf) != null
            val hasBulkOut = findBulkOutEndpoint(intf) != null
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA && hasBulkIn && hasBulkOut) {
                return intf
            }
        }
        return null
    }

    private fun findInterruptInEndpoint(intf: android.hardware.usb.UsbInterface): android.hardware.usb.UsbEndpoint? {
        for (index in 0 until intf.endpointCount) {
            val endpoint = intf.getEndpoint(index)
            if (endpoint.direction == UsbConstants.USB_DIR_IN && endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                return endpoint
            }
        }
        return null
    }

    private fun findBulkInEndpoint(intf: android.hardware.usb.UsbInterface): android.hardware.usb.UsbEndpoint? {
        for (index in 0 until intf.endpointCount) {
            val endpoint = intf.getEndpoint(index)
            if (endpoint.direction == UsbConstants.USB_DIR_IN && endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                return endpoint
            }
        }
        return null
    }

    private fun findBulkOutEndpoint(intf: android.hardware.usb.UsbInterface): android.hardware.usb.UsbEndpoint? {
        for (index in 0 until intf.endpointCount) {
            val endpoint = intf.getEndpoint(index)
            if (endpoint.direction == UsbConstants.USB_DIR_OUT && endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                return endpoint
            }
        }
        return null
    }

    private fun sendRndisControlMessage(
        connection: UsbDeviceConnection,
        interfaceId: Int,
        message: ByteArray,
    ): Int {
        return connection.controlTransfer(
            0x21,
            0x00,
            0,
            interfaceId,
            message,
            message.size,
            2000,
        )
    }

    private fun readRndisResponse(connection: UsbDeviceConnection, interfaceId: Int): ByteArray {
        val buffer = ByteArray(4096)
        val bytesRead = connection.controlTransfer(
            0xA1,
            0x01,
            0,
            interfaceId,
            buffer,
            buffer.size,
            2000,
        )
        if (bytesRead <= 0) {
            throw IllegalStateException("No RNDIS response received")
        }
        return buffer.copyOf(bytesRead)
    }

    private fun waitForRndiSNotification(connection: UsbDeviceConnection, endpoint: android.hardware.usb.UsbEndpoint) {
        val buffer = ByteArray(8)
        repeat(5) {
            val bytesRead = connection.bulkTransfer(endpoint, buffer, buffer.size, 400)
            if (bytesRead >= 8) {
                return
            }
        }
    }

    private fun buildRndisInitializeMessage(requestId: Int, maxTransferSize: Int): ByteArray {
        val buffer = ByteArray(24)
        writeInt32LE(buffer, 0, 0x00000002)
        writeInt32LE(buffer, 4, 24)
        writeInt32LE(buffer, 8, requestId)
        writeInt32LE(buffer, 12, 1)
        writeInt32LE(buffer, 16, 0)
        writeInt32LE(buffer, 20, maxTransferSize)
        return buffer
    }

    private fun queryRndisBytes(connection: UsbDeviceConnection, interfaceId: Int, oid: Int, responseSize: Int): ByteArray {
        val requestId = nextRequestId()
        val request = ByteArray(28)
        writeInt32LE(request, 0, 0x00000004)
        writeInt32LE(request, 4, 28)
        writeInt32LE(request, 8, requestId)
        writeInt32LE(request, 12, oid)
        writeInt32LE(request, 16, 0)
        writeInt32LE(request, 20, 0)
        writeInt32LE(request, 24, 0)
        val sent = sendRndisControlMessage(connection, interfaceId, request)
        if (sent < 0) {
            throw IllegalStateException("RNDIS query send failed for oid 0x${oid.toString(16)}")
        }
        return readRndisQueryComplete(connection, interfaceId, requestId, responseSize)
    }

    private fun setRndisValue(connection: UsbDeviceConnection, interfaceId: Int, oid: Int, value: ByteArray): ByteArray {
        val requestId = nextRequestId()
        val request = ByteArray(24 + value.size)
        writeInt32LE(request, 0, 0x00000005)
        writeInt32LE(request, 4, 24 + value.size)
        writeInt32LE(request, 8, requestId)
        writeInt32LE(request, 12, oid)
        writeInt32LE(request, 16, value.size)
        writeInt32LE(request, 20, 20)
        System.arraycopy(value, 0, request, 24, value.size)
        val sent = sendRndisControlMessage(connection, interfaceId, request)
        if (sent < 0) {
            throw IllegalStateException("RNDIS set send failed for oid 0x${oid.toString(16)}")
        }
        return readRndisSetComplete(connection, interfaceId, requestId)
    }

    private fun readRndisQueryComplete(connection: UsbDeviceConnection, interfaceId: Int, requestId: Int, responseSize: Int): ByteArray {
        val response = readRndisResponse(connection, interfaceId)
        val messageType = readInt32LE(response, 0)
        if (messageType != 0x80000004) {
            throw IllegalStateException("Unexpected RNDIS query response 0x${messageType.toString(16)}")
        }
        val returnedRequestId = readInt32LE(response, 8)
        if (returnedRequestId != requestId) {
            throw IllegalStateException("RNDIS query request id mismatch")
        }
        val status = readInt32LE(response, 12)
        if (status != 0x00000000) {
            throw IllegalStateException("RNDIS query failed status 0x${status.toString(16)}")
        }
        val length = readInt32LE(response, 16)
        val offset = readInt32LE(response, 20)
        val start = 24 + offset
        val end = (start + length).coerceAtMost(response.size)
        return response.copyOfRange(start, end)
    }

    private fun readRndisSetComplete(connection: UsbDeviceConnection, interfaceId: Int, requestId: Int): ByteArray {
        val response = readRndisResponse(connection, interfaceId)
        val messageType = readInt32LE(response, 0)
        if (messageType != 0x80000005) {
            throw IllegalStateException("Unexpected RNDIS set response 0x${messageType.toString(16)}")
        }
        val returnedRequestId = readInt32LE(response, 8)
        if (returnedRequestId != requestId) {
            throw IllegalStateException("RNDIS set request id mismatch")
        }
        val status = readInt32LE(response, 12)
        if (status != 0x00000000) {
            throw IllegalStateException("RNDIS set failed status 0x${status.toString(16)}")
        }
        return response
    }

    private fun parseRndisInitializeComplete(response: ByteArray): RndisInitializeResult {
        val messageType = readInt32LE(response, 0)
        if (messageType != 0x80000002) {
            throw IllegalStateException("Unexpected RNDIS initialize response 0x${messageType.toString(16)}")
        }
        val status = readInt32LE(response, 12)
        val medium = readInt32LE(response, 28)
        val maxTransferSize = readInt32LE(response, 36)
        val packetAlignmentFactor = readInt32LE(response, 40)
        return RndisInitializeResult(
            success = status == 0x00000000,
            medium = medium,
            maxTransferSize = maxTransferSize,
            packetAlignmentFactor = packetAlignmentFactor,
        )
    }

    private fun parseRndisSetComplete(response: ByteArray): Boolean {
        val messageType = readInt32LE(response, 0)
        if (messageType != 0x80000005) {
            return false
        }
        return readInt32LE(response, 12) == 0x00000000
    }

    private fun writeInt32LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun readInt32LE(buffer: ByteArray, offset: Int): Int {
        return (buffer[offset].toInt() and 0xFF) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private var rndisRequestId = 1
    private fun nextRequestId(): Int = rndisRequestId++
}
