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
import java.io.File

class ReleasePluginCheckSnapshotDependenciesTests {

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
    fun `when no deps then no exception`() {
        runGradleTask(testDir, "checkSnapshotDependencies")
    }

    @Test
    fun `when no SNAPSHOT deps then no exception`() {
        buildFile.appendText("""
            buildscript {
                dependencies {
                    classpath("junit:junit:4.12")
                    classpath("org.slf4j:slf4j-api:1.7.30")
                }
            }
            
            val custom by configurations.creating
            
            dependencies {
                implementation("junit:junit:4.12")
                custom("org.slf4j:slf4j-api:1.7.30")
            }
        """.trimIndent())
        runGradleTask(testDir, "checkSnapshotDependencies")
    }

    @Test
    fun `when SNAPSHOT in buildscript cfg then exception`() {
        buildFile.appendText("""
            buildscript {
                repositories { maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") } }
                dependencies {
                    classpath("com.squareup.retrofit:retrofit:2.0.0-SNAPSHOT")
                }
            }
        """.trimIndent())
        failGradleTask(testDir, "checkSnapshotDependencies")
    }

    @Test
    fun `when SNAPSHOT in custom deps then exception`() {
        buildFile.appendText("""
            val custom by configurations.creating
            
            repositories { maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") } }
            dependencies {
                custom("com.squareup.retrofit:retrofit:2.0.0-SNAPSHOT")
             }
        """.trimIndent())
        failGradleTask(testDir, "checkSnapshotDependencies")
    }

    @Test
    fun `when SNAPSHOT in subprojects then exception`() {
        val sub = File(testDir, "sub")
        sub.mkdir()
        val subBuild = File(sub, "build.gradle.kts")
        subBuild.applyPlugin()
        subBuild.appendText("""
            repositories { maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") } }
            dependencies {
                implementation("com.squareup.retrofit:retrofit:2.0.0-SNAPSHOT")
             }
        """.trimIndent())
        File(testDir, "settings.gradle.kts").writeText("include(\"sub\")")
        failGradleTask(testDir, "checkSnapshotDependencies")
    }

    @Test
    fun `when a SNAPSHOT dep is ignored then no exception`() {
        buildFile.appendText("""
            release {
                ignoredSnapshotDependencies = listOf("com.squareup.retrofit:retrofit")
            }
            repositories { maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots/") } }
            dependencies {
                implementation("com.squareup.retrofit:retrofit:2.0.0-SNAPSHOT")
             }
        """.trimIndent())
        runGradleTask(testDir, "checkSnapshotDependencies")
    }
}