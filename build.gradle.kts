plugins {
    groovy
    kotlin("jvm") version "1.3.72"
    `java-gradle-plugin`
    idea
    id("com.gradle.plugin-publish") version "0.12.0"
    `maven-publish`
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")
    val spockVersion: String by project
    val junitVersion: String by project
    val jgitVersion: String by project
    val cglibVersion: String by project
    testImplementation("org.spockframework:spock-core:$spockVersion") { exclude(group = "org.codehaus.groovy") }
    testImplementation("junit:junit:$junitVersion")
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
    testImplementation("cglib:cglib-nodep:$cglibVersion")
}

gradlePlugin {
    plugins {
        create("releasePlugin") {
            id = "com.faendir.gradle.release"
            implementationClass = "net.researchgate.release.ReleasePlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/F43nd1r/gradle-release"
    vcsUrl = "https://github.com/F43nd1r/gradle-release"
    description = "gradle-release is a plugin for providing a Maven-like release process to project using Gradle."

    (plugins) {
        "releasePlugin" {
            displayName = "Gradle Release Plugin"
            tags = listOf("release", "git")
            version = project.version.toString()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            from(components["java"])
        }
    }
}

