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
import java.util.regex.Pattern

class GitAdapter(project: Project, attributes: Attributes) : BaseScmAdapter(project, attributes) {
    companion object {
        const val LINE = "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~"
        const val UNCOMMITTED = "uncommitted"
        const val UNVERSIONED = "unversioned"
        const val AHEAD = "ahead"
        const val BEHIND = "behind"
    }

    private var workingBranch: String? = null
    private var releaseBranch: String? = null
    private val push = mutableListOf<List<String>>()
    private var head: String? = null
    private var tag: String? = null

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
        push.clear()
        head = gitCurrentCommit()
        tag = null
        extension.git.requireBranch?.let {
            if (!Regex(it).matches(workingBranch!!)) {
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
        exec("git", "remote", "update", directory = workingDirectory, errorPatterns = listOf("error: ", "fatal: "))
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
        log.info("Tag for version ${project.version} is $tagName")
        val params = mutableListOf("git", "tag", "-a", tagName, "-m", message)
        if (extension.git.signTag) {
            params.add("-s")
        }
        exec(*params.toTypedArray(),
                directory = workingDirectory,
                errorMessage = "Duplicate tag [$tagName] or signing error",
                errorPatterns = listOf("already exists", "failed to sign"))
        tag = tagName
        push.add(listOf(extension.git.pushToRemote ?: "", tagName) + extension.git.pushOptions)
    }

    override fun commit(message: String) {
        val command = mutableListOf("git", "commit", "-m", message)
        if (extension.git.commitVersionFileOnly) {
            command.add(project.file(extension.versionPropertyFile).toString())
        } else {
            command.add("-a")
        }

        exec(*command.toTypedArray(), directory = workingDirectory, errorPatterns = listOf("error: ", "fatal: "))

        var branch = gitCurrentBranch()
        if (extension.git.pushToBranchPrefix != null) {
            branch = "HEAD:${extension.git.pushToBranchPrefix}${branch}"
        }
        push.add(listOf(extension.git.pushToRemote ?: "", branch) + extension.git.pushOptions)
    }

    override fun add(file: File) {
        exec("git", "add", file.path,
                directory = workingDirectory,
                errorMessage = "Error adding file ${file.name}",
                errorPatterns = listOf("error: ", "fatal: "))
    }

    override fun push() {
        if (shouldPush()) {
            for (command in push) {
                exec("git", "push", "--porcelain", *command.toTypedArray(),
                        directory = workingDirectory,
                        errorMessage = "Failed to push to remote: $command",
                        errorPatterns = listOf("[rejected]", "error: ", "fatal: "))
            }
        }
    }

    override fun revert() {
        if (head != null && head != gitCurrentCommit()) {
            log.info("Reverting commits...")
            exec("git", "reset", "--soft", head!!, directory = workingDirectory, errorMessage = "Error reverting commits made by the release plugin.")
            head = null
        }
        if (tag != null) {
            log.info("Reverting tag...")
            exec("git", "tag", "--delete", tag!!, directory = workingDirectory, errorMessage = "Error reverting tag made by the release plugin.")
            tag = null
        }
        // Revert changes on gradle.properties
        log.info("Reverting property file")
        exec("git", "checkout", "HEAD", "--", findPropertiesFile().name, directory = workingDirectory, errorMessage = "Error reverting changes made by the release plugin.")
    }

    override fun checkoutMergeToReleaseBranch() {
        checkoutMerge(workingBranch!!, releaseBranch!!)
    }

    override fun checkoutMergeFromReleaseBranch() {
        checkoutMerge(releaseBranch!!, workingBranch!!)
    }

    private fun checkoutMerge(fromBranch: String, toBranch: String) {
        exec("git", "fetch", directory = workingDirectory, errorPatterns = listOf("error: ", "fatal: "))
        exec("git", "checkout", toBranch, directory = workingDirectory, errorPatterns = listOf("error: ", "fatal: "))
        exec("git", "merge", "--no-ff", "--no-commit", fromBranch, directory = workingDirectory, errorPatterns = listOf("error: ", "fatal: ", "CONFLICT"))
    }

    private fun shouldPush(): Boolean {
        var shouldPush = false
        if (extension.git.pushToRemote != null) {
            exec("git", "remote", directory = workingDirectory).lines().forEach { line ->
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
        val matches = exec("git", "branch", "--no-color", directory = workingDirectory).lines().filter { it.matches(Regex("""\s*\*.*""")) }
        if (matches.isNotEmpty()) {
            return matches[0].trim().replace(Regex("""^\*\s+"""), "")
        } else {
            throw GradleException("Error, this repository is empty.")
        }
    }

    private fun gitStatus(): Map<String, List<String>> {
        return exec("git", "status", "--porcelain", directory = workingDirectory).lines().groupBy {
            when {
                it.matches(Regex("""^\s*\?{2}.*""")) -> UNVERSIONED
                it.isNotBlank() -> UNCOMMITTED
                else -> "ignore"
            }
        }
    }

    private fun gitCurrentCommit(): String {
        return exec("git", "rev-parse", "--verify", "--porcelain", "HEAD", directory = workingDirectory).trim()
    }

    private fun gitRemoteStatus(): Map<String, Int> {
        val branchStatus = exec("git", "status", "--porcelain", "-b", directory = workingDirectory).lines()[0]
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