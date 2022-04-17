# nebuia

[![N|Nebula](https://i.ibb.co/DC46xJv/banner-min.png)](https://nebuia.com)

[![dist-bit](https://circleci.com/gh/dist-bit/nebuia_native_android.svg?style=svg)](https://app.circleci.com/pipelines/github/dist-bit/nebuia_native_android) [![](https://jitpack.io/v/dist-bit/nebuia_native_android.svg)](https://jitpack.io/#dist-bit/nebuia_native_android)

### Introduction

NebuIA Native Core is library for nebuia services integration. NebuIA use platform native channels and run over native
languages Objective-C for IOS and Kotlin/Java for Android

NebuIA is an Deep Learning library and supports
  - Face Detection and auto crop
  - Proof of Live in real time
    - Darkened images
    - Office fluorescent lighting
    - Office with lighting turned off, illuminated only by natural sunlight
    - Supported attacks
      - Print
      - 2D Mask
      - 2 Replay
  - Document Detection in real time
    - Faster and best accuracy than conventional ID Detector (95%)
    - Support auto cropping
  - Proof of Address
    - Automatic extract address
  - Tipical OTP Verifications for Email and Phone Number SMS
    - Time-Based One-Time Password (TOTP)
    - HMAC-Based One-Time Password (HOTP)
    * [RFC 4226: "RFC 4226 HOTP: An HMAC-Based One-Time Password Algorithm"](https://www.ietf.org/rfc/rfc4226.txt)
    * [RFC 6238: "TOTP: Time-Based One-Time Password Algorithm"](https://tools.ietf.org/html/rfc6238)


### Requirements
 - Kotlin 1.5.10

If your app does not have Kotlin support add this to your build.gradle level project
```groovy
dependencies {
    classpath "com.android.tools.build:gradle:4.1.3"
    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
}
```
In your build.gradle level app
```groovy
plugins {
    id 'com.android.library'
    id 'kotlin-android'
}
```

### Integration
Add module android from this repo
[Import module in android](https://developer.android.com/studio/projects/add-app-module#ImportAModule)

Add implementation to gradle.build level app
```groovy
implementation project(path: ':nebuia')
```

Sources file with your private and public key
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="nebuia_public_key">S5PS103-RHWM8E8-*******-7GQ0NX1</string>
    <string name="nebuia_secret_key">c96d9080-c479-4439-****-b1523c2e0af4</string>
</resources>
```

Sample Integration
```kotlin

private var nebuIA: NebuIA? = null

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // set nebuIA instance
    nebuIA = NebuIA(this)
    // set temporal code
    nebuIA!!.setTemporalCode("52882602")

    setNebuIAColors()
    setUpActionNebuIA()
}

private fun setNebuIATheme() {
	NebuIA.theme = Theme(
        primaryColor = 0xff904afa.toInt(),
        secondaryColor = 0xffffffff.toInt(),
        primaryTextButtonColor = 0xffffffff.toInt(),
        secondaryTextButtonColor = 0xff904afa.toInt(),
        boldFont = ResourcesCompat.getFont(this, R.font.gilroy_bold),
        normalFont = ResourcesCompat.getFont(this, R.font.gilroy_medium),
	    thinFont = ResourcesCompat.getFont(this, R.font.gilroy_light)
	)
}

private fun setUpActionNebuIA() {

    // set buttons listeners
    getFace!!.setOnClickListener {
        execute {
            // get face image
            nebuIA!!.getFaceImage {
                if(it != null)
                    faceUser!!.setImageBitmap(it)
            }
        }
    }

    getDocument!!.setOnClickListener {
        execute {
            // get id image [FRONT, BACK]
            nebuIA!!.getIDImage(Side.FRONT) {
                if(it != null) {
                    // set bitmap to image view
                }
            }
        }
    }

    getDocumentData!!.setOnClickListener {
        execute {
            // get report data
            nebuIA!!.getIDData{
                Log.i("nebuia_demo", it.toString())
            }
        }
    }

    createReport!!.setOnClickListener {
        execute {
            // generate new report id
            nebuIA!!.createReport {
                Log.i("nebuia_demo", it)
            }
        }
    }

    proofLive!!.setOnClickListener {
        execute {
            // start face live activity
            nebuIA!!.faceLiveDetection {
                // call another activity
                Log.i("nebuia_demo", "proof of live complete")
            }
        }
    }

    fingerPrint!!.setOnClickListener {
        execute {
            // start fingerprint scanner and return image from 4 fingers
            nebuIA!!.fingerDetection { index, middle, ring, little: Bitmap ->
               // generate WSQ File from finger image
                    nebuIA!!.generateWSQFingerprint(index) { file: ByteArray ->
                        // return file in byte array
                    }
            }
        }
    }

    scanner!!.setOnClickListener {
        execute {
            // start ID scanner
            nebuIA!!.documentDetection {
            // call another activity
            Log.i("nebuia_demo", "complete id document")
            }
        }
    }

    recorder!!.setOnClickListener {
        execute {
            // start video record activity and return video file
            nebuIA!!.recordActivity("declaro que adquiri un crédito por 100 pesos mxn") { video ->
                Log.i("nebuia_demo", "video record complete")
            }
    }
 }

    // catch NebuIA errors
    private fun execute(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            errorDescription!!.text = e.message
        } catch (e: ReportException) {
            errorDescription!!.text = e.message
        } catch (e: CodeException) {
            errorDescription!!.text = e.message
        }
    }
}


```


### Notes

##### Report identifier

You can use existing report id.
```kotlin
nebuIA!!.setReport("60cfc........13c1ca32")
```

or create new
```kotlin
nebuIA!!.createReport {
	Log.i("detector", it)
}
```

##### Video recording

On record end, the activity return video file.
```kotlin
nebuIA!!.recordActivity("declaro que adquiri un crédito por 100 pesos mxn") { video ->
	Log.i("nebuia_demo", "video record complete")
}
```

##### Fingerprint

On fingerprint scanner endyou can pass any finger image to get WSQ File.
```kotlin
nebuIA!!.generateWSQFingerprint(index) { file: ByteArray ->

}
```


### Images
