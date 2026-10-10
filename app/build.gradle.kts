import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Assinatura da release (o APK distribuido pelo GitHub). O keystore.properties
// fica so na maquina de quem publica (fora do git); sem ele -- no F-Droid, por
// exemplo, que assina com a propria chave -- a release sai sem assinatura.
val signingProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "io.github.giova.metrofortaleza"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.giova.metrofortaleza"
        minSdk = 24
        // compileSdk vai no 37 para acompanhar as bibliotecas, mas targetSdk
        // fica em 36 de proposito: subir o targetSdk muda comportamento em
        // tempo de execucao, e o app ainda nao foi testado em aparelho.
        targetSdk = 36
        versionCode = 15
        versionName = "1.14"
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // O build roda em JDK 21, mas o bytecode continua em 17: e o nivel que o
    // D8/R8 suporta plenamente no Android.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // O banco ja esta comprimido o suficiente; nao vale reempacotar.
        noCompress += "db"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
