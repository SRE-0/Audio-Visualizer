plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "com.sre404.audiovisualizer"
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
// Artifact : audiovisualizer  (no hyphens — GitHub Packages requirement)
// Version  : injected at build time via -Pversion_name=X.X.X
// URL      : must match the exact GitHub repo name (case sensitive)
// -----------------------------------------------------------------------
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {

                from(components["release"])

                // No hyphens allowed in artifactId for GitHub Packages
                groupId    = "com.sre404.audiovisualizer"
                artifactId = "audiovisualizer"
                version    = project.findProperty("version_name")?.toString() ?: "1.0.0"

                pom {
                    name        = "Flux AudioVisualizer"
                    description = "Real-time audio visualization library for Android. " +
                            "Native C++ DSP pipeline with FFT and volume modes."
                    url         = "https://github.com/SRE-0/Audio-Visualizer"

                    licenses {
                        license {
                            name = "MIT License"
                            url  = "https://opensource.org/licenses/MIT"
                        }
                    }

                    developers {
                        developer {
                            id   = "SRE-0"
                            name = "SRE-0"
                            url  = "https://github.com/SRE-0"
                        }
                    }

                    scm {
                        connection          = "scm:git:git://github.com/SRE-0/Audio-Visualizer.git"
                        developerConnection = "scm:git:ssh://github.com/SRE-0/Audio-Visualizer.git"
                        url                 = "https://github.com/SRE-0/Audio-Visualizer"
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"

                // Exact repo URL — case sensitive, must match GitHub exactly
                url = uri("https://maven.pkg.github.com/SRE-0/Audio-Visualizer")

                credentials {
                    // GITHUB_ACTOR is injected by the Action as lowercase
                    // so we hardcode the username to avoid case mismatch
                    username = "SRE-0"
                    password = project.findProperty("gpr.key")?.toString()
                        ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}