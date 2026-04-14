plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    `signing`
}

android {
    namespace = "io.jishu.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.review)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "page.jishu"
            artifactId = "jishu-android"
            version = "0.1.9"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Jishu Android SDK")
                description.set("Android SDK for Jishu — check promo access grants, send contact messages, and collect feature proposals from native Android apps.")
                url.set("https://github.com/jeremieb/jishu-sdk-kotlin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("jeremieb")
                        name.set("Jeremie Berduck")
                        email.set("jeremieberduck@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/jeremieb/jishu-sdk-kotlin.git")
                    developerConnection.set("scm:git:ssh://github.com/jeremieb/jishu-sdk-kotlin.git")
                    url.set("https://github.com/jeremieb/jishu-sdk-kotlin")
                }
            }
        }
    }
}

signing {
    val keyId = findProperty("signing.keyId") as String? ?: ""
    val keyFile = findProperty("signing.keyFile") as String? ?: ""
    val password = findProperty("signing.password") as String? ?: ""
    val key = if (keyFile.isNotEmpty()) file(keyFile).readText() else ""
    useInMemoryPgpKeys(keyId, key, password)
    sign(publishing.publications["release"])
}
