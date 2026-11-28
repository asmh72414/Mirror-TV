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
    private lateinit var searchButton: Button
    private lateinit var mirrorButton: Button
    private lateinit var stopButton: Button
    private var selectedLocation: String? = null
    private lateinit var projectionManager: MediaProjectionManager

    data class DlnaDevice(val location: String, val ip: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 40, 28, 40)
            setBackgroundColor(0xFF0C233C.toInt())
        }

        val title = TextView(this).apply {
            text = "Mirror TV"
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "اتصل الهاتف والرسيفر بنفس شبكة Wi-Fi أو بنقطة اتصال الهاتف"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        searchButton = Button(this).apply {
            text = "البحث عن التلفاز"
            setOnClickListener { searchForTv() }
        }

        mirrorButton = Button(this).apply {
            text = "بدء عكس الشاشة"
            isEnabled = false
            setOnClickListener {
                if (selectedLocation != null) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE)
                }
            }
        }

        stopButton = Button(this).apply {
            text = "إيقاف عكس الشاشة"
            setOnClickListener {
                stopService(Intent(this@MainActivity, MirroringService::class.java))
                status.text = "تم إيقاف العكس"
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(searchButton)
        layout.addView(mirrorButton)
        layout.addView(stopButton)
        setContentView(layout)
    }

    private fun searchForTv() {
        searchButton.isEnabled = false
        mirrorButton.isEnabled = false
        selectedLocation = null
        status.text = "جارٍ البحث عن رسيفر DLNA..."

        Thread {
            val devices = discoverRenderers()
            runOnUiThread {
                searchButton.isEnabled = true
                if (devices.isEmpty()) {
                    status.text = "لم يتم العثور على رسيفر DLNA. تأكد من اتصال الجهازين بنفس الشبكة."
                } else {
                    val labels = devices.map { "رسيفر DLNA — ${it.ip}" }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("اختر الرسيفر")
                        .setItems(labels) { _, index ->
                            selectedLocation = devices[index].location
                            mirrorButton.isEnabled = true
                            status.text = "تم اختيار الرسيفر: ${devices[index].ip}. اضغط بدء عكس الشاشة."
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            }
        }.start()
    }

    private fun discoverRenderers(): List<DlnaDevice> {
        val found = LinkedHashMap<String, DlnaDevice>()
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("MirrorTVDiscovery")
        lock.setReferenceCounted(false)
        var socket: DatagramSocket? = null

        try {
            lock.acquire()
            socket = DatagramSocket().apply { soTimeout = 500 }
            val multicast = InetAddress.getByName("239.255.255.250")
            val targets = listOf(
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "urn:schemas-upnp-org:service:AVTransport:1",
                "upnp:rootdevice"
            )
            for (target in targets) {
                val message = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: $target\r\n\r\n"
                val bytes = message.toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(bytes, bytes.size, multicast, 1900))
            }

            val deadline = System.currentTimeMillis() + 6000L
            while (System.currentTimeMillis() < deadline) {
                try {
                    val buffer = ByteArray(4096)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val location = response.lineSequence()
                        .firstOrNull { it.startsWith("LOCATION:", true) }
                        ?.substringAfter(":")?.trim()
                    if (!location.isNullOrBlank()) {
                        val ip = packet.address.hostAddress ?: "غير معروف"
                        found[location] = DlnaDevice(location, ip)
                    }
                } catch (_: SocketTimeoutException) {
                    // Keep listening until the discovery window ends.
                }
            }
        } catch (_: Exception) {
            // The UI reports that no renderer was found.
        } finally {
            socket?.close()
            if (lock.isHeld) lock.release()
        }
        return found.values.toList()
    }

    @Deprecated("Deprecated by Android; retained for compatibility with the project's current setup")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null && selectedLocation != null) {
                val serviceIntent = Intent(this, MirroringService::class.java).apply {
                    putExtra(MirroringService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(MirroringService.EXTRA_RESULT_DATA, data)
                    putExtra(MirroringService.EXTRA_DEVICE_LOCATION, selectedLocation)
                }
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
                else startService(serviceIntent)
                status.text = "جارٍ تشغيل بث الشاشة إلى الرسيفر..."
            } else {
                status.text = "تم إلغاء إذن التقاط الشاشة"
            }
        }
    }

    companion object {
        private const val REQUEST_CAPTURE = 501
    }
}
