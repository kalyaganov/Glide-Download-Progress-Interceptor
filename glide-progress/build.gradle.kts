plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
}

android {
    namespace = "ru.kalyaganov.glidedownloadinterceptor.lib"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.glide)
    api(libs.glide.okhttp3.integration)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "ru.kalyaganov"
                artifactId = "glide-download-interceptor"
                version = "2.0.0"

                pom {
                    name.set("Glide Download Progress Interceptor")
                    description.set("OkHttp network interceptor for tracking Glide image download progress on Android")
                    url.set("https://github.com/kalyaganov/Glide-Download-Progress-Interceptor")

                    licenses {
                        license {
                            name.set("MIT")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("kalyaganov")
                            name.set("Alexey")
                            email.set("alexey@kalyaganov.ru")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/kalyaganov/Glide-Download-Progress-Interceptor.git")
                        developerConnection.set("scm:git:ssh://github.com/kalyaganov/Glide-Download-Progress-Interceptor.git")
                        url.set("https://github.com/kalyaganov/Glide-Download-Progress-Interceptor")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "central"
                url = uri("https://central.sonatype.com/api/v1/publisher")
                credentials {
                    username = findProperty("mavenCentralUsername") as String?
                    password = findProperty("mavenCentralPassword") as String?
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}
