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

class GitReleasePluginCheckUpdateNeededTests : BaseGitTest() {
    @Test
    internal fun `checkUpdateNeeded should detect local changes to push`() {
        gitAddAndCommit(localGit, "gradle.properties", "111")
        val result = failGradleTask(workTree, "checkUpdateNeeded")
        expectThat(result.output).contains("You have 1 local change(s) to push.")
    }

    @Test
    internal fun `checkUpdateNeeded should detect remote changes to pull`() {
        gitAddAndCommit(remoteGit, "gradle.properties", "222")
        val result = failGradleTask(workTree, "checkUpdateNeeded")
        expectThat(result.output).contains("You have 1 remote change(s) to pull.")
    }
}