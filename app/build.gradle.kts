plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "hu.vagyonnaplo.app"
    compileSdk = 35
    defaultConfig { applicationId = "hu.vagyonnaplo.app"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    buildFeatures { compose = true }
    kotlinOptions { jvmTarget = "17" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
}
