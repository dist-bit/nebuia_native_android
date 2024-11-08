package com.distbit.nebuiaexample

import android.app.Activity

import android.os.Bundle
import android.widget.Button
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.model.ui.Theme

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // INIT NEBUIA OBJECT

        val nebuIA = NebuIA(this)
        // SET THEME
        NebuIA.theme = Theme(
            //primaryColor = 0xff2886de.toInt(),
            //secondaryColor = 0xffffffff.toInt(),
            primaryTextButtonColor = 0xffffffff.toInt(),
            secondaryTextButtonColor = 0xff904afa.toInt(),
            //boldFont = ResourcesCompat.getFont(this, R.font.gilroy_bold),
            //normalFont = ResourcesCompat.getFont(this, R.font.gilroy_medium),
            //thinFont = ResourcesCompat.getFont(this, R.font.gilroy_light)
        )

        val video = findViewById<Button>(R.id.evidence)

        video.setOnClickListener {
            nebuIA.recordActivity(
                arrayListOf("Hola", "soy miguel"), nameFromId = false, onRecordComplete = {})

        }


    }

}