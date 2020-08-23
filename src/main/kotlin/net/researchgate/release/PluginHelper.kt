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
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

open class PluginHelper {
    protected lateinit var project: Project
    protected lateinit var extension: ReleaseExtension
    protected val executor: Executor by lazy { Executor(log) }
    protected var attributes: Attributes = Attributes()

    /**
     * SLF4J [Logger] instance.
     *
     * The logger is taken from the [Project] instance if it's initialized already
     * or from SLF4J [LoggerFactory] if it's not.
     *
     */
    protected val log: Logger
        get() = this.project.logger ?: LoggerFactory.getLogger(this.javaClass)

    protected val useAutomaticVersion: Boolean by lazy { findProperty("release.useAutomaticVersion") == "true" }

    protected val isVersionDefined: Boolean
        get() = project.version.toString().isNotEmpty() && Project.DEFAULT_VERSION != project.version

    val propertiesFile: File by lazy {
        val propertiesFile = project.file(extension.versionPropertyFile)
        if (!propertiesFile.isFile) {
            if (!isVersionDefined) {
                project.version = getReleaseVersion("1.0.0")
            }
            if (!useAutomaticVersion && promptYesOrNo("Do you want to use SNAPSHOT versions in between releases")) {
                attributes.usesSnapshot = true
            }
            if (useAutomaticVersion || promptYesOrNo("[${propertiesFile.canonicalPath}] not found, create it with version = ${project.version}")) {
                writeVersion(propertiesFile, "version", project.version)
                attributes.propertiesFileCreated = true
            } else {
                throw GradleException(
                        """[${propertiesFile.canonicalPath}] not found and you opted out of it being created, please create it manually and specify the version property.""")
            }
        }
        propertiesFile
    }

    private fun writeVersion(file: File, key: String?, version: Any?) {
        try {
            if (!file.isFile) {
                file.writeText("$key=$version")
            } else {
                file.writeText(file.readText().lines().joinToString("\n") { it.replace(Regex("""^(\s*)$key((\s*[=|:]\s*)|(\s+)).+$"""), """$1$key$2$version""") })
            }
        } catch (e: IOException) {
            throw GradleException("Unable to write version property.", e)
        }
    }

    fun warnOrThrow(doThrow: Boolean, message: String) {
        if (doThrow) {
            throw GradleException(message)
        } else {
            log.warn("!!WARNING!! $message")
        }
    }

    fun tagName(): String = extension.tagTemplate.replace(Regex.fromLiteral("\$version"), project.version.toString()).replace(Regex.fromLiteral("\$name"), project.name)

    fun findProperty(key: String): String? = System.getProperty(key) ?: project.findProperty(key)?.toString()

    fun getReleaseVersion(candidateVersion: String = project.version.toString()): String {
        val releaseVersion = findProperty("release.releaseVersion") ?: candidateVersion
        return getIf(!useAutomaticVersion) { readLine("This release version: [${releaseVersion}]") } ?: releaseVersion
    }

    /**
     * Updates properties file (`gradle.properties` by default) with new version specified.
     * If configured in plugin convention then updates other properties in file additionally to `version` property
     *
     * @param newVersion new version to store in the file
     */
    fun updateVersionProperty(newVersion: String) {
        val oldVersion = project.version.toString()
        if (oldVersion != newVersion) {
            project.version = newVersion
            attributes.versionModified = true
            project.subprojects { it.version = newVersion }
            (extension.versionProperties.map { it } + "version").forEach { writeVersion(propertiesFile, it, project.version) }
        }
    }

    companion object {
        /**
         * Reads user input from the console.
         *
         * @param message      Message to display
         * @return User input entered or default value if user enters no data
         */
        @JvmStatic
        protected fun readLine(message: String): String? {
            val fullMessage = "$PROMPT $message"
            System.console()?.run {
                return readLine(fullMessage)?.ifBlank { null }
            }
            println("$fullMessage (WAITING FOR INPUT BELOW)")
            return System.`in`.bufferedReader().readLine()?.ifBlank { null }
        }

        private fun promptYesOrNo(message: String, defaultValue: Boolean = false): Boolean {
            val defaultStr = if (defaultValue) "Y" else "n"
            val consoleVal = readLine("$message (Y|n)") ?: defaultStr
            return consoleVal.toLowerCase().startsWith("y")
        }

        private val LINE_SEP = System.getProperty("line.separator")
        private val PROMPT = "$LINE_SEP??>"
    }
}