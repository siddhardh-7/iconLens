package io.github.siddhardh7.iconlens

import java.awt.geom.Path2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class UnsupportedPathDataException(message: String) : Exception(message)

private val PATH_TOKEN_REGEX = Regex("[MmLlHhVvCcSsQqTtAaZz]|-?\\d+\\.?\\d*(?:[eE][-+]?\\d+)?|-?\\.\\d+(?:[eE][-+]?\\d+)?")

fun parsePathData(data: String): Path2D {
    val tokens = PATH_TOKEN_REGEX.findAll(data).map { it.value }.toList()
    val consumedLength = tokens.sumOf { it.length }
    val significantLength = data.count { !it.isWhitespace() && it != ',' }
    if (consumedLength != significantLength) {
        throw UnsupportedPathDataException("Unrecognized character in path data: '$data'")
    }
    val path = Path2D.Double()

    if (tokens.isEmpty() || tokens[0][0] !in "Mm") {
        throw UnsupportedPathDataException("Path data must start with 'M'/'m': '$data'")
    }

    var i = 0
    var command = ' '
    var lastCommand = ' '
    var currentX = 0.0
    var currentY = 0.0
    var startX = 0.0
    var startY = 0.0
    var lastControlX = 0.0
    var lastControlY = 0.0

    fun nextNumber(): Double {
        if (i >= tokens.size) throw UnsupportedPathDataException("Unexpected end of path data: '$data'")
        val value = tokens[i].toDoubleOrNull()
            ?: throw UnsupportedPathDataException("Expected number, got '${tokens[i]}' in '$data'")
        i++
        return value
    }

    while (i < tokens.size) {
        if (tokens[i][0].isLetter()) {
            command = tokens[i][0]
            i++
        }

        when (command) {
            'M', 'm' -> {
                val x = nextNumber(); val y = nextNumber()
                if (command == 'm' && lastCommand != ' ') {
                    currentX += x; currentY += y
                } else {
                    currentX = x; currentY = y
                }
                path.moveTo(currentX, currentY)
                startX = currentX; startY = currentY
                command = if (command == 'm') 'l' else 'L'
            }
            'L', 'l' -> {
                val x = nextNumber(); val y = nextNumber()
                currentX = if (command == 'l') currentX + x else x
                currentY = if (command == 'l') currentY + y else y
                path.lineTo(currentX, currentY)
            }
            'H', 'h' -> {
                val x = nextNumber()
                currentX = if (command == 'h') currentX + x else x
                path.lineTo(currentX, currentY)
            }
            'V', 'v' -> {
                val y = nextNumber()
                currentY = if (command == 'v') currentY + y else y
                path.lineTo(currentX, currentY)
            }
            'C', 'c' -> {
                val x1 = nextNumber(); val y1 = nextNumber()
                val x2 = nextNumber(); val y2 = nextNumber()
                val ex = nextNumber(); val ey = nextNumber()
                val relative = command == 'c'
                val cx1 = if (relative) currentX + x1 else x1
                val cy1 = if (relative) currentY + y1 else y1
                val cx2 = if (relative) currentX + x2 else x2
                val cy2 = if (relative) currentY + y2 else y2
                val endX = if (relative) currentX + ex else ex
                val endY = if (relative) currentY + ey else ey
                path.curveTo(cx1, cy1, cx2, cy2, endX, endY)
                lastControlX = cx2; lastControlY = cy2
                currentX = endX; currentY = endY
            }
            'S', 's' -> {
                val x2 = nextNumber(); val y2 = nextNumber()
                val ex = nextNumber(); val ey = nextNumber()
                val relative = command == 's'
                val cx1 = if (lastCommand in "CcSs") 2 * currentX - lastControlX else currentX
                val cy1 = if (lastCommand in "CcSs") 2 * currentY - lastControlY else currentY
                val cx2 = if (relative) currentX + x2 else x2
                val cy2 = if (relative) currentY + y2 else y2
                val endX = if (relative) currentX + ex else ex
                val endY = if (relative) currentY + ey else ey
                path.curveTo(cx1, cy1, cx2, cy2, endX, endY)
                lastControlX = cx2; lastControlY = cy2
                currentX = endX; currentY = endY
            }
            'Q', 'q' -> {
                val x1 = nextNumber(); val y1 = nextNumber()
                val ex = nextNumber(); val ey = nextNumber()
                val relative = command == 'q'
                val cx = if (relative) currentX + x1 else x1
                val cy = if (relative) currentY + y1 else y1
                val endX = if (relative) currentX + ex else ex
                val endY = if (relative) currentY + ey else ey
                path.quadTo(cx, cy, endX, endY)
                lastControlX = cx; lastControlY = cy
                currentX = endX; currentY = endY
            }
            'T', 't' -> {
                val ex = nextNumber(); val ey = nextNumber()
                val relative = command == 't'
                val cx = if (lastCommand in "QqTt") 2 * currentX - lastControlX else currentX
                val cy = if (lastCommand in "QqTt") 2 * currentY - lastControlY else currentY
                val endX = if (relative) currentX + ex else ex
                val endY = if (relative) currentY + ey else ey
                path.quadTo(cx, cy, endX, endY)
                lastControlX = cx; lastControlY = cy
                currentX = endX; currentY = endY
            }
            'A', 'a' -> {
                val rx = nextNumber(); val ry = nextNumber()
                val rotation = nextNumber()
                val largeArc = nextNumber() != 0.0
                val sweep = nextNumber() != 0.0
                val x = nextNumber(); val y = nextNumber()
                val relative = command == 'a'
                val endX = if (relative) currentX + x else x
                val endY = if (relative) currentY + y else y
                appendArc(path, currentX, currentY, rx, ry, rotation, largeArc, sweep, endX, endY)
                currentX = endX; currentY = endY
            }
            'Z', 'z' -> {
                path.closePath()
                currentX = startX; currentY = startY
                if (i < tokens.size && !tokens[i][0].isLetter()) {
                    throw UnsupportedPathDataException("Unexpected argument after 'Z' in '$data'")
                }
            }
            else -> throw UnsupportedPathDataException("Unsupported path command '$command' in '$data'")
        }
        lastCommand = command
    }
    return path
}

private fun appendArc(
    path: Path2D,
    x0: Double, y0: Double,
    rxIn: Double, ryIn: Double,
    rotationDeg: Double,
    largeArc: Boolean,
    sweep: Boolean,
    x: Double, y: Double,
) {
    if (rxIn == 0.0 || ryIn == 0.0 || (x0 == x && y0 == y)) {
        path.lineTo(x, y)
        return
    }
    var rx = abs(rxIn)
    var ry = abs(ryIn)
    val phi = Math.toRadians(rotationDeg % 360.0)
    val cosPhi = cos(phi)
    val sinPhi = sin(phi)

    val dx2 = (x0 - x) / 2.0
    val dy2 = (y0 - y) / 2.0
    val x1p = cosPhi * dx2 + sinPhi * dy2
    val y1p = -sinPhi * dx2 + cosPhi * dy2

    val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if (lambda > 1.0) {
        val scale = sqrt(lambda)
        rx *= scale
        ry *= scale
    }

    val rxSq = rx * rx
    val rySq = ry * ry
    val x1pSq = x1p * x1p
    val y1pSq = y1p * y1p
    val num = (rxSq * rySq - rxSq * y1pSq - rySq * x1pSq).coerceAtLeast(0.0)
    val denom = (rxSq * y1pSq + rySq * x1pSq).let { if (it == 0.0) 1.0 else it }
    val coef = (if (largeArc == sweep) -1.0 else 1.0) * sqrt(num / denom)
    val cxp = coef * (rx * y1p / ry)
    val cyp = coef * -(ry * x1p / rx)

    val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x) / 2.0
    val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y) / 2.0

    fun angleBetween(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        var ang = acos((dot / len).coerceIn(-1.0, 1.0))
        if (ux * vy - uy * vx < 0.0) ang = -ang
        return ang
    }

    val theta1 = angleBetween(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    var deltaTheta = angleBetween(
        (x1p - cxp) / rx, (y1p - cyp) / ry,
        (-x1p - cxp) / rx, (-y1p - cyp) / ry,
    )
    if (!sweep && deltaTheta > 0.0) deltaTheta -= 2 * PI
    if (sweep && deltaTheta < 0.0) deltaTheta += 2 * PI

    val segmentCount = ceil(abs(deltaTheta) / (PI / 2.0)).toInt().coerceAtLeast(1)
    val delta = deltaTheta / segmentCount
    val t = 4.0 / 3.0 * tan(delta / 4.0)

    var theta = theta1
    repeat(segmentCount) {
        val cosTheta1 = cos(theta)
        val sinTheta1 = sin(theta)
        val theta2 = theta + delta
        val cosTheta2 = cos(theta2)
        val sinTheta2 = sin(theta2)

        val e1x = cx + rx * cosPhi * cosTheta1 - ry * sinPhi * sinTheta1
        val e1y = cy + rx * sinPhi * cosTheta1 + ry * cosPhi * sinTheta1
        val de1x = -rx * cosPhi * sinTheta1 - ry * sinPhi * cosTheta1
        val de1y = -rx * sinPhi * sinTheta1 + ry * cosPhi * cosTheta1

        val e2x = cx + rx * cosPhi * cosTheta2 - ry * sinPhi * sinTheta2
        val e2y = cy + rx * sinPhi * cosTheta2 + ry * cosPhi * sinTheta2
        val de2x = -rx * cosPhi * sinTheta2 - ry * sinPhi * cosTheta2
        val de2y = -rx * sinPhi * sinTheta2 + ry * cosPhi * cosTheta2

        path.curveTo(
            e1x + t * de1x, e1y + t * de1y,
            e2x - t * de2x, e2y - t * de2y,
            e2x, e2y,
        )
        theta = theta2
    }
}
