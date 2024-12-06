val ktor_version: String = "3.0.2"  // Ktor 3.0.2
val kotlin_version: String = "2.0.0-RC1" // Update to 2.0.0-RC1 or later
val logback_version: String = "1.4.14"
val kotlinx_serialization_version: String = "1.6.2"
val firebase_version: String = "8.2.0"

plugins {
    kotlin("jvm") version "2.0.0-RC1"
    id("io.ktor.plugin") version "3.0.2"  // Updated Ktor plugin version
    kotlin("plugin.serialization") version "2.0.0-RC1"
    "com.google.gms.google-services"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Explicit Kotlin stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")

    // Ktor dependencies
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")  // This includes routing
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-default-headers-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")
    implementation("io.ktor:ktor-server-request-validation:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")

    // Firebase dependencies
    implementation("com.google.firebase:firebase-admin:$firebase_version")
    implementation("com.google.cloud:google-cloud-firestore:3.7.3")

    // BCrypt for password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // Test dependencies
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

// Java compatibility
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Kotlin compiler settings
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
}

// Diagnostic task to print versions
tasks.register("printVersions") {
    doLast {
        println("Kotlin Version: $kotlin_version")
        println("Ktor Version: $ktor_version")
        println("Serialization Version: $kotlinx_serialization_version")
        println("Firebase Version: $firebase_version")
    }
}

// Task to check Firebase service account
tasks.register("checkFirebaseServiceAccount") {
    doLast {
        val serviceAccountFile = file("src/main/resources/firebase-service-account.json")
        if (!serviceAccountFile.exists()) {
            println("WARNING: Firebase service account JSON file is missing!")
            println("Please download the service account key from Firebase Console.")
        }
    }
}

// Ensure Firebase check runs before build
tasks.whenTaskAdded {
    if (name == "build") {
        dependsOn("checkFirebaseServiceAccount")
    }
}