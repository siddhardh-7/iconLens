package io.github.siddhardh7.iconlens

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBList
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

fun androidResourceReference(resource: IconResource): String = "R.drawable.${resource.name}"

private fun JBList<GalleryTile>.selectedResourceOrNull(): IconResource? =
    selectedValue?.icon?.resource

private fun openResource(project: Project, resource: IconResource) {
    if (!resource.file.isValid) return
    FileEditorManager.getInstance(project).openFile(resource.file, true)
}

private class OpenResourceAction(private val list: JBList<GalleryTile>) :
    AnAction(IconLensBundle.message("toolwindow.IconLens.action.open")) {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = list.selectedResourceOrNull() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val resource = list.selectedResourceOrNull() ?: return
        openResource(e.project ?: return, resource)
    }
}

private class RevealInProjectViewAction(private val list: JBList<GalleryTile>) :
    AnAction(IconLensBundle.message("toolwindow.IconLens.action.revealInProjectView")) {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = list.selectedResourceOrNull() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val resource = list.selectedResourceOrNull() ?: return
        if (!resource.file.isValid) return
        ProjectView.getInstance(e.project ?: return).select(null, resource.file, true)
    }
}

private class CopyResourceNameAction(private val list: JBList<GalleryTile>) :
    AnAction(IconLensBundle.message("toolwindow.IconLens.action.copyName")) {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = list.selectedResourceOrNull() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val resource = list.selectedResourceOrNull() ?: return
        CopyPasteManager.copyTextToClipboard(resource.name)
    }
}

private class CopyResourceReferenceAction(private val list: JBList<GalleryTile>) :
    AnAction(IconLensBundle.message("toolwindow.IconLens.action.copyReference")) {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = list.selectedResourceOrNull() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val resource = list.selectedResourceOrNull() ?: return
        CopyPasteManager.copyTextToClipboard(androidResourceReference(resource))
    }
}

internal fun installGalleryResourceActions(project: Project, list: JBList<GalleryTile>) {
    val group = DefaultActionGroup()
    group.addAll(
        OpenResourceAction(list),
        RevealInProjectViewAction(list),
        CopyResourceNameAction(list),
        CopyResourceReferenceAction(list),
    )
    PopupHandler.installSelectionListPopup(list, group, "IconLens.GalleryPopup")

    list.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (e.clickCount != 2) return
            val resource = list.selectedResourceOrNull() ?: return
            openResource(project, resource)
        }
    })
}
