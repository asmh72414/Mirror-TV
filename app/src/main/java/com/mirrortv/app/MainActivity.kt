
package com.mirrortv.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var mirrorButton: Button
    private lateinit var projectionManager: MediaProjectionManager
    private var selectedLocation: String? = null

    data class Device(
        val location: String,
        val ip: String
    )

    companion object {
        private const val REQUEST_CAPTURE = 501
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager = getSystemService(
            MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 40, 28, 40)
            setBackgroundColor(0xFF0C233C.toInt())
        }

        val title = TextView(this).apply {
            text = "Mirror TV"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }

        status = TextView(this).apply {
            text = "جاهز لعكس الشاشة"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }

        mirrorButton = Button(this).apply {
            text = "اعكس الشاشة"
            setOnClickListener {
                findDevices()
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(mirrorButton)

        setContentView(layout)
    }

    private fun findDevices() {
        mirrorButton.isEnabled = false
        status.text = "جارٍ البحث عن الرسيفر..."

        Thread {
            val devices = discoverDevices()

            runOnUiThread {
                mirrorButton.isEnabled = true

                if (devices.isEmpty()) {
                    status.text = "لم يتم العثور على رسيفر"
                    return@runOnUiThread
                }

                val names = devices.map {
                    it.ip
                }.toTypedArray()

                status.text = "اختر الرسيفر"

                AlertDialog.Builder(this)
                    .setTitle("الأجهزة المتاحة")
                    .setItems(names) { _, index ->
                        selectedLocation =
                            devices[index].location

                        requestCapture()
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
        }.start()
    }

    private fun discoverDevices(): List<Device> {
        val found = LinkedHashMap<String, Device>()

        val wifi = applicationContext.getSystemService(
            WIFI_SERVICE
        ) as WifiManager

        val lock = wifi.createMulticastLock("MirrorTV")
        lock.setReferenceCounted(false)

        var socket: DatagramSocket? = null

        try {
            lock.acquire()

            socket = DatagramSocket()
            socket.soTimeout = 500

            val address =
                InetAddress.getByName("239.255.255.250")

            val message =
                "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"

            val bytes = message.toByteArray()

            socket.send(
                DatagramPacket(
                    bytes,
                    bytes.size,
                    address,
                    1900
                )
            )

            val deadline =
                System.currentTimeMillis() + 7000

            while (System.currentTimeMillis() < deadline) {
                try {
                    val buffer = ByteArray(4096)
                    val packet =
                        DatagramPacket(buffer, buffer.size)

                    socket.receive(packet)

                    val response = String(
                        packet.data,
                        0,
                        packet.length
                    )

                    val location = response
                        .lineSequence()
                        .firstOrNull {
                            it.startsWith("LOCATION:", true)
                        }
                        ?.substringAfter(":")
                        ?.trim()

                    if (!location.isNullOrBlank()) {
                        val ip =
                            packet.address.hostAddress
                                ?: "Unknown"

                        found[location] = Device(location, ip)
                    }

                } catch (_: SocketTimeoutException) {
                    // Continue searching
                }
            }

        } catch (_: Exception) {
            // Search failed
        } finally {
            socket?.close()
            if (lock.isHeld) lock.release()
        }

        return found.values.toList()
    }

    private fun requestCapture() {
        if (selectedLocation == null) return

        @Suppress("DEPRECATION")
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_CAPTURE
        )
    }

    @Deprecated("Deprecated")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CAPTURE) return

        if (resultCode != RESULT_OK || data == null) {
            status.text = "تم إلغاء مشاركة الشاشة"
            return
        }

        val serviceIntent = Intent(
            this,
            MirroringService::class.java
        ).apply {
            putExtra(
                MirroringService.EXTRA_RESULT_CODE,
                resultCode
            )
            putExtra(
                MirroringService.EXTRA_RESULT_DATA,
                data
            )
            putExtra(
                MirroringService.EXTRA_DEVICE_LOCATION,
                selectedLocation
            )
        }

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        status.text = "جارٍ بدء عكس الشاشة..."
    }
}
