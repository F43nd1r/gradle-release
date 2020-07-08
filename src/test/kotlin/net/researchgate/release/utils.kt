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
import java.io.File

fun runGradleTask(dir: File, vararg task: String): BuildResult = GradleRunner.create().withProjectDir(dir)
        .withArguments(*task)
        .withPluginClasspath()
        .build()