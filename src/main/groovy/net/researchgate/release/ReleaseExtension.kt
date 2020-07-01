package net.researchgate.release

import org.gradle.api.Action
import java.util.regex.Matcher

class ReleaseExtension {
    var failOnCommitNeeded = true
    var failOnPublishNeeded = true
    var failOnSnapshotDependencies = true
    var failOnUnversionedFiles = true
    var failOnUpdateNeeded = true
    var revertOnFail = true
    var preCommitText = ""
    var preTagCommitMessage = "[Gradle Release Plugin] - pre tag commit: "
    var tagCommitMessage = "[Gradle Release Plugin] - creating tag: "
    var newVersionCommitMessage = "[Gradle Release Plugin] - new version commit: "
    var snapshotSuffix = "-SNAPSHOT"
    var pushReleaseVersionBranch: String? = null
    var tagTemplate: String = "\$version"
    var versionPropertyFile = "gradle.properties"
    var versionProperties: List<String> = emptyList()
    var buildTasks: List<*> = listOf("build")
    var ignoredSnapshotDependencies: List<String> = emptyList()
    var versionPatterns: Map<String, (Matcher) -> String> = mapOf("""(\d+)([^\d]*$)""" to { m -> m.replaceAll("${(m.group(1).toInt()) + 1}${m.group(2)}") })
    var scmAdapters: List<Class<out BaseScmAdapter>> = listOf(GitAdapter::class.java)
    var git = GitAdapter.GitConfig()

    fun git(action: Action<GitAdapter.GitConfig>) {
        action.execute(git)
    }
}