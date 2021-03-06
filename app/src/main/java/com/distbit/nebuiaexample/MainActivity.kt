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
            primaryColor = 0xff2886de.toInt(),
            secondaryColor = 0xffffffff.toInt(),
            primaryTextButtonColor = 0xffffffff.toInt(),
            secondaryTextButtonColor = 0xff904afa.toInt(),
            //boldFont = ResourcesCompat.getFont(this, R.font.gilroy_bold),
            //normalFont = ResourcesCompat.getFont(this, R.font.gilroy_medium),
            //thinFont = ResourcesCompat.getFont(this, R.font.gilroy_light)
        )
        // SET TEMPORAL CODE FROM IP REQUEST
        nebuIA.setClientURI("https://api.nebuia.com/api/v1/services")
        nebuIA.setTemporalCode("000000")
        // SET CLIENT REPORT
        nebuIA.setReport("62422330ad9791096fd9c4fe")
        //nebuIA.setReport("60ef54921bc1004d709a1a05")
        // CALL NEBUIA METHOD
        /*nebuIA.fingerDetection(0, false, 4.4, onSkip = {

        }, onFingerDetectionComplete = { fingers, fingers2, fingers3, fingers4 ->
            // LOGIC HERE
        }, onSkipWithFingers = { fingers, fingers2, fingers3, fingers4 ->

        }) */

        val action = findViewById<Button>(R.id.action)

        action.setOnClickListener {
            nebuIA.faceLiveDetection(false, onFaceComplete = {})
        }

    }

}