plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "net.credflare.sdk"
    compileSdk = 36
    defaultConfig { minSdk = 24; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["test"].resources.srcDirs("src/test/resources", "../example/src/androidTest/assets")
}
dependencies {
    implementation("androidx.core:core:1.18.0")
    implementation("com.google.mlkit:face-detection:16.1.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
