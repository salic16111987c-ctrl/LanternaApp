plugins {
    id("com.android.application")
}

android {
    namespace = "com.techcell.caixadaloja"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.techcell.techcellacs.gestao.pdvpreview"
        minSdk = 24
        targetSdk = 35
        versionCode = 475
        versionName = "3.0.0-alpha42-pdvpreview-r45"
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

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
