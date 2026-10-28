plugins {
    java
    `maven-publish`
}

group = "com.rounds"
version = "1.7"

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
    // Только Paper API (и PlaceholderAPI для офлайн-таб-комплита placeholders).
    // Любой net.minecraft.* / org.bukkit.craftbukkit.* доступ запрещён: он
    // изолирован в версионных модулях rounds-nms1204 / rounds-nms262 за NmsBridge.
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
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
    // Промежуточный модульный jar. Финальные артефакты собирают rounds-nms1204 / rounds-nms262.
    archiveFileName.set("rounds-core-v${project.version}.jar")
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.named<JavaCompile>("compileTestJava") {
    options.encoding = "UTF-8"
    options.release.set(17)
}

publishing {
    publications {
        create<MavenPublication>("core") {
            from(components["java"])
        }
    }
}
