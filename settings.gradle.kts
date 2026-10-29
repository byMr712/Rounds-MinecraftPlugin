rootProject.name = "RoundsPlugin"
include("rounds-api")
include("rounds-core")

val nmsModules = listOf(
    "rounds-nms1204",
    "rounds-nms1205",
    "rounds-nms1206",
    "rounds-nms121",
    "rounds-nms1211",
    // 1.21.2 недоступна upstream: PaperMC Fill не отдаёт билды (404), dev-bundle и maven-артефакты
    // удалены (404/410). Модуль не собирается, пока PaperMC не вернёт артефакты этой версии.
    // "rounds-nms1212",
    "rounds-nms1213",
    "rounds-nms1214",
    "rounds-nms1215",
    "rounds-nms1216",
    "rounds-nms1217",
    "rounds-nms1218",
    "rounds-nms1219",
    "rounds-nms12110",
    "rounds-nms12111",
    "rounds-nms2611",
    "rounds-nms2612",
    "rounds-nms262",
    "rounds-nms263"
)

nmsModules.forEach { name ->
    include(name)
    project(":$name").projectDir = file("rounds-nms/$name")
}
