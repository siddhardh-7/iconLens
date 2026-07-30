package io.github.siddhardh7.iconlens

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
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
import java.awt.datatransfer.DataFlavor
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.TransferHandler
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

        val queryPreviewLabel = JLabel(IconLensBundle.message("toolwindow.IconLens.queryEmpty")).apply {
            horizontalAlignment = JLabel.CENTER
        }
        val querySourceLabel = JLabel(" ")

        fun showQueryImage(result: QueryImage) {
            when (result) {
                is QueryImage.Loaded -> {
                    queryPreviewLabel.icon = ImageIcon(result.image)
                    queryPreviewLabel.text = null
                    querySourceLabel.text = result.sourceDescription
                }
                is QueryImage.Failed -> {
                    queryPreviewLabel.icon = AllIcons.General.Warning
                    queryPreviewLabel.text = null
                    querySourceLabel.text = result.reason
                }
            }
        }

        fun loadAndShow(load: () -> QueryImage?) {
            scope.launch {
                val result = load() ?: return@launch
                ApplicationManager.getApplication().invokeLater { showQueryImage(result) }
            }
        }

        val pasteButton = JButton(IconLensBundle.message("toolwindow.IconLens.paste")).apply {
            addActionListener { loadAndShow(::loadQueryImageFromClipboard) }
        }

        val chooseFileButton = JButton(IconLensBundle.message("toolwindow.IconLens.chooseFile")).apply {
            addActionListener {
                val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                    .withFileFilter { it.extension?.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
                val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return@addActionListener
                val file = VfsUtilCore.virtualToIoFile(chosen)
                loadAndShow { loadQueryImageFromFile(file) }
            }
        }

        val queryPreviewArea = JPanel(BorderLayout()).apply {
            add(queryPreviewLabel, BorderLayout.CENTER)
            transferHandler = object : TransferHandler() {
                override fun canImport(support: TransferSupport) =
                    support.isDataFlavorSupported(DataFlavor.imageFlavor) ||
                        support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

                override fun importData(support: TransferSupport): Boolean {
                    if (!canImport(support)) return false
                    loadAndShow { loadQueryImageFromTransferable(support.transferable, "Dropped image") }
                    return true
                }
            }
        }

        val queryButtonsPanel = JPanel().apply {
            add(pasteButton)
            add(chooseFileButton)
        }

        val queryPanel = JPanel(BorderLayout()).apply {
            add(queryPreviewArea, BorderLayout.CENTER)
            add(querySourceLabel, BorderLayout.SOUTH)
            add(queryButtonsPanel, BorderLayout.EAST)
        }

        val filterPanel = JPanel(BorderLayout()).apply {
            add(JLabel(IconLensBundle.message("toolwindow.IconLens.filterLabel")), BorderLayout.WEST)
            add(filterField, BorderLayout.CENTER)
        }

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(queryPanel)
            add(filterPanel)
        }

        val panel = JPanel(BorderLayout()).apply {
            add(topPanel, BorderLayout.NORTH)
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
