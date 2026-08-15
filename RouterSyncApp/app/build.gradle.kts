plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.routersync.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.routersync.app"
        minSdk = 26          // Necessario per SAF completo e WorkManager senza limitazioni
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // compatibile con Kotlin 1.9.24
    }
    packaging {
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    // --- Core Android (FileProvider, estensioni Kotlin) ---
    implementation("androidx.core:core-ktx:1.13.1")

    // --- UI: Jetpack Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // --- Sincronizzazione in background ---
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // --- Persistenza profili di sync ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Client di rete per i protocolli richiesti ---
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")           // SMB / CIFS (share Windows/NAS/router)
    implementation("commons-net:commons-net:3.11.1")           // FTP
    implementation("com.github.thegrizzlylabs:sardine-android:0.8") // WebDAV

    // --- Coroutine ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Icone Material ---
    implementation("androidx.compose.material:material-icons-extended:1.6.8")

    // --- Storage Access Framework (scelta cartelle locali) ---
    implementation("androidx.documentfile:documentfile:1.0.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
