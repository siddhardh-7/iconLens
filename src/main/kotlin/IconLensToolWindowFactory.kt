package io.github.siddhardh7.iconlens

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
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
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
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
private const val CHECKER_CELL_SIZE = 8
private val CHECKER_LIGHT = Color(222, 222, 222)
private val CHECKER_DARK = Color(196, 196, 196)

private fun paintCheckerboard(g: Graphics2D, width: Int, height: Int) {
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            g.color = if (((x / CHECKER_CELL_SIZE) + (y / CHECKER_CELL_SIZE)) % 2 == 0) CHECKER_LIGHT else CHECKER_DARK
            g.fillRect(x, y, CHECKER_CELL_SIZE, CHECKER_CELL_SIZE)
            x += CHECKER_CELL_SIZE
        }
        y += CHECKER_CELL_SIZE
    }
}

internal data class GalleryTile(val icon: RenderedIcon, val score: Double?)

private fun BufferedImage.scaledForPreview(): Image {
    if (width <= MAX_QUERY_PREVIEW_DIMENSION && height <= MAX_QUERY_PREVIEW_DIMENSION) return this
    val scale = MAX_QUERY_PREVIEW_DIMENSION.toDouble() / maxOf(width, height)
    return getScaledInstance((width * scale).toInt(), (height * scale).toInt(), Image.SCALE_SMOOTH)
}

class IconLensToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val normalizer = CenteredImageNormalizer()
        val engine = DHashSimilarityEngine()
        var activeQueryImage: BufferedImage? = null
        var contentDisposed = false

        val listModel = DefaultListModel<GalleryTile>()
        val list = JBList(listModel).apply {
            layoutOrientation = JList.HORIZONTAL_WRAP
            visibleRowCount = 0
            cellRenderer = IconTileRenderer()
            emptyText.text = IconLensBundle.message("toolwindow.IconLens.emptyText")
        }
        installGalleryResourceActions(project, list)

        var allIcons: List<RenderedIcon> = emptyList()

        fun applyFilter(query: String) {
            if (activeQueryImage != null) return
            listModel.clear()
            filterByName(allIcons, query).forEach { listModel.addElement(GalleryTile(it, null)) }
        }

        val filterField = JBTextField()
        filterField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilter(filterField.text)
            override fun removeUpdate(e: DocumentEvent) = applyFilter(filterField.text)
            override fun changedUpdate(e: DocumentEvent) = applyFilter(filterField.text)
        })

        fun refresh() {
            val queryAtRefreshTime = activeQueryImage
            scope.launch {
                val rendered = project.service<IconIndex>().refresh(DrawableIconSource(project), DrawableIconRenderer())
                val ranked = queryAtRefreshTime?.let {
                    rankRenderedIcons(rendered.filterIsInstance<RenderedIcon.Rendered>(), it, normalizer, engine)
                }
                ApplicationManager.getApplication().invokeLater {
                    if (!contentDisposed) {
                        allIcons = rendered
                        if (ranked != null && activeQueryImage != null) {
                            listModel.clear()
                            ranked.forEach { listModel.addElement(GalleryTile(it.candidate, it.score)) }
                        } else {
                            applyFilter(filterField.text)
                        }
                    }
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

        fun loadAndShow(load: () -> QueryImage?) {
            val requestId = ++latestRequestId
            val galleryAtRequestTime = allIcons.filterIsInstance<RenderedIcon.Rendered>()
            scope.launch {
                val result = load() ?: QueryImage.Failed("No image found")
                val ranked = if (result is QueryImage.Loaded) {
                    rankRenderedIcons(galleryAtRequestTime, result.image, normalizer, engine)
                } else {
                    null
                }
                ApplicationManager.getApplication().invokeLater {
                    if (!contentDisposed && requestId == latestRequestId) {
                        showQueryImage(result)
                        if (result is QueryImage.Loaded && ranked != null) {
                            activeQueryImage = result.image
                            filterField.isEnabled = false
                            filterField.text = ""
                            listModel.clear()
                            ranked.forEach { listModel.addElement(GalleryTile(it.candidate, it.score)) }
                        }
                    }
                }
            }
        }

        fun clearQuery() {
            latestRequestId++
            activeQueryImage = null
            filterField.isEnabled = true
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

private class IconTileRenderer : ListCellRenderer<GalleryTile> {
    override fun getListCellRendererComponent(
        list: JList<out GalleryTile>,
        value: GalleryTile,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        }
        val renderedIcon = value.icon
        val icon = when (renderedIcon) {
            is RenderedIcon.Rendered -> ImageIcon(renderedIcon.image)
            is RenderedIcon.Failed -> AllIcons.General.Warning
        }
        val score = value.score
        val iconLabel = object : JLabel(icon) {
            override fun paintComponent(g: Graphics) {
                paintCheckerboard(g as Graphics2D, width, height)
                super.paintComponent(g)
                if (score == null) return
                val text = "${(score * 100).roundToInt()}%"
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.font = font.deriveFont(Font.BOLD, 11f)
                    val metrics = g2.fontMetrics
                    val padding = 4
                    val badgeWidth = metrics.stringWidth(text) + padding * 2
                    val badgeHeight = metrics.height + padding
                    val x = width - badgeWidth
                    val y = height - badgeHeight
                    g2.color = Color(0, 0, 0, 180)
                    g2.fillRoundRect(x, y, badgeWidth, badgeHeight, 8, 8)
                    g2.color = Color.WHITE
                    g2.drawString(text, x + padding, y + metrics.ascent + padding / 2)
                } finally {
                    g2.dispose()
                }
            }
        }.apply {
            alignmentX = Component.CENTER_ALIGNMENT
            horizontalAlignment = JLabel.CENTER
            verticalAlignment = JLabel.CENTER
            val tileSize = Dimension(RENDER_SIZE, RENDER_SIZE)
            preferredSize = tileSize
            minimumSize = tileSize
            maximumSize = tileSize
        }
        val nameLabel = JLabel(renderedIcon.resource.name).apply {
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.BOLD)
        }
        val typeLabel = JLabel("${renderedIcon.resource.type} · ${renderedIcon.resource.moduleName}").apply {
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
