package io.github.siddhardh7.iconlens

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class IconLensToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val listModel = DefaultListModel<RenderedIcon>()
        val list = JBList(listModel).apply {
            layoutOrientation = JList.HORIZONTAL_WRAP
            visibleRowCount = 0
            cellRenderer = IconTileRenderer()
            emptyText.text = IconLensBundle.message("toolwindow.IconLens.emptyText")
        }

        var allIcons: List<RenderedIcon> = emptyList()

        fun applyFilter(query: String) {
            listModel.clear()
            filterByName(allIcons, query).forEach(listModel::addElement)
        }

        val filterField = JBTextField()
        filterField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilter(filterField.text)
            override fun removeUpdate(e: DocumentEvent) = applyFilter(filterField.text)
            override fun changedUpdate(e: DocumentEvent) = applyFilter(filterField.text)
        })

        fun refresh() {
            scope.launch {
                val rendered = loadGallery(DrawableIconSource(project), DrawableIconRenderer())
                ApplicationManager.getApplication().invokeLater {
                    allIcons = rendered
                    applyFilter(filterField.text)
                }
            }
        }

        val refreshAction = object : AnAction(
            IconLensBundle.message("toolwindow.IconLens.refresh"),
            null,
            AllIcons.Actions.Refresh,
        ) {
            override fun actionPerformed(e: AnActionEvent) = refresh()
        }
        toolWindow.setTitleActions(listOf(refreshAction))

        val filterPanel = JPanel(BorderLayout()).apply {
            add(JLabel(IconLensBundle.message("toolwindow.IconLens.filterLabel")), BorderLayout.WEST)
            add(filterField, BorderLayout.CENTER)
        }

        val panel = JPanel(BorderLayout()).apply {
            add(filterPanel, BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
        }

        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
        Disposer.register(content, Disposable { scope.cancel() })

        refresh()
    }
}

private class IconTileRenderer : ListCellRenderer<RenderedIcon> {
    override fun getListCellRendererComponent(
        list: JList<out RenderedIcon>,
        value: RenderedIcon,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        }
        val icon = when (value) {
            is RenderedIcon.Rendered -> ImageIcon(value.image)
            is RenderedIcon.Failed -> AllIcons.General.Warning
        }
        panel.add(JLabel(icon).apply { alignmentX = Component.CENTER_ALIGNMENT })
        panel.add(JLabel(value.resource.name).apply { alignmentX = Component.CENTER_ALIGNMENT })
        panel.add(
            JLabel("${value.resource.type} · ${value.resource.moduleName}").apply {
                alignmentX = Component.CENTER_ALIGNMENT
            },
        )
        return panel
    }
}
