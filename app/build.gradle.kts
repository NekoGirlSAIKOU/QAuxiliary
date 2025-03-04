import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.util.UUID

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "${Version.kotlin}-${Version.ksp}"
}

val currentBuildUuid = UUID.randomUUID().toString()
println("Current build ID is $currentBuildUuid")

android {
    defaultConfig {
        applicationId = "io.github.qauxv"
        buildConfigField("String", "BUILD_UUID", "\"$currentBuildUuid\"")
        buildConfigField("long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")

        resourceConfigurations += listOf("zh", "en")

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DQAUXV_VERSION=$versionName",
                    "-DCMAKE_C_COMPILER_LAUNCHER=ccache",
                    "-DCMAKE_CXX_COMPILER_LAUNCHER=ccache",
                    "-DNDK_CCACHE=ccache",
                    "-DANDROID_CCACHE=ccache"
                )
                targets += "qauxv"
            }
        }
    }
    if (System.getenv("KEYSTORE_PATH") != null) {
        signingConfigs {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_PATH"))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
    buildTypes {
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            kotlinOptions.suppressWarnings = true
            val ltoCacheFlags = listOf(
                "-flto=thin",
                "-Wl,--thinlto-cache-policy,cache_size_bytes=300m",
                "-Wl,--thinlto-cache-dir=${buildDir.absolutePath}/.lto-cache",
            )
            externalNativeBuild.cmake {
                cFlags += ltoCacheFlags
                cppFlags += ltoCacheFlags
            }
        }
        getByName("debug") {
            isShrinkResources = false
            isMinifyEnabled = false
            isCrunchPngs = false
            proguardFiles("proguard-rules.pro")
        }
    }
    flavorDimensions += "abi"
    productFlavors {
        create("arm32") {
            dimension = "abi"
            ndk {
                abiFilters.add("armeabi-v7a")
            }
        }
        create("arm64") {
            dimension = "abi"
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
        create("armAll") {
            dimension = "abi"
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
        }
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
            }
        }
    }

    androidResources {
        additionalParameters("--allow-reserved-package-id", "--package-id", "0x39")
    }
    kotlinOptions.jvmTarget = Version.java.toString()

    externalNativeBuild {
        cmake {
            path = File(projectDir, "src/main/cpp/CMakeLists.txt")
            version = Version.getCMakeVersion(project)
        }
    }
    buildFeatures {
        viewBinding = true
    }
    lint {
        checkDependencies = true
    }
    namespace = "io.github.qauxv"
    applicationVariants.all {
        if (!this.buildType.isDebuggable) {
            val outputFileName = "QAuxv-v${defaultConfig.versionName}-${productFlavors.first().name}.apk"
            outputs.all {
                val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output?.outputFileName = outputFileName
            }
        }
    }
}

dependencies {
    compileOnly(projects.libs.qqStub)
    implementation(projects.libs.mmkv)
    ksp(projects.libs.ksp)
    // androidx
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.browser:browser:1.4.0")
    val lifecycleVersion = "2.4.1"
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")

    compileOnly("de.robv.android.xposed:api:82")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("com.google.android.material:material:1.6.1")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.afollestad.material-dialogs:core:3.3.0")
    implementation("com.afollestad.material-dialogs:input:3.3.0")
    implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation("com.github.kyuubiran:EzXHelper:0.9.9")
    // festival title
    implementation("com.github.jinatonic.confetti:confetti:1.1.2")
    implementation("com.github.MatteoBattilana:WeatherView:3.0.0")
}

val adb: String = androidComponents.sdkComponents.adb.get().asFile.absolutePath
val killQQ = tasks.register<Exec>("killQQ") {
    group = "qauxv"
    commandLine(adb, "shell", "am", "force-stop", "com.tencent.mobileqq")
    isIgnoreExitValue = true
}
val openQQ = tasks.register<Exec>("openQQ") {
    group = "qauxv"
    commandLine(adb, "shell", "am", "start", "$(pm resolve-activity --components com.tencent.mobileqq)")
    isIgnoreExitValue = true
}
tasks.register<Exec>("openTroubleShooting") {
    group = "qauxv"
    commandLine(adb, "shell", "am", "start",
        "-e", "qa_jump_action_cmd", "io.github.qauxv.TROUBLE_SHOOTING_ACTIVITY",
        "com.tencent.mobileqq/.activity.JumpActivity")
    isIgnoreExitValue = true
}

androidComponents.onVariants { variant ->
    val variantCapped = variant.name.capitalize()
    tasks.register("checkTargetNativeLibs$variantCapped") {
        dependsOn(":app:externalNativeBuild$variantCapped")
        doLast {
            val targetAbi = listOf("arm64-v8a", "armeabi-v7a")
            val soName = "libqauxv.so"
            val libPath = "app/build/intermediates/cmake/debug/obj"
            for (abi in targetAbi) {
                var tmpPath = "$libPath/$abi/$soName"
                if ("/" != File.separator) {
                    tmpPath = tmpPath.replace('/', File.separatorChar)
                }
                val f = File(rootProject.projectDir, tmpPath)
                if (!f.exists()) {
                    throw IllegalStateException("Native library missing for the target abi: $abi. Please run gradle task ':app:externalNativeBuild$variantCapped' manually to force android gradle plugin to satisfy all required ABIs.")
                }
            }
        }
    }

    task("installAndRestart${variantCapped}") {
        group = "qauxv"
        dependsOn(":app:install$variantCapped", killQQ)
        finalizedBy(openQQ)
    }
}

tasks.register<ReplaceIcon>("replaceIcon") {
    group = "qauxv"
    projectDir.set(project.projectDir)
    commitHash = Common.getGitHeadRefsSuffix(rootProject)
    config()
}.also { tasks.preBuild.dependsOn(it) }

tasks.register<Delete>("cleanCxxIntermediates") {
    group = "qauxv"
    delete(file(".cxx"))
}.also { tasks.clean.dependsOn(it) }

tasks.register<Delete>("cleanOldIcon") {
    group = "qauxv"
    val drawableDir= File(projectDir, "src/main/res/drawable")
    drawableDir
        .listFiles()
        ?.filter { it.isFile && it.name.startsWith("icon") }
        ?.forEach(::delete)
    delete(file("src/main/res/drawable-anydpi-v26/icon.xml"))
}.also { tasks.clean.dependsOn(it) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    if (name.contains("release", true)) {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xno-call-assertions",
                "-Xno-receiver-assertions",
                "-Xno-param-assertions",
            )
        }
    }
}

tasks.register("checkGitSubmodule") {
    group = "qauxv"
    val projectDir = rootProject.projectDir
    doLast {
        listOf(
            "libs/mmkv/MMKV/Core".replace('/', File.separatorChar),
            "app/src/main/cpp/dex_builder"
        ).forEach {
            val submoduleDir = File(projectDir, it)
            if (!submoduleDir.exists()) {
                throw IllegalStateException(
                    "submodule dir not found: $submoduleDir" +
                        "\nPlease run 'git submodule init' and 'git submodule update' manually."
                )
            }
        }
    }
}.also { tasks.preBuild.dependsOn(it) }
