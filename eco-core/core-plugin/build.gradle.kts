group = "io.auxilor"
version = rootProject.version

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    // So Bukkit/Paper types (e.g. ItemStack) resolve at test-compile time.
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
}

tasks {
    build {
        dependsOn(publishToMavenLocal)
    }
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            from(components["java"])
            artifactId = rootProject.name
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
