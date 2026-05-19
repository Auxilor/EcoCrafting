import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.3.1"
}

group = "ru.oftendev"
version = findProperty("version")!!

base {
    archivesName.set(rootProject.name)
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.auxilor.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.willfp:eco:7.6.0")
    compileOnly("com.willfp:EcoShop:2.5.0")
    compileOnly("com.willfp:libreforge:5.4.2")
    compileOnly("org.jetbrains:annotations:26.0.2")

    // Kotlin is not provided by Paper/eco for this plugin's relocated package,
    // so it must be included in the shaded jar.
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")

    // bStats is shaded/relocated into the plugin jar.
    implementation("org.bstats:bstats-bukkit:3.1.0")

    testImplementation(kotlin("test"))
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks {
    compileJava {
        options.isDeprecation = true
        options.encoding = "UTF-8"
    }

    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    processResources {
        filesMatching(listOf("**plugin.yml", "**eco.yml")) {
            expand(
                "version" to project.version,
                "pluginName" to rootProject.name
            )
        }
    }

    shadowJar {
        archiveFileName.set("RecipeBook.jar")
        exclude("META-INF/**")
        relocate("kotlin", "ru.oftendev.recipebook.libs.kotlin")
        relocate("kotlin.jvm", "ru.oftendev.recipebook.libs.kotlin.jvm")
        relocate("kotlin.coroutines", "ru.oftendev.recipebook.libs.kotlin.coroutines")
        relocate("kotlin.reflect", "ru.oftendev.recipebook.libs.kotlin.reflect")
        relocate("org.bstats", "ru.oftendev.recipebook.libs.bstats")
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}

kotlin {
    jvmToolchain(21)
}
