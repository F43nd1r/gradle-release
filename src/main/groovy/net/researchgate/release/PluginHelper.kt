package net.researchgate.release

import net.researchgate.release.cli.Executor
import org.apache.tools.ant.BuildException
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

open class PluginHelper {
    /**
     * Retrieves SLF4J [Logger] instance.
     *
     *
     * The logger is taken from the [Project] instance if it's initialized already
     * or from SLF4J [LoggerFactory] if it's not.
     *
     * @return SLF4J [Logger] instance
     */
    val log: Logger
        get() = this.project.logger ?: LoggerFactory.getLogger(this.javaClass)

    fun useAutomaticVersion(): Boolean {
        return findProperty("release.useAutomaticVersion", null, "gradle.release.useAutomaticVersion") == "true"
    }

    /**
     * Executes command specified and retrieves its "stdout" output.
     *
     * @param commands     commands to execute
     * @return command "stdout" output
     */
    fun exec(commands: List<String>, directory: File = project.rootDir, env: Map<*, *>? = null, failOnStdErr: Boolean = false,
             errorPatterns: List<String> = emptyList(), errorMessage: String? = null): String {
        return executor.exec(commands, directory, env, failOnStdErr, errorPatterns, errorMessage)
    }

    fun findPropertiesFile(): File {
        val propertiesFile = project.file(extension.versionPropertyFile)
        if (!propertiesFile.isFile) {
            if (!isVersionDefined) {
                project.version = getReleaseVersion("1.0.0")
            }
            if (!useAutomaticVersion() && promptYesOrNo("Do you want to use SNAPSHOT versions inbetween releases")) {
                attributes["usesSnapshot"] = true
            }
            if (useAutomaticVersion() || promptYesOrNo("[${propertiesFile.canonicalPath}] not found, create it with version = ${project.version}")) {
                writeVersion(propertiesFile, "version", project.version)
                attributes["propertiesFileCreated"] = true
            } else {
                log.debug("[${propertiesFile.canonicalPath}] was not found, and user opted out of it being created. Throwing exception.")
                throw GradleException(
                        """[${propertiesFile.canonicalPath}] not found and you opted out of it being created, please create it manually and specify the version property.""")
            }
        }
        return propertiesFile
    }

    protected fun writeVersion(file: File, key: String?, version: Any?) {
        try {
            if (!file.isFile) {
                file.writeText("$key=$version")
            } else {
                file.writeText(file.readText().lines().joinToString("\n") { it.replace(Regex("^(\\s*)$key((\\s*[=|:]\\s*)|(\\s+)).+\$"), "\\1$key\\2$version") })
            }
        } catch (e: BuildException) {
            throw GradleException("Unable to write version property.", e)
        }
    }

    val isVersionDefined: Boolean
        get() = project.version.toString().isNotEmpty() && Project.DEFAULT_VERSION != project.version

    fun warnOrThrow(doThrow: Boolean, message: String) {
        if (doThrow) {
            throw GradleException(message)
        } else {
            log.warn("!!WARNING!! $message")
        }
    }

    fun tagName(): String {
        return extension.tagTemplate.replace(Regex.fromLiteral("\$version"), project.version.toString()).replace(Regex.fromLiteral("\$name"), project.name)
    }

    @JvmOverloads
    fun findProperty(key: String, defaultVal: String? = null, deprecatedKey: String? = null): String? {
        val property1: String? = System.getProperty(key)
        var property: String? = property1 ?: if (project.hasProperty(key)) project.property(key)!!.toString() else null
        if (property == null && deprecatedKey != null) {
            val property2: String? = System.getProperty(deprecatedKey)
            property = property2 ?: if (project.hasProperty(deprecatedKey)) project.property(deprecatedKey)!!.toString() else null
            if (property != null) {
                log.warn("You are using the deprecated parameter '$deprecatedKey'. Please use the new parameter '$key'. The deprecated parameter will be removed in 3.0")
            }
        }
        return property ?: defaultVal
    }

    fun getReleaseVersion(candidateVersion: String = project.version.toString()): String {
        val releaseVersion = findProperty("release.releaseVersion", null, "releaseVersion")
        return if (useAutomaticVersion()) {
            releaseVersion ?: candidateVersion
        } else readLine("This release version:", releaseVersion ?: candidateVersion)!!
    }

    /**
     * Updates properties file (`gradle.properties` by default) with new version specified.
     * If configured in plugin convention then updates other properties in file additionally to `version` property
     *
     * @param newVersion new version to store in the file
     */
    fun updateVersionProperty(newVersion: String) {
        val oldVersion = DefaultGroovyMethods.asType(project.version, String::class.java)
        if (oldVersion != newVersion) {
            project.version = newVersion
            attributes["versionModified"] = true
            project.subprojects {
                it.version = newVersion
            }
            val versionProperties: List<String> = extension.versionProperties.map { it } + "version"
            versionProperties.forEach {
                writeVersion(findPropertiesFile(), it, project.version)
            }
        }
    }

    protected lateinit var project: Project
    protected lateinit var extension: ReleaseExtension
    protected val executor: Executor by lazy { Executor(log) }
    protected var attributes: MutableMap<String, Any> = LinkedHashMap()

    companion object {
        /**
         * Reads user input from the console.
         *
         * @param message      Message to display
         * @param defaultValue (optional) default value to display
         * @return User input entered or default value if user enters no data
         */
        @JvmStatic
        protected fun readLine(message: String, defaultValue: String? = null): String? {
            val fullMessage = "$PROMPT $message${if (!defaultValue.isNullOrEmpty()) " [$defaultValue] " else ""}"
            if (System.console() != null) {
                val line: String? = System.console().readLine(fullMessage)
                return if (line.isNullOrBlank()) defaultValue else line
            }
            println("$fullMessage (WAITING FOR INPUT BELOW)")
            val line: String? = System.`in`.bufferedReader().readLine()
            return if (line.isNullOrBlank()) defaultValue else line
        }

        private fun promptYesOrNo(message: String, defaultValue: Boolean = false): Boolean {
            val defaultStr = if (defaultValue) "Y" else "n"
            val consoleVal = readLine("$message (Y|n)", defaultStr)
            return consoleVal?.toLowerCase()?.startsWith("y") ?: defaultValue
        }

        private val LINE_SEP = System.getProperty("line.separator")
        private val PROMPT = "$LINE_SEP??>"
    }
}