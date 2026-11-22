import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing: keystore.properties (local, git-ignored) or environment variables (CI, from
// GitHub secrets): EZPLANNER_KEYSTORE_FILE, EZPLANNER_KEYSTORE_PASSWORD, EZPLANNER_KEY_ALIAS, EZPLANNER_KEY_PASSWORD.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
data class ReleaseKey(val file: File, val storePassword: String, val alias: String, val keyPassword: String)
val releaseKey: ReleaseKey? = run {
    val env = System.getenv()
    val envFile = env["EZPLANNER_KEYSTORE_FILE"]
    when {
        !envFile.isNullOrBlank() -> ReleaseKey(
            file(envFile),
            env["EZPLANNER_KEYSTORE_PASSWORD"].orEmpty(),
            env["EZPLANNER_KEY_ALIAS"].orEmpty(),
            env["EZPLANNER_KEY_PASSWORD"].orEmpty(),
        )
        keystoreProps.isNotEmpty() -> ReleaseKey(
            rootProject.file(keystoreProps.getProperty("storeFile")),
            keystoreProps.getProperty("storePassword"),
            keystoreProps.getProperty("keyAlias"),
            keystoreProps.getProperty("keyPassword"),
        )
        else -> null
    }
}
// CI passes -Pezplanner.requireReleaseKey=true: a release signed with anything but the real key
// could never update an installed app, so such a build must fail instead of falling back.
val requireReleaseKey = providers.gradleProperty("ezplanner.requireReleaseKey").map { it.toBoolean() }.getOrElse(false)
if (requireReleaseKey && (releaseKey == null || !releaseKey.file.exists())) {
    throw GradleException("Release signing key missing (EZPLANNER_KEYSTORE_FILE / keystore.properties)")
}

// App version: single source in gradle.properties (ezplanner.versionName = MAJOR.MINOR.PATCH).
// The versionCode is derived from it, so every higher version installs as an update.
val appVersionName: String = providers.gradleProperty("ezplanner.versionName").get()
val appVersionCode: Int = run {
    val parts = appVersionName.split('.').map { it.toIntOrNull() }
    if (parts.size != 3 || parts.any { it == null || it < 0 || it > 999 }) {
        throw GradleException("ezplanner.versionName must be MAJOR.MINOR.PATCH (each 0..999), got '$appVersionName'")
    }
    parts[0]!! * 1_000_000 + parts[1]!! * 1_000 + parts[2]!!
}

/** Copies the repository's CHANGELOG.md into the APK assets (shown in the app after updates). */
abstract class ChangelogAssetTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val changelog: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        changelog.get().asFile.copyTo(File(out, "CHANGELOG.md"), overwrite = true)
    }
}

val changelogAsset = tasks.register<ChangelogAssetTask>("changelogAsset") {
    changelog.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
    outputDir.set(layout.buildDirectory.dir("generated/changelogAssets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(changelogAsset, ChangelogAssetTask::outputDir)
        val label = if (variant.buildType == "debug") "EzPlanner Debug" else "EzPlanner"
        variant.resValues.put(variant.makeResValueKey("string", "app_name"), com.android.build.api.variant.ResValue(label))
    }
}

android {
    namespace = "com.moddingforge.ezplanner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.moddingforge.ezplanner"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        // In-app updater source (gradle.properties: ezplanner.updateRepo=owner/name).
        buildConfigField("String", "UPDATE_REPO", "\"${providers.gradleProperty("ezplanner.updateRepo").getOrElse("")}\"")
        // Release asset the in-app updater installs.
        buildConfigField("String", "UPDATE_ASSET_PREFIX", "\"EzPlanner-\"")
    }

    signingConfigs {
        releaseKey?.let { key ->
            create("release") {
                storeFile = key.file
                storePassword = key.storePassword
                keyAlias = key.alias
                keyPassword = key.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
