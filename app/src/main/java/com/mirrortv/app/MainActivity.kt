
package com.mirrortv.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.ScrollView
import android.net.wifi.WifiManager
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.SocketTimeoutException

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(30, 30, 30, 30)
        layout.setBackgroundColor(Color.rgb(12, 25, 48))

        val title = TextView(this)
        title.text = "Mirror TV"
        title.textSize = 34f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "البحث عن أجهزة التلفزيون والريسيفرات"
        subtitle.textSize = 18f
        subtitle.setTextColor(Color.LTGRAY)
        subtitle.gravity = Gravity.CENTER

        val searchButton = Button(this)
        searchButton.text = "البحث عن الأجهزة"

        val status = TextView(this)
        status.text = "اضغط للبحث عن الأجهزة المتصلة بالشبكة"
        status.textSize = 16f
        status.setTextColor(Color.WHITE)
        status.gravity = Gravity.CENTER

        val results = TextView(this)
        results.textSize = 16f
        results.setTextColor(Color.WHITE)
        results.gravity = Gravity.CENTER

        val scroll = ScrollView(this)
        scroll.addView(results)

        searchButton.setOnClickListener {
            searchButton.isEnabled = false
            status.text = "جارٍ البحث عن الأجهزة..."
            results.text = ""

            Thread {
                val devices = discoverDevices()

                handler.post {
                    searchButton.isEnabled = true

                    if (devices.isEmpty()) {
                        status.text = "لم يتم العثور على أجهزة DLNA"
                    } else {
                        status.text = "تم العثور على ${devices.size} جهاز"

                        results.text = devices.joinToString(
                            separator = "\n\n"
                        )
                    }
                }
            }.start()
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(searchButton)
        layout.addView(status)
        layout.addView(scroll)

        setContentView(layout)
    }

    private fun discoverDevices(): List<String> {
        val devices = LinkedHashSet<String>()

        val wifi = applicationContext
            .getSystemService(WIFI_SERVICE) as WifiManager

        val lock = wifi.createMulticastLock("MirrorTVLock")
        lock.setReferenceCounted(false)

        var socket: DatagramSocket? = null

        try {
            lock.acquire()

            socket = DatagramSocket()
            socket.soTimeout = 700

            val address = InetAddress.getByName("239.255.255.250")

            val message = (
                "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: ssdp:all\r\n\r\n"
            )

            val data = message.toByteArray()

            val packet = DatagramPacket(
                data,
                data.size,
                address,
                1900
            )

            socket.send(packet)

            val endTime = System.currentTimeMillis() + 6000

            while (System.currentTimeMillis() < endTime) {
                try {
                    val buffer = ByteArray(4096)
                    val response = DatagramPacket(
                        buffer,
                        buffer.size
                    )

                    socket.receive(response)

                    val text = String(
                        response.data,
                        0,
                        response.length
                    )

                    val location = text
                        .lineSequence()
                        .firstOrNull {
                            it.startsWith(
                                "LOCATION:",
                                ignoreCase = true
                            )
                        }
                        ?.substringAfter(":")
                        ?.trim()

                    val server = text
                        .lineSequence()
                        .firstOrNull {
                            it.startsWith(
                                "SERVER:",
                                ignoreCase = true
                            )
                        }
                        ?.substringAfter(":")
                        ?.trim()

                    if (location != null) {
                        devices.add(
                            "الجهاز: ${server ?: "DLNA"}\nالعنوان: $location"
                        )
                    }

                } catch (_: SocketTimeoutException) {
                    // Continue searching
                }
            }

        } catch (_: Exception) {
            // Search failed
        } finally {
            socket?.close()

            if (lock.isHeld) {
                lock.release()
            }
        }

        return devices.toList()
    }
}
