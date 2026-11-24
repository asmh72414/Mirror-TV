
package com.mirrortv.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {

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
        subtitle.text = "عكس شاشة هاتفك على التلفزيون"
        subtitle.textSize = 18f
        subtitle.setTextColor(Color.LTGRAY)
        subtitle.gravity = Gravity.CENTER

        val searchButton = Button(this)
        searchButton.text = "البحث عن الأجهزة"

        searchButton.setOnClickListener {
            Toast.makeText(
                this,
                "سيتم تجهيز البحث عن أجهزة DLNA",
                Toast.LENGTH_SHORT
            ).show()
        }

        val mirrorButton = Button(this)
        mirrorButton.text = "بدء عكس الشاشة"

        mirrorButton.setOnClickListener {
            Toast.makeText(
                this,
                "سيتم تجهيز خاصية انعكاس الشاشة",
                Toast.LENGTH_SHORT
            ).show()
        }

        layout.addView(title)
        layout.addView(subtitle)

        layout.addView(searchButton)
        layout.addView(mirrorButton)

        setContentView(layout)
    }
}
