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
import strikt.api.expectThat
import strikt.assertions.contains
import java.io.File

class GitReleasePluginMultiProjectTests : BaseGitTest() {
    private lateinit var subProject: File

    @BeforeEach
    override fun setUp() {
        super.setUp()
        subProject = File(workTree, "sub")
        subProject.mkdir()
        val subBuild = File(subProject, "build.gradle.kts")
        subBuild.applyPlugin()
        localGit.add().addFilepattern(subBuild.name).call()
        gitAdd(localGit, "settings.gradle.kts", "include(\"sub\")")
        localGit.commit().setAll(true).setMessage("Init subproject").call()
        localGit.push().call()
    }

    @Test
    internal fun `subproject should work with git being in parentProject`() {
        runGradleTask(subProject, "checkUpdateNeeded")
    }

    @Test
    internal fun `checkUpdateNeeded should detect remote changes to pull in subproject`() {
        gitAddAndCommit(remoteGit, "gradle.properties", "222")
        val result = failGradleTask(subProject, "checkUpdateNeeded")
        expectThat(result.output).contains("You have 1 remote change(s) to pull.")
    }
}