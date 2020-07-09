/*
 * This file is part of the gradle-release plugin.
 *
 * (c) F43nd1r
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 *
 */

package net.researchgate.release

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import strikt.api.Assertion
import java.io.File

private fun prepareGradleRunner(dir: File, vararg task: String): GradleRunner = GradleRunner.create().withProjectDir(dir)
        .withArguments(*task, "--stacktrace")
        .withPluginClasspath()

fun runGradleTask(dir: File, vararg task: String): BuildResult = prepareGradleRunner(dir, *task).build()

fun failGradleTask(dir: File, vararg task: String): BuildResult = prepareGradleRunner(dir, *task).buildAndFail()


infix fun <T : Collection<E>?, E> Assertion.Builder<T>.contains(expected: E): Assertion.Builder<T> =
        assert("contains %s", expected) {
            if (it?.contains(expected) == true) {
                pass()
            } else {
                fail(actual = it)
            }
        }

infix fun <T : Iterable<E>?, E> Assertion.Builder<T>.any(predicate: (E) -> Boolean): Assertion.Builder<T> =
        assert("matches %s", predicate) {
            if (it?.any<E>(predicate) == true) {
                pass()
            } else {
                fail(actual = it)
            }
        }