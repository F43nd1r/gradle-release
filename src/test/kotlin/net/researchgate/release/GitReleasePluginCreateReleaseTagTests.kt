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

class GitReleasePluginCreateReleaseTagTests : BaseGitTest() {
    @Test
    internal fun `createReleaseTag should create tag and push to remote`() {
        runGradleTask(workTree, "createReleaseTag", "push")
        expectThat(localGit.tagList().call()).any { it.name == "refs/tags/0.0" }
        expectThat(remoteGit.tagList().call()).any { it.name == "refs/tags/0.0" }
    }

    @Test
    internal fun `createReleaseTag should throw exception when tag exist`() {
        localGit.tag().setName("0.0").call()
        failGradleTask(workTree, "createReleaseTag")
    }

    @Test
    internal fun `createReleaseTag with configured remote should push to it`() {
        updateBuildFile("""
            release.git.pushToRemote = "myRemote"
        """.trimIndent())
        localGit.repository.config.apply {
            setString("remote", "myRemote", "url", remoteGit.repository.directory.canonicalPath)
            save()
        }
        runGradleTask(workTree, "createReleaseTag", "push")
        expectThat(localGit.tagList().call()).any { it.name == "refs/tags/0.0" }
        expectThat(remoteGit.tagList().call()).any { it.name == "refs/tags/0.0" }
    }
}