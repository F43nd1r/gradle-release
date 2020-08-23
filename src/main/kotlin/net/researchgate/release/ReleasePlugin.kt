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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.TaskProvider
import java.util.*
import java.util.concurrent.Callable

class ReleasePlugin : PluginHelper(), Plugin<Project> {
    companion object {
        const val RELEASE_GROUP = "Release"
    }

    private lateinit var scmAdapter: BaseScmAdapter

    private fun registerTask(name: String, description: String, vararg dependencies: Any, onlyIf: () -> Boolean = { true }, action: () -> Unit = {}): TaskProvider<Task> {
        return project.tasks.register(name) {
            it.group = RELEASE_GROUP
            it.description = description
            it.dependsOn(*dependencies)
            it.doLast { action() }
            it.onlyIf { onlyIf() }
        }
    }

    override fun apply(project: Project) {
        this.project = project
        extension = project.extensions.create("release", ReleaseExtension::class.java, project)
        val preCommitText: String? = findProperty("release.preCommitText")
        if (preCommitText?.isNotEmpty() == true) {
            extension.preCommitText = preCommitText
        }

        val createScmAdapter = registerTask("createScmAdapter", "Finds the correct SCM plugin") { createScmAdapter() }
        val initScmAdapter = registerTask("initScmAdapter", "Initializes the SCM plugin", createScmAdapter) { initScmAdapter() }
        val checkCommitNeeded = registerTask("checkCommitNeeded", "Checks to see if there are any added, modified, removed, or un-versioned files.",
                initScmAdapter) { checkCommitNeeded() }
        val checkUpdateNeeded = registerTask("checkUpdateNeeded", "Checks to see if there are any incoming or outgoing changes that haven't been applied locally.",
                initScmAdapter) { checkUpdateNeeded() }
        val checkoutMergeToReleaseBranch = registerTask("checkoutMergeToReleaseBranch",
                "Checkout to the release branch, and merge modifications from the main branch in working tree.",
                initScmAdapter, onlyIf = { extension.pushReleaseVersionBranch != null }) { checkoutAndMergeToReleaseBranch() }
        val unSnapshotVersion = registerTask("unSnapshotVersion", "Removes the snapshot suffix (eg. \"-SNAPSHOT\") from your project's current version.") { unSnapshotVersion() }
        val confirmReleaseVersion = registerTask("confirmReleaseVersion", "Prompts user for this release version. Allows for alpha or pre releases.") { confirmReleaseVersion() }
        val checkSnapshotDependencies = registerTask("checkSnapshotDependencies", "Checks to see if your project has any SNAPSHOT dependencies.") { checkSnapshotDependencies() }
        val beforeReleaseBuild = registerTask("beforeReleaseBuild", "Runs immediately before the build when doing a release")
        val runReleaseBuild = registerTask("runReleaseBuild", "Runs the build when doing a release", Callable<List<Any>> { extension.buildTasks })
        val afterReleaseBuild = registerTask("afterReleaseBuild", "Runs immediately after the build when doing a release")
        val buildTasks = listOf(beforeReleaseBuild, runReleaseBuild, afterReleaseBuild)
        buildTasks.enforceTaskOrder()
        val runBuildTasks = project.tasks.register("runBuildTasks", GradleBuild::class.java) { task ->
            task.group = RELEASE_GROUP
            task.description = "Runs the build process in a separate gradle run."
            task.startParameter = project.gradle.startParameter.newInstance()
            task.tasks = buildTasks.map { it.get().path }
        }
        val preTagCommit = registerTask("preTagCommit", "Commits any changes made by the Release plugin - eg. If the unSnapshotVersion task was executed",
                initScmAdapter) { preTagCommit() }
        val createReleaseTag = registerTask("createReleaseTag", "Creates a tag in SCM for the current (un-snapshotted) version.", initScmAdapter) { commitTag() }
        val checkoutMergeFromReleaseBranch = registerTask("checkoutMergeFromReleaseBranch",
                "Checkout to the main branch, and merge modifications from the release branch in working tree.", initScmAdapter,
                onlyIf = { extension.pushReleaseVersionBranch != null }) { checkoutAndMergeFromReleaseBranch() }
        val updateVersion = registerTask("updateVersion", "Prompts user for the next version. Does it's best to supply a smart default.") { updateVersion() }
        val commitNewVersion = registerTask("commitNewVersion", "Commits the version update to your SCM", initScmAdapter) { commitNewVersion() }
        val push = registerTask("push", "Pushes all release commits and tags to your SCM", initScmAdapter) { push() }
        val tasks = listOf(createScmAdapter, initScmAdapter, checkCommitNeeded, checkUpdateNeeded, checkoutMergeToReleaseBranch, unSnapshotVersion, confirmReleaseVersion,
                checkSnapshotDependencies, preTagCommit, createReleaseTag, runBuildTasks, checkoutMergeFromReleaseBranch, updateVersion, commitNewVersion, push)
        tasks.enforceTaskOrder()
        registerTask("release", "Verify project, release, and update version to next.", tasks)
        project.gradle.taskGraph.afterTask { task ->
            if (task.state.failure != null && project.gradle.taskGraph.allTasks.any { it.name == "release" }) {
                kotlin.runCatching { createScmAdapter() }
                if (::scmAdapter.isInitialized && extension.revertOnFail && project.file(extension.versionPropertyFile).exists()) {
                    log.error("Release process failed, reverting back any changes made by Release Plugin.")
                    scmAdapter.revert()
                } else {
                    log.error("Release process failed, please remember to revert any uncommitted changes made by the Release Plugin.")
                }
            }
        }
    }

    private fun List<TaskProvider<out Task>>.enforceTaskOrder() = forEachIndexed { index, task -> task.configure { it.mustRunAfter(subList(0, index)) } }

    private fun createScmAdapter() {
        if (!::scmAdapter.isInitialized) {
            scmAdapter = findScmAdapter()
        }
    }

    private fun initScmAdapter() {
        scmAdapter.init()
    }

    private fun checkCommitNeeded() {
        scmAdapter.checkCommitNeeded()
    }

    private fun checkUpdateNeeded() {
        scmAdapter.checkUpdateNeeded()
    }

    private fun checkoutAndMergeToReleaseBranch() {
        checkBadCombination()
        scmAdapter.checkoutMergeToReleaseBranch()
    }

    private fun checkoutAndMergeFromReleaseBranch() {
        checkBadCombination()
        scmAdapter.checkoutMergeFromReleaseBranch()
    }

    private fun checkBadCombination() {
        if (extension.pushReleaseVersionBranch != null && !extension.failOnCommitNeeded) {
            log.warn("/!\\Warning/!\\")
            log.warn("It is strongly discouraged to set failOnCommitNeeded to false when pushReleaseVersionBranch is enabled.")
            log.warn("Merging with an uncleaned working directory will lead to unexpected results.")
        }
    }

    private fun checkSnapshotDependencies() {
        project.allprojects.mapNotNull {
            val snapshotDependencies = (project.configurations + project.buildscript.configurations).flatMap { configuration ->
                configuration.dependencies.filter { it.version?.contains("SNAPSHOT") == true && !extension.ignoredSnapshotDependencies.contains("${it.group ?: ""}:${it.name}") }
                        .map { (it.group ?: "") + ":" + it.name + ":" + (it.version ?: "") }
            }
            if (snapshotDependencies.isNotEmpty()) "\n\t${project.name}: $snapshotDependencies" else null
        }.ifEmpty { null }?.joinToString(separator = "")?.let { warnOrThrow(extension.failOnSnapshotDependencies, "Snapshot dependencies detected: $it") }
    }

    private fun commitTag() {
        scmAdapter.createReleaseTag(message = "${extension.preCommitText} ${extension.tagCommitMessage} '${tagName()}'.".trim())
    }

    private fun confirmReleaseVersion() {
        if (!attributes.propertiesFileCreated) {
            updateVersionProperty(getReleaseVersion())
        }
    }

    private fun unSnapshotVersion() {
        checkPropertiesFile()
        val version = project.version.toString()
        if (version.contains(extension.snapshotSuffix)) {
            attributes.usesSnapshot = true
            updateVersionProperty(version.replace(extension.snapshotSuffix, ""))
        }
    }

    private fun preTagCommit() {
        if (attributes.usesSnapshot || attributes.versionModified || attributes.propertiesFileCreated) {
            // should only be committed if the project was using a snapshot version.
            if (attributes.propertiesFileCreated) {
                scmAdapter.add(propertiesFile)
            }
            scmAdapter.commit(message = "${extension.preCommitText} ${extension.preTagCommitMessage} '${tagName()}'.".trim())
        }
    }

    private fun updateVersion() {
        checkPropertiesFile()
        val version = project.version.toString()
        for ((pattern, createNextVersion) in extension.versionPatterns) {
            val regex = Regex(pattern)
            if (regex.containsMatchIn(version)) {
                val nextVersion = version.replace(regex, createNextVersion) + (if (attributes.usesSnapshot) extension.snapshotSuffix else "")
                updateVersionProperty(getNextVersion(nextVersion))
                return
            }
        }
        throw GradleException("Failed to increase version [$version] - unknown pattern")
    }

    private fun getNextVersion(candidateVersion: String): String {
        val nextVersion = findProperty("release.newVersion") ?: candidateVersion
        return getIf(!useAutomaticVersion) { readLine("Enter the next version (current one released as [${project.version}]): [${nextVersion}]") } ?: nextVersion
    }

    private fun commitNewVersion() {
        scmAdapter.commit(message = "${extension.preCommitText} ${extension.newVersionCommitMessage} '${tagName()}'.".trim())
    }

    private fun push() {
        scmAdapter.push()
    }

    private fun checkPropertiesFile() {
        if (!propertiesFile.canRead() || !propertiesFile.canWrite()) {
            throw GradleException("Unable to update version property. Please check file permissions.")
        }
        val properties = Properties().apply { load(propertiesFile.bufferedReader()) }
        val version = properties["version"] as? String
        check(version != null) { "[${propertiesFile.canonicalPath}] contains no 'version' property" }
        check(extension.versionPatterns.keys.any { Regex(it).containsMatchIn(version) }) {
            "[${propertiesFile.canonicalPath}] version [$properties.version] doesn't match any of known version patterns: ${extension.versionPatterns.keys}"
        }

        // set the project version from the properties file if it was not otherwise specified
        if (!isVersionDefined) {
            project.version = version
        }
    }

    /**
     * Recursively look for the type of the SCM we are dealing with, if no match is found look in parent directory
     */
    private fun findScmAdapter(): BaseScmAdapter {
        val projectPath = project.projectDir.canonicalFile
        return extension.scmAdapters.map {
            assert(BaseScmAdapter::class.java.isAssignableFrom(it))
            it.getConstructor(Project::class.java, Attributes::class.java).newInstance(project, attributes) as BaseScmAdapter
        }.firstOrNull { it.isSupported(projectPath) } ?: throw GradleException("No supported Adapter could be found. Are [$projectPath] or its parents valid scm directories?")
    }
}