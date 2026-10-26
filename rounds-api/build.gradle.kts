plugins {
    `java-library`
    `maven-publish`
}

group = "com.rounds"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Только чистый Paper-API без сторонних зависимостей и NMS
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        docTitle = "Rounds Public API v1.0.0 Documentation"
        windowTitle = "Rounds API v1.0.0"
        links("https://docs.oracle.com/en/java/javase/17/docs/api/")
        links("https://jd.papermc.io/paper/1.20/")
        addStringOption("Xdoclint:all,-missing", "-quiet")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.rounds"
            artifactId = "rounds-api"
            version = "1.0.0"
        }
    }
}
