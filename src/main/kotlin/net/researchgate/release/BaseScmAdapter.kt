package net.researchgate.release

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

abstract class BaseScmAdapter(project: Project, attributes: Attributes) : PluginHelper() {
    abstract fun isSupported(directory: File): Boolean
    abstract fun init()
    abstract fun checkCommitNeeded()
    abstract fun checkUpdateNeeded()
    abstract fun createReleaseTag(message: String)
    abstract fun add(file: File)
    abstract fun commit(message: String)
    abstract fun push()
    abstract fun revert()
    open fun checkoutMergeToReleaseBranch() {
        throw GradleException("Checkout and merge is supported only for GIT projects")
    }

    open fun checkoutMergeFromReleaseBranch() {
        throw GradleException("Checkout and merge is supported only for GIT projects")
    }

    init {
        this.project = project
        this.attributes = attributes
        this.extension = project.extensions.getByType(ReleaseExtension::class.java)
    }
}