plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.xiaofan"
version = "1.0.0"
description = "FanVerify Plugin"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("dev.folia:folia-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.4.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.google.code.gson:gson:2.11.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.20.1")
        jvmArgs("-Xms2G", "-Xmx2G")
    }


    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
