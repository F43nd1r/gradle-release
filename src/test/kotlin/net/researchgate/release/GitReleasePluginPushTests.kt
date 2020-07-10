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

class GitReleasePluginPushTests : BaseGitTest() {

    @Test
    internal fun `push with configured but non existent remote should throw exception`() {
        updateBuildFile("""
            release.git.pushToRemote = "myRemote"
        """.trimIndent())
        val result = failGradleTask(workTree, "createReleaseTag", "push")
        expectThat(result.output).contains("myRemote")
    }
}