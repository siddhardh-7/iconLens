package io.github.siddhardh7.iconlens

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.ContentFactory

class IconLensToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = JBPanelWithEmptyText().apply {
            emptyText.text = IconLensBundle.message("toolwindow.IconLens.emptyText")
        }
        val toolWindowContent = ContentFactory.getInstance().createContent(content, null, false)
        toolWindow.contentManager.addContent(toolWindowContent)
    }
}
