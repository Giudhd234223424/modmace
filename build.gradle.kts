plugins {
    id("fabric-loom") version "1.14-SNAPSHOT"
}

group = project.property("maven_group") as String
version = project.property("mod_version") as String

base {
    archivesName = project.property("archives_base_name") as String
}

repositories {
    maven { name = "meteor-maven"; url = uri("https://maven.meteordev.org/releases") }
    maven { name = "meteor-maven-snapshots"; url = uri("https://maven.meteordev.org/snapshots") }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("meteordevelopment:meteor-client:${project.property("meteor_version")}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

fun minecraftCompat(version: String): String {
    val stable = Regex("""^(\\d{2})\\.([1-9]\\d*)(?:\\.(\\d+))?$""")
    stable.matchEntire(version)?.let {
        val (year, drop, _) = it.destructured
        return "~$year.$drop"
    }
    return version
}

tasks {
    processResources {
        val props = mapOf(
            "version" to project.version,
            "minecraft_version" to minecraftCompat(project.property("minecraft_version") as String),
            "loader_version" to project.property("loader_version")
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${project.property("archives_base_name")}" }
        }
    }

    withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }
}
