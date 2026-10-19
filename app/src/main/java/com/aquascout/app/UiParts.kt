package com.aquascout.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Page(title: String, onBack: (() -> Unit)? = null, scroll: Boolean = true, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Назад", tint = Ink) }
            else Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            trailing()
        }
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content
        )
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, color: Color = Sea, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line)) { Text(text, color = Sea, fontWeight = FontWeight.Bold) }
}

@Composable
fun AquaCard(modifier: Modifier = Modifier, bg: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).border(1.dp, Line, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content
    )
}

@Composable
fun Chip(text: String, color: Color = Sea) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Dot(color: Color, size: Int = 12) { Box(Modifier.size(size.dp).clip(CircleShape).background(color)) }

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Foam).padding(12.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(label, fontSize = 12.sp, color = Mist)
    }
}

@Composable
fun Note(text: String) {
    Text(text, fontSize = 12.sp, color = Mist, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Foam).padding(10.dp))
}

@Composable
fun WarnBox(text: String) {
    Text(text, fontSize = 13.sp, color = Color(0xFF6E4708),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFBF0DD)).padding(12.dp))
}

// ---------- схема водойми (спільна для зони і місії) ----------

class PondGeo(val size: Size) {
    val shoreH = size.height * (2f / (POND_D + 2f))
    val waterH = size.height - shoreH
    fun px(p: Pt) = Offset(p.x / POND_W * size.width, waterH - p.d / POND_D * waterH)
    fun toPt(o: Offset) = Pt(clampF(o.x / size.width * POND_W, 0f, POND_W), clampF((waterH - o.y) / waterH * POND_D, 0f, POND_D))
}

fun DrawScope.drawPond(g: PondGeo) {
    drawRect(Color(0xFF8CC8EE), size = Size(size.width, g.waterH))
    drawRect(Color(0xFFE9DCC0), topLeft = Offset(0f, g.waterH), size = Size(size.width, g.shoreH))
    val grid = Color.White.copy(alpha = 0.5f)
    var x = 0f
    while (x <= POND_W) { val o = g.px(Pt(x, 0f)); drawLine(grid, Offset(o.x, 0f), Offset(o.x, g.waterH), 1f); x += 5f }
    var d = 0f
    while (d <= POND_D) { val o = g.px(Pt(0f, d)); drawLine(grid, Offset(0f, o.y), Offset(size.width, o.y), 1f); d += 5f }
    val bt = g.px(Pt(0f, BT_RANGE_M)).y
    drawLine(Warn, Offset(0f, bt), Offset(size.width, bt), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
}

fun DrawScope.drawRoute(g: PondGeo, r: Route, color: Color, alpha: Float = 1f) {
    val path = Path()
    r.points.forEachIndexed { i, p -> val o = g.px(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    drawPath(path, color.copy(alpha = alpha), style = Stroke(width = 5f))
    drawCircle(color, 12f, g.px(r.points.first()))
}

@Composable
fun SeriesChart(series: Map<String, List<Int>>, colors: Map<String, Color>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF4FAFE))) {
            val y = { v: Float -> size.height - 8f - v / 100f * (size.height - 16f) }
            listOf(30f to Good, 60f to Bad).forEach { (v, c) ->
                drawLine(c.copy(alpha = 0.6f), Offset(0f, y(v)), Offset(size.width, y(v)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            val longest = series.values.maxOfOrNull { it.size } ?: 0
            if (longest > 1) {
                val step = size.width / (longest - 1)
                series.forEach { (name, vals) ->
                    if (vals.size < 2) return@forEach
                    val path = Path()
                    vals.forEachIndexed { i, v -> val o = Offset(i * step, y(v.toFloat())); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                    drawPath(path, colors[name] ?: Sea, style = Stroke(width = 4f))
                }
            }
        }
        Text("Провідність, % · пунктир 30 і 60 — межі оцінки" + if (series.size > 1) " · колір = робот" else "", fontSize = 11.sp, color = Mist)
    }
}
