package io.spine.tools

import com.intellij.core.JavaCoreProjectEnvironment
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.mock.MockProject
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import java.io.IOException
import java.time.Instant

object ParserRunner {

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        setupIdeaStandaloneExecution()

        val project = createProject()
        val psiFileFactory = PsiFileFactory.getInstance(project)
        //val psiFacade = JavaPsiFacade.getInstance(project)
        val javaSource =
            """
            public class MyClass {
                
                public static class MyInnerClass {
                 
                    public static enum MyEnum {
                        ONE, TWO, THREE
                    }
                }
            }    
            """.trimIndent()

        val psiJavaFile = parseJavaSource(javaSource, psiFileFactory)

        val classes = psiJavaFile.classes
        val firstClass = classes.first()
        val innerClasses = firstClass.innerClasses
        val found = innerClasses.first().innerClasses.find {
                it.isEnum && it.name == "MyEnum"
            }

        found?.let {
            println("Found enum: ${it.name}")
            println("Line number: ${it.lineNumber()}")
        }

        val node = psiJavaFile.node

        node.psi.accept(PrinterVisitor())
//        val translator: TypeScriptTranslator = TypeScriptTranslator()
//        translator.getCtx().setTranslatedFile(file)
//        node.psi.accept(translator)
//        System.out.println(translator.getCtx().getText())
    }

    private fun createPsiFactory(project: Project): PsiFileFactory {
        return PsiFileFactory.getInstance(project)
    }

    private fun parseJavaSource(javaSource: String, psiFileFactory: PsiFileFactory): PsiJavaFile {
        val modificationStamp = Instant.now().toEpochMilli()
        val psiFile =
            psiFileFactory.createFileFromText(
                "__dummy_file__.java",
                JavaFileType.INSTANCE,
                javaSource,
                modificationStamp,
                true)
        return psiFile as PsiJavaFile
    }

    private fun createProject(): MockProject {
        val appEnvironment = ProtoDataJavaAppEnvironment.create({ })
        val environment = JavaCoreProjectEnvironment({ }, appEnvironment)

        val project = environment.project
        return project
    }
}

private val PsiElement.documentManager: FileDocumentManager
    get() = FileDocumentManager.getInstance()

private fun PsiClass.lineNumber(): Int {
    val virtualFile = containingFile.virtualFile
    checkNotNull(virtualFile)
    val document = documentManager.getDocument(virtualFile)
    return document?.getLineNumber(textOffset) ?: -1
}
