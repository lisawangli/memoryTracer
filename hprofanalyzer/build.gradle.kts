plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // Gradle 7.0+ 版本

    id("maven-publish")
}



afterEvaluate{
    publishing {
        repositories {
            maven {
                // isAllowInsecureProtocol = true // 如果Maven仓库仅支持http协议, 请打开此注释
                url = uri("https://packages.aliyun.com/66c3fc94f168c2ed4c91bd2a/maven/repo-vnunv")
                authentication {
                    create<BasicAuthentication>("basic")
                }
                credentials {
                    username = "66c3fb599a2728f7f2b47f5f"
                    password = "qCumIWT]qbRi"
                }
            }
        }

        publications {
            create<MavenPublication>("product") {
                from(components["release"])
                groupId = "com.source.hprofanalyzer" // 请填入你的组件名
                artifactId = "plugin" // 请填入你的工件名
                version = "1.0" // 请填入工件的版本名
            }
        }
    }
}


android {
    namespace = "com.source.hprofanalyzer"
    compileSdk = 33

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("com.squareup.okio:okio:1.14.0")
    implementation("com.google.code.gson:gson:2.8.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

