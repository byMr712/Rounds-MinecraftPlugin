import io.papermc.paperweight.tasks.RemapJar
import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.3"
}

group = "com.rounds"
version = "1.7"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(project(":rounds-api"))
    compileOnly(project(":rounds-core"))
    // MC 1.20.4: на этом этапе Paper/Purpur используют SPIGOT-маппинги в рантайме
    // (net.minecraft.server.v1_20_R3.*), поэтому dev-bundle даёт Mojang-mapped NMS
    // на компиляции, а paperweight ремапит классы модуля (reobfJar) в рантайм-маппинги.
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")
}

paperweight.reobfArtifactConfiguration = ReobfArtifactConfiguration.REOBF_PRODUCTION

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
    options.release.set(17)
}

val reobfJar = tasks.named<RemapJar>("reobfJar").apply {
    // Вывод reobf кладём рядом с остальными промежуточными артефактами
    configure {
        outputJar.set(layout.buildDirectory.file("libs/intermediate/rounds-nms1204-1.6-reobf.jar"))
    }
}

// Промежуточные userdev артефакты (rounds-nms1204-1.6.jar и -dev.jar) складываем
// в build/libs/intermediate, чтобы build/libs содержал только финальный плагин.
tasks.named<org.gradle.api.tasks.bundling.Jar>("jar") {
    destinationDirectory.set(layout.buildDirectory.dir("libs/intermediate"))
}

// Финальный артефакт для Paper/Purpur 1.20.4: ядро + api + reobf-нутые NMS-классы адаптера.
// Отдельная задача, т.к. userdev сам линкует reobfJar от стандартного jar (иначе цикл).
tasks.register<org.gradle.api.tasks.bundling.Zip>("pluginJar") {
    dependsOn(reobfJar)
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    archiveFileName.set("RoundsPlugin-1.20.4-v1.7-byMr712.jar")
    from(project(":rounds-api").sourceSets["main"].output)
    from(project(":rounds-core").sourceSets["main"].output)
    from(zipTree(reobfJar.map { it.outputJar.get().asFile }))
}

tasks.assemble {
    dependsOn("pluginJar")
}
