plugins {
    id("com.github.johnrengelman.shadow")
    application
}

application {
    mainClass.set("huncho.main.lobby.MainKt")
}

dependencies {
    // Minestom - Minecraft 1.21.8 support
    implementation("net.minestom:minestom:${rootProject.extra["minestomVersion"]}")
    
    // Brigadier
    implementation("com.mojang:brigadier:1.0.18")
    
    // Configuration
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${rootProject.extra["jacksonVersion"]}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${rootProject.extra["jacksonVersion"]}")
    implementation("com.fasterxml.jackson.core:jackson-core:${rootProject.extra["jacksonVersion"]}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${rootProject.extra["jacksonVersion"]}")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${rootProject.extra["coroutinesVersion"]}")
    
    // Adventure API
    implementation("net.kyori:adventure-api:${rootProject.extra["adventureVersion"]}")
    implementation("net.kyori:adventure-text-minimessage:${rootProject.extra["adventureVersion"]}")
    implementation("net.kyori:adventure-text-serializer-legacy:${rootProject.extra["adventureVersion"]}")
    
    // HTTP Client for Radium API
    implementation("com.squareup.okhttp3:okhttp:${rootProject.extra["okhttpVersion"]}")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // HTTP API server for gamemode synchronization
    implementation("io.javalin:javalin:5.6.3")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    
    // Redis for real-time punishment/mute checks
    implementation("io.lettuce:lettuce-core:${rootProject.extra["redisVersion"]}")
    
    // Schematics
    implementation("dev.hollowcube:schem:1.3.1")
}


tasks {
    shadowJar {
        archiveBaseName.set("LobbyPlugin")
        archiveClassifier.set("")
        archiveVersion.set("")
    }
    
    build {
        dependsOn(shadowJar)
    }
    
    run.configure {
        dependsOn(shadowJar)
        classpath = files(shadowJar.get().archiveFile)
        mainClass.set("huncho.main.lobby.MainKt")
    }
}