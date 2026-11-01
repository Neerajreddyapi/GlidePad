import java.util.Properties

// Release signing credentials come from keystore.properties at the repo root (gitignored).
// Without the file the project still builds: release falls back to the debug key so a fresh
// clone works, but Google Play only accepts the upload-key build.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

plugins {
    id("com.android.application")
}

android {
    namespace = "com.glidepad.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neerajreddy.glidepad" // permanent on Google Play
        minSdk = 28 // BluetoothHidDevice (phone-as-keyboard/mouse) was added in Android 9
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        create("upload") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePropsFile.exists()) signingConfigs.getByName("upload")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
