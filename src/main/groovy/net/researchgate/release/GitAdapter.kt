/*
 * This file is part of the gradle-release plugin.
 *
 * (c) Eric Berry
 * (c) ResearchGate GmbH
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package net.researchgate.release

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.util.regex.Pattern

class GitAdapter(project: Project, attributes: Map<String, Any>) : BaseScmAdapter(project, attributes) {
    companion object {
        const val LINE = "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        const val UNCOMMITTED = "uncommitted"
        const val UNVERSIONED = "unversioned"
        const val AHEAD = "ahead"
        const val BEHIND = "behind"
    }

    private var workingBranch: String? = null
    private var releaseBranch: String? = null

    private lateinit var workingDirectory: File

    class GitConfig {
        var requireBranch: String? = "master"
        var pushToRemote: String? = "origin"
        var pushOptions: List<String> = emptyList()
        var signTag: Boolean = false
        var pushToBranchPrefix: String? = null
        var commitVersionFileOnly: Boolean = false
    }

    override fun isSupported(directory: File): Boolean {
        if (directory.list()?.contains(".git") != true) {
            return directory.parentFile?.let { isSupported(it) } ?: false
        }
        workingDirectory = directory
        return true
    }

    override fun init() {
        workingBranch = gitCurrentBranch()
        releaseBranch = extension.pushReleaseVersionBranch ?: workingBranch
        if (extension.git.requireBranch != null) {
            if (workingBranch != extension.git.requireBranch) {
                throw GradleException("Current Git branch is \"$workingBranch\" and not \"${extension.git.requireBranch}\".")
            }
        }
    }

    override fun checkCommitNeeded() {
        val status = gitStatus()

        if (status[UNVERSIONED] != null) {
            warnOrThrow(extension.failOnUnversionedFiles, listOf("You have unversioned files:", LINE, *status.getValue(UNVERSIONED).toTypedArray(), LINE).joinToString("\n"))
        }

        if (status[UNCOMMITTED] != null) {
            warnOrThrow(extension.failOnCommitNeeded, listOf("You have uncommitted files:", LINE, *status.getValue(UNCOMMITTED).toTypedArray(), LINE).joinToString("\n"))
        }
    }

    override fun checkUpdateNeeded() {
        exec(listOf("git", "remote", "update"), directory = workingDirectory, errorPatterns = listOf("error: ", "fatal: "))

        val status = gitRemoteStatus()

        if (status[AHEAD] != null) {
            warnOrThrow(extension.failOnPublishNeeded, "You have ${status[AHEAD]} local change(s) to push.")
        }

        if (status[BEHIND] != null) {
            warnOrThrow(extension.failOnUpdateNeeded, "You have ${status[BEHIND]} remote change(s) to pull.")
        }
    }

    override fun createReleaseTag(message: String) {
        val tagName = tagName()
        val params = mutableListOf("git", "tag", "-a", tagName, "-m", message)
        if (extension.git.signTag) {
            params.add("-s")
        }
        exec(params,
                directory = workingDirectory,
                errorMessage = "Duplicate tag [$tagName] or signing error",
                errorPatterns = listOf("already exists", "failed to sign"))
        if (shouldPush()) {
            exec(listOf("git", "push", "--porcelain", extension.git.pushToRemote!!, tagName) + extension.git.pushOptions,
                    directory = workingDirectory,
                    errorMessage = "Failed to push tag [$tagName] to remote",
                    errorPatterns = listOf("[rejected]", "error: ", "fatal: "))
        }
    }

    override fun commit(message: String) {
        val command = mutableListOf("git", "commit", "-m", message)
        if (extension.git.commitVersionFileOnly) {
            command.add(project.file(extension.versionPropertyFile).toString())
        } else {
            command.add("-a")
        }

        exec(command, directory = workingDirectory, errorPatterns = listOf("error: ", "fatal: "))

        if (shouldPush()) {
            var branch = gitCurrentBranch()
            if (extension.git.pushToBranchPrefix != null) {
                branch = "HEAD:${extension.git.pushToBranchPrefix}${branch}"
            }
            exec(listOf("git", "push", "--porcelain", extension.git.pushToRemote!!, branch) + extension.git.pushOptions,
                    directory = workingDirectory,
                    errorMessage = "Failed to push to remote",
                    errorPatterns = listOf("[rejected]", "error: ", "fatal: "))
        }
    }

    override fun add(file: File) {
        exec(listOf("git", "add", file.path),
                directory = workingDirectory,
                errorMessage = "Error adding file ${file.name}",
                errorPatterns = listOf("error: ", "fatal: "))
    }

    override fun revert() {
        // Revert changes on gradle.properties
        exec(listOf("git", "checkout", findPropertiesFile().name), directory = workingDirectory, errorMessage = "Error reverting changes made by the release plugin.")
    }

    override fun checkoutMergeToReleaseBranch() {
        checkoutMerge(workingBranch!!, releaseBranch!!)
    }

    override fun checkoutMergeFromReleaseBranch() {
        checkoutMerge(releaseBranch!!, workingBranch!!)
    }

    private fun checkoutMerge(fromBranch: String, toBranch: String) {
        exec(listOf("git", "fetch"), directory = workingDirectory, errorPatterns = listOf("error: ", "fatal: "))
        exec(listOf("git", "checkout", toBranch), directory = workingDirectory, errorPatterns = listOf("error: ", "fatal: "))
        exec(listOf("git", "merge", "--no-ff", "--no-commit", fromBranch), directory = workingDirectory, errorPatterns = listOf("error: ", "fatal: ", "CONFLICT"))
    }

    private fun shouldPush(): Boolean {
        var shouldPush = false
        if (extension.git.pushToRemote != null) {
            exec(listOf("git", "remote"), directory = workingDirectory).lines().forEach { line ->
                val matcher = Pattern.compile("^\\s*(.*)\\s*$").matcher(line)
                if (matcher.matches() && matcher.group(1) == extension.git.pushToRemote) {
                    shouldPush = true
                }
            }
            if (!shouldPush && extension.git.pushToRemote != "origin") {
                throw GradleException("Could not push to remote ${extension.git.pushToRemote} as repository has no such remote")
            }
        }
        return shouldPush
    }

    private fun gitCurrentBranch(): String {
        val matches = exec(listOf("git", "branch", "--no-color"), directory = workingDirectory).lines().filter { it.matches(Regex("""\s*\*.*""")) }
        if (matches.isNotEmpty()) {
            return matches[0].trim().replace(Regex("""^*\s+"""), "")
        } else {
            throw GradleException("Error, this repository is empty.")
        }
    }

    private fun gitStatus(): Map<String, List<String>> {
        return exec(listOf("git", "status", "--porcelain"), directory = workingDirectory).lines().groupBy {
            if (it.matches(Regex("""^\s*\?{2}.*"""))) {
                UNVERSIONED
            } else {
                UNCOMMITTED
            }
        }
    }

    private fun gitRemoteStatus(): Map<String, Int> {
        val branchStatus = exec(listOf("git", "status", "--porcelain", "-b"), directory = workingDirectory).lines()[0]
        val aheadMatcher = Pattern.compile(""".*ahead (\d+).*""").matcher(branchStatus)
        val behindMatcher = Pattern.compile(""".*behind (\d+).*""").matcher(branchStatus)

        val remoteStatus = mutableMapOf<String, Int>()

        if (aheadMatcher.matches()) {
            remoteStatus[AHEAD] = aheadMatcher.group(1).toInt()
        }
        if (behindMatcher.matches()) {
            remoteStatus[BEHIND] = behindMatcher.group(1).toInt()
        }
        return remoteStatus
    }
}