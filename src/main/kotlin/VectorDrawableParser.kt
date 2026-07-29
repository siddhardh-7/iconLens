package io.github.siddhardh7.iconlens

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class UnsupportedVectorDrawableException(message: String) : Exception(message)

data class StyledPath(
    val path: Path2D,
    val fillColor: Color?,
    val strokeColor: Color?,
    val strokeWidth: Float,
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
    collectPaths(root, AffineTransform(), paths)
    return VectorDrawableShape(viewportWidth, viewportHeight, paths)
}

private fun collectPaths(parent: Element, transform: AffineTransform, out: MutableList<StyledPath>) {
    var node: Node? = parent.firstChild
    while (node != null) {
        if (node is Element) {
            when (localName(node)) {
                "path" -> out += parsePathElement(node, transform)
                "group" -> collectPaths(node, transform.groupTransform(node), out)
                else -> throw UnsupportedVectorDrawableException("Unsupported element '<${node.tagName}>'")
            }
        }
        node = node.nextSibling
    }
}

private fun parsePathElement(element: Element, transform: AffineTransform): StyledPath {
    if (childElements(element).isNotEmpty()) {
        throw UnsupportedVectorDrawableException("Unsupported <aapt:attr>/gradient fill on <path>")
    }
    val pathData = element.attribute("android:pathData")
        ?: throw UnsupportedVectorDrawableException("<path> missing android:pathData")
    val shape = parsePathData(pathData)
    shape.transform(transform)
    val fillColor = element.attribute("android:fillColor")?.let(::parseAndroidColor)
    val strokeColor = element.attribute("android:strokeColor")?.let(::parseAndroidColor)
    val strokeWidth = element.attribute("android:strokeWidth")?.toFloatOrNull() ?: 0f
    return StyledPath(shape, fillColor, strokeColor, strokeWidth)
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
    val hex = value.removePrefix("#")
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
