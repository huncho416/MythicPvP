import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("kapt")
    id("com.github.johnrengelman.shadow")
    id("eclipse")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8"
    id("xyz.jpenilla.run-velocity") version "2.3.1"
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:${rootProject.extra["velocityVersion"]}")
    kapt("com.velocitypowered:velocity-api:${rootProject.extra["velocityVersion"]}")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:${rootProject.extra["coroutinesVersion"]}")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-velocity-api:2.22.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-velocity-core:2.22.0")

    // Lamp Command Library
    implementation("io.github.revxrsal:lamp.common:${rootProject.extra["lampVersion"]}")
    implementation("io.github.revxrsal:lamp.velocity:${rootProject.extra["lampVersion"]}")
    implementation("io.github.revxrsal:lamp.brigadier:${rootProject.extra["lampVersion"]}")

    implementation("org.yaml:snakeyaml:2.0")

    // MongoDB Driver (Reactive Streams + Multithreading)
    implementation("org.mongodb:mongodb-driver-reactivestreams:4.10.2")

    // Redis Driver (Lettuce)
    implementation("io.lettuce:lettuce-core:${rootProject.extra["redisVersion"]}")

    // HTTP Client for API communication
    implementation("com.squareup.okhttp3:okhttp:${rootProject.extra["okhttpVersion"]}")

    // HTTP Server for API
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-gson:2.3.12")
    implementation("io.ktor:ktor-server-cors:2.3.12")
    implementation("io.ktor:ktor-server-auth:2.3.12")
    implementation("io.ktor:ktor-server-status-pages:2.3.12")
}

tasks {
    runVelocity {
        velocityVersion("${rootProject.extra["velocityVersion"]}")
    }
    
    shadowJar {
        archiveBaseName.set("Radium")
        archiveClassifier.set("")
        archiveVersion.set("")
    }
    
    build {
        dependsOn(shadowJar)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        javaParameters = true
    }
}

val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")
val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)

    from(templateSource)
    into(templateDest)
    expand(props)
}

sourceSets.main.configure { java.srcDir(generateTemplates.map { it.outputs }) }
