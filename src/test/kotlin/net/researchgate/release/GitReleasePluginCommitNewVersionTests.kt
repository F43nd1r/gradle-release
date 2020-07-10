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

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import java.io.File

class GitReleasePluginCommitNewVersionTests : BaseGitTest() {
    @Test
    internal fun `should push new version to remote tracking branch by default`() {
        File(workTree, "gradle.properties").writeText("version=1.1")
        runGradleTask(workTree, "commitNewVersion", "push")
        gitHardReset(remoteGit)
        expectThat(remoteGit.repository.workTree.listFiles()?.asList()).any { it.name == "gradle.properties" && it.readText().contains("version=1.1")}
    }
    @Test
    internal fun `should push new version to branch using the branch prefix when it is specified`() {
        updateBuildFile("""
            release {
                git.pushToBranchPrefix = "refs/for/"
            }
        """.trimIndent())
        File(workTree, "gradle.properties").writeText("version=1.1")
        runGradleTask(workTree, "commitNewVersion", "push")
        gitCheckoutBranch(remoteGit, "refs/for/master")
        expectThat(remoteGit.repository.workTree.listFiles()?.asList()).any { it.name == "gradle.properties" && it.readText().contains("version=1.1")}
    }
    @Test
    internal fun `should only push the version file to branch when pushVersionFileOnly is true`() {
        updateBuildFile("""
            release {
                git.commitVersionFileOnly = true
            }
        """.trimIndent())
        File(workTree, "gradle.properties").writeText("version=1.1")
        File(workTree, "test.txt").writeText("testTarget")
        runGradleTask(workTree, "commitNewVersion", "push")
        gitHardReset(remoteGit)
        expectThat(remoteGit.repository.workTree.listFiles()?.asList()){
            any { it.name == "gradle.properties" && it.readText().contains("version=1.1")}
            none { it.name == "test.txt"}
        }
    }
}