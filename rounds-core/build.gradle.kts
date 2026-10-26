plugins {
    java
    `maven-publish`
}

group = "com.rounds"
version = "1.6"

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
    implementation(project(":rounds-api"))
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly(files("C:/Users/Mr712/AppData/Roaming/ATLauncher/servers/Minecraft262withPurpur/purpur-26.2-2633.jar"))
    compileOnly(files("C:/Users/Mr712/AppData/Roaming/ATLauncher/servers/Minecraft262withPurpur/versions/26.2/purpur-26.2.jar"))
    compileOnly(fileTree("C:/Users/Mr712/AppData/Roaming/ATLauncher/servers/Minecraft262withPurpur/libraries") {
        include("**/*.jar")
    })
    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("RoundsPlugin-v${project.version}-byMr712.jar")
    from(project(":rounds-api").the<SourceSetContainer>()["main"].output)
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
    options.release.set(17)
    classpath = files(
        configurations.compileClasspath.get().filter { it.name.startsWith("paper-api") || it.name.startsWith("placeholderapi") },
        configurations.compileClasspath.get().filter { !it.name.startsWith("paper-api") && !it.name.startsWith("placeholderapi") }
    )
}

tasks.named<JavaCompile>("compileTestJava") {
    options.encoding = "UTF-8"
    options.release.set(17)
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            from(components["java"])
        }
    }
}
