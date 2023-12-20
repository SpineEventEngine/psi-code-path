package io.spine.tools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

fun setupIdeaStandaloneExecution() = IdeaStandaloneExecutionSetup.doSetup()

// Original code is in `org.jetbrains.kotlin.cli.jvm.compiler.compat.kt`.
object IdeaStandaloneExecutionSetup {

    private val LOG: Logger = Logger.getInstance(IdeaStandaloneExecutionSetup::class.java)

    // Copy-pasted from com.intellij.openapi.util.BuildNumber#FALLBACK_VERSION
    private const val FALLBACK_IDEA_BUILD_NUMBER = "999.SNAPSHOT"

    fun doSetup() {
        checkInHeadlessMode()
        setSystemProperties()
    }

    private fun checkInHeadlessMode() {
        // If `application` is `null` it means that we are in progress of set-up
        // application environment i.e. we are not in the running IDEA.
        val application = ApplicationManager.getApplication() ?: return
        if (!application.isHeadlessEnvironment) {
            LOG.error(Throwable("`${this::class.simpleName}` should be called only in headless environment"))
        }
    }

    private fun setSystemProperties() {
        // As in `org.jetbrains.kotlin.cli.common.CLITool.doMain()`.

        // We depend on swing (indirectly through PSI or something), so we want to declare headless mode,
        // to avoid accidentally starting the UI thread
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true")
        }

        // As in `org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback()`.
        System.setProperty("idea.io.use.nio2", java.lang.Boolean.TRUE.toString())

        System.getProperties().let {
            it["project.structure.add.tools.jar.to.new.jdk"] = "false"
            it["psi.track.invalidation"] = "true"
            it["psi.incremental.reparse.depth.limit"] = "1000"
            it["ide.hide.excluded.files"] = "false"
            it["ast.loading.filter"] = "false"
            it["idea.ignore.disabled.plugins"] = "true"
            // Setting the build number explicitly avoids the command-line compiler
            // reading /tmp/build.txt in an attempt to get a build number from there.
            // See intellij platform PluginManagerCore.getBuildNumber.
            it["idea.plugins.compatible.build"] = FALLBACK_IDEA_BUILD_NUMBER
        }
    }
}
