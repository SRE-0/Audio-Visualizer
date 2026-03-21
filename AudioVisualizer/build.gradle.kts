plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "com.sre404.audiovisualizer"

    // compileSdk with preview API syntax requires AGP 8.7+
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-O3 -std=c++17"
            }
        }
    }

    // Single externalNativeBuild block — was duplicated before
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Required so maven-publish can locate the release component
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// -----------------------------------------------------------------------
// Maven publishing configuration
//
// Group    : com.sre404.audiovisualizer
// Artifact : Audio-Visualizer
// Version  : injected at build time via -Pversion_name=X.X.X
// -----------------------------------------------------------------------
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {

                from(components["release"])

                groupId    = "com.sre404.audiovisualizer"
                artifactId = "Audio-Visualizer"
                version    = project.findProperty("version_name")?.toString() ?: "1.0.0"

                pom {
                    name        = "AudioVisualizer"
                    description = "Real-time audio visualization library for Android. " +
                            "Native C++ DSP pipeline with FFT and volume modes."
                    url         = "https://github.com/sre-0/Audio-Visualizer"

                    licenses {
                        license {
                            name = "MIT License"
                            url  = "https://opensource.org/licenses/MIT"
                        }
                    }

                    developers {
                        developer {
                            id   = "sre-0"
                            name = "sre-0"
                            url  = "https://github.com/sre-0"
                        }
                    }

                    scm {
                        connection          = "scm:git:git://github.com/sre-0/Audio-Visualizer.git"
                        developerConnection = "scm:git:ssh://github.com/sre-0/Audio-Visualizer.git"
                        url                 = "https://github.com/sre-0/Audio-Visualizer"
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url  = uri("https://maven.pkg.github.com/sre-0/Audio-Visualizer")

                credentials {
                    username = project.findProperty("gpr.user")?.toString()
                        ?: System.getenv("GITHUB_ACTOR")
                    password = project.findProperty("gpr.key")?.toString()
                        ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}