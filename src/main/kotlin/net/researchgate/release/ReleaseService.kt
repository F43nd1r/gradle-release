package net.researchgate.release

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

class ReleaseService : BuildService<BuildServiceParameters.None> {
    override fun getParameters(): BuildServiceParameters.None? {
        return null
    }
}