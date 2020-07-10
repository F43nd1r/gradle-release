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

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.matches

class ExecutorTests {
    lateinit var executor: Executor

    @BeforeEach
    internal fun setUp() {
        executor = Executor()
    }

    @Test
    internal fun `supplied envs are taken`() {
        val output = executor.exec("env", env = mapOf("TEST_RELEASE" to 1234))
        expectThat(output).contains(Regex("(?m)^TEST_RELEASE=1234\$"))
    }

    @Test
    internal fun `system envs are merged`() {
        val output = executor.exec("env")
        expectThat(output).contains(Regex("(?m)^PATH="))
    }

    @Test
    internal fun `supplied envs overwrite system envs`() {
        val output = executor.exec("env", env = mapOf("PATH" to 1234))
        expectThat(output).contains(Regex("(?m)^PATH=1234\$"))
    }
}