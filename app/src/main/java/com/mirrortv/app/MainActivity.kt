
package com.mirrortv.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.app.AlertDialog
import android.net.wifi.WifiManager
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.SocketTimeoutException

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var results: TextView
    private lateinit var searchButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(28, 35, 28, 35)
        layout.setBackgroundColor(Color.rgb(12, 35, 60))

        val title = TextView(this)
        title.text = "Mirror TV"
        title.textSize = 34f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "عكس شاشة الهاتف على التلفاز"
        subtitle.textSize = 18f
        subtitle.setTextColor(Color.LTGRAY)
        subtitle.gravity = Gravity.CENTER

        searchButton = Button(this)
        searchButton.text = "البحث عن التلفاز"

        val manualButton = Button(this)
        manualButton.text = "إضافة جهاز يدوياً"

        val mirrorButton = Button(this)
        mirrorButton.text = "بدء عكس الشاشة"

        status = TextView(this)
        status.text = "اختر طريقة الاتصال بالتلفاز"
        status.textSize = 17f
        status.setTextColor(Color.WHITE)
        status.gravity = Gravity.CENTER

        results = TextView(this)
        results.textSize = 15f
        results.setTextColor(Color.WHITE)
        results.gravity = Gravity.CENTER

        val scroll = ScrollView(this)
        scroll.addView(results)

        searchButton.setOnClickListener {
            searchButton.isEnabled = false
            status.text = "جارٍ البحث عن أجهزة DLNA..."
            results.text = ""

            Thread {
                val found = discoverDevices()

                runOnUiThread {
                    searchButton.isEnabled = true

                    if (found.isEmpty()) {
                        status.text = "لم يتم العثور على أجهزة DLNA"
                    } else {
                        status.text = "الأجهزة التي تم العثور عليها:"
                        results.text = found.joinToString("\n\n")
                    }
                }
            }.start()
        }

        manualButton.setOnClickListener {
            val input = EditText(this)
            input.hint = "مثال: 192.168.1.100"

            AlertDialog.Builder(this)
                .setTitle("إضافة جهاز يدوياً")
                .setMessage("أدخل عنوان IP للتلفاز")
                .setView(input)
                .setPositiveButton("إضافة") { _, _ ->
                    val ip = input.text.toString().trim()

                    if (isValidIp(ip)) {
                        status.text = "تمت إضافة العنوان"
                        results.text =
                            "عنوان الجهاز: $ip\nلم يتم الاتصال به بعد."
                    } else {
                        status.text = "عنوان IP غير صحيح"
                    }
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        mirrorButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("عكس الشاشة")
                .setMessage(
                    "عكس الشاشة يحتاج إلى جهاز استقبال متوافق."
                )
                .setPositiveButton("حسناً", null)
                .show()
        }

        layout.addView(title)
        layout.addView(subtitle)
        addSpace(layout, 30)
        layout.addView(searchButton)
        layout.addView(manualButton)
        layout.addView(mirrorButton)
        addSpace(layout, 20)
        layout.addView(status)
        layout.addView(scroll)

        setContentView(layout)
    }

    private fun addSpace(layout: LinearLayout, height: Int) {
        val space = TextView(this)
        layout.addView(
            space,
            LinearLayout.LayoutParams(1, height)
        )
    }

    private fun isValidIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false

        return parts.all {
            val number = it.toIntOrNull()
            number != null && number in 0..255
        }
    }

    private fun discoverDevices(): List<String> {
        val found = LinkedHashMap<String, String>()

        val wifi = applicationContext
            .getSystemService(WIFI_SERVICE) as WifiManager

        val lock = wifi.createMulticastLock("MirrorTVLock")
        lock.setReferenceCounted(false)

        var socket: DatagramSocket? = null

        try {
            lock.acquire()

            socket = DatagramSocket()
            socket.soTimeout = 500

            val address =
                InetAddress.getByName("239.255.255.250")

            val targets = listOf(
                "ssdp:all",
                "upnp:rootdevice",
                "urn:schemas-upnp-org:device:MediaRenderer:1"
            )

            for (target in targets) {
                val message =
                    "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: $target\r\n\r\n"

                val data = message.toByteArray()

                val packet = DatagramPacket(
                    data,
                    data.size,
                    address,
                    1900
                )

                socket.send(packet)
            }

            val endTime =
                System.currentTimeMillis() + 8000

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

                    fun header(name: String): String? {
                        return text.lineSequence()
                            .firstOrNull {
                                it.startsWith(
                                    "$name:",
                                    ignoreCase = true
                                )
                            }
                            ?.substringAfter(":")
                            ?.trim()
                    }

                    val location = header("LOCATION")
                    val server = header("SERVER")
                    val st = header("ST")
                    val usn = header("USN")
                    val ip =
                        response.address.hostAddress ?: "غير معروف"

                    val key = location ?: usn ?: ip

                    found[key] =
                        "معلومات الجهاز: ${server ?: "DLNA / UPnP"}\n" +
                        "عنوان الشبكة: $ip\n" +
                        "نوع الجهاز: ${st ?: "غير محدد"}\n" +
                        "العنوان: ${location ?: "غير متوفر"}"

                } catch (_: SocketTimeoutException) {
                    // استمرار البحث
                }
            }

        } catch (_: Exception) {
            // فشل البحث
        } finally {
            socket?.close()

            if (lock.isHeld) {
                lock.release()
            }
        }

        return found.values.toList()
    }
}
