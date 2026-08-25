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
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.LayoutManager
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import javax.swing.Box
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

        val queryDropZone = QueryDropZone()
        val querySourceLabel = JLabel(" ").apply {
            alignmentX = Component.CENTER_ALIGNMENT
            horizontalAlignment = JLabel.CENTER
            font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
            foreground = UIManager.getColor("Label.disabledForeground") ?: foreground
        }

        fun showQueryImage(result: QueryImage) {
            when (result) {
                is QueryImage.Loaded -> {
                    queryDropZone.showImage(result.image.scaledForPreview())
                    querySourceLabel.text = result.sourceDescription
                }
                is QueryImage.Failed -> {
                    queryDropZone.showError()
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
            queryDropZone.showEmpty()
            querySourceLabel.text = " "
            filterField.text = ""
            applyFilter(filterField.text)
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
                // Must read the payload here, synchronously: a drop's Transferable is only
                // valid for this call's duration (see readQueryTransferPayload's kdoc).
                val payload = try {
                    readQueryTransferPayload(support.transferable)
                } catch (e: Exception) {
                    loadAndShow { QueryImage.Failed(e.message ?: e.javaClass.simpleName) }
                    return true
                } ?: return false
                loadAndShow { queryImageFromTransferPayload(payload, "Dropped image") }
                return true
            }
        }

        querySourceLabel.transferHandler = queryDropHandler
        queryDropZone.transferHandler = queryDropHandler

        val queryPreviewPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            queryDropZone.alignmentX = Component.CENTER_ALIGNMENT
            add(queryDropZone)
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(querySourceLabel)
            transferHandler = queryDropHandler
        }

        val queryButtonsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            val buttons = listOf(pasteButton, chooseFileButton, clearButton)
            val buttonWidth = buttons.maxOf { it.preferredSize.width }
            buttons.forEachIndexed { index, button ->
                button.alignmentX = Component.LEFT_ALIGNMENT
                button.maximumSize = Dimension(buttonWidth, button.preferredSize.height)
                add(button)
                if (index != buttons.lastIndex) add(Box.createVerticalStrut(JBUI.scale(6)))
            }
            transferHandler = queryDropHandler
        }

        val queryPanel = JPanel(QueryRowLayout(JBUI.scale(16))).apply {
            border = BorderFactory.createEmptyBorder(JBUI.scale(8), JBUI.scale(8), JBUI.scale(8), JBUI.scale(8))
            add(queryPreviewPanel)
            add(queryButtonsPanel)
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

private val QUERY_DROP_ZONE_HEIGHT = JBUI.scale(MAX_QUERY_PREVIEW_DIMENSION + 16)
private val QUERY_DROP_ZONE_MAX_WIDTH = QUERY_DROP_ZONE_HEIGHT * 4
private val QUERY_DROP_ZONE_DASH = floatArrayOf(JBUI.scale(6).toFloat(), JBUI.scale(5).toFloat())

/**
 * Lays out exactly two children: a drop-zone block (index 0) sized dynamically between
 * [QUERY_DROP_ZONE_HEIGHT] and [QUERY_DROP_ZONE_MAX_WIDTH], and a button column (index 1) docked
 * to its right. The button column is hidden — never wrapped — once there isn't room for both.
 */
private class QueryRowLayout(private val gap: Int) : LayoutManager {
    override fun addLayoutComponent(name: String?, comp: Component) = Unit
    override fun removeLayoutComponent(comp: Component) = Unit

    override fun minimumLayoutSize(parent: Container): Dimension = preferredLayoutSize(parent)

    override fun preferredLayoutSize(parent: Container): Dimension {
        val insets = parent.insets
        val dropZone = parent.getComponent(0)
        val height = maxOf(dropZone.preferredSize.height, QUERY_DROP_ZONE_HEIGHT)
        return Dimension(insets.left + insets.right + QUERY_DROP_ZONE_HEIGHT, insets.top + insets.bottom + height)
    }

    override fun layoutContainer(parent: Container) {
        val insets = parent.insets
        val availableWidth = parent.width - insets.left - insets.right
        val dropZone = parent.getComponent(0)
        val buttons = parent.getComponent(1)

        val buttonsWidth = buttons.preferredSize.width
        val fitsButtons = availableWidth >= QUERY_DROP_ZONE_HEIGHT + gap + buttonsWidth
        buttons.isVisible = fitsButtons

        val dropZoneWidth = if (fitsButtons) {
            (availableWidth - gap - buttonsWidth).coerceIn(QUERY_DROP_ZONE_HEIGHT, QUERY_DROP_ZONE_MAX_WIDTH)
        } else {
            availableWidth.coerceIn(0, QUERY_DROP_ZONE_MAX_WIDTH)
        }
        val rowContentWidth = dropZoneWidth + if (fitsButtons) gap + buttonsWidth else 0
        val startX = insets.left + maxOf(0, (availableWidth - rowContentWidth) / 2)

        val dropZoneHeight = dropZone.preferredSize.height
        val rowHeight = maxOf(dropZoneHeight, if (fitsButtons) buttons.preferredSize.height else 0)
        dropZone.setBounds(startX, insets.top + (rowHeight - dropZoneHeight) / 2, dropZoneWidth, dropZoneHeight)

        if (fitsButtons) {
            val buttonsHeight = buttons.preferredSize.height
            buttons.setBounds(
                startX + dropZoneWidth + gap,
                insets.top + (rowHeight - buttonsHeight) / 2,
                buttonsWidth,
                buttonsHeight,
            )
        }
    }
}

private sealed interface QueryDropZoneState {
    data object Empty : QueryDropZoneState
    data object Error : QueryDropZoneState
    data class Preview(val image: Image) : QueryDropZoneState
}

/** Drop target with a fixed height that stretches to fill available width, capped at 4x its height. */
private class QueryDropZone : JPanel() {
    private var state: QueryDropZoneState = QueryDropZoneState.Empty

    init {
        preferredSize = Dimension(QUERY_DROP_ZONE_HEIGHT, QUERY_DROP_ZONE_HEIGHT)
        minimumSize = Dimension(QUERY_DROP_ZONE_HEIGHT, QUERY_DROP_ZONE_HEIGHT)
        maximumSize = Dimension(QUERY_DROP_ZONE_MAX_WIDTH, QUERY_DROP_ZONE_HEIGHT)
    }

    fun showImage(image: Image) {
        state = QueryDropZoneState.Preview(image)
        repaint()
    }

    fun showError() {
        state = QueryDropZoneState.Error
        repaint()
    }

    fun showEmpty() {
        state = QueryDropZoneState.Empty
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            when (val current = state) {
                is QueryDropZoneState.Preview -> paintPreview(g2, current.image)
                QueryDropZoneState.Error -> paintPlaceholder(g2, AllIcons.General.Warning)
                QueryDropZoneState.Empty -> paintPlaceholder(g2, AllIcons.FileTypes.Image)
            }
            paintDashedBorder(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun paintPreview(g2: Graphics2D, image: Image) {
        paintCheckerboard(g2, width, height)
        val iw = image.getWidth(this)
        val ih = image.getHeight(this)
        if (iw <= 0 || ih <= 0) return
        val scale = minOf(width.toDouble() / iw, height.toDouble() / ih, 1.0)
        val dw = (iw * scale).roundToInt()
        val dh = (ih * scale).roundToInt()
        g2.drawImage(image, (width - dw) / 2, (height - dh) / 2, dw, dh, this)
    }

    private fun paintPlaceholder(g2: Graphics2D, icon: javax.swing.Icon) {
        val hintText = IconLensBundle.message("toolwindow.IconLens.queryEmpty")
        val gap = JBUI.scale(6)
        val metrics = g2.getFontMetrics(font)
        val textWidth = metrics.stringWidth(hintText)
        val blockHeight = icon.iconHeight + gap + metrics.height
        val iconX = (width - icon.iconWidth) / 2
        val iconY = (height - blockHeight) / 2
        icon.paintIcon(this, g2, iconX, iconY)
        g2.color = UIManager.getColor("Label.disabledForeground") ?: foreground
        g2.drawString(hintText, (width - textWidth) / 2, iconY + icon.iconHeight + gap + metrics.ascent)
    }

    private fun paintDashedBorder(g2: Graphics2D) {
        g2.stroke = BasicStroke(JBUI.scale(1.5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, QUERY_DROP_ZONE_DASH, 0f)
        g2.color = JBColor.border()
        val inset = JBUI.scale(1).toFloat()
        val arc = JBUI.scale(14).toFloat()
        g2.draw(RoundRectangle2D.Float(inset, inset, width - inset * 2, height - inset * 2, arc, arc))
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
