package net.researchgate.release

import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

abstract class BaseScmAdapter(project: Project, attributes: Map<String, Any>) : PluginHelper() {
    abstract fun isSupported(directory: File): Boolean
    abstract fun init()
    abstract fun checkCommitNeeded()
    abstract fun checkUpdateNeeded()
    abstract fun createReleaseTag(message: String)
    abstract fun add(file: File)
    abstract fun commit(message: String)
    abstract fun revert()
    open fun checkoutMergeToReleaseBranch() {
        throw GradleException("Checkout and merge is supported only for GIT projects")
    }

    open fun checkoutMergeFromReleaseBranch() {
        throw GradleException("Checkout and merge is supported only for GIT projects")
    }

    init {
        this.project = project
        this.attributes = attributes.toMutableMap()
        extension = project.extensions.getByType(ReleaseExtension::class.java)
    }
}