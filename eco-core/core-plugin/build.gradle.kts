group = "io.auxilor"
version = rootProject.version

val freeVersion = rootProject.hasProperty("free")
val buildConfigDir = layout.buildDirectory.dir("generated/buildconfig")

sourceSets {
    main {
        kotlin.srcDir(buildConfigDir)
    }
}

val generateBuildConfig by tasks.registering {
    inputs.property("freeVersion", freeVersion)
    outputs.dir(buildConfigDir)
    doFirst {
        val file = buildConfigDir.get().file("io/auxilor/ecocrafting/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.auxilor.ecocrafting

            object BuildConfig {
                const val FREE_VERSION = $freeVersion
            }
            """.trimIndent()
        )
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
}

tasks {
    compileKotlin {
        dependsOn(generateBuildConfig)
    }

    named("sourcesJar") {
        dependsOn(generateBuildConfig)
    }

    build {
        dependsOn(publishToMavenLocal)
    }
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            from(components["java"])
            artifactId = if (freeVersion) "${rootProject.name}-Free" else rootProject.name
        }
    }

    repositories {
        maven {
            name = "Auxilor"
            url = uri("https://repo.auxilor.io/repository/maven-releases/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}
