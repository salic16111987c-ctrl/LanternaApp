plugins {
    id("com.android.application")
}

android {
    namespace = "com.techcell.caixadaloja"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.techcell.techcellacs.gestao"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "3.0.0-alpha6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
