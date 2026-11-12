package dev.nihal.edgereach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nihal.edgereach.data.Config
import dev.nihal.edgereach.data.Direction
import dev.nihal.edgereach.data.Length
import dev.nihal.edgereach.data.LongMode
import dev.nihal.edgereach.data.MAX_SWIPE_ANGLE
import dev.nihal.edgereach.data.Side
import kotlin.math.cos
import kotlin.math.sin

object ZoneColors {
    val up = Color(0xFF66BB6A)
    val straight = Color(0xFF42A5F5)
    val down = Color(0xFFFFA726)

    fun of(d: Direction) = when (d) {
        Direction.UP -> up
        Direction.STRAIGHT -> straight
        Direction.DOWN -> down
    }
}

private fun Direction.shortName() = when (this) {
    Direction.UP -> "Up"
    Direction.STRAIGHT -> "Straight"
    Direction.DOWN -> "Down"
}

/**
 * Draws the three swipe zones of a handle as a fan coming out of the screen edge, with rings at
 * the short and long swipe distances and the action each zone runs.
 *
 * The fan starts at the vertical centre of the canvas, on the [side] edge. [fanRadius] is how far
 * the fan reaches; [dpScale] maps the configured distances to the drawing (1 = real size).
 */
@Composable
fun ZoneFan(
    config: Config,
    side: Side,
    fanRadius: Dp,
    dpScale: Float,
    label: (String) -> String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    textBackground: Color = Color(0xCC000000),
) {
    val measurer = rememberTextMeasurer()
    val hc = config.handle(side)
    Canvas(modifier) {
        val left = side == Side.LEFT
        val origin = Offset(if (left) 0f else size.width, size.height / 2f)
        val r = fanRadius.toPx()
        val up = config.upAngle.toFloat()
        val down = config.downAngle.toFloat()
        val max = MAX_SWIPE_ANGLE

        // Converts "degrees upward from straight in" to Compose's clockwise-from-3-o'clock angle.
        fun screenAngle(a: Float) = if (left) -a else 180f + a

        fun wedge(from: Float, to: Float, color: Color) {
            val a = screenAngle(from)
            val b = screenAngle(to)
            val start = minOf(a, b)
            drawArc(
                color = color.copy(alpha = 0.38f),
                startAngle = start,
                sweepAngle = kotlin.math.abs(b - a),
                useCenter = true,
                topLeft = Offset(origin.x - r, origin.y - r),
                size = Size(2 * r, 2 * r),
            )
        }
        wedge(up, max, ZoneColors.up)
        wedge(-down, up, ZoneColors.straight)
        wedge(-max, -down, ZoneColors.down)

        fun ring(distDp: Float, dashed: Boolean) {
            val rr = (distDp.dp.toPx() * dpScale)
            if (rr <= 0f || rr > r) return
            val a = screenAngle(-max)
            val b = screenAngle(max)
            drawArc(
                color = Color.White.copy(alpha = 0.85f),
                startAngle = minOf(a, b),
                sweepAngle = kotlin.math.abs(b - a),
                useCenter = false,
                topLeft = Offset(origin.x - rr, origin.y - rr),
                size = Size(2 * rr, 2 * rr),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                ),
            )
        }
        ring(config.shortDistDp.toFloat(), dashed = true)
        if (config.longMode == LongMode.DISTANCE) ring(config.longDistDp.toFloat(), dashed = false)

        // Labels in the middle of each wedge.
        val labelR = r * 0.62f
        val mids = listOf(
            Direction.UP to (up + max) / 2f,
            Direction.STRAIGHT to (up - down) / 2f,
            Direction.DOWN to -(down + max) / 2f,
        )
        for ((d, mid) in mids) {
            val rad = Math.toRadians(mid.toDouble())
            val x = origin.x + (if (left) 1 else -1) * labelR * cos(rad).toFloat()
            val y = origin.y - labelR * sin(rad).toFloat()
            val text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ZoneColors.of(d))) { append(d.shortName()) }
                append("\n")
                append(label(hc.action(d, Length.SHORT)))
                if (config.longMode == LongMode.DISTANCE || config.longMode == LongMode.HOLD) {
                    val long = hc.action(d, Length.LONG)
                    if (long != "NONE") append("\nlong: ${label(long)}")
                }
            }
            val layout = measurer.measure(
                text,
                style = TextStyle(color = textColor, fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.Center),
            )
            val w = layout.size.width.toFloat()
            val h = layout.size.height.toFloat()
            val tl = Offset(
                (x - w / 2f).coerceIn(2f, size.width - w - 2f),
                (y - h / 2f).coerceIn(2f, size.height - h - 2f),
            )
            drawRoundRect(
                color = textBackground,
                topLeft = Offset(tl.x - 6f, tl.y - 4f),
                size = Size(w + 12f, h + 8f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
            )
            drawText(layout, topLeft = tl)
        }
        drawEdgeDot(origin)
    }
}

private fun DrawScope.drawEdgeDot(origin: Offset) {
    drawCircle(Color.White, radius = 4.dp.toPx(), center = origin)
}
