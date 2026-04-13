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
                        "alternateSetting" to usbInterface.alternateSetting,
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
            result.error("usb_eth_missing", "Missing USB device name", null)
            return
        }
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList[deviceName]
        if (device == null) {
            result.error("usb_eth_missing_device", "USB device not found: $deviceName", null)
            return
        }
        if (!usbManager.hasPermission(device)) {
            result.error("usb_eth_no_permission", "USB permission has not been granted for $deviceName", null)
            return
        }

        val commInterfaces = findRndisCommunicationInterfaces(device)
        val dataInterfaces = findRndisDataInterfaces(device)
        if (commInterfaces.isEmpty() || dataInterfaces.isEmpty()) {
            result.error("usb_eth_missing_interfaces", "Could not locate USB Ethernet communication/data interfaces", null)
            return
        }

        val attempts = mutableListOf<String>()
        for (commInterface in commInterfaces) {
            for (dataInterface in dataInterfaces) {
                val label =
                    "comm ${commInterface.id} class ${commInterface.interfaceClass}/${commInterface.interfaceSubclass}/${commInterface.interfaceProtocol}, " +
                    "data ${dataInterface.id} class ${dataInterface.interfaceClass}/${dataInterface.interfaceSubclass}/${dataInterface.interfaceProtocol}"
                val probe = runCatching {
                    probeRndisPair(device, commInterface, dataInterface)
                }
                if (probe.isSuccess) {
                    val details = probe.getOrThrow().toMutableMap()
                    details["attempts"] = attempts + "$label -> success"
                    result.success(details)
                    return
                }
                attempts.add("$label -> ${probe.exceptionOrNull()?.message ?: "unknown error"}")
            }
        }

        result.error(
            "usb_eth_probe_failed",
            buildString {
                append("All USB Ethernet-style interface pairs failed.\n")
                append(attempts.joinToString("\n"))
            },
            null,
        )
    }

    private data class RndisInitializeResult(
        val success: Boolean,
        val medium: Int,
        val maxTransferSize: Int,
        val packetAlignmentFactor: Int,
    )

    private fun findRndisCommunicationInterfaces(device: UsbDevice): List<android.hardware.usb.UsbInterface> {
        val candidates = mutableListOf<android.hardware.usb.UsbInterface>()
        for (index in 0 until device.interfaceCount) {
            val intf = device.getInterface(index)
            val hasInterrupt = findInterruptInEndpoint(intf) != null
            if (intf.interfaceClass == UsbConstants.USB_CLASS_COMM && hasInterrupt) {
                candidates.add(intf)
            }
        }
        return candidates.sortedWith(compareBy({ it.interfaceSubclass }, { it.interfaceProtocol }))
    }

    private fun findRndisDataInterfaces(device: UsbDevice): List<android.hardware.usb.UsbInterface> {
        val candidates = mutableListOf<android.hardware.usb.UsbInterface>()
        for (index in 0 until device.interfaceCount) {
            val intf = device.getInterface(index)
            val hasBulkIn = findBulkInEndpoint(intf) != null
            val hasBulkOut = findBulkOutEndpoint(intf) != null
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA && hasBulkIn && hasBulkOut) {
                candidates.add(intf)
            }
        }
        return candidates.sortedWith(compareBy({ it.interfaceSubclass }, { it.interfaceProtocol }))
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

    private fun setInterfaceAlternateSetting(
        connection: UsbDeviceConnection,
        intf: android.hardware.usb.UsbInterface,
    ) {
        val requested = connection.controlTransfer(
            0x01,
            0x0B,
            intf.alternateSetting,
            intf.id,
            null,
            0,
            2000,
        )
        if (requested < 0) {
            throw IllegalStateException(
                "Unable to select alternate setting ${intf.alternateSetting} for interface ${intf.id}",
            )
        }
    }

    private fun probeRndisPair(
        device: UsbDevice,
        commInterface: android.hardware.usb.UsbInterface,
        dataInterface: android.hardware.usb.UsbInterface,
    ): Map<String, Any?> {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val connection = usbManager.openDevice(device)
            ?: throw IllegalStateException("Unable to open USB device")

        try {
            if (!connection.claimInterface(commInterface, true)) {
                throw IllegalStateException("Unable to claim communication interface ${commInterface.id}")
            }
            if (!connection.claimInterface(dataInterface, true)) {
                throw IllegalStateException("Unable to claim data interface ${dataInterface.id}")
            }
            setInterfaceAlternateSetting(connection, commInterface)
            setInterfaceAlternateSetting(connection, dataInterface)

            val interruptEndpoint = findInterruptInEndpoint(commInterface)
                ?: throw IllegalStateException("Communication interface ${commInterface.id} has no interrupt endpoint")
            val bulkIn = findBulkInEndpoint(dataInterface)
                ?: throw IllegalStateException("Data interface ${dataInterface.id} has no bulk IN endpoint")
            val bulkOut = findBulkOutEndpoint(dataInterface)
                ?: throw IllegalStateException("Data interface ${dataInterface.id} has no bulk OUT endpoint")

            val attempts = mutableListOf<String>()

            val rndisResult = runCatching {
                val initializeRequestId = 1
                val initializeRequest = buildRndisInitializeMessage(initializeRequestId, 16384)
                val initTransferred = sendRndisControlMessage(connection, commInterface.id, initializeRequest)
                if (initTransferred < 0) {
                    throw IllegalStateException("Failed to send USB Ethernet initialize message on interface ${commInterface.id}")
                }

                waitForRndiSNotification(connection, interruptEndpoint)
                val initResponseBytes = readRndisResponse(connection, commInterface.id)
                val initResponse = parseRndisInitializeComplete(initResponseBytes)

                val supportedOids = queryRndisBytes(connection, commInterface.id, 0x00010101, 256)
                val currentAddress = queryRndisBytes(connection, commInterface.id, 0x01010102, 16)
                val maxFrameSize = queryRndisBytes(connection, commInterface.id, 0x00010106, 8)
                val linkStatus = queryRndisBytes(connection, commInterface.id, 0x00010114, 8)

                val packetFilter = 0x0000000f
                val packetFilterBytes = ByteArray(4)
                writeInt32LE(packetFilterBytes, 0, packetFilter)
                val setFilterResponse = setRndisValue(connection, commInterface.id, 0x0001010e, packetFilterBytes)
                val setFilterOk = parseRndisSetComplete(setFilterResponse)

                mapOf(
                    "transport" to "RNDIS",
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
            }

            if (rndisResult.isSuccess) {
                return rndisResult.getOrThrow()
            }
            attempts.add("RNDIS init -> ${rndisResult.exceptionOrNull()?.message ?: "unknown error"}")

            val rawResult = runCatching {
                probeUsbEthernetRaw(connection, commInterface, dataInterface, bulkIn, bulkOut)
            }
            if (rawResult.isSuccess) {
                val rawDetails = rawResult.getOrThrow().toMutableMap()
                rawDetails["transport"] = "USB Ethernet raw"
                rawDetails["attempts"] = attempts
                return rawDetails
            }
            attempts.add("Raw Ethernet -> ${rawResult.exceptionOrNull()?.message ?: "unknown error"}")

            throw IllegalStateException(attempts.joinToString("; "))
        } finally {
            runCatching { connection.close() }
        }
    }

    private fun probeUsbEthernetRaw(
        connection: UsbDeviceConnection,
        commInterface: android.hardware.usb.UsbInterface,
        dataInterface: android.hardware.usb.UsbInterface,
        bulkIn: android.hardware.usb.UsbEndpoint,
        bulkOut: android.hardware.usb.UsbEndpoint,
    ): Map<String, Any?> {
        setUsbEthernetPacketFilter(connection, commInterface.id, 0x0000000f)
        val sourceMac = byteArrayOf(0x02, 0x12, 0x34, 0x56, 0x78, 0x9a.toByte())
        val sourceIp = byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 250.toByte())
        val targetIp = byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 111.toByte())
        val request = buildArpRequestFrame(sourceMac, sourceIp, targetIp)
        val sent = connection.bulkTransfer(bulkOut, request, request.size, 2000)
        if (sent <= 0) {
            throw IllegalStateException("Failed to send raw Ethernet ARP request")
        }

        val buffer = ByteArray(4096)
        repeat(10) {
            val bytesRead = connection.bulkTransfer(bulkIn, buffer, buffer.size, 500)
            if (bytesRead > 0) {
                val reply = parseEthernetArpReply(buffer, bytesRead, sourceMac, targetIp)
                if (reply != null) {
                    return mapOf(
                        "deviceName" to dataInterface.id,
                        "sourceMac" to macToString(sourceMac),
                        "sourceIp" to ipToString(sourceIp),
                        "cameraMac" to reply,
                        "bulkInEndpoint" to bulkIn.address,
                        "bulkOutEndpoint" to bulkOut.address,
                    )
                }
            }
        }
        throw IllegalStateException("No ARP reply received from camera")
    }

    private fun setUsbEthernetPacketFilter(
        connection: UsbDeviceConnection,
        interfaceId: Int,
        packetFilter: Int,
    ) {
        val requested = connection.controlTransfer(
            0x21,
            0x43,
            packetFilter,
            interfaceId,
            null,
            0,
            2000,
        )
        if (requested < 0) {
            throw IllegalStateException("Unable to set USB Ethernet packet filter on interface $interfaceId")
        }
    }

    private fun buildArpRequestFrame(sourceMac: ByteArray, sourceIp: ByteArray, targetIp: ByteArray): ByteArray {
        val frame = ByteArray(60)
        for (index in 0 until 6) {
            frame[index] = 0xFF.toByte()
        }
        System.arraycopy(sourceMac, 0, frame, 6, 6)
        frame[12] = 0x08
        frame[13] = 0x06
        writeUInt16BE(frame, 14, 1)
        writeUInt16BE(frame, 16, 0x0800)
        frame[18] = 6
        frame[19] = 4
        writeUInt16BE(frame, 20, 1)
        System.arraycopy(sourceMac, 0, frame, 22, 6)
        System.arraycopy(sourceIp, 0, frame, 28, 4)
        for (index in 32 until 38) {
            frame[index] = 0x00
        }
        System.arraycopy(targetIp, 0, frame, 38, 4)
        return frame
    }

    private fun parseEthernetArpReply(
        buffer: ByteArray,
        bytesRead: Int,
        sourceMac: ByteArray,
        targetIp: ByteArray,
    ): String? {
        if (bytesRead < 42) {
            return null
        }
        val ethertype = readUInt16BE(buffer, 12)
        if (ethertype != 0x0806) {
            return null
        }
        val opcode = readUInt16BE(buffer, 20)
        if (opcode != 2) {
            return null
        }
        val senderMac = buffer.copyOfRange(22, 28)
        val senderIp = buffer.copyOfRange(28, 32)
        val targetMac = buffer.copyOfRange(32, 38)
        val replyTargetIp = buffer.copyOfRange(38, 42)
        if (!replyTargetIp.contentEquals(targetIp)) {
            return null
        }
        if (!targetMac.contentEquals(sourceMac)) {
            return null
        }
        return macToString(senderMac) + " from " + ipToString(senderIp)
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
            throw IllegalStateException("No USB Ethernet response received")
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
            throw IllegalStateException("USB Ethernet query send failed for oid 0x${oid.toString(16)}")
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
            throw IllegalStateException("USB Ethernet set send failed for oid 0x${oid.toString(16)}")
        }
        return readRndisSetComplete(connection, interfaceId, requestId)
    }

    private fun readRndisQueryComplete(connection: UsbDeviceConnection, interfaceId: Int, requestId: Int, responseSize: Int): ByteArray {
        val response = readRndisResponse(connection, interfaceId)
        val messageType = readInt32LE(response, 0)
        if (messageType != 0x80000004.toInt()) {
            throw IllegalStateException("Unexpected USB Ethernet query response 0x${messageType.toString(16)}")
        }
        val returnedRequestId = readInt32LE(response, 8)
        if (returnedRequestId != requestId) {
            throw IllegalStateException("USB Ethernet query request id mismatch")
        }
        val status = readInt32LE(response, 12)
        if (status != 0x00000000) {
            throw IllegalStateException("USB Ethernet query failed status 0x${status.toString(16)}")
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
        if (messageType != 0x80000005.toInt()) {
            throw IllegalStateException("Unexpected USB Ethernet set response 0x${messageType.toString(16)}")
        }
        val returnedRequestId = readInt32LE(response, 8)
        if (returnedRequestId != requestId) {
            throw IllegalStateException("USB Ethernet set request id mismatch")
        }
        val status = readInt32LE(response, 12)
        if (status != 0x00000000) {
            throw IllegalStateException("USB Ethernet set failed status 0x${status.toString(16)}")
        }
        return response
    }

    private fun parseRndisInitializeComplete(response: ByteArray): RndisInitializeResult {
        val messageType = readInt32LE(response, 0)
        if (messageType != 0x80000002.toInt()) {
            throw IllegalStateException("Unexpected USB Ethernet initialize response 0x${messageType.toString(16)}")
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
        if (messageType != 0x80000005.toInt()) {
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

    private fun writeUInt16BE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    private fun readUInt16BE(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
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

    private fun macToString(bytes: ByteArray): String {
        return bytes.joinToString(separator = ":") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private fun ipToString(bytes: ByteArray): String {
        return bytes.joinToString(separator = ".") { byte -> (byte.toInt() and 0xFF).toString() }
    }

    private var rndisRequestId = 1
    private fun nextRequestId(): Int = rndisRequestId++
}
