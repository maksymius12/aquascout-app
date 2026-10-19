package com.aquascout.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ---------- Крок 1: форма ----------

@Composable
fun FormScreen(vm: AppViewModel) {
    val f = vm.form
    var error by remember { mutableStateOf<String?>(null) }
    Page("Нова перевірка", onBack = { vm.back() }) {
        StepBar(1)
        Text("Що це за водойма", color = Mist, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WaterType.entries.forEach { t ->
                val on = f.type == t
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) Foam else Color.White)
                        .border(1.dp, if (on) Sea else Line, RoundedCornerShape(12.dp))
                        .clickable { vm.form = f.copy(type = t) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(t.label, fontSize = 12.sp, color = if (on) Sea else Ink, fontWeight = FontWeight.Bold) }
            }
        }
        if (f.type == WaterType.OTHER) {
            OutlinedTextField(f.typeOther, { vm.form = f.copy(typeOther = it) }, label = { Text("Що це? Кар'єр, канал…") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(f.location, { vm.form = f.copy(location = it); error = null }, label = { Text("Назва або локація") },
                singleLine = true, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { vm.go(Screen.MAP) }, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line)) { Text("Карта", color = Sea) }
        }
        if (f.lat != null && f.lon != null) Text("📍 %.5f, %.5f".format(f.lat, f.lon), color = Sea, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Причина перевірки", color = Mist, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp))) {
            REASONS.forEach { r ->
                val on = f.reason == r
                Text(
                    (if (on) "● " else "○ ") + r, color = if (on) Sea else Ink, fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().background(if (on) Foam else Color.White)
                        .clickable { vm.form = f.copy(reason = r) }.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        OutlinedTextField(f.note, { vm.form = f.copy(note = it) }, label = { Text("Коментар (необов'язково)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = Bad, fontSize = 13.sp) }
        PrimaryButton("Далі: позначити зону") {
            if (f.location.isBlank()) error = "Вкажи назву водойми або познач її на карті."
            else vm.go(Screen.ZONE)
        }
    }
}

@Composable
fun StepBar(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i -> Box(Modifier.weight(1f).padding(vertical = 2.dp).clip(RoundedCornerShape(2.dp)).background(if (i < step) Sea else Line).padding(vertical = 2.dp)) }
    }
}

// ---------- Крок 2: зона ----------

@Composable
fun ZoneScreen(vm: AppViewModel) {
    val bots = vm.plannedBots()
    val multi = bots.size > 1
    Page("Зона перевірки", onBack = { vm.back() }) {
        StepBar(2)
        Text("Хто пливе", color = Mist, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.robots.forEach { r ->
                val on = r.address in vm.selected
                Row(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, if (on) Sea else Line, RoundedCornerShape(50))
                        .background(if (on) Foam else Color.White)
                        .clickable {
                            if (on && vm.selected.size == 1) vm.message = "Потрібен хоча б один робот."
                            else if (on) vm.selected.remove(r.address) else vm.selected.add(r.address)
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) { Dot(r.color, 10); Text(r.shortName, fontSize = 13.sp, color = if (on) Sea else Ink, fontWeight = FontWeight.Bold) }
            }
        }
        if (multi) Text("Зону поділено на ${bots.size} смуги — кожен робот іде своєю.", color = Mist, fontSize = 13.sp)
        Text("Проведи пальцем по воді, щоб позначити зону. Знизу берег, клітинка = 5 м, помаранчевий пунктир — межа Bluetooth ≈10 м.", color = Mist, fontSize = 13.sp)

        var dragStart by remember { mutableStateOf<Pt?>(null) }
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(POND_W / (POND_D + 2f)).clip(RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> dragStart = PondGeo(Size(size.width.toFloat(), size.height.toFloat())).toPt(o) },
                        onDragEnd = { dragStart = null },
                        onDrag = { change, _ ->
                            val a = dragStart ?: return@detectDragGestures
                            val b = PondGeo(Size(size.width.toFloat(), size.height.toFloat())).toPt(change.position)
                            val w = abs(b.x - a.x)
                            val h = abs(b.d - a.d)
                            if (w > 0.5f && h > 0.5f) vm.zone = Zone(min(a.x, b.x), min(a.d, b.d), w, h).clamp()
                        }
                    )
                }
        ) {
            val g = PondGeo(size)
            drawPond(g)
            val z = vm.zone
            val tl = g.px(Pt(z.x, z.farEdge))
            val br = g.px(Pt(z.x + z.w, z.off))
            drawRect(Sea.copy(alpha = 0.12f), tl, Size(br.x - tl.x, br.y - tl.y))
            drawRect(Sea, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(4f))
            bots.forEachIndexed { i, b ->
                if (i > 0) {
                    val x = g.px(Pt(b.zone.x, 0f)).x
                    drawLine(Ink, Offset(x, tl.y), Offset(x, br.y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                }
                drawRoute(g, b.route, if (multi) b.link.color else Sea)
            }
        }

        // ручне введення
        Text("Розміри вручну, м", color = Mist, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        val z = vm.zone
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Ширина", z.w, Modifier.weight(1f)) { vm.zone = z.copy(w = it).clamp() }
            NumField("Вглиб", z.h, Modifier.weight(1f)) { vm.zone = z.copy(h = it).clamp() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Від берега", z.off, Modifier.weight(1f)) { vm.zone = z.copy(off = it).clamp() }
            NumField("Від лівого краю", z.x, Modifier.weight(1f)) { vm.zone = z.copy(x = it).clamp() }
        }
        Text("Відстань між проходами: ${fmt1(vm.spacing)} м", color = Mist, fontSize = 13.sp)
        Slider(vm.spacing, { vm.spacing = (it * 2).toInt() / 2f }, valueRange = 1f..5f)

        if (bots.isNotEmpty()) {
            val length = bots.sumOf { it.route.lengthM.toDouble() }.toFloat()
            val time = bots.maxOf { it.route.timeS }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${fmt1(z.w)} × ${fmt1(z.h)} м", "розмір зони", Modifier.weight(1f))
                Stat("${length.toInt()} м", "довжина маршруту", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(mmss(time), "час проходу", Modifier.weight(1f))
                Stat("${bots[0].route.lanes}", if (multi) "проходів у кожного" else "проходів змійкою", Modifier.weight(1f))
            }
        } else WarnBox("Немає підключених роботів. Підключи робота через «+».")
        if (z.farEdge > BT_RANGE_M) WarnBox("Дальній край зони далі ніж 10 м від берега. Bluetooth HC-05 може втратити зв'язок — тоді робот сам зупиниться.")
        Note(
            "Робот не має GPS: маршрут рахується за часом (${fmt1(vm.speed)} м/с, поворот ${fmt1(vm.turnS)} с — змінюється в Профілі). " +
                if (multi) "Постав кожного робота на березі біля його кольорової точки, носом до води."
                else "Постав робота на березі біля точки маршруту, носом до води."
        )
        PrimaryButton("Запустити перевірку", enabled = bots.isNotEmpty()) { vm.startMission() }
    }
}

@Composable
private fun NumField(label: String, value: Float, modifier: Modifier, onValue: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(fmt1(value).replace(',', '.')) }
    OutlinedTextField(
        text, { t -> text = t; t.replace(',', '.').toFloatOrNull()?.let { if (it > 0f || label.startsWith("Від")) onValue(it) } },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

// ---------- Крок 3: місія ----------

@Composable
fun MissionScreen(vm: AppViewModel) {
    val multi = vm.missionBots.size > 1
    val colors = vm.missionBots.associate { it.link.name to it.link.color }
    Page("Перевірка триває") {
        StepBar(3)
        Canvas(Modifier.fillMaxWidth().aspectRatio(POND_W / (POND_D + 2f)).clip(RoundedCornerShape(18.dp))) {
            val g = PondGeo(size)
            drawPond(g)
            vm.missionBots.forEach { drawRoute(g, it.route, if (multi) it.link.color else Sea, 0.3f) }
            vm.missionReadings.forEach { r -> drawCircle(levelColor(verdictFor(r.p.toFloat()).level), 9f, g.px(r.pos)) }
            vm.missionBots.forEach { b ->
                val p = g.px(positionAt(b.route, min(vm.missionElapsed, b.route.timeS)))
                drawCircle(b.link.color, 16f, p)
                drawCircle(Ink, 16f, p, style = Stroke(4f))
            }
        }
        LinearProgressIndicator(
            progress = { if (vm.missionTotal > 0) min(1f, vm.missionElapsed / vm.missionTotal) else 0f },
            modifier = Modifier.fillMaxWidth(), color = Sea, trackColor = Line
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(vm.missionReadings.lastOrNull()?.let { "${it.p}%" } ?: "—", "провідність зараз", Modifier.weight(1f))
            Stat(mmss(max(0f, vm.missionTotal - vm.missionElapsed)), "залишилось", Modifier.weight(1f))
        }
        SeriesChart(vm.missionReadings.groupBy { it.robot }.mapValues { e -> e.value.map { it.p } }, colors)
        Text(vm.missionLog.takeLast(8).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFBFE3F7),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink).padding(10.dp))
        PrimaryButton("Аварійна зупинка (S)", color = Bad) { vm.emergencyStop() }
    }
}

// ---------- Результат ----------

@Composable
fun ResultScreen(vm: AppViewModel) {
    val h = vm.result ?: run { vm.tab(Screen.DEVICES); return }
    val v = verdictFor(h.avgP)
    val ctx = LocalContext.current
    val colors = h.robots.mapIndexed { i, n -> n to ROBOT_COLORS[i % ROBOT_COLORS.size] }.toMap()
    Page("Результат", onBack = { vm.back() }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(levelColor(v.level)).padding(20.dp)) {
            Text("${h.typeLabel.uppercase()} · ${java.text.SimpleDateFormat("dd.MM.yyyy").format(java.util.Date(h.dateMs))}",
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(v.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(h.location, color = Color.White.copy(alpha = 0.92f))
        }
        if (h.stopped) WarnBox("Перевірку зупинено достроково — дані неповні.")
        if (h.count == 0) WarnBox("Заміри не надійшли. Перевір датчик і зв'язок з роботом.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${fmt1(h.avgP)}%", "середня провідність", Modifier.weight(1f))
            Stat("${h.maxP}%", "максимум", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${h.count}", "замірів", Modifier.weight(1f))
            Stat(mmss(h.durationS), "час проходу", Modifier.weight(1f))
        }
        SeriesChart(h.series, colors)
        AquaCard {
            Text("Що робити зараз", fontWeight = FontWeight.Bold, color = Ink)
            h.hotspot?.let { Text("Найвище значення — ≈${it.d.toInt()} м від берега, ${it.x.toInt()} м від лівого краю схеми.", fontSize = 13.sp, color = Ink) }
            adviceFor(v.level).forEachIndexed { i, a -> Text("${i + 1}. $a", fontSize = 14.sp, color = Ink) }
            Text("AquaScout міряє провідність — розчинені солі й домішки. Бактерії й хімію він не бачить: для пиття чи купання потрібен лабораторний аналіз.",
                fontSize = 12.sp, color = Mist)
        }
        AquaCard {
            Text("Причина перевірки", color = Mist, fontSize = 13.sp)
            Text(h.reason, fontWeight = FontWeight.Bold, color = Ink)
            if (h.note.isNotBlank()) Text(h.note, fontSize = 14.sp, color = Ink)
            if (h.robots.isNotEmpty()) Text("Роботи: ${h.robots.joinToString()}", fontSize = 13.sp, color = Mist)
            if (h.lat != null && h.lon != null) {
                Text("📍 %.5f, %.5f · відкрити на карті".format(h.lat, h.lon), color = Sea, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        val url = "https://www.openstreetmap.org/?mlat=${h.lat}&mlon=${h.lon}#map=17/${h.lat}/${h.lon}"
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                    })
            }
        }
        PrimaryButton("Нова перевірка") { if (vm.robots.isEmpty()) vm.tab(Screen.DEVICES) else vm.newCheck() }
    }
}
