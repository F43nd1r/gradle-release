/*
 * This file is part of the gradle-release plugin.
 *
 * It was modified and ported to kotlin by
 * (c) F43nd1r
 *
 * Original source by
 * (c) Eric Berry
 * (c) ResearchGate GmbH
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 *
 */

package net.researchgate.release

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isTrue
import java.io.File

class ReleasePluginTests {

    @TempDir
    lateinit var testDir: File
    lateinit var buildFile: File

    @BeforeEach
    internal fun setUp() {
        buildFile = File(testDir, "build.gradle.kts")
        buildFile.applyPlugin()
        buildFile.addTestAdapter()
    }

    @Test
    fun `plugin is successfully applied`() {
        val result = runGradleTask(testDir, "tasks")
        expectThat(result.output).contains("release")
    }

    @Test
    fun `when a custom properties file is used to specify the version`() {
        File(testDir, "version.properties").writeText("version=1.2")
        buildFile.appendText("""
            release {
                versionPropertyFile = "version.properties"
            }
            tasks.register("getVersion") {
                dependsOn("unSnapshotVersion")
                doLast {
                    println("~~~${'$'}{project.version}~~~")
                }
            }
        """.trimIndent())
        val result = runGradleTask(testDir, "getVersion")
        expectThat(result.output).contains("~~~1.2~~~")
    }

    @Test
    fun `version is properly unsnapshot when using default snapshot suffix`() {
        File(testDir, "gradle.properties").writeText("version=1.3-SNAPSHOT")
        buildFile.appendText("""
            tasks.register("getVersion") {
                dependsOn("unSnapshotVersion")
                doLast {
                    println("~~~${'$'}{project.version}~~~")
                }
            }
        """.trimIndent())
        val result = runGradleTask(testDir, "getVersion")
        expectThat(result.output).contains("~~~1.3~~~")
    }

    @Test
    fun `version is properly unsnapshot when using custom snapshot suffix`() {
        File(testDir, "gradle.properties").writeText("version=1.4-dev")
        buildFile.appendText("""
            release {
                snapshotSuffix = "-dev"
            }
            tasks.register("getVersion") {
                dependsOn("unSnapshotVersion")
                doLast {
                    println("~~~${'$'}{project.version}~~~")
                }
            }
        """.trimIndent())
        val result = runGradleTask(testDir, "getVersion")
        expectThat(result.output).contains("~~~1.4~~~")
    }

    @Test
    fun `version cannot be unsnapshot when using invalid snapshot suffix`() {
        File(testDir, "gradle.properties").writeText("version=1.4-dev")
        buildFile.appendText("""
            release {
                snapshotSuffix = "-SNAPSHOT"
            }
            tasks.register("getVersion") {
                dependsOn("unSnapshotVersion")
                doLast {
                    println("~~~${'$'}{project.version}~~~")
                }
            }
        """.trimIndent())
        val result = runGradleTask(testDir, "getVersion")
        expectThat(result.output).contains("~~~1.4-dev~~~")
    }

    @Test
    fun `snapshot version should be updated to new snapshot version with default snapshot suffix`() {
        File(testDir, "gradle.properties").writeText("version=1.4-SNAPSHOT")
        buildFile.appendText("""
            tasks.register("getVersion") {
                dependsOn("updateVersion")
                doLast {
                    println("~~~${'$'}{project.version}~~~")
                }
            }
        """.trimIndent())
        val result = runGradleTask(testDir, "getVersion", "-Prelease.useAutomaticVersion=true")
        expectThat(result.output).contains("~~~1.5-SNAPSHOT~~~")
    }

    @Test
    fun `snapshot version should be updated to new snapshot version with custom snapshot suffix`() {
        File(testDir, "gradle.properties").writeText("version=1.4-dev")
        buildFile.appendText("""
            release {
                snapshotSuffix = "-dev"
            }
            tasks.register("getVersion") {
                dependsOn("updateVersion")
                doLast {
                    println("~~~${'$'}{project.version}~~~")
                }
            }
        """.trimIndent())
        val result = runGradleTask(testDir, "getVersion", "-Prelease.useAutomaticVersion=true")
        expectThat(result.output).contains("~~~1.5-dev~~~")
    }

    @ParameterizedTest
    @CsvSource(value = ["1.4-SNAPSHOT,1.4", "1.4-SNAPSHOT+meta,1.4+meta", "1.4-SNAPSHOT+3.2.1,1.4+3.2.1", "1.4-SNAPSHOT+rel-201908,1.4+rel-201908"])
    fun `version should be unsnapshot to release version`(currentVersion: String, expectedVersion: String) {
        File(testDir, "gradle.properties").writeText("version=$currentVersion")
        buildFile.appendText("""
            tasks.register("getVersion") {
                dependsOn("unSnapshotVersion")
                doLast {
                    println("~~~${'$'}{project.version}~~~")
                }
            }
        """.trimIndent())
        val result = runGradleTask(testDir, "getVersion", "-Prelease.useAutomaticVersion=true")
        expectThat(result.output).contains("~~~$expectedVersion~~~")
    }

    @ParameterizedTest
    @CsvSource(value = ["1.4-SNAPSHOT,1.5-SNAPSHOT", "1.4-SNAPSHOT+meta,1.5-SNAPSHOT+meta", "1.4+meta,1.5+meta", "1.4,1.5", "1.4.2,1.4.3"])
    fun `version should be updated to new version with default versionPatterns`(currentVersion: String, expectedVersion: String) {
        File(testDir, "gradle.properties").writeText("version=$currentVersion")
        buildFile.appendText("""
            tasks.register("getVersion") {
                dependsOn("updateVersion")
                doLast {
                    println("~~~${'$'}{project.version}~~~")
                }
            }
        """.trimIndent())
        val result = runGradleTask(testDir, "getVersion", "-Prelease.useAutomaticVersion=true")
        expectThat(result.output).contains("~~~$expectedVersion~~~")
    }

    @ParameterizedTest
    @CsvSource(value = ["1.4-SNAPSHOT,1.5-SNAPSHOT", "1.4-SNAPSHOT+meta,1.5-SNAPSHOT+meta", "1.4-SNAPSHOT+3.2.1,1.5-SNAPSHOT+3.2.1",
        "1.4-SNAPSHOT+rel-201908,1.5-SNAPSHOT+rel-201908", "1.4+meta,1.5+meta", "1.4,1.5", "1.4.2,1.4.3", "1.4.2+4.5.6,1.4.3+4.5.6", "1.4+rel-201908,1.5+rel-201908"])
    fun `version should be updated to new version with semver based versionPatterns`(currentVersion: String, expectedVersion: String) {
        File(testDir, "gradle.properties").writeText("version=$currentVersion")
        buildFile.appendText("""
            release {
                versionPatterns = mapOf("(\\d+)([^\\d]*|[-\\+].*)${'$'}" to { m -> "${'$'}{(m.groupValues[1].toInt()) + 1}${'$'}{m.groupValues[2]}" })
            }
            tasks.register("getVersion") {
                dependsOn("updateVersion")
                doLast {
                    println("~~~${'$'}{project.version}~~~")
                }
            }
        """.trimIndent())
        val result = runGradleTask(testDir, "getVersion", "-Prelease.useAutomaticVersion=true")
        expectThat(result.output).contains("~~~$expectedVersion~~~")
    }

    @Test
    fun `subproject tasks are named with qualified paths`() {
        val sub = File(testDir, "sub")
        sub.mkdir()
        val subBuild = File(sub, "build.gradle.kts")
        subBuild.writeText("""
            plugins {
                java
                id("com.faendir.gradle.release")
            }
            
            tasks.register("printTaskPaths") {
                doLast {
                    tasks.forEach { println(it.path) }
                }
            }
        """.trimIndent())
        File(testDir, "settings.gradle.kts").writeText("include(\"sub\")")
        val result = runGradleTask(testDir, "printTaskPaths")
        expectThat(result.output).contains(":sub:release")
    }
}