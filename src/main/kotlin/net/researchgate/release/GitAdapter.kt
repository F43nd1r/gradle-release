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

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

class GitAdapter(project: Project, attributes: Attributes) : BaseScmAdapter(project, attributes) {
    companion object {
        const val LINE = "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        val BASIC_ERROR_PATTERNS = listOf("error: ", "fatal: ")
    }

    private data class LocalStatus(val uncommitted: List<String>, val unversioned: List<String>)
    private data class RemoteStatus(val ahead: Int, val behind: Int)

    private lateinit var workingBranch: String
    private lateinit var releaseBranch: String
    private lateinit var workingDirectory: File

    /**
     * tags/commits created by the release process which are yet to be pushed
     */
    private val push = mutableListOf<List<String>>()

    /**
     * the latest commit before the release process started
     */
    private lateinit var head: String

    /**
     * the tag created by the release process, if any
     */
    private var tag: String? = null

    class GitConfig {
        var requireBranch: String? = "master"
        var pushToRemote: String? = "origin"
        var pushOptions: List<String> = emptyList()
        var signTag: Boolean = false
        var pushToBranchPrefix: String? = null
        var commitVersionFileOnly: Boolean = false
    }

    override fun isSupported(directory: File): Boolean {
        return directory.list()?.contains(".git")?.falseToNull()?.also { workingDirectory = directory } ?: directory.parentFile?.let { isSupported(it) } ?: false
    }

    override fun init() {
        push.clear()
        workingBranch = gitCurrentBranch()
        head = gitCurrentCommit()
        releaseBranch = extension.pushReleaseVersionBranch ?: workingBranch
        tag = null
        extension.git.requireBranch?.let {
            if (!Regex(it).matches(workingBranch)) {
                throw GradleException("""Current Git branch is "$workingBranch" and not "${extension.git.requireBranch}".""")
            }
        }
    }

    override fun checkCommitNeeded() {
        val status = gitStatus()
        if (status.unversioned.isNotEmpty()) {
            warnOrThrow(extension.failOnUnversionedFiles, listOf("You have unversioned files:", LINE, *status.unversioned.toTypedArray(), LINE).joinToString("\n"))
        }
        if (status.uncommitted.isNotEmpty()) {
            warnOrThrow(extension.failOnCommitNeeded, listOf("You have uncommitted files:", LINE, *status.uncommitted.toTypedArray(), LINE).joinToString("\n"))
        }
    }

    override fun checkUpdateNeeded() {
        executor.exec("git", "remote", "update", directory = workingDirectory, errorPatterns = BASIC_ERROR_PATTERNS)
        val status = gitRemoteStatus()
        if (status.ahead != 0) {
            warnOrThrow(extension.failOnPublishNeeded, "You have ${status.ahead} local change(s) to push.")
        }
        if (status.behind != 0) {
            warnOrThrow(extension.failOnUpdateNeeded, "You have ${status.behind} remote change(s) to pull.")
        }
    }

    override fun createReleaseTag(message: String) {
        val tagName = tagName()
        executor.exec("git", "tag", "-a", tagName, "-m", message, *optionalArg(extension.git.signTag, "-s"), directory = workingDirectory,
                errorPatterns = listOf("already exists", "failed to sign"),
                errorMessage = "Duplicate tag [$tagName] or signing error")
        tag = tagName
        push.add(listOfNotNull(extension.git.pushToRemote, tagName))
    }

    override fun commit(message: String) {
        executor.exec("git", "commit", "-m", message, if (extension.git.commitVersionFileOnly) project.file(extension.versionPropertyFile).canonicalPath else "-a",
                directory = workingDirectory, errorPatterns = BASIC_ERROR_PATTERNS)
        val branch = (extension.git.pushToBranchPrefix?.let { "HEAD:$it" } ?: "") + gitCurrentBranch()
        push.add(listOfNotNull(extension.git.pushToRemote, branch))
    }

    override fun add(file: File) {
        executor.exec("git", "add", file.canonicalPath, directory = workingDirectory, errorPatterns = BASIC_ERROR_PATTERNS, errorMessage = "Error adding file ${file.name}")
    }

    override fun push() {
        if (shouldPush()) push.forEach {
            executor.exec("git", "push", "--porcelain", *(it + extension.git.pushOptions).toTypedArray(), directory = workingDirectory,
                    errorPatterns = BASIC_ERROR_PATTERNS + "[rejected]", errorMessage = "Failed to push to remote: $it")
        }
    }

    override fun revert() {
        val gitCurrentCommit = gitCurrentCommit()
        if (::head.isInitialized && head != gitCurrentCommit) {
            log.info("Reverting commits...")
            executor.exec("git", "reset", "--soft", head, directory = workingDirectory, errorMessage = "Failed to revert commits made by the release plugin.")
            head = gitCurrentCommit
        }
        tag?.let {
            log.info("Reverting tag...")
            executor.exec("git", "tag", "--delete", it, directory = workingDirectory, errorMessage = "Failed to revert tag made by the release plugin.")
            tag = null
        }
        log.info("Reverting property file...")
        executor.exec("git", "checkout", "HEAD", "--", propertiesFile.name, directory = workingDirectory, errorMessage = "Failed to revert changes made by the release plugin.")
    }

    override fun checkoutMergeToReleaseBranch() = checkoutMerge(workingBranch, releaseBranch)

    override fun checkoutMergeFromReleaseBranch() = checkoutMerge(releaseBranch, workingBranch)

    private fun checkoutMerge(fromBranch: String, toBranch: String) {
        executor.exec("git", "fetch", directory = workingDirectory, errorPatterns = BASIC_ERROR_PATTERNS)
        executor.exec("git", "checkout", toBranch, directory = workingDirectory, errorPatterns = BASIC_ERROR_PATTERNS)
        executor.exec("git", "merge", "--no-ff", "--no-commit", fromBranch, directory = workingDirectory, errorPatterns = BASIC_ERROR_PATTERNS + "CONFLICT")
    }

    private fun shouldPush(): Boolean {
        return extension.git.pushToRemote?.let { remote ->
            val shouldPush = executor.exec("git", "remote", directory = workingDirectory).lines().any { Regex("^\\s*(.*)\\s*$").matchEntire(it)?.groupValues[1] == remote }
            if (!shouldPush && remote != "origin") {
                throw GradleException("Could not push to remote ${extension.git.pushToRemote} as repository has no such remote")
            }
            shouldPush
        } ?: false
    }

    private fun gitCurrentBranch(): String {
        val matches = executor.exec("git", "branch", "--no-color", directory = workingDirectory).lines().filter { it.matches(Regex("""\s*\*.*""")) }
        if (matches.isNotEmpty()) {
            return matches[0].trim().replace(Regex("""^\*\s+"""), "")
        } else {
            throw GradleException("Error, this repository is empty.")
        }
    }

    private fun gitStatus(): LocalStatus {
        return executor.exec("git", "status", "--porcelain", directory = workingDirectory)
                .lines()
                .filter { it.isNotBlank() }
                .partition { it.matches(Regex("""^\s*\?{2}.*""")) /* unversioned files are noted on lines starting with "??" */ }
                .let { LocalStatus(unversioned = it.first, uncommitted = it.second) }
    }

    private fun gitCurrentCommit(): String {
        return executor.exec("git", "rev-parse", "--verify", "--porcelain", "HEAD", directory = workingDirectory).trim()
    }

    private fun gitRemoteStatus(): RemoteStatus {
        val branchStatus = executor.exec("git", "status", "--porcelain", "-b", directory = workingDirectory).lines()[0]
        return RemoteStatus(ahead = Regex(""".*ahead (\d+).*""").matchEntire(branchStatus)?.groupValues[1]?.toInt() ?: 0,
                behind = Regex(""".*behind (\d+).*""").matchEntire(branchStatus)?.groupValues[1]?.toInt() ?: 0)
    }
}