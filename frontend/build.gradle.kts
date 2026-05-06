plugins {
    alias(libs.plugins.android.application) apply false

    // 👇 1. On aligne la sérialisation sur Kotlin 2.2.0 👇
    kotlin("plugin.serialization") version "2.2.0" apply false

    id("com.google.gms.google-services") version "4.4.1" apply false
    alias(libs.plugins.kotlin.android) apply false

    // 👇 2. On utilise la VRAIE version de KSP qui correspond à Kotlin 2.2.0 👇
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" apply false
}