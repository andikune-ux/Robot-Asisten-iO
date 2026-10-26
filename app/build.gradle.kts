plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

val projectDocsAssetsDir = layout.buildDirectory.dir("generated/project-docs-assets")
val projectDocsTemplate = layout.projectDirectory.file("src/main/project_docs/relx_project_handoff_template.txt")
val generateProjectHandoff by tasks.registering {
  val outputFile = projectDocsAssetsDir.map { it.file("relx_project_handoff.txt") }
  notCompatibleWithConfigurationCache("Generates a fresh repository inventory for the user-facing project handoff document.")
  inputs.file(projectDocsTemplate)
  inputs.files(fileTree(rootProject.projectDir) {
    exclude(".git/**", "**/.gradle/**", "**/.idea/**", "**/build/**", "**/node_modules/**")
    exclude("local.properties", "**/local.properties", "**/secrets.properties", "**/*.jks", "**/*.keystore", "**/*.pem", "**/*.key", "**/.env", "**/.env.local")
  })
  outputs.file(outputFile)
  doLast {
    val root = rootProject.projectDir
    val excludedDirectories = setOf(".git", ".gradle", ".idea", "build", "node_modules")
    val inventory = root.walkTopDown()
      .onEnter { directory -> directory.name !in excludedDirectories }
      .filter { it.isFile }
      .map { file -> root.toPath().relativize(file.toPath()).toString().replace('\\', '/') }
      .filter { path ->
        val name = path.substringAfterLast('/').lowercase()
        path != "local.properties" && name !in setOf("secrets.properties", ".env", ".env.local") &&
          !name.endsWith(".jks") && !name.endsWith(".keystore") && !name.endsWith(".pem") && !name.endsWith(".key") &&
          !path.startsWith("app/build/") && !path.startsWith(".gradle/")
      }
      .sorted()
      .toList()
    val versionName = android.defaultConfig.versionName ?: "unknown"
    val versionCode = android.defaultConfig.versionCode
    val content = buildString {
      val template = projectDocsTemplate.asFile.readText().replace(
        "Versi build dokumen ini ditambahkan otomatis oleh Gradle dari konfigurasi aplikasi.",
        "Versi build: $versionName / versionCode $versionCode"
      )
      append(template)
      appendLine("\n\nBUILD-GENERATED REPOSITORY INVENTORY")
      appendLine("====================================")
      appendLine("Dibuat otomatis oleh Gradle untuk RELX.iO $versionName (versionCode $versionCode). Daftar ini diperbarui pada setiap build dari file yang ada di repository saat build dilakukan.")
      appendLine("File kunci signing, konfigurasi rahasia/lokal, dan output build sengaja tidak dimasukkan.")
      appendLine("\nFILE AKTUAL DI REPOSITORY (relatif terhadap root)")
      appendLine("================================================")
      inventory.forEach(::appendLine)
    }
    val destination = outputFile.get().asFile
    destination.parentFile.mkdirs()
    destination.writeText(content, Charsets.UTF_8)
  }
}

android {
  namespace = "dev.andikune.robotasisten.io"
  compileSdk = 34

  defaultConfig {
    applicationId = "dev.andikune.robotasisten.io"
    minSdk = 26
    targetSdk = 34
    versionCode = 6
    versionName = "1.2.3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      if (!keystorePath.isNullOrBlank()) storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS")
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugStable") {
      val keystorePath = System.getenv("DEBUG_KEYSTORE_PATH")
      if (!keystorePath.isNullOrBlank()) storeFile = file(keystorePath)
      storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD")
      keyAlias = System.getenv("DEBUG_KEY_ALIAS")
      keyPassword = System.getenv("DEBUG_KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      val debugKeystorePath = System.getenv("DEBUG_KEYSTORE_PATH")
      if (!debugKeystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("debugStable")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  sourceSets.getByName("main").assets.srcDir(projectDocsAssetsDir.get().asFile)

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

tasks.named("preBuild").configure { dependsOn(generateProjectHandoff) }

dependencies {
  implementation(platform("androidx.compose:compose-bom:2024.09.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.activity:activity-compose:1.9.2")
  implementation("androidx.navigation:navigation-compose:2.8.0")
  implementation("androidx.documentfile:documentfile:1.0.1")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
  implementation(libs.androidx.core.ktx)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  implementation("com.google.dagger:hilt-android:2.51.1")
  // ksp("com.google.dagger:hilt-compiler:2.51.1")
  implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

  implementation("androidx.work:work-runtime-ktx:2.9.1")

  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-gson:2.11.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

  implementation("androidx.security:security-crypto:1.1.0-alpha06")
  implementation("com.google.mlkit:text-recognition:16.0.1")
  implementation("com.google.mediapipe:tasks-genai:0.10.27")

  testImplementation(libs.junit)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
