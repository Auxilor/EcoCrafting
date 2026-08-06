group = "com.exanthiax"
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
        val file = buildConfigDir.get().file("com/exanthiax/ecocrafting/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.exanthiax.ecocrafting

            object BuildConfig {
                const val FREE_VERSION = $freeVersion
            }
            """.trimIndent()
        )
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    api(project(":eco-core:core-api"))

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Model/service tests construct real eco/libreforge value types (ConditionList, Chain,
    // ConfiguredPrice) rather than mocking them - those are compileOnly in production (the
    // server provides them at runtime) but need to be present on the test runtime classpath.
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    testImplementation("com.willfp:eco:${rootProject.findProperty("eco-version")}")
    testImplementation("com.willfp:libreforge-loader:${rootProject.findProperty("libreforge-version")}")
    testImplementation("com.willfp:libreforge:${rootProject.findProperty("libreforge-version")}:shadow") {
        // The shadow jar already bundles its own dependencies; the published module
        // metadata's transitive deps point at bad coordinates that don't resolve.
        isTransitive = false
    }
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

    test {
        useJUnitPlatform()
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
