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

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import java.util.concurrent.Callable

open class ReleaseExtension(private val project: Project) {
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
    var buildTasks: List<Any> = listOf(Callable<List<Task>> { project.allprojects.mapNotNull { it.tasks.findByName("build") } })
    var ignoredSnapshotDependencies: List<String> = emptyList()
    var versionPatterns: Map<String, (MatchResult) -> String> = mapOf("""(\d+)([^\d]*$)""" to { m -> "${m.groupValues[1].toInt() + 1}${m.groupValues[2]}" })
    var scmAdapters: List<Class<out BaseScmAdapter>> = listOf(GitAdapter::class.java)
    var git = GitAdapter.GitConfig()

    fun git(action: Action<GitAdapter.GitConfig>) {
        action.execute(git)
    }
}