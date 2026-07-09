package com.keithfalcon.softball.ui.scorecard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keithfalcon.softball.data.RunnerResult
import com.keithfalcon.softball.ui.theme.FieldGreen
import com.keithfalcon.softball.ui.theme.MonoDigits
import com.keithfalcon.softball.ui.theme.OutRed
import com.keithfalcon.softball.ui.theme.TextFaint

/**
 * Signature glyph (spec §8A): mini baseball diamond.
 * Filled = scored · half-filled = on base · red X = out · hollow = left on base.
 */
@Composable
fun DiamondGlyph(result: RunnerResult, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val green = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        MaterialTheme.colorScheme.primary else FieldGreen
    val faint = TextFaint
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension * 0.72f
        val half = s / 2f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        when (result) {
            RunnerResult.SCORED -> rotate(45f, Offset(cx, cy)) {
                drawRect(green, topLeft = Offset(cx - half, cy - half), size = androidx.compose.ui.geometry.Size(s, s))
            }
            RunnerResult.ON_BASE -> rotate(45f, Offset(cx, cy)) {
                drawRect(
                    green, topLeft = Offset(cx - half, cy - half),
                    size = androidx.compose.ui.geometry.Size(s, s), style = Stroke(width = 1.5.dp.toPx()),
                )
                val tri = Path().apply {
                    moveTo(cx - half, cy - half)
                    lineTo(cx + half, cy - half)
                    lineTo(cx + half, cy + half)
                    close()
                }
                drawPath(tri, green)
            }
            RunnerResult.OUT -> {
                val r = s * 0.42f
                drawLine(OutRed, Offset(cx - r, cy - r), Offset(cx + r, cy + r), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawLine(OutRed, Offset(cx + r, cy - r), Offset(cx - r, cy + r), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            }
            RunnerResult.LEFT_ON_BASE -> rotate(45f, Offset(cx, cy)) {
                drawRect(
                    faint, topLeft = Offset(cx - half, cy - half),
                    size = androidx.compose.ui.geometry.Size(s, s), style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            RunnerResult.NONE -> Unit
        }
    }
}

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)

/** Out counter dots: filled amber for recorded outs, hollow for remaining. */
@Composable
fun OutDots(outs: Int, modifier: Modifier = Modifier, dotColor: Color = Color(0xFFD69A2D), emptyColor: Color = Color(0xFF9FC2A6)) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            if (i < outs) {
                Box(Modifier.size(11.dp).background(dotColor, CircleShape))
            } else {
                Box(Modifier.size(11.dp).border(1.5.dp, emptyColor, CircleShape))
            }
        }
    }
}

/** Line score strip (spec §6.6): runs per inning for both teams, opponent row tappable. */
@Composable
fun LineScoreStrip(
    runsByInning: Map<Int, Int>,
    oppByInning: Map<Int, Int>,
    currentInning: Int,
    ourTotal: Int,
    oppTotal: Int,
    onOpponentTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val innings = maxOf(7, currentInning, runsByInning.keys.maxOrNull() ?: 0, oppByInning.keys.maxOrNull() ?: 0)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            val scroll = rememberScrollState()
            @Composable
            fun row(
                label: String,
                labelColor: Color,
                cells: (Int) -> String,
                total: String,
                onTap: (() -> Unit)? = null,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MonoDigits, color = labelColor, modifier = Modifier.width(46.dp))
                    Row(Modifier.weight(1f).horizontalScroll(scroll)) {
                        for (i in 1..innings) {
                            Text(
                                cells(i),
                                style = MonoDigits,
                                color = if (cells(i) == "·") TextFaint else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(26.dp),
                            )
                        }
                    }
                    Text(total, style = MonoDigits.copy(fontSize = 14.sp), color = labelColor)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("INN", style = MonoDigits, color = TextFaint, modifier = Modifier.width(46.dp))
                Row(Modifier.weight(1f).horizontalScroll(scroll)) {
                    for (i in 1..innings) {
                        Text("$i", style = MonoDigits, color = TextFaint, modifier = Modifier.width(26.dp))
                    }
                }
                Text("R", style = MonoDigits.copy(fontSize = 14.sp), color = TextFaint)
            }
            Spacer(Modifier.height(4.dp))
            row(
                label = "US",
                labelColor = MaterialTheme.colorScheme.primary,
                cells = { i -> runsByInning[i]?.toString() ?: if (i <= currentInning) "0" else "·" },
                total = "$ourTotal",
            )
            Spacer(Modifier.height(4.dp))
            row(
                label = "THEM",
                labelColor = MaterialTheme.colorScheme.error,
                cells = { i -> oppByInning[i]?.toString() ?: if (i <= currentInning) "0" else "·" },
                total = "$oppTotal",
                onTap = onOpponentTap,
            )
        }
    }
}

/** Inning separator: chalk-dashed line on an infield-clay strip (spec §8A). */
@Composable
fun InningSeparator(inning: Int, runsThisInning: Int, ourTotal: Int, oppTotal: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Color(0xFFB85C38), RoundedCornerShape(8.dp))
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
            drawLine(
                Color(0xFFF6F4ED),
                Offset(16.dp.toPx(), center.y),
                Offset(size.width - 16.dp.toPx(), center.y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dash,
            )
        }
        Text(
            "END OF ${ordinal(inning)} · $runsThisInning ${if (runsThisInning == 1) "RUN" else "RUNS"} · $ourTotal—$oppTotal",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .background(Color(0xFFB85C38))
                .padding(horizontal = 10.dp),
        )
    }
}

fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "${n}TH"
    n % 10 == 1 -> "${n}ST"
    n % 10 == 2 -> "${n}ND"
    n % 10 == 3 -> "${n}RD"
    else -> "${n}TH"
}
