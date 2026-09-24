plugins {
    id("com.android.application")
}

android {
    namespace = "com.techcell.caixadaloja"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.techcell.techcellacs.gestao.alpha8teste"
        minSdk = 24
        targetSdk = 35
        versionCode = 80
        versionName = "3.0.0-alpha8-pdvteste"
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
