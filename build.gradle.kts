// Root multi-project build file

tasks.register("buildCardPack") {
    group = "rounds"
    description = "Builds cross-platform universal resource packs via rounds-core."
    dependsOn(":rounds-core:buildCardPack")
}

tasks.register("quickBuild") {
    group = "build"
    description = "Quick build: compiles rounds-core and one target NMS jar (default 26.2, or -PtargetNms=1.20.4, etc.)."
    val target = (project.findProperty("targetNms") as? String) ?: "26.2"
    val nmsProjectName = when (target) {
        "1.20.4", "1204" -> ":rounds-nms1204"
        "1.20.5", "1205" -> ":rounds-nms1205"
        "1.20.6", "1206" -> ":rounds-nms1206"
        "1.21", "121" -> ":rounds-nms121"
        "1.21.1", "1211" -> ":rounds-nms1211"
        "1.21.3", "1213" -> ":rounds-nms1213"
        "1.21.4", "1214" -> ":rounds-nms1214"
        "1.21.5", "1215" -> ":rounds-nms1215"
        "1.21.6", "1216" -> ":rounds-nms1216"
        "1.21.7", "1217" -> ":rounds-nms1217"
        "1.21.8", "1218" -> ":rounds-nms1218"
        "1.21.9", "1219" -> ":rounds-nms1219"
        "1.21.10", "12110" -> ":rounds-nms12110"
        "1.21.11", "12111" -> ":rounds-nms12111"
        "26.1.1", "2611" -> ":rounds-nms2611"
        "26.1.2", "2612" -> ":rounds-nms2612"
        "26.2", "262" -> ":rounds-nms262"
        "26.3", "263" -> ":rounds-nms263"
        else -> ":rounds-nms262"
    }
    dependsOn(":rounds-core:jar", "$nmsProjectName:jar")
}
