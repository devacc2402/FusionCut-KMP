import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(project(":shared"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.okhttp)
            
            // Material Icons
            implementation(compose.materialIconsExtended)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    namespace = "com.erik.fusioncut"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.erik.fusioncut"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 8
        versionName = "1.1.1"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-fexceptions")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
            storeFile = file(keystorePath)
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
        create("debugConfig") {
            val customKeystore = file("${rootDir}/debug.keystore")
            if (customKeystore.exists()) {
                storeFile = customKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            val releaseConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseConfig.storeFile?.exists() == true) releaseConfig else signingConfigs.getByName("debug")
        }
        getByName("debug") {
            val customDebug = signingConfigs.getByName("debugConfig")
            signingConfig = if (customDebug.storeFile?.exists() == true) customDebug else signingConfigs.getByName("debug")
        }
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    
    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
        resources.srcDirs("src/androidMain/resources")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "FusionCut"
            packageVersion = "1.1.0"
            
            windows {
                perUserInstall = false
                shortcut = true
                menu = true
                upgradeUuid = "a6f23b2d-1284-4b5c-a12d-8e4726b91a2e"
                iconFile.set(project.file("src/jvmMain/resources/icon.png"))
            }
        }
    }
}

// Task to sync app icon from ic_app_logo.jpg to Desktop icon.png
tasks.register("syncAppIcon") {
    doLast {
        val logoFile = file("src/androidMain/res/drawable/ic_app_logo.jpg")
        if (logoFile.exists()) {
            val resDir = file("src/jvmMain/resources")
            resDir.mkdirs()
            val img = javax.imageio.ImageIO.read(logoFile)
            if (img != null) {
                val pngFile = file("${resDir.absolutePath}/icon.png")
                javax.imageio.ImageIO.write(img, "PNG", pngFile)
                println("FusionCut: Desktop icon.png generated from ic_app_logo.jpg")
            }
        }
    }
}

// Helper function to sign Windows executables, DLLs, or MSI installers using a local self-signed certificate
fun signWindowsBinary(fileToSign: File) {
    if (!System.getProperty("os.name").contains("Windows", ignoreCase = true) || !fileToSign.exists()) return
    println("FusionCut: Signing ${fileToSign.name} with local code-signing certificate...")
    val psScript = """
        ${'$'}cert = Get-ChildItem -Path Cert:\CurrentUser\My | Where-Object { ${'$'}_.Subject -like '*CN=FusionCut Developer*' } | Select-Object -First 1
        if (-not ${'$'}cert) {
            ${'$'}cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject 'CN=FusionCut Developer' -CertStoreLocation 'Cert:\CurrentUser\My'
            Export-Certificate -Cert ${'$'}cert -FilePath "${'$'}env:TEMP\FusionCutDev.cer" | Out-Null
            Import-Certificate -FilePath "${'$'}env:TEMP\FusionCutDev.cer" -CertStoreLocation 'Cert:\CurrentUser\Root' | Out-Null
        }
        Set-AuthenticodeSignature -FilePath '${fileToSign.absolutePath.replace("\\", "/")}' -Certificate ${'$'}cert | Out-Null
    """.trimIndent()
    try {
        val process = ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript).start()
        process.waitFor()
        println("FusionCut: Successfully signed ${fileToSign.name}.")
    } catch (e: Exception) {
        println("FusionCut Warning: Auto-signing failed for ${fileToSign.name}: ${e.message}")
    }
}

// Native Build for Windows (fusion_engine.dll)
tasks.register("compileNativeWindows") {
    doLast {
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            val nativeSrcDir = file("src/jvmMain/cpp")
            val buildDir = file("build/native/windows")
            val resDir = file("src/jvmMain/resources")
            buildDir.mkdirs()
            resDir.mkdirs()

            println("FusionCut: Compiling C++ Render Engine for Windows...")

            val sdkCmake = file("${System.getProperty("user.home")}/AppData/Local/Android/Sdk/cmake/3.22.1/bin/cmake.exe")
            val sdkNinja = file("${System.getProperty("user.home")}/AppData/Local/Android/Sdk/cmake/3.22.1/bin/ninja.exe")

            val cmakePath = if (sdkCmake.exists()) sdkCmake.absolutePath else "cmake"
            val ninjaPath = if (sdkNinja.exists()) sdkNinja.absolutePath else "ninja"

            val vcvarsFile = file("C:/Program Files/Microsoft Visual Studio/18/Community/VC/Auxiliary/Build/vcvars64.bat").takeIf { it.exists() }
                ?: file("C:/Program Files (x86)/Microsoft Visual Studio/2022/Community/VC/Auxiliary/Build/vcvars64.bat").takeIf { it.exists() }

            try {
                if (vcvarsFile != null) {
                    val batchScript = file("build/build_msvc.bat")
                    batchScript.parentFile.mkdirs()
                    batchScript.writeText("""
                        @echo off
                        call "${vcvarsFile.absolutePath}"
                        set CMAKE="${cmakePath}"
                        set NINJA="${ninjaPath}"
                        set SRCDIR="${nativeSrcDir.absolutePath}"
                        set BUILDDIR="${buildDir.absolutePath}"

                        if not exist %BUILDDIR% mkdir %BUILDDIR%

                        %CMAKE% -G "Ninja" -DCMAKE_MAKE_PROGRAM=%NINJA% -S %SRCDIR% -B %BUILDDIR%
                        %CMAKE% --build %BUILDDIR%
                    """.trimIndent())

                    providers.exec {
                        commandLine("cmd", "/c", batchScript.absolutePath)
                    }.result.get()
                }
            } catch (exc: Exception) {
                println("FusionCut: C++ compilation note: $exc")
            }

            val dllFile = file("${buildDir.absolutePath}/fusion_engine.dll")
            if (dllFile.exists()) {
                val targetDll = file("${resDir.absolutePath}/fusion_engine.dll")
                dllFile.copyTo(targetDll, overwrite = true)
                println("FusionCut: Native DLL successfully bundled to resources (${targetDll.length()} bytes).")
                signWindowsBinary(targetDll)
            } else {
                println("FusionCut WARNING: DLL not found after build at ${dllFile.absolutePath}")
            }
        }
    }
}

tasks.named("jvmProcessResources") {
    dependsOn("compileNativeWindows")
}



// Automatically sign app image binaries before packaging, and sign the MSI installer post-build
tasks.matching { it.name.startsWith("package") && it.name.endsWith("Msi") }.configureEach {
    doFirst {
        // Sign all executables and DLLs in the app image before WiX packages them into the MSI
        val appDir = file("build/compose/binaries/main/app")
        if (appDir.exists()) {
            appDir.walkTopDown().filter { it.isFile && (it.extension.lowercase() == "exe" || it.extension.lowercase() == "dll") }.forEach { file ->
                signWindowsBinary(file)
            }
        }
    }
    doLast {
        val msiDir = file("build/compose/binaries/main/msi")
        if (msiDir.exists()) {
            msiDir.listFiles()?.filter { it.extension.lowercase() == "msi" }?.forEach { msiFile ->
                signWindowsBinary(msiFile)
            }
        }
    }
}

// Automatically generate/sync AppVersion Kotlin actual files from build.gradle.kts settings
tasks.register("syncAppVersion") {
    doLast {
        val androidVersion = android.defaultConfig.versionName ?: "1.0.0"
        val desktopVersion = compose.desktop.application.nativeDistributions.packageVersion ?: "1.0.0"

        val androidFile = file("../shared/src/androidMain/kotlin/com/example/util/AppVersion.kt")
        if (androidFile.parentFile.exists()) {
            androidFile.writeText("package com.example.util\n\nactual object AppVersion {\n    actual val name: String = \"$androidVersion\"\n}\n")
        }

        val jvmFile = file("../shared/src/jvmMain/kotlin/com/example/util/AppVersion.kt")
        if (jvmFile.parentFile.exists()) {
            jvmFile.writeText("package com.example.util\n\nactual object AppVersion {\n    actual val name: String = \"$desktopVersion\"\n}\n")
        }

        println("FusionCut: AppVersion synced (Android: $androidVersion, Desktop: $desktopVersion)")
    }
}

tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn("syncAppVersion")
}

