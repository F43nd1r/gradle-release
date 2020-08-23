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

inline fun <T> getIf(shouldGet: Boolean, get: () -> T): T? = if(shouldGet) get() else null

inline fun <reified T> optionalArg(shouldAddArg: Boolean, arg: T): Array<T> = if(shouldAddArg) arrayOf(arg) else emptyArray()

operator fun <T> List<T>?.get(index: Int) : T? = this?.get(index)

fun Boolean.falseToNull() : Boolean? = if(this) true else null