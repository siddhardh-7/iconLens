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
import java.awt.Dimension
import java.awt.Font
import java.awt.Image
import java.awt.image.BufferedImage
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
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

private const val MAX_QUERY_PREVIEW_DIMENSION = 128

private fun BufferedImage.scaledForPreview(): Image {
    if (width <= MAX_QUERY_PREVIEW_DIMENSION && height <= MAX_QUERY_PREVIEW_DIMENSION) return this
    val scale = MAX_QUERY_PREVIEW_DIMENSION.toDouble() / maxOf(width, height)
    return getScaledInstance((width * scale).toInt(), (height * scale).toInt(), Image.SCALE_SMOOTH)
}

class IconLensToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                    queryPreviewLabel.icon = ImageIcon(result.image.scaledForPreview())
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

        var latestRequestId = 0
        var contentDisposed = false

        fun loadAndShow(load: () -> QueryImage?) {
            val requestId = ++latestRequestId
            scope.launch {
                val result = load() ?: QueryImage.Failed("No image found")
                ApplicationManager.getApplication().invokeLater {
                    if (!contentDisposed && requestId == latestRequestId) showQueryImage(result)
                }
            }
        }

        fun clearQuery() {
            latestRequestId++
            queryPreviewLabel.icon = null
            queryPreviewLabel.text = IconLensBundle.message("toolwindow.IconLens.queryEmpty")
            querySourceLabel.text = " "
            filterField.text = ""
        }

        val pasteButton = JButton(IconLensBundle.message("toolwindow.IconLens.paste")).apply {
            addActionListener { loadAndShow(::loadQueryImageFromClipboard) }
        }

        val chooseFileButton = JButton(IconLensBundle.message("toolwindow.IconLens.chooseFile")).apply {
            addActionListener {
                val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                    .withFileFilter {
                        resolveIconResourceType(it.name) in
                            setOf(
                                IconResourceType.PNG,
                                IconResourceType.WEBP,
                                IconResourceType.JPEG,
                                IconResourceType.VECTOR_DRAWABLE,
                            ) ||
                            it.extension.equals("svg", ignoreCase = true)
                    }
                val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return@addActionListener
                val file = VfsUtilCore.virtualToIoFile(chosen)
                loadAndShow { loadQueryImageFromFile(file) }
            }
        }

        val clearButton = JButton(IconLensBundle.message("toolwindow.IconLens.clear")).apply {
            addActionListener { clearQuery() }
        }

        val queryDropHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport) =
                SUPPORTED_TRANSFERABLE_FLAVORS.any { support.isDataFlavorSupported(it) }

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) return false
                loadAndShow { loadQueryImageFromTransferable(support.transferable, "Dropped image") }
                return true
            }
        }

        querySourceLabel.transferHandler = queryDropHandler

        val queryPreviewArea = JPanel(BorderLayout()).apply {
            add(queryPreviewLabel, BorderLayout.CENTER)
            transferHandler = queryDropHandler
        }

        val queryButtonsPanel = JPanel().apply {
            add(pasteButton)
            add(chooseFileButton)
            add(clearButton)
            transferHandler = queryDropHandler
        }

        val queryPanel = JPanel(BorderLayout()).apply {
            add(queryPreviewArea, BorderLayout.CENTER)
            add(querySourceLabel, BorderLayout.SOUTH)
            add(queryButtonsPanel, BorderLayout.EAST)
            transferHandler = queryDropHandler
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
        Disposer.register(
            content,
            Disposable {
                contentDisposed = true
                scope.cancel()
            },
        )

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
        val iconLabel = JLabel(icon).apply {
            alignmentX = Component.CENTER_ALIGNMENT
            horizontalAlignment = JLabel.CENTER
            verticalAlignment = JLabel.CENTER
            val tileSize = Dimension(RENDER_SIZE, RENDER_SIZE)
            preferredSize = tileSize
            minimumSize = tileSize
            maximumSize = tileSize
        }
        val nameLabel = JLabel(value.resource.name).apply {
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.BOLD)
        }
        val typeLabel = JLabel("${value.resource.type} · ${value.resource.moduleName}").apply {
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
            foreground = UIManager.getColor("Label.disabledForeground") ?: foreground
        }
        panel.add(iconLabel)
        panel.add(nameLabel)
        panel.add(typeLabel)
        return panel
    }
}
