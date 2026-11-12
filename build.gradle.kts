import dev.kikugie.loomx.LoomCompatDependencyExtension

plugins {
    id("dev.kikugie.loom-back-compat")
}

val minecraft = sc.current.version

version = "0.2.0+$minecraft"
base.archivesName = "polyreflex"

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    dependencies.extensions.getByType<LoomCompatDependencyExtension>().applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:0.19.3")
    if (sc.current.parsed < "26.2") {
        compileOnly("org.lwjgl:lwjgl-vulkan:${if (sc.current.parsed >= "26.1") "3.4.1" else "3.3.3"}") { isTransitive = false }
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(if (sc.current.parsed >= "26.1") 25 else 21)
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "minecraft" to minecraft,
        "polydium" to if (sc.current.parsed < "26.2") ",\n    \"polydium\": \"*\"" else "",
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
