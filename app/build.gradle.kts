plugins {
    id("com.android.application")
}

android {
    namespace = "com.glidepad.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.glidepad.app"
        minSdk = 28 // BluetoothHidDevice (phone-as-keyboard/mouse) was added in Android 9
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sideload build: signed with the local debug key so the APK installs directly.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
