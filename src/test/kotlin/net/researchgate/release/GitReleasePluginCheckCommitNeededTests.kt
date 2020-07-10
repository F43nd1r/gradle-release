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
import strikt.assertions.contains
import java.io.File

class GitReleasePluginCheckCommitNeededTests : BaseGitTest() {

    @Test
    fun `checkCommitNeeded should detect untracked files`() {
        File(workTree, "untracked.txt").writeText("untracked")
        val result = failGradleTask(workTree, "checkCommitNeeded")
        expectThat(result.output) {
            contains("You have unversioned files")
            contains("untracked.txt")
        }
    }

    @Test
    fun `checkCommitNeeded should detect added files`() {
        gitAdd(localGit, "added.txt", "added")
        val result = failGradleTask(workTree, "checkCommitNeeded")
        expectThat(result.output) {
            contains("You have uncommitted files")
            contains("added.txt")
        }
    }

    @Test
    fun `checkCommitNeeded should detect changed files`() {
        gitAddAndCommit(localGit, "changed.txt", "changed1")
        File(workTree, "changed.txt").writeText("changed2")
        val result = failGradleTask(workTree, "checkCommitNeeded")
        expectThat(result.output) {
            contains("You have uncommitted files")
            contains("changed.txt")
        }
    }

    @Test
    fun `checkCommitNeeded should detect modified files`() {
        gitAddAndCommit(localGit, "modified.txt", "modified1")
        gitAdd(localGit, "modified.txt", "modified2")
        val result = failGradleTask(workTree, "checkCommitNeeded")
        expectThat(result.output) {
            contains("You have uncommitted files")
            contains("modified.txt")
        }
    }


}