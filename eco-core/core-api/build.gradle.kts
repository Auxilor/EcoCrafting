group = "com.exanthiax"
version = rootProject.version

// The root build's `allprojects` block wires compileJava -> dependsOn(clean) (meant for
// projects with real Java sources). This module is Kotlin-only (compileJava is NO-SOURCE),
// so that edge only races clean against compileKotlin when this module's jar is resolved
// as a project dependency - clear it rather than risk wiping just-compiled classes.
tasks.named("compileJava") {
    dependsOn.clear()
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            from(components["java"])
            artifactId = "ecocrafting-api"
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
