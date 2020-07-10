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
import strikt.assertions.isEmpty
import java.io.File

class GitReleasePluginIntegrationTests : BaseGitTest() {

    @Test
    internal fun `integration test`() {
        gitAddAndCommit(localGit, "gradle.properties", "version=1.1")
        localGit.push().call()
        runGradleTask(workTree, "release", "-Prelease.useAutomaticVersion=true", "-x", "runBuildTasks")
        val status = localGit.status().call()
        gitHardReset(remoteGit)
        expectThat(File(workTree, "gradle.properties").readLines()).contains("version=1.2")
        expectThat(status) {
            get { modified }.isEmpty()
            get { added }.isEmpty()
            get { changed }.isEmpty()
        }
        expectThat(localGit.tagList().call()).any { it.name == "refs/tags/1.1" }
        expectThat(workTree.listFiles()?.asList()).any { it.name == "gradle.properties" && it.readLines().contains("version=1.2") }
        expectThat(remoteGit.tagList().call()).any { it.name == "refs/tags/1.1" }
        expectThat(remoteGit.repository.workTree.listFiles()?.asList()).any { it.name == "gradle.properties" && it.readLines().contains("version=1.2") }
    }
}