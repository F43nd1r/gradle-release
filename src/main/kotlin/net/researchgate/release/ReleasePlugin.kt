package net.researchgate.release

import org.codehaus.groovy.runtime.StringGroovyMethods
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.GradleBuild
import java.util.*
import java.util.regex.Pattern

class ReleasePlugin : PluginHelper(), Plugin<Project> {
    companion object {
        const val RELEASE_GROUP = "Release"
    }

    override fun apply(project: Project) {
        this.project = project
        extension = project.extensions.create("release", ReleaseExtension::class.java)
        val preCommitText: String? = findProperty("release.preCommitText", null, "preCommitText")
        if (preCommitText?.isNotEmpty() == true) {
            extension.preCommitText = preCommitText
        }


        val createScmAdapter = project.tasks.register("createScmAdapter") {
            it.group = RELEASE_GROUP
            it.description = "Finds the correct SCM plugin"
            it.doLast { createScmAdapter() }
        }
        val initScmAdapter = project.tasks.register("initScmAdapter") {
            it.group = RELEASE_GROUP
            it.description = "Initializes the SCM plugin"
            it.doLast { initScmAdapter() }
        }
        val checkCommitNeeded = project.tasks.register("checkCommitNeeded") {
            it.group = RELEASE_GROUP
            it.description = "Checks to see if there are any added, modified, removed, or un-versioned files."
            it.doLast { checkCommitNeeded() }
        }
        val checkUpdateNeeded = project.tasks.register("checkUpdateNeeded") {
            it.group = RELEASE_GROUP
            it.description = "Checks to see if there are any incoming or outgoing changes that haven't been applied locally."
            it.doLast { checkUpdateNeeded() }
        }
        val checkoutMergeToReleaseBranch = project.tasks.register("checkoutMergeToReleaseBranch") {
            it.group = RELEASE_GROUP
            it.description = "Checkout to the release branch, and merge modifications from the main branch in working tree."
            it.doLast { checkoutAndMergeToReleaseBranch() }
            it.onlyIf { extension.pushReleaseVersionBranch != null }
        }
        val unSnapshotVersion = project.tasks.register("unSnapshotVersion") {
            it.group = RELEASE_GROUP
            it.description = "Removes the snapshot suffix (eg. \"-SNAPSHOT\") from your project's current version."
            it.doLast { unSnapshotVersion() }
        }
        val confirmReleaseVersion = project.tasks.register("confirmReleaseVersion") {
            it.group = RELEASE_GROUP
            it.description = "Prompts user for this release version. Allows for alpha or pre releases."
            it.doLast { confirmReleaseVersion() }
        }
        val checkSnapshotDependencies = project.tasks.register("checkSnapshotDependencies") {
            it.group = RELEASE_GROUP
            it.description = "Checks to see if your project has any SNAPSHOT dependencies."
            it.doLast { checkSnapshotDependencies() }
        }
        val runBuildTasks = project.tasks.register("runBuildTasks", GradleBuild::class.java) {
            it.group = RELEASE_GROUP
            it.description = "Runs the build process in a separate gradle run."
            it.startParameter = project.gradle.startParameter.newInstance()
            val p = project.path.let { path -> if (!path.endsWith(Project.PATH_SEPARATOR)) path + Project.PATH_SEPARATOR else path }
            it.tasks = listOf("${p}beforeReleaseBuild") + extension.buildTasks.map { task -> "${p}$task" } + "${p}afterReleaseBuild"
        }
        val preTagCommit = project.tasks.register("preTagCommit") {
            it.group = RELEASE_GROUP
            it.description = "Commits any changes made by the Release plugin - eg. If the unSnapshotVersion task was executed"
            it.doLast { preTagCommit() }
        }
        val createReleaseTag = project.tasks.register("createReleaseTag") {
            it.group = RELEASE_GROUP
            it.description = "Creates a tag in SCM for the current (un-snapshotted) version."
            it.doLast { commitTag() }
        }
        val checkoutMergeFromReleaseBranch = project.tasks.register("checkoutMergeFromReleaseBranch") {
            it.group = RELEASE_GROUP
            it.description = "Checkout to the main branch, and merge modifications from the release branch in working tree."
            it.doLast { checkoutAndMergeFromReleaseBranch() }
            it.onlyIf { extension.pushReleaseVersionBranch != null }
        }
        val updateVersion = project.tasks.register("updateVersion") {
            it.group = RELEASE_GROUP
            it.description = "Prompts user for the next version. Does it's best to supply a smart default."
            it.doLast { updateVersion() }
        }
        val commitNewVersion = project.tasks.register("commitNewVersion") {
            it.group = RELEASE_GROUP
            it.description = "Commits the version update to your SCM"
            it.doLast { commitNewVersion() }
        }
        val push = project.tasks.register("push") {
            it.group = RELEASE_GROUP
            it.description = "Pushes all release commits and tags to your SCM"
            it.doLast { push() }
        }
        listOf(createScmAdapter, initScmAdapter, checkCommitNeeded, checkUpdateNeeded, checkoutMergeToReleaseBranch, unSnapshotVersion, confirmReleaseVersion,
                checkSnapshotDependencies, preTagCommit, createReleaseTag, runBuildTasks, checkoutMergeFromReleaseBranch, updateVersion, commitNewVersion, push).zipWithNext()
                .forEach { pair ->
                    pair.second.configure { it.mustRunAfter(pair.first.get())  }
                }
        val beforeReleaseBuild = project.tasks.register("beforeReleaseBuild") {
            it.group = RELEASE_GROUP
            it.description = "Runs immediately before the build when doing a release"
        }
        val afterReleaseBuild = project.tasks.register("afterReleaseBuild") {
            it.group = RELEASE_GROUP
            it.description = "Runs immediately after the build when doing a release"
        }
        project.afterEvaluate {
            val buildTasks = extension.buildTasks
            if (buildTasks.isNotEmpty()) {
                project.tasks.getAt(buildTasks.first().toString()).mustRunAfter(beforeReleaseBuild.get())
                afterReleaseBuild.get().mustRunAfter(buildTasks.last().toString())
            }
        }

        project.tasks.register("release") {
            it.group = RELEASE_GROUP
            it.description = "Verify project, release, and update version to next."
            it.dependsOn(createScmAdapter, initScmAdapter, checkCommitNeeded, checkUpdateNeeded, checkoutMergeToReleaseBranch, unSnapshotVersion, confirmReleaseVersion,
                    checkSnapshotDependencies, preTagCommit, createReleaseTag, runBuildTasks, checkoutMergeFromReleaseBranch, updateVersion, commitNewVersion, push)
        }
        project.gradle.taskGraph.afterTask { task ->
            val state = task.state
            if (state.failure != null && project.gradle.taskGraph.allTasks.any { it.name == "release" }) {
                try {
                    createScmAdapter()
                } catch (ignored: Exception) {
                }
                if (scmAdapter != null && extension.revertOnFail && project.file(extension.versionPropertyFile).exists()) {
                    log.error("Release process failed, reverting back any changes made by Release Plugin.")
                    scmAdapter!!.revert()
                } else {
                    log.error("Release process failed, please remember to revert any uncommitted changes made by the Release Plugin.")
                }
            }
        }
    }

    fun createScmAdapter() {
        if (scmAdapter == null) {
            scmAdapter = findScmAdapter()
        }
    }

    fun initScmAdapter() {
        scmAdapter!!.init()
    }

    fun checkCommitNeeded() {
        scmAdapter!!.checkCommitNeeded()
    }

    fun checkUpdateNeeded() {
        scmAdapter!!.checkUpdateNeeded()
    }

    fun checkoutAndMergeToReleaseBranch() {
        if (extension.pushReleaseVersionBranch != null && !extension.failOnCommitNeeded) {
            log.warn("/!\\Warning/!\\")
            log.warn("It is strongly discouraged to set failOnCommitNeeded to false with pushReleaseVersionBranch is enabled.")
            log.warn("Merging with an uncleaned working directory will lead to unexpected results.")
        }
        scmAdapter!!.checkoutMergeToReleaseBranch()
    }

    fun checkoutAndMergeFromReleaseBranch() {
        if (extension.pushReleaseVersionBranch != null && !extension.failOnCommitNeeded) {
            log.warn("/!\\Warning/!\\")
            log.warn("It is strongly discouraged to set failOnCommitNeeded to false with pushReleaseVersionBranch is enabled.")
            log.warn("Merging with an uncleaned working directory will lead to unexpected results.")
        }
        scmAdapter!!.checkoutMergeFromReleaseBranch()
    }

    fun checkSnapshotDependencies() {
        val matcher: (Dependency) -> Boolean = { d ->
            val group = d.group
            d.version?.contains("SNAPSHOT") == true && !extension.ignoredSnapshotDependencies.contains("${group ?: ""}:${d.name}")
        }
        val collector: (Dependency) -> String = { d ->
            val group = d.group
            val s = "" + ":" + d.name + ":" + d.version
            group ?: s ?: ""
        }
        var message = ""
        project.allprojects {
            val snapshotDependencies = mutableSetOf<Any>()
            project.configurations.forEach {
                snapshotDependencies.addAll(it.dependencies.filter(matcher).map(collector))
            }
            project.buildscript.configurations.forEach {
                snapshotDependencies.addAll(it.dependencies.filter(matcher).map(collector))
            }

            if (snapshotDependencies.size > 0) {
                message += "\n\t${project.name}: $snapshotDependencies"
            }
        }
        if (message.isNotEmpty()) {
            message = "Snapshot dependencies detected: $message"
            warnOrThrow(extension.failOnSnapshotDependencies, message)
        }
    }

    fun commitTag() {
        var message = "${extension.tagCommitMessage} '${tagName()}'."
        if (StringGroovyMethods.asBoolean(extension.preCommitText)) {
            message = "${extension.preCommitText} $message"
        }
        scmAdapter!!.createReleaseTag(message)
    }

    fun confirmReleaseVersion() {
        if (attributes.propertiesFileCreated) {
            return
        }
        updateVersionProperty(getReleaseVersion())
    }

    fun unSnapshotVersion() {
        checkPropertiesFile()
        var version = project.version.toString()
        if (version.endsWith(extension.snapshotSuffix)) {
            attributes.usesSnapshot = true
            version = version.removeSuffix(extension.snapshotSuffix)
            updateVersionProperty(version)
        }
    }

    fun preTagCommit() {
        if (attributes.usesSnapshot || attributes.versionModified || attributes.propertiesFileCreated) {
            // should only be committed if the project was using a snapshot version.
            var message = "${extension.preTagCommitMessage} '${tagName()}'."
            if (extension.preCommitText.isNotEmpty()) {
                message = "${extension.preCommitText} $message"
            }
            if (attributes.propertiesFileCreated) {
                scmAdapter!!.add(findPropertiesFile())
            }
            scmAdapter!!.commit(message)
        }
    }

    fun updateVersion() {
        checkPropertiesFile()
        val version = project.version.toString()
        val patterns = extension.versionPatterns
        for ((pattern, handler) in patterns) {
            val matcher = Pattern.compile(pattern).matcher(version)
            if (matcher.find()) {
                var nextVersion = handler(matcher)
                if (attributes.usesSnapshot) {
                    nextVersion += extension.snapshotSuffix
                }
                nextVersion = getNextVersion(nextVersion)
                updateVersionProperty(nextVersion)
                return
            }
        }
        throw GradleException("Failed to increase version [$version] - unknown pattern")
    }

    fun getNextVersion(candidateVersion: String): String {
        val nextVersion = findProperty("release.newVersion", null, "newVersion")
        return if (useAutomaticVersion()) {
            nextVersion ?: candidateVersion
        } else {
            readLine("Enter the next version (current one released as [${project.version.toString()}]):", nextVersion ?: candidateVersion)!!
        }
    }

    fun commitNewVersion() {
        var message = "${extension.newVersionCommitMessage} '${tagName()}'."
        if (!extension.preCommitText.isBlank()) {
            message = "${extension.preCommitText} $message"
        }
        scmAdapter!!.commit(message)
    }

    fun push() {
        scmAdapter!!.push()
    }

    fun checkPropertiesFile() {
        val propertiesFile = findPropertiesFile()
        if (!propertiesFile.canRead() || !propertiesFile.canWrite()) {
            throw GradleException("Unable to update version property. Please check file permissions.")
        }
        val properties = Properties()
        properties.load(propertiesFile.bufferedReader())
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
    protected fun findScmAdapter(): BaseScmAdapter {
        val projectPath = project.projectDir.canonicalFile
        return extension.scmAdapters.map {
            assert(BaseScmAdapter::class.java.isAssignableFrom(it))
            it.getConstructor(Project::class.java, Attributes::class.java).newInstance(project, attributes) as BaseScmAdapter
        }.firstOrNull { it.isSupported(projectPath) } ?: throw GradleException("No supported Adapter could be found. Are [$projectPath] or its parents are valid scm directories?")
    }

    private var scmAdapter: BaseScmAdapter? = null
}