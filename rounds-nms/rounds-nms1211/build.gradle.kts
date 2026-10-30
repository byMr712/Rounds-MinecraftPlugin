plugins {
    java
}

group = "com.rounds"
version = "2.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(project(":rounds-api"))
    compileOnly(project(":rounds-core"))
    // paper-api 1.20.4 strictly FIRST in classpath - 1.20.4 Bukkit constants (rule AGENTS.md).
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    // NMS MC 1.21.1 (Mojang-mapped runtime, org.bukkit.craftbukkit without version suffix):
    compileOnly(files("E:/GitHub/Rounds-MinecraftPlugin/libs/extract/1.21.1/versions/1.21.1/paper-1.21.1.jar"))
    compileOnly(fileTree("E:/GitHub/Rounds-MinecraftPlugin/libs/extract/1.21.1/libraries") { include("**/*.jar") })
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
    options.release.set(17)
    classpath = files(
        configurations.compileClasspath.get().filter {
            it.name.startsWith("paper-api") || it.name.startsWith("rounds-core") || it.name.startsWith("rounds-api")
        },
        configurations.compileClasspath.get().filter { n ->
            !(n.startsWith("paper-api") || n.startsWith("rounds-core") || n.startsWith("rounds-api"))
        }
    )
}

// Final artifact for Paper/Purpur 1.21.1: core + api + NMS adapter.
tasks.jar {
    archiveFileName.set("RoundsPlugin-1.21.1-v2.0-byMr712.jar")
    from(project(":rounds-api").sourceSets["main"].output)
    from(project(":rounds-core").sourceSets["main"].output)
}