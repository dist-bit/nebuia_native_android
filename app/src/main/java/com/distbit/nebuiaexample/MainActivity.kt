package com.distbit.nebuiaexample

import android.app.Activity

import android.os.Bundle
import android.util.Log
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
        // SET TEMPORAL CODE FROM IP REQUEST
        //nebuIA.setClientURI("http://192.168.1.111:8080/api/v1/services")
        nebuIA.setTemporalCode("000000")
        // SET CLIENT REPORT
        nebuIA.setReport("63f25f106e1a6b708a57596f")

        val spoof = findViewById<Button>(R.id.spoofing)
        val id = findViewById<Button>(R.id.id_scanner)
        val fingerprints = findViewById<Button>(R.id.fingerprints)
        val address = findViewById<Button>(R.id.address)
        val video = findViewById<Button>(R.id.evidence)
        val sign = findViewById<Button>(R.id.sign_document)

        id.setOnClickListener {
            nebuIA.documentDetection(onIDComplete = {}, onIDError = {})
        }

        video.setOnClickListener {
            nebuIA.recordActivity(
                arrayListOf(), nameFromId = false, onRecordComplete = {})

        }

        fingerprints.setOnClickListener {
            nebuIA.fingerDetection(1, false, onSkip = {

            }, onFingerDetectionComplete = { fingers, fingers2, fingers3, fingers4 ->
                // LOGIC HERE
            }, onSkipWithFingers = { fingers, fingers2, fingers3, fingers4 ->

            })
        }

        spoof.setOnClickListener {
            nebuIA.faceLiveDetection(true, onFaceComplete = {})
        }

        address.setOnClickListener {
            nebuIA.captureAddress(onAddressComplete = {})
        }

        val signature = nebuIA.NebuIASigner()
        sign.setOnClickListener {
            signature.getSignTemplates(onDocumentTemplates = {
                Log.i("SIGGGG", it.last().toString())
                signature.signDocument(it.last().id, "miguel@distbit.io", mutableMapOf(
                    "key_value_one" to "VALOR1",
                    "rfc_user" to "RFC USERR",
                ), onDocumentSign = {
                    Log.i("SIGGGG", "SIGNED")
                })

            })

        }
    }

}