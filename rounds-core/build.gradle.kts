plugins {
    java
    `maven-publish`
}

group = "com.rounds"
version = "1.9"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
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

tasks.register<JavaExec>("buildResourcePacks") {
    group = "rounds"
    description = "Cross-platform universal resource pack generator (RU, EN, Default)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.rounds.tool.CardPackBuilder")
    val packArgs = mutableListOf<String>()
    if (project.hasProperty("deployDesktop")) packArgs.add("--deploy-desktop")
    if (project.hasProperty("deployInstances")) packArgs.add("--deploy-instances")
    args = packArgs
}

tasks.register("buildCardPack") {
    group = "rounds"
    description = "Alias for buildResourcePacks."
    dependsOn("buildResourcePacks")
}

publishing {
    publications {
        create<MavenPublication>("core") {
            from(components["java"])
        }
    }
}
