package io.spine.tools

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

class PrinterVisitor : PsiElementVisitor() {
    private var ident = 0

    override fun visitElement(element: PsiElement) {
        for (i in 0 until ident) {
            print(' ')
        }
        printElement(element)

        ident += 2
        element.acceptChildren(this)
        ident -= 2
    }

    protected fun printElement(element: PsiElement) {
        println(element.toString())
    }
}
