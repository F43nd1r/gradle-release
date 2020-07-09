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

package net.researchgate.release.cli

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.gradle.api.GradleException
import org.slf4j.Logger
import java.io.File

class Executor(private val logger: Logger? = null) {
    fun exec(vararg commands: String, directory: File? = null, env: Map<*, *> = emptyMap<Any, Any>(), failOnStdErr: Boolean = false,
             errorPatterns: List<String> = emptyList(), errorMessage: String? = null): String {
        val processEnv: Map<*, *> = System.getenv() + env
        logger?.info("Running $commands in [${directory.toString()}]")
        val process = Runtime.getRuntime().exec(commands, processEnv.map { "${it.key}=${it.value}" }.toTypedArray(), directory)
        val (out, err) = runBlocking {
            val out = async { process.inputStream.bufferedReader().readText() }
            val err = async { process.errorStream.bufferedReader().readText() }
            out.await() to err.await()
        }
        process.waitFor()
        logger?.info("Running $commands produced output: [${out.trim { it <= ' ' }}]")
        if (process.exitValue() != 0) {
            val message = "Running $commands produced an error: [${err.trim { it <= ' ' }}]"
            if (failOnStdErr) {
                throw GradleException(message)
            } else {
                logger?.warn(message)
            }
        }
        if (listOf(out, err).any { s -> errorPatterns.any { s.contains(it) } }) {
            throw GradleException(errorMessage ?: "Failed to run [${commands.joinToString(" ")}] - [$out][$err]")
        }
        return out
    }

}