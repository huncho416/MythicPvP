plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "MythicPvP"

include("radium", "lobby")

project(":radium").projectDir = file("Radium")
project(":lobby").projectDir = file("Lobby")