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
    // paper-api 1.20.4 строго ПЕРВЫМ в classpath — Bukkit-константы 1.20.4
    // (`Attribute.GENERIC_MAX_HEALTH` и т.п.), как требует AGENTS.md.
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    // NMS MC 26.3 (Mojang-mapped runtime, org.bukkit.craftbukkit без версионного суффикса):
    compileOnly(files("C:/Users/Mr712/AppData/Roaming/ATLauncher/servers/Minecraft263withPaper/paper-26.3-222.jar"))
    compileOnly(files("C:/Users/Mr712/AppData/Roaming/ATLauncher/servers/Minecraft263withPaper/versions/26.3/paper-26.3.jar"))
    compileOnly(fileTree("C:/Users/Mr712/AppData/Roaming/ATLauncher/servers/Minecraft263withPaper/libraries") {
        include("**/*.jar")
    })
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

// Финальный артефакт для Paper/Purpur 26.3: ядро + api + NMS-адаптер.
tasks.jar {
    archiveFileName.set("RoundsPlugin-26.3-v2.0-byMr712.jar")
    from(project(":rounds-api").sourceSets["main"].output)
    from(project(":rounds-core").sourceSets["main"].output)
}
