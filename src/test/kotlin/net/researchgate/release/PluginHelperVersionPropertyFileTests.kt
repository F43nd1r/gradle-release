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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.File

class PluginHelperVersionPropertyFileTests {

    @TempDir
    lateinit var testDir: File
    lateinit var project: Project
    lateinit var extension: ReleaseExtension
    lateinit var helper: PluginHelper

    @BeforeEach
    internal fun setUp() {
        project = ProjectBuilder.builder().withName("ReleasePluginTest").withProjectDir(testDir).build()
        project.version = "1.1"
        extension = project.extensions.create("release", ReleaseExtension::class.java, project)
        helper = object : PluginHelper() {
            init {
                extension = this@PluginHelperVersionPropertyFileTests.extension
                project = this@PluginHelperVersionPropertyFileTests.project
            }
        }
        File(testDir, "gradle.properties").writeText("version=1.1")
    }

    @Test
    fun `should find gradle properties by default`() {
        expectThat(helper.propertiesFile.name).isEqualTo("gradle.properties")
    }

    @Test
    fun `should find properties from convention`() {
        val file = File(testDir, "custom.properties")
        file.writeText("version=1.2")
        extension.versionPropertyFile = file.name
        expectThat(helper.propertiesFile.name).isEqualTo("custom.properties")
    }

    @Test
    fun `by default should update version property from props file`() {
        helper.updateVersionProperty("2.2")
        expectThat(File(testDir, "gradle.properties").readLines()).contains("version=2.2")
    }

    @Test
    fun `when configured then update version and additional properties from props file`() {
        val file = File(testDir, "custom.properties")
        file.writeText("""
            version=1.1
            version1=1.1
            version2=1.1
        """.trimIndent())
        extension.versionPropertyFile = file.name
        extension.versionProperties = listOf("version1")
        helper.updateVersionProperty("2.2")
        expectThat(file.readLines()) {
            contains("version=2.2")
            contains("version1=2.2")
            contains("version2=1.1")
        }
    }

    @Test
    fun `should update version of project and subprojects`() {
        val proj1 = ProjectBuilder.builder().withParent(project).withName("proj1").build()
        proj1.version = project.version
        val proj2 = ProjectBuilder.builder().withParent(project).withName("proj2").build()
        proj2.version = project.version
        helper.updateVersionProperty("2.2")
        expectThat(project.version).isEqualTo("2.2")
        expectThat(proj1.version).isEqualTo("2.2")
        expectThat(proj2.version).isEqualTo("2.2")
    }

    @Test
    fun `should not fail when version contains spaces`() {
        val file = File(testDir, "gradle.properties")
        file.writeText("""
            version = 1.1
            version1 : 1.1
            version2   1.1
            """.trimIndent())
        extension.versionProperties = listOf("version1", "version2")
        helper.updateVersionProperty("2.2")
        expectThat(file.readLines()) {
            contains("version = 2.2")
            contains("version1 : 2.2")
            contains("version2   2.2")
        }
    }

    @Test
    fun `should not escape other stuff`() {
        val file = File(testDir, "gradle.properties")
        file.writeText("""
            version=1.1
            something=http://www.gradle.org/test
              another.prop.version =  1.1
            """.trimIndent())
        helper.updateVersionProperty("2.2")
        expectThat(file.readLines()) {
            contains("version=2.2")
            contains("something=http://www.gradle.org/test")
            contains("  another.prop.version =  1.1")
        }
    }

    @Test
    fun `should not fail on other property separators`() {
        val file = File(testDir, "gradle.properties")
        file.writeText("""
            version=1.1
            version1:1.1
            version2 1.1
            """.trimIndent())
        extension.versionProperties = listOf("version1", "version2")
        helper.updateVersionProperty("2.2")
        expectThat(file.readLines()) {
            contains("version=2.2")
            contains("version1:2.2")
            contains("version2 2.2")
        }
    }
}