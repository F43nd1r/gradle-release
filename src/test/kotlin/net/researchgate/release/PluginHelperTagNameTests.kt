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

class PluginHelperTagNameTests {

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
        helper = object: PluginHelper() {
            init {
                extension = this@PluginHelperTagNameTests.extension
                project = this@PluginHelperTagNameTests.project
            }
        }
    }

    @Test
    fun `when no includeProjectNameInTag then tag name is version`() {
        expectThat(helper.tagName()).isEqualTo("1.1")
    }

    @Test
    fun `when tagTemplate not blank then it is used as tag name`() {
        extension.tagTemplate = "PREF-\$name-\$version"
        expectThat(helper.tagName()).isEqualTo("PREF-ReleasePluginTest-1.1")
    }
}