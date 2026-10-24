plugins {
    java
    `maven-publish`
}

group = "com.rounds"
version = "1.5"

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
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly(files("C:/Users/Mr712/AppData/Roaming/ATLauncher/servers/Minecraft262withPurpur/purpur-26.2-2633.jar"))
    compileOnly(files("C:/Users/Mr712/AppData/Roaming/ATLauncher/servers/Minecraft262withPurpur/versions/26.2/purpur-26.2.jar"))
    compileOnly(fileTree("C:/Users/Mr712/AppData/Roaming/ATLauncher/servers/Minecraft262withPurpur/libraries") {
        include("**/*.jar")
    })
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
    classpath = files(
        configurations.compileClasspath.get().filter { it.name.startsWith("paper-api") || it.name.startsWith("placeholderapi") },
        configurations.compileClasspath.get().filter { !it.name.startsWith("paper-api") && !it.name.startsWith("placeholderapi") }
    )
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            from(components["java"])
        }
    }
}
