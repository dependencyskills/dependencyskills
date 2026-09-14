plugins {
    kotlin("jvm") version "2.4.20"
    `maven-publish`
}

group = "com.example.acme"
version = "0.1.0"

repositories { mavenCentral() }
java { withSourcesJar() }

publishing {
    publications { create<MavenPublication>("lib") { from(components["java"]) } }
    repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) } }
}
