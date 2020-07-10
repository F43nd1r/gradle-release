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

import net.researchgate.release.cli.Executor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import java.io.File

class GitReleasePluginTests {

    @TempDir
    lateinit var testDir: File
    lateinit var localRepo: File
    lateinit var remoteRepo: File
    lateinit var buildFile: File
    lateinit var executor: Executor

    @BeforeEach
    internal fun setUp() {
        localRepo = File(testDir, "GitReleasePluginTestLocal")
        remoteRepo = File(testDir, "GitReleasePluginTestRemote")
        buildFile = File(localRepo, "build.gradle.kts")
        executor = Executor()

        // create remote repository
        executor.exec("git", "init", remoteRepo.name, failOnStdErr = true, directory = testDir)
        // suppress errors while pushing
        executor.exec("git", "config", "--add", "receive.denyCurrentBranch", "ignore", failOnStdErr = true, directory = remoteRepo)

        executor.exec("git", "clone", remoteRepo.canonicalPath, localRepo.name, failOnStdErr = true, directory = testDir)
        executor.exec("git", "config", "--add", "user.name", "Unit Test", failOnStdErr = true, directory = localRepo)
        executor.exec("git", "config", "--add", "user.email", "unit@test", failOnStdErr = true, directory = localRepo)

        buildFile.applyPlugin()

        val testFile = File(localRepo, "somename.txt")
        testFile.writeText("test")
        executor.exec("git", "add", testFile.name, failOnStdErr = true, directory = localRepo)
        executor.exec("git", "commit", "-m", "test", testFile.name, failOnStdErr = true, directory = localRepo)

        val props = File(localRepo, "gradle.properties")
        props.writeText("version=1.1")
        executor.exec("git", "add", props.name, failOnStdErr = true, directory = localRepo)
    }

    @Test
    fun `when requireBranch is configured then throw exception when different branch`() {
        buildFile.appendText("""
            release {
                git.requireBranch = "myBranch"
            }
        """.trimIndent())
        failGradleTask(localRepo, "createScmAdapter", "initScmAdapter")
    }

    @Test
    fun `when requireBranch is configured using a regex that matches current branch then don't throw exception`() {
        buildFile.appendText("""
            release {
                git.requireBranch = "myBranch|master"
            }
        """.trimIndent())
        runGradleTask(localRepo, "createScmAdapter", "initScmAdapter")
    }

    @Test
    fun `should accept config as closure`() {
        buildFile.appendText("""
            release {
                git {
                    requireBranch = "master"
                    pushOptions = listOf("--no-verify", "--verbose")
                }
            }
        """.trimIndent())
        runGradleTask(localRepo, "createScmAdapter", "initScmAdapter")
    }

    @Test
    fun `should push new version to remote tracking branch by default`() {
        runGradleTask(localRepo, "createScmAdapter", "initScmAdapter", "commitNewVersion")
        executor.exec("git", "reset", "--hard", "HEAD", failOnStdErr = true, directory = localRepo)
        expectThat(localRepo.listFiles()?.asList()).any { it.name == "gradle.properties" }
    }
}