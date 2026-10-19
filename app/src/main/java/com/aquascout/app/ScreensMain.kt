package com.aquascout.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Вхід ----------

@Composable
fun LoginScreen(vm: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Sea, Wave))).padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("МОНІТОРИНГ ВОДИ", color = Color(0xFFCFEAFA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("AquaScout", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("Підключи робота, познач зону і дізнайся, що у воді.", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (register) "Реєстрація" else "Вхід", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            OutlinedTextField(email, { email = it; error = null }, label = { Text("E-mail") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it; error = null }, label = { Text("Пароль") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Bad, fontSize = 13.sp) }
            PrimaryButton(if (register) "Створити акаунт" else "Увійти") { error = vm.auth(email, pass, register) }
            GhostButton(if (register) "У мене вже є акаунт" else "Створити акаунт") { register = !register; error = null }
            Note("Акаунт зберігається тільки на цьому телефоні.")
        }
    }
}

// ---------- Мої пристрої ----------

@Composable
fun DevicesScreen(vm: AppViewModel) {
    Page("Мої пристрої", trailing = { Chip(vm.userEmail?.substringBefore('@') ?: "") ; Spacer(Modifier.size(12.dp)) }) {
        if (vm.robots.isEmpty()) {
            AquaCard {
                Text("Робота ще не підключено", fontWeight = FontWeight.Bold, color = Ink, fontSize = 17.sp)
                Text("Увімкни AquaScout і натисни «Знайти пристрій». Можна підключити кількох роботів.", color = Mist, fontSize = 14.sp)
                PrimaryButton("Знайти пристрій") { vm.go(Screen.SCAN) }
            }
        } else {
            Text("ПІДКЛЮЧЕНО: ${vm.robots.size}", color = Wave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            vm.robots.forEach { r ->
                val on by r.connected.collectAsState()
                val last by r.last.collectAsState()
                AquaCard(Modifier.clickable { vm.openRobot(r) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Dot(r.color, 14)
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.Bold, color = Ink)
                            Text(last?.let { "Провідність ${it.p}% · сигнал ${it.w}" } ?: "Чекаємо заміри…", color = Mist, fontSize = 13.sp)
                        }
                        Chip(if (on) "онлайн" else "немає зв'язку", if (on) Good else Bad)
                    }
                }
            }
            GhostButton("+ Додати ще робота") { vm.go(Screen.SCAN) }
            PrimaryButton("Аналізувати стан води" + if (vm.robots.size > 1) " · ${vm.robots.size} роботи" else "") { vm.newCheck() }
        }
        vm.history.firstOrNull()?.let { h ->
            Text("ОСТАННЯ ПЕРЕВІРКА", color = Wave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            HistoryRow(h) { vm.openRecord(h) }
        }
    }
}

// ---------- Пошук робота ----------

@Composable
fun ScanScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val hasPerms = { btPermissions().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED } }
    var granted by remember { mutableStateOf(hasPerms()) }
    var enabled by remember { mutableStateOf(vm.scanner.isEnabled) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasPerms() }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { enabled = vm.scanner.isEnabled }
    val found by vm.scanner.found.collectAsState()
    val scanning by vm.scanner.scanning.collectAsState()

    LaunchedEffect(granted, enabled) { if (granted && enabled) vm.scanner.start() }
    DisposableEffect(Unit) { onDispose { vm.scanner.stop() } }

    Page("Пошук пристрою", onBack = { vm.back() }) {
        when {
            vm.scanner.adapter == null -> WarnBox("На цьому телефоні немає Bluetooth.")
            !granted -> {
                Text("Щоб знайти робота, застосунку потрібен доступ до Bluetooth.", color = Ink)
                PrimaryButton("Дозволити") { permLauncher.launch(btPermissions()) }
            }
            !enabled -> {
                Text("Bluetooth вимкнено.", color = Ink)
                PrimaryButton("Увімкнути Bluetooth") {
                    try { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (e: SecurityException) { granted = false }
                }
            }
            else -> {
                val bonded = remember(granted, enabled) { vm.scanner.bonded() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(if (scanning) "Шукаємо пристрої поруч…" else "Пошук завершено", color = Mist, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.scanner.start() }) { Text("Шукати ще") }
                }
                if (bonded.isNotEmpty()) {
                    Text("СПАРЕНІ", color = Wave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    bonded.sortedByDescending { it.looksLikeRobot() }.forEach { DeviceRow(it, vm) }
                }
                val fresh = found.filter { f -> bonded.none { it.address == f.address } }
                Text("ПОРУЧ", color = Wave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (fresh.isEmpty()) Text(if (scanning) "Поки нічого…" else "Нових пристроїв не знайдено.", color = Mist, fontSize = 14.sp)
                fresh.sortedByDescending { it.looksLikeRobot() }.forEach { DeviceRow(it, vm) }
                Note("Перше підключення: телефон попросить PIN модуля HC-05 — зазвичай 1234 або 0000. Bluetooth HC-05 працює до ≈10 м.")
            }
        }
    }
}

@Composable
private fun DeviceRow(d: BluetoothDevice, vm: AppViewModel) {
    val busy = vm.connectingAddress == d.address
    val already = vm.robots.any { it.address == d.address }
    AquaCard(Modifier.clickable(enabled = !busy && !already) { vm.connect(d) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(d.safeName(), fontWeight = FontWeight.Bold, color = Ink)
                Text(d.address, color = Mist, fontSize = 12.sp)
            }
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                already -> Chip("підключено", Good)
                d.looksLikeRobot() -> Chip("схоже на робота")
            }
        }
    }
}

// ---------- Робот ----------

@Composable
fun RobotScreen(vm: AppViewModel) {
    val r = vm.current ?: run { vm.tab(Screen.DEVICES); return }
    val on by r.connected.collectAsState()
    val last by r.last.collectAsState()
    val log by r.log.collectAsState()
    var speedStep by remember { mutableFloatStateOf(9f) }

    Page(r.name, onBack = { vm.back() }, trailing = { Chip(if (on) "підключено" else "немає зв'язку", if (on) Good else Bad); Spacer(Modifier.size(12.dp)) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(last?.let { "${it.p}%" } ?: "—", "провідність", Modifier.weight(1f))
            Stat(last?.w?.toString() ?: "—", "сирий сигнал", Modifier.weight(1f))
        }
        if (!on) WarnBox("Зв'язку з роботом немає. Перевір, що він увімкнений і ближче 10 м, і підключи знову через «+».")
        PrimaryButton("Аналізувати стан води", enabled = on) { vm.newCheck() }

        AquaCard {
            Text("Ручне керування", fontWeight = FontWeight.Bold, color = Ink)
            Text("Тримай кнопку — робот їде. Відпустиш — зупиниться.", color = Mist, fontSize = 13.sp)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldButton("↑", r, 'F')
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HoldButton("←", r, 'L')
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Bad).clickable { r.sendAsync('S') }, contentAlignment = Alignment.Center) {
                        Text("STOP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    HoldButton("→", r, 'R')
                }
                HoldButton("↓", r, 'B')
            }
            Text("Швидкість: ${speedStep.toInt()} з 9", color = Mist, fontSize = 13.sp)
            Slider(speedStep, { speedStep = it }, valueRange = 0f..9f, steps = 8,
                onValueChangeFinished = { r.sendAsync('0' + speedStep.toInt()) })
            Text(log.takeLast(6).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFBFE3F7),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink).padding(10.dp))
        }
        GhostButton("Відключити робота") { vm.disconnect(r) }
    }
}

@Composable
private fun HoldButton(label: String, r: RobotLink, cmd: Char) {
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Foam)
            .pointerInput(cmd) {
                detectTapGestures(onPress = {
                    r.addLog("→ $cmd · ${cmdName(cmd)}")
                    // повторюємо команду, поки кнопку тримають (прошивка стопить через 1,5 с без команд)
                    val job: Job = scope.launch { while (isActive) { r.send(cmd); delay(300) } }
                    tryAwaitRelease()
                    job.cancel()
                    r.sendAsync('S')
                })
            },
        contentAlignment = Alignment.Center
    ) { Text(label, color = Sea, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
}

// ---------- Історія ----------

private val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("uk"))

@Composable
fun HistoryRow(h: CheckRecord, onClick: () -> Unit) {
    val v = verdictFor(h.avgP)
    AquaCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp, 40.dp).clip(RoundedCornerShape(5.dp)).background(levelColor(v.level)))
            Column(Modifier.weight(1f)) {
                Text(h.location.ifBlank { "Без назви" }, fontWeight = FontWeight.Bold, color = Ink)
                Text("${dateFmt.format(Date(h.dateMs))} · ${h.typeLabel}", color = Mist, fontSize = 12.sp)
            }
            Chip("${fmt1(h.avgP)}%", levelColor(v.level))
        }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel) {
    Page("Історія перевірок") {
        if (vm.history.isEmpty()) Text("Перевірок ще немає. Підключи робота і запусти першу.", color = Mist)
        vm.history.forEach { h -> HistoryRow(h) { vm.openRecord(h) } }
    }
}

// ---------- Профіль і налаштування ----------

@Composable
fun ProfileScreen(vm: AppViewModel) {
    var sp by remember { mutableStateOf(vm.speed.toString()) }
    var tn by remember { mutableStateOf(vm.turnS.toString()) }
    Page("Профіль") {
        AquaCard {
            Text("Акаунт", color = Mist, fontSize = 13.sp)
            Text(vm.userEmail ?: "", fontWeight = FontWeight.Bold, color = Ink)
        }
        AquaCard {
            Text("Калібрування руху", fontWeight = FontWeight.Bold, color = Ink)
            Text("Робот не має GPS: маршрут рахується за часом. Заміряй на воді, скільки метрів робот проходить за 10 с, і скільки триває поворот на 90°.", color = Mist, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sp, { sp = it }, label = { Text("Швидкість, м/с") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(tn, { tn = it }, label = { Text("Поворот 90°, с") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            PrimaryButton("Зберегти") {
                val s = sp.replace(',', '.').toFloatOrNull()
                val t = tn.replace(',', '.').toFloatOrNull()
                if (s == null || t == null) vm.message = "Впиши числа, наприклад 0.3 і 1.5"
                else { vm.setMotion(s, t); sp = vm.speed.toString(); tn = vm.turnS.toString(); vm.message = "Збережено" }
            }
        }
        AquaCard {
            Text("Як читати провідність", fontWeight = FontWeight.Bold, color = Ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("до 30%", Good); Text("мало розчинених домішок", fontSize = 13.sp, color = Ink) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("30–60%", Warn); Text("помірно", fontSize = 13.sp, color = Ink) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("понад 60%", Bad); Text("багато солей і домішок", fontSize = 13.sp, color = Ink) }
            Text("Відсотки рахує прошивка між калібруваннями «щупи в повітрі» (0%) і «солона вода» (100%). Межі орієнтовні.", color = Mist, fontSize = 12.sp)
        }
        GhostButton("Вийти") { vm.logout() }
        Spacer(Modifier.height(8.dp))
    }
}
