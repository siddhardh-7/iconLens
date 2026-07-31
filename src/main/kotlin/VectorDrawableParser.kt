package io.github.siddhardh7.iconlens

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.awt.Color
import java.awt.LinearGradientPaint
import java.awt.Paint
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class UnsupportedVectorDrawableException(message: String) : Exception(message)

data class StyledPath(
    val path: Path2D,
    val fillPaint: Paint?,
    val strokeColor: Color?,
    val strokeWidth: Float,
    val clip: Path2D? = null,
)

data class VectorDrawableShape(
    val viewportWidth: Double,
    val viewportHeight: Double,
    val paths: List<StyledPath>,
)

fun parseVectorDrawable(xml: String): VectorDrawableShape {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    val document = try {
        factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
    } catch (e: Exception) {
        throw UnsupportedVectorDrawableException("Malformed XML: ${e.message}")
    }
    val root = document.documentElement
        ?: throw UnsupportedVectorDrawableException("Empty document")
    if (localName(root) != "vector") {
        throw UnsupportedVectorDrawableException("Unsupported root element '<${root.tagName}>'")
    }
    val viewportWidth = root.attribute("android:viewportWidth")?.toDoubleOrNull()
        ?: throw UnsupportedVectorDrawableException("Missing android:viewportWidth")
    val viewportHeight = root.attribute("android:viewportHeight")?.toDoubleOrNull()
        ?: throw UnsupportedVectorDrawableException("Missing android:viewportHeight")

    val paths = mutableListOf<StyledPath>()
    collectPaths(root, AffineTransform(), null, paths)
    return VectorDrawableShape(viewportWidth, viewportHeight, paths)
}

private fun collectPaths(parent: Element, transform: AffineTransform, clip: Path2D?, out: MutableList<StyledPath>) {
    var activeClip = clip
    var node: Node? = parent.firstChild
    while (node != null) {
        if (node is Element) {
            when (localName(node)) {
                "path" -> out += parsePathElement(node, transform, activeClip)
                "group" -> collectPaths(node, transform.groupTransform(node), activeClip, out)
                "clip-path" -> activeClip = intersectClip(activeClip, parseClipPathElement(node, transform))
                else -> throw UnsupportedVectorDrawableException("Unsupported element '<${node.tagName}>'")
            }
        }
        node = node.nextSibling
    }
}

private fun parseClipPathElement(element: Element, transform: AffineTransform): Path2D {
    val pathData = element.attribute("android:pathData")
        ?: throw UnsupportedVectorDrawableException("<clip-path> missing android:pathData")
    val shape = parsePathData(pathData)
    shape.transform(transform)
    return shape
}

private fun intersectClip(existing: Path2D?, additional: Path2D): Path2D {
    if (existing == null) return additional
    val area = Area(existing)
    area.intersect(Area(additional))
    return Path2D.Double(area)
}

private fun parsePathElement(element: Element, transform: AffineTransform, clip: Path2D?): StyledPath {
    val children = childElements(element)
    val gradientFill = parseGradientFillChild(children, transform)
    if (children.isNotEmpty() && gradientFill == null) {
        throw UnsupportedVectorDrawableException("Unsupported <aapt:attr>/gradient fill on <path>")
    }
    val pathData = element.attribute("android:pathData")
        ?: throw UnsupportedVectorDrawableException("<path> missing android:pathData")
    val shape = parsePathData(pathData)
    shape.transform(transform)
    if (element.attribute("android:fillType").equals("evenOdd", ignoreCase = true)) {
        shape.windingRule = Path2D.WIND_EVEN_ODD
    }
    val fillPaint: Paint? = gradientFill ?: element.attribute("android:fillColor")?.let(::parseAndroidColor)
    val strokeColor = element.attribute("android:strokeColor")?.let(::parseAndroidColor)
    val strokeWidth = element.attribute("android:strokeWidth")?.toFloatOrNull() ?: 0f
    return StyledPath(shape, fillPaint, strokeColor, strokeWidth, clip)
}

/** Recognizes the `<aapt:attr name="android:fillColor"><gradient>...</gradient></aapt:attr>` shape. */
private fun parseGradientFillChild(children: List<Element>, transform: AffineTransform): Paint? {
    val attr = children.singleOrNull() ?: return null
    if (localName(attr) != "attr" || attr.attribute("name") != "android:fillColor") return null
    val gradient = childElements(attr).firstOrNull { localName(it) == "gradient" }
        ?: throw UnsupportedVectorDrawableException("<aapt:attr> missing <gradient>")
    return parseLinearGradient(gradient, transform)
}

// ponytail: only "linear" gradients are implemented (the only type seen in real project icons so
// far); "radial"/"sweep" fail gracefully with a clear reason instead of guessing at the math.
private fun parseLinearGradient(element: Element, transform: AffineTransform): Paint {
    val type = element.attribute("android:type") ?: "linear"
    if (!type.equals("linear", ignoreCase = true)) {
        throw UnsupportedVectorDrawableException("Unsupported gradient type '$type'")
    }
    val items = childElements(element).filter { localName(it) == "item" }
    if (items.size < 2) {
        throw UnsupportedVectorDrawableException("<gradient> needs at least 2 <item> stops")
    }
    val fractions = items.map {
        it.attribute("android:offset")?.toFloatOrNull()
            ?: throw UnsupportedVectorDrawableException("<item> missing android:offset")
    }.toFloatArray()
    val colors = items.map {
        it.attribute("android:color")?.let(::parseAndroidColor)
            ?: throw UnsupportedVectorDrawableException("<item> missing android:color")
    }.toTypedArray()
    val startX = element.attribute("android:startX")?.toDoubleOrNull() ?: 0.0
    val startY = element.attribute("android:startY")?.toDoubleOrNull() ?: 0.0
    val endX = element.attribute("android:endX")?.toDoubleOrNull() ?: 0.0
    val endY = element.attribute("android:endY")?.toDoubleOrNull() ?: 0.0
    val start = transform.transform(Point2D.Double(startX, startY), null)
    val end = transform.transform(Point2D.Double(endX, endY), null)
    return LinearGradientPaint(start, end, fractions, colors)
}

private fun AffineTransform.groupTransform(group: Element): AffineTransform {
    val translateX = group.attribute("android:translateX")?.toDoubleOrNull() ?: 0.0
    val translateY = group.attribute("android:translateY")?.toDoubleOrNull() ?: 0.0
    val scaleX = group.attribute("android:scaleX")?.toDoubleOrNull() ?: 1.0
    val scaleY = group.attribute("android:scaleY")?.toDoubleOrNull() ?: 1.0
    val rotation = group.attribute("android:rotation")?.toDoubleOrNull() ?: 0.0
    val pivotX = group.attribute("android:pivotX")?.toDoubleOrNull() ?: 0.0
    val pivotY = group.attribute("android:pivotY")?.toDoubleOrNull() ?: 0.0

    val result = AffineTransform(this)
    result.translate(translateX + pivotX, translateY + pivotY)
    result.rotate(Math.toRadians(rotation))
    result.scale(scaleX, scaleY)
    result.translate(-pivotX, -pivotY)
    return result
}

private fun childElements(element: Element): List<Element> {
    val result = mutableListOf<Element>()
    var node: Node? = element.firstChild
    while (node != null) {
        if (node is Element) result += node
        node = node.nextSibling
    }
    return result
}

private fun Element.attribute(name: String): String? =
    if (hasAttribute(name)) getAttribute(name) else null

private fun localName(element: Element): String =
    element.tagName.substringAfterLast(':')

private fun parseAndroidColor(value: String): Color {
    if (!value.startsWith("#")) {
        throw UnsupportedVectorDrawableException("Unsupported color reference '$value'")
    }
    val shorthand = value.removePrefix("#")
    val hex = when (shorthand.length) {
        3, 4 -> shorthand.map { "$it$it" }.joinToString("")
        else -> shorthand
    }
    return when (hex.length) {
        6 -> Color(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
        8 -> Color(
            hex.substring(2, 4).toInt(16),
            hex.substring(4, 6).toInt(16),
            hex.substring(6, 8).toInt(16),
            hex.substring(0, 2).toInt(16),
        )
        else -> throw UnsupportedVectorDrawableException("Unsupported color format '$value'")
    }
}
