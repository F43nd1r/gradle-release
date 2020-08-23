/*
 * This file is part of the gradle-release plugin.
 *
 * (c) F43nd1r
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 *
 */

plugins {
    kotlin("jvm") version "1.3.72"
    `java-gradle-plugin`
    idea
    id("com.gradle.plugin-publish") version "0.12.0"
    `maven-publish`
    id("com.faendir.gradle.release") version "3.3.1"
}

group="com.faendir.gradle"

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")
    val junitVersion: String by project
    val striktVersion: String by project
    val mockkVersion: String by project
    val jgitVersion: String by project
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("io.strikt:strikt-core:$striktVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation(gradleKotlinDsl())
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
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

release {
    tagTemplate = "v\$version"
}

tasks.named("afterReleaseBuild").configure {
    dependsOn("publishPlugins")
}

