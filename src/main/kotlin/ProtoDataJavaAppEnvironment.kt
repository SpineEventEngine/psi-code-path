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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.*
import com.intellij.psi.FileContextProvider
import com.intellij.psi.JavaModuleSystem
import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy
import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider
import com.intellij.psi.meta.MetaDataContributor
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.io.URLUtil
import com.intellij.util.lang.JavaVersion
import io.spine.tools.IdeaExtensionPoints.registerVersionSpecificAppExtensionPoints
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

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

@Suppress("UnstableApiUsage")
class KotlinCompilerCoreJrtFileSystem : DeprecatedVirtualFileSystem() {
    private val roots =
        ConcurrentFactoryMap.createMap<String, CoreJrtVirtualFile?> { jdkHomePath ->
            val fileSystem = globalJrtFsCache[jdkHomePath] ?: return@createMap null
            CoreJrtVirtualFile(this, jdkHomePath, fileSystem.getPath(""), parent = null)
        }

    override fun getProtocol(): String = StandardFileSystems.JRT_PROTOCOL

    override fun findFileByPath(path: String): VirtualFile? {
        val (jdkHomePath, pathInImage) = splitPath(path)
        val root = roots[jdkHomePath] ?: return null

        if (pathInImage.isEmpty()) return root

        return root.findFileByRelativePath(pathInImage)
    }

    override fun refresh(asynchronous: Boolean) {}

    override fun refreshAndFindFileByPath(path: String): VirtualFile? = findFileByPath(path)

    fun clearRoots() {
        roots.clear()
    }

    companion object {
        private fun loadJrtFsJar(jdkHome: File): File? =
            File(jdkHome, "lib/jrt-fs.jar").takeIf(File::exists)

        fun isModularJdk(jdkHome: File): Boolean =
            loadJrtFsJar(jdkHome) != null

        fun splitPath(path: String): Pair<String, String> {
            val separator = path.indexOf(URLUtil.JAR_SEPARATOR)
            if (separator < 0) {
                throw IllegalArgumentException("Path in CoreJrtFileSystem must contain a separator: $path")
            }
            val localPath = path.substring(0, separator)
            val pathInJar = path.substring(separator + URLUtil.JAR_SEPARATOR.length)
            return Pair(localPath, pathInJar)
        }

        private val globalJrtFsCache = ConcurrentFactoryMap.createMap<String, FileSystem?> { jdkHomePath ->
            val jdkHome = File(jdkHomePath)
            val jrtFsJar = loadJrtFsJar(jdkHome) ?: return@createMap null
            val rootUri = URI.create(StandardFileSystems.JRT_PROTOCOL + ":/")
            /*
              The ClassLoader, that was used to load JRT FS Provider actually lives as long as current thread due to ThreadLocal leak in jrt-fs,
              See https://bugs.openjdk.java.net/browse/JDK-8260621
              So that cache allows us to avoid creating too many classloaders for same JDK and reduce severity of that leak
            */
            if (isAtLeastJava9()) {
                // If the runtime JDK is set to 9+ it has JrtFileSystemProvider,
                // but to load proper jrt-fs (one that is pointed by jdkHome) we should provide "java.home" path
                FileSystems.newFileSystem(rootUri, mapOf("java.home" to jdkHome.absolutePath))
            } else {
                val classLoader = URLClassLoader(arrayOf(jrtFsJar.toURI().toURL()), null)
                // If the runtime JDK is set to <9, there are no JrtFileSystemProvider,
                // we should create classloader with jrt-fs.jar, and DO NOT NEED to pass "java.home" path,
                // as otherwise it will incur additional classloader creation
                FileSystems.newFileSystem(rootUri, emptyMap<String, Nothing>(), classLoader)
            }
        }
    }
}

internal class CoreJrtVirtualFile(
    private val virtualFileSystem: KotlinCompilerCoreJrtFileSystem,
    private val jdkHomePath: String,
    private val path: Path,
    private val parent: CoreJrtVirtualFile?,
) : VirtualFile() {
    // TODO: catch IOException?
    private val attributes: BasicFileAttributes get() = Files.readAttributes(path, BasicFileAttributes::class.java)

    override fun getFileSystem(): VirtualFileSystem = virtualFileSystem

    override fun getName(): String =
        path.fileName.toString()

    override fun getPath(): String =
        FileUtil.toSystemIndependentName(jdkHomePath + URLUtil.JAR_SEPARATOR + path)

    override fun isWritable(): Boolean = false

    override fun isDirectory(): Boolean = Files.isDirectory(path)

    override fun isValid(): Boolean = true

    override fun getParent(): VirtualFile? = parent

    private val myChildren by lazy { computeChildren() }

    override fun getChildren(): Array<out VirtualFile> = myChildren

    private fun computeChildren(): Array<out VirtualFile> {
        val paths = try {
            Files.newDirectoryStream(path).use(Iterable<Path>::toList)
        } catch (e: IOException) {
            emptyList<Path>()
        }
        return when {
            paths.isEmpty() -> EMPTY_ARRAY
            else -> paths.map { path -> CoreJrtVirtualFile(virtualFileSystem, jdkHomePath, path, parent = this) }.toTypedArray()
        }
    }

    override fun getOutputStream(requestor: Any, newModificationStamp: Long, newTimeStamp: Long): OutputStream =
        throw UnsupportedOperationException()

    override fun contentsToByteArray(): ByteArray =
        Files.readAllBytes(path)

    override fun getTimeStamp(): Long =
        attributes.lastModifiedTime().toMillis()

    override fun getLength(): Long = attributes.size()

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}

    override fun getInputStream(): InputStream =
        VfsUtilCore.inputStreamSkippingBOM(Files.newInputStream(path).buffered(), this)

    override fun getModificationStamp(): Long = 0

    override fun equals(other: Any?): Boolean =
        other is CoreJrtVirtualFile && path == other.path && fileSystem == other.fileSystem

    override fun hashCode(): Int =
        path.hashCode()
}

private fun isAtLeastJava9(): Boolean {
    return JavaVersion.current() >= JavaVersion.compose(9)
}
