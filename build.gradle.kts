plugins {
    id("net.fabricmc.fabric-loom")
}

val minecraft = sc.current.version

version = "0.2.0+$minecraft"
base.archivesName = "polyreflex"

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    implementation("net.fabricmc:fabric-loader:0.19.3")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.processResources {
    val props = mapOf("version" to version, "minecraft" to minecraft)
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
