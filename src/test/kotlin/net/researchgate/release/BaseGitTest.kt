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

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.StoredConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File

abstract class BaseGitTest {

    @TempDir
    lateinit var testDir: File
    lateinit var localGit: Git
    lateinit var remoteGit: Git
    lateinit var buildFile: File
    lateinit var workTree: File

    @BeforeEach
    internal open fun setUp() {
        val remoteRepo = File(testDir, "remote")
        val localRepo = File(testDir, "local")

        remoteGit = Git.init().setDirectory(remoteRepo).call()
        remoteGit.repository.config.setString("receive", null, "denyCurrentBranch", "ignore")
        remoteGit.repository.config.save()

        gitAddAndCommit(remoteGit, "gradle.properties", "version=0.0")

        localGit = Git.cloneRepository().setDirectory(localRepo).setURI(remoteRepo.canonicalPath).call()
        localGit.repository.config.apply {
            setString("user", null, "name", "Unit Test")
            setString("user", null, "email", "unit@test")
            save()
        }

        workTree = localGit.repository.workTree
        buildFile = File(workTree, "build.gradle.kts")
        buildFile.applyPlugin()
        localGit.add().addFilepattern(buildFile.name).call()
        gitAdd(localGit, ".gitignore", ".gradle/")
        localGit.commit().setAll(true).setMessage("initial commit").call()
        localGit.push().call()
    }

    fun updateBuildFile(text: String) {
        buildFile.appendText(text)
        localGit.add().addFilepattern(buildFile.name).call()
        localGit.commit().setAll(true).setMessage("update build file").call()
        localGit.push().call()
    }

    @AfterEach
    internal open fun tearDown() {
        localGit.repository.lockDirCache().unlock()
        localGit.repository.close()
    }

    companion object {
        fun gitAddAndCommit(git: Git, name: String, content:String) {
            gitAdd(git, name, content)
            git.commit().setAll(true).setMessage("commit $name").call()
        }

        fun gitAdd(git: Git, name: String, content:String) {
            File(git.repository.workTree, name).writeText(content)
            git.add().addFilepattern(name).call()
        }

        fun gitHardReset(git: Git) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call()
        }

        fun gitCheckoutBranch(git: Git, branchName: String = "master", createBranch: Boolean = false) {
            git.checkout().setName(branchName).setCreateBranch(createBranch).setForceRefUpdate(true).call()
        }
    }
}