plugins {
    kotlin("jvm") version "2.0.20" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

allprojects {
    group = "mythicpvp"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://repo.spongepowered.org/maven")
        maven("https://libraries.minecraft.net")
        maven("https://repo.minestom.net/repository/maven-public/")
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc-repo"
        }
        maven("https://oss.sonatype.org/content/groups/public/") {
            name = "sonatype"
        }
        maven("https://repo.opencollab.dev/main/")
        maven("https://maven.hapily.me/releases")
    }
}

// Shared dependency versions
extra["kotlinVersion"] = "2.0.20"
extra["minestomVersion"] = "2025.08.12-1.21.8"
extra["velocityVersion"] = "3.4.0-SNAPSHOT"
extra["mongoVersion"] = "4.11.1"
extra["redisVersion"] = "6.3.2.RELEASE"
extra["okhttpVersion"] = "4.12.0"
extra["jacksonVersion"] = "2.16.1"
extra["coroutinesVersion"] = "1.7.3"
extra["logbackVersion"] = "1.4.14"
extra["guavaVersion"] = "32.1.3-jre"
extra["adventureVersion"] = "4.15.0"
extra["lampVersion"] = "4.0.0-rc.12"
extra["junitVersion"] = "5.9.2"

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    
    dependencies {
        val implementation by configurations
        val testImplementation by configurations
        
        // Common Kotlin dependencies for all subprojects
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.extra["coroutinesVersion"]}")
        
        // Common logging
        implementation("ch.qos.logback:logback-classic:${rootProject.extra["logbackVersion"]}")
        implementation("org.slf4j:slf4j-api:2.0.9")
        
        // Common utilities
        implementation("com.google.guava:guava:${rootProject.extra["guavaVersion"]}")
        
        // Common testing
        testImplementation("org.junit.jupiter:junit-jupiter-api:${rootProject.extra["junitVersion"]}")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:${rootProject.extra["junitVersion"]}")
        testImplementation("org.mockito:mockito-core:4.11.0")
        testImplementation("org.mockito:mockito-junit-jupiter:4.11.0")
        testImplementation(kotlin("test"))
    }
    
    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
    
    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }
}