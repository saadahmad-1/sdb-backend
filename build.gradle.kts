val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val kotlinx_serialization_version: String by project
val firebase_version: String = "9.2.0"

plugins {
    kotlin("jvm") version "2.0.21"
    id("io.ktor.plugin") version "3.0.1"
    kotlin("plugin.serialization") version "1.9.21"
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
    // Ktor dependencies
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-default-headers-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")
    implementation("io.ktor:ktor-server-request-validation:$ktor_version")
    
    // Firebase dependencies
    implementation("com.google.firebase:firebase-admin:$firebase_version")
    implementation("com.google.cloud:google-cloud-firestore:3.7.3")
    implementation("io.ktor:ktor-server-auth:$ktor_version")

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
