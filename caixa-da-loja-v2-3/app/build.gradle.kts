plugins {
    id("com.android.application")
}

android {
    namespace = "com.techcell.caixadaloja"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.techcell.caixadaloja.teste261"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "2.6.1-teste"
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
