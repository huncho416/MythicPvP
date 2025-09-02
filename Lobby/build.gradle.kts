import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "2.0.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.org/repository/maven-public/")
}

dependencies {
    // Minestom
    implementation("net.minestom:minestom:2025.08.29-1.21.8")
    
    // Brigadier
    implementation("com.mojang:brigadier:1.0.18")
    
    // Configuration
    implementation("org.yaml:snakeyaml:2.2")
    
    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.1")
    
    // Adventure API
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.17.0")
    
    // HTTP Client for Radium API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // HTTP API server for gamemode synchronization
    implementation("io.javalin:javalin:5.6.3")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    
    // Redis for real-time punishment/mute checks
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
    
    // Schematics
    implementation("dev.hollowcube:schem:1.3.1")
}

tasks.shadowJar {
    archiveBaseName.set("lobby")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
    
    manifest {
        attributes["Main-Class"] = "huncho.main.lobby.MainKt"
    }
}

application {
    mainClass.set("huncho.main.lobby.MainKt")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}