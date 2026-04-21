# Nebuia Native Android - Integration Guide

[![N|Nebula](https://i.ibb.co/DC46xJv/banner-min.png)](https://nebuia.com)

[![dist-bit](https://circleci.com/gh/dist-bit/nebuia_native_android.svg?style=svg)](https://app.circleci.com/pipelines/github/dist-bit/nebuia_native_android) [![](https://jitpack.io/v/dist-bit/nebuia_native_android.svg)](https://jitpack.io/#dist-bit/nebuia_native_android)

## Installation

### Step 1: Add JitPack repository

Add JitPack repository to your root `build.gradle` at the end of repositories:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Or if you're using `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### Step 2: Add the dependency

Add the dependency to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'com.github.dist-bit:nebuia_native_android:0.0.96'
}
```

### Step 3: Add API keys

Create a `nebuia.xml` file in your app's `res/values` folder with your API credentials:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="nebuia_public_key">YOUR_PUBLIC_KEY</string>
    <string name="nebuia_secret_key">YOUR_SECRET_KEY</string>
</resources>
```

## Basic Integration

### Initialize NebuIA

```kotlin
import com.distbit.nebuia_plugin.NebuIA

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize NebuIA
        val nebuIA = NebuIA(this)
        
        // Configure the client URI
        nebuIA.setClientURI("https://your-api-endpoint.com/api/v1/services")
        
        // Set temporal code
        nebuIA.setTemporalCode("000000")
        
        // Set report ID if needed
        nebuIA.setReport("your-report-id")
    }
}
```

### Customize Theme (Optional)

```kotlin
import com.distbit.nebuia_plugin.model.ui.Theme

NebuIA.theme = Theme(
    primaryColor = 0xff2886de.toInt(),
    secondaryColor = 0xffffffff.toInt(),
    primaryTextButtonColor = 0xffffffff.toInt(),
    secondaryTextButtonColor = 0xff904afa.toInt()
    // You can also set custom fonts:
    // boldFont = ResourcesCompat.getFont(this, R.font.your_bold_font),
    // normalFont = ResourcesCompat.getFont(this, R.font.your_normal_font),
    // thinFont = ResourcesCompat.getFont(this, R.font.your_thin_font)
)
```

## Available Features

### Face Liveness Detection

```kotlin
nebuIA.faceLiveDetection(
    showIntro = true,
    onFaceComplete = {
        // Handle successful face detection
    }
)
```

### Document Scanner

```kotlin
nebuIA.documentDetection(
    onIDComplete = {
        // Handle successful document scan
    },
    onIDError = {
        // Handle error
    }
)
```

### Fingerprint Detection

```kotlin
nebuIA.fingerDetection(
    numberOfFingers = 1,
    showTutorial = false,
    onSkip = {
        // Handle skip action
    },
    onFingerDetectionComplete = { finger1, finger2, finger3, finger4 ->
        // Handle successful fingerprint detection
    },
    onSkipWithFingers = { finger1, finger2, finger3, finger4 ->
        // Handle skip with partial fingerprints
    }
)
```

### Address Capture

```kotlin
nebuIA.captureAddress(
    onAddressComplete = { address ->
        // Handle captured address
    }
)
```

### Evidence Recording

```kotlin
nebuIA.recordActivity(
    questions = arrayListOf(),
    nameFromId = false,
    onRecordComplete = {
        // Handle recording completion
    }
)
```

### Document Signing

```kotlin
val signer = nebuIA.NebuIASigner()

// Get available templates
signer.getSignTemplates(
    onDocumentTemplates = { templates ->
        // Select a template and sign
        signer.signDocument(
            templateId = templates.first().id,
            email = "user@example.com",
            dynamicData = mutableMapOf(
                "field_key" to "field_value"
            ),
            onDocumentSign = {
                // Handle successful signature
            }
        )
    }
)
```

## Report Management

### Create a new report

```kotlin
nebuIA.createReport { reportId ->
    // Handle new report ID
}
```

### Use existing report

```kotlin
nebuIA.setReport("existing-report-id")
```

### Get report data

```kotlin
nebuIA.getIDData { data ->
    // Handle report data
}
```

### Get captured images

```kotlin
// Get face image
nebuIA.getFaceImage { bitmap ->
    // Handle face bitmap
}

// Get document image
nebuIA.getIDImage(Side.FRONT) { bitmap ->
    // Handle document bitmap
}
```

## Requirements

- Android SDK 24 or higher
- Kotlin 1.8 or higher
- AndroidX libraries

## Permissions

Add these permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## ProGuard Rules

If you're using ProGuard, add these rules to your `proguard-rules.pro`:

```proguard
-keep class com.distbit.nebuia_plugin.** { *; }
-keepclassmembers class com.distbit.nebuia_plugin.** { *; }
```

## Error Handling

```kotlin
private fun execute(action: () -> Unit) {
    try {
        action()
    } catch (e: Exception) {
        // Handle general exceptions
        Log.e("NebuIA", e.message ?: "Unknown error")
    } catch (e: ReportException) {
        // Handle report-specific exceptions
        Log.e("NebuIA", "Report error: ${e.message}")
    } catch (e: CodeException) {
        // Handle code-specific exceptions
        Log.e("NebuIA", "Code error: ${e.message}")
    }
}
```

## Support

For issues and feature requests, please visit: https://github.com/dist-bit/nebuia_native_android