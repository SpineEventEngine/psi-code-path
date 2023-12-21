package io.spine.tools

//import org.jetbrains.kotlin.cli.jvm.compiler.DummyJavaFileCodeStyleFacadeFactory
//import org.jetbrains.kotlin.cli.jvm.compiler.jarfs.FastJarFileSystem
//import org.jetbrains.kotlin.cli.jvm.modules.CoreJrtFileSystem
import com.intellij.DynamicBundle.LanguageBundleEP
import com.intellij.codeInsight.ContainerProvider
import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.core.CoreApplicationEnvironment.registerExtensionPoint
import com.intellij.core.JavaCoreApplicationEnvironment
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.lang.MetaLanguage
import com.intellij.lang.jvm.facade.JvmElementProvider
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.FileContextProvider
import com.intellij.psi.JavaModuleSystem
import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy
import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider
import com.intellij.psi.meta.MetaDataContributor
import io.spine.tools.IdeaExtensionPoints.registerVersionSpecificAppExtensionPoints

class ProtoDataJavaAppEnvironment private constructor(
    parentDisposable: Disposable
) : JavaCoreApplicationEnvironment(parentDisposable, false) {

    init {
        //registerApplicationService(JavaFileCodeStyleFacadeFactory::class.java, DummyJavaFileCodeStyleFacadeFactory())
        registerFileType(JavaClassFileType.INSTANCE, "sig")
    }

    override fun createJrtFileSystem(): VirtualFileSystem {
        return KotlinCompilerCoreJrtFileSystem()
//        return CoreJrtFileSystem()
    }

    override fun createApplication(parentDisposable: Disposable): MockApplication {
        val mock = super.createApplication(parentDisposable)
        return mock
    }

//    private var fastJarFileSystemField: FastJarFileSystem? = null
//    private var fastJarFileSystemFieldInitialized = false
//
//    val fastJarFileSystem: FastJarFileSystem?
//        get() {
//            synchronized(APPLICATION_LOCK) {
//                if (!fastJarFileSystemFieldInitialized) {
//
//                    // may return null e.g. on the old JDKs, therefore fastJarFileSystemFieldInitialized flag is needed
//                    fastJarFileSystemField = FastJarFileSystem.createIfUnmappingPossible()?.also {
//                        Disposer.register(parentDisposable) {
//                            it.clearHandlersCache()
//                        }
//                    }
//                    fastJarFileSystemFieldInitialized = true
//                }
//                return fastJarFileSystemField
//            }
//        }
//
//    fun idleCleanup() {
//        fastJarFileSystemField?.clearHandlersCache()
//    }

    companion object {

        @PublishedApi
        internal val APPLICATION_LOCK = Object()

        fun create(
            parentDisposable: Disposable
        ): ProtoDataJavaAppEnvironment {
            val environment = ProtoDataJavaAppEnvironment(parentDisposable)
            registerExtensionPoints()
            return environment
        }

        private fun registerExtensionPoints() {
            registerAppExtensionPoints()
            registerVersionSpecificAppExtensionPoints(ApplicationManager.getApplication().extensionArea)
        }

        private fun registerAppExtensionPoints() {
            @Suppress("UnstableApiUsage")
            registerPoint(LanguageBundleEP.EP_NAME)
            registerPoint(FileContextProvider.EP_NAME)
            @Suppress("UnstableApiUsage")
            registerPoint(MetaDataContributor.EP_NAME)
            registerPoint(PsiAugmentProvider.EP_NAME)
            registerPoint(JavaMainMethodProvider.EP_NAME)
            registerPoint(ContainerProvider.EP_NAME)
            registerPoint(MetaLanguage.EP_NAME)
            registerPoint(SmartPointerAnchorProvider.EP_NAME)
        }

        private inline fun <reified T: Any> registerPoint(name: ExtensionPointName<T>) =
            registerApplicationExtensionPoint(name, T::class.java)
    }
}

private object IdeaExtensionPoints {

    fun registerVersionSpecificAppExtensionPoints(area: ExtensionsArea) {
        registerExtensionPoint(area, ClsCustomNavigationPolicy.EP_NAME, ClsCustomNavigationPolicy::class.java)
        registerExtensionPoint(area, JavaModuleSystem.EP_NAME, JavaModuleSystem::class.java)
    }

    fun registerVersionSpecificProjectExtensionPoints(area: ExtensionsArea) {
        registerExtensionPoint(area, JvmElementProvider.EP_NAME, JvmElementProvider::class.java)
    }
}
