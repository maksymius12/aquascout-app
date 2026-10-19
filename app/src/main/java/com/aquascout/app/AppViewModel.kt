package com.aquascout.app

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

enum class Screen { LOGIN, DEVICES, SCAN, ROBOT, FORM, MAP, ZONE, MISSION, RESULT, HISTORY, PROFILE }

val ROBOT_COLORS = listOf(Color(0xFFE08A1E), Color(0xFF7B4BC4), Color(0xFF138D75), Color(0xFFC0392F))

/** Один робот у місії: його смуга зони і маршрут. */
data class MissionBot(val link: RobotLink, val zone: Zone, val route: Route)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val scanner = BtScanner(app)

    var screen by mutableStateOf(if (store.userEmail != null) Screen.DEVICES else Screen.LOGIN)
        private set
    private val backStack = ArrayDeque<Screen>()

    fun go(s: Screen) { if (s != screen) { backStack.addLast(screen); screen = s } }
    fun tab(s: Screen) { backStack.clear(); screen = s }
    fun back(): Boolean {
        if (screen == Screen.MISSION) return true // під час місії «назад» вимкнено
        val prev = backStack.removeLastOrNull() ?: return false
        screen = prev
        return true
    }

    // ---------- акаунт (локально на телефоні) ----------
    var userEmail by mutableStateOf(store.userEmail)
        private set

    /** Повертає текст помилки або null, якщо все добре. */
    fun auth(email: String, password: String, register: Boolean): String? {
        val e = email.trim().lowercase()
        if (!Regex("^\\S+@\\S+\\.\\S+$").matches(e)) return "Перевір e-mail: схоже, в ньому помилка."
        if (password.length < 6) return "Пароль має містити щонайменше 6 символів."
        if (register) {
            if (store.hasAccount(e)) return "Такий акаунт уже є. Увійди."
            store.createAccount(e, password)
        } else {
            if (!store.hasAccount(e)) return "Акаунта не знайдено. Створи його."
            if (!store.checkPassword(e, password)) return "Невірний пароль."
        }
        store.userEmail = e
        userEmail = e
        tab(Screen.DEVICES)
        return null
    }

    fun logout() {
        robots.forEach { it.close() }
        robots.clear()
        store.userEmail = null
        userEmail = null
        tab(Screen.LOGIN)
    }

    // ---------- роботи ----------
    val robots = mutableStateListOf<RobotLink>()
    var current by mutableStateOf<RobotLink?>(null)
        private set
    var connectingAddress by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    fun openRobot(r: RobotLink) { current = r; go(Screen.ROBOT) }

    fun connect(device: BluetoothDevice) {
        if (robots.any { it.address == device.address }) { message = "${device.safeName()} уже підключено."; return }
        if (connectingAddress != null) return
        connectingAddress = device.address
        viewModelScope.launch {
            val link = RobotLink(device, ROBOT_COLORS[robots.size % ROBOT_COLORS.size], viewModelScope)
            val res = link.connect(scanner.adapter)
            connectingAddress = null
            if (res.isSuccess) {
                robots.add(link)
                selected.add(link.address)
                message = "${link.name} підключено"
                current = link
                screen = Screen.ROBOT
            } else {
                message = "Не вдалося підключитися до ${device.safeName()}. Робот увімкнений? PIN модуля 1234 або 0000."
            }
        }
    }

    fun disconnect(r: RobotLink) {
        r.close()
        robots.remove(r)
        selected.remove(r.address)
        current = robots.firstOrNull()
        tab(Screen.DEVICES)
    }

    // ---------- налаштування руху ----------
    var speed by mutableStateOf(store.speed)
        private set
    var turnS by mutableStateOf(store.turnS)
        private set
    fun setMotion(speedMs: Float, turnSec: Float) {
        speed = speedMs.coerceIn(0.05f, 2f); turnS = turnSec.coerceIn(0.2f, 10f)
        store.speed = speed; store.turnS = turnS
    }

    // ---------- форма і зона ----------
    var form by mutableStateOf(CheckForm())
    var zone by mutableStateOf(Zone(10f, 2f, 12f, 8f))
    var spacing by mutableStateOf(2f)
    val selected = mutableStateListOf<String>()

    fun selectedRobots(): List<RobotLink> = robots.filter { it.address in selected && it.connected.value }

    fun plannedBots(): List<MissionBot> {
        val bots = selectedRobots()
        if (bots.isEmpty()) return emptyList()
        val strips = splitZone(zone, bots.size)
        return bots.mapIndexed { i, b -> MissionBot(b, strips[i], planRoute(strips[i], spacing, speed, turnS)) }
    }

    // ---------- місія ----------
    var missionBots by mutableStateOf<List<MissionBot>>(emptyList())
        private set
    var missionElapsed by mutableStateOf(0f)
        private set
    var missionTotal by mutableStateOf(0f)
        private set
    var missionRunning by mutableStateOf(false)
        private set
    val missionReadings = mutableStateListOf<Reading>()
    val missionLog = mutableStateListOf<String>()
    private var missionJob: Job? = null
    private var missionStopped = false

    var result by mutableStateOf<CheckRecord?>(null)
        private set
    val history = mutableStateListOf<CheckRecord>().apply { addAll(store.loadHistory()) }

    fun startMission() {
        val bots = plannedBots()
        if (bots.isEmpty()) { message = "Немає підключених роботів для перевірки."; return }
        missionBots = bots
        missionTotal = bots.maxOf { it.route.timeS }
        missionElapsed = 0f
        missionReadings.clear()
        missionLog.clear()
        missionLog.add("Старт: " + bots.joinToString { it.link.name })
        missionStopped = false
        missionRunning = true
        go(Screen.MISSION)

        val startMs = System.currentTimeMillis()
        val elapsed = { (System.currentTimeMillis() - startMs) / 1000f }

        missionJob = viewModelScope.launch {
            // таймер для екрана
            val ticker = launch { while (isActive) { missionElapsed = elapsed(); delay(200) } }
            // збір замірів з кожного робота
            val collectors = bots.map { mb ->
                launch {
                    mb.link.readings.collect { r ->
                        val t = elapsed()
                        if (t <= mb.route.timeS) {
                            val pos = positionAt(mb.route, t)
                            if (pos.d >= mb.zone.off - 0.3f) missionReadings.add(Reading(t, mb.link.name, r.w, r.p, pos))
                        }
                    }
                }
            }
            // кожен робот їде своїм маршрутом одночасно з іншими
            val drivers = bots.map { mb ->
                launch {
                    for (st in mb.route.steps) {
                        if (!isActive) break
                        missionLog.add("[${mb.link.shortName}] ${st.cmd} · ${cmdName(st.cmd)} ${fmt1(st.durS)} с")
                        val end = System.currentTimeMillis() + (st.durS * 1000).toLong()
                        // повторюємо команду кожні 300 мс — прошивка зупиняє робота, якщо команд немає 1,5 с
                        while (isActive && System.currentTimeMillis() < end) {
                            if (!mb.link.send(st.cmd)) { missionLog.add("[${mb.link.shortName}] зв'язок втрачено — робот зупиниться сам"); return@launch }
                            delay(300)
                        }
                    }
                    mb.link.send('S')
                    if (bots.size > 1) missionLog.add("[${mb.link.shortName}] смугу пройдено")
                }
            }
            drivers.joinAll()
            ticker.cancel()
            collectors.forEach { it.cancel() }
            finishMission()
        }
    }

    fun emergencyStop() {
        missionStopped = true
        missionJob?.cancel()
        viewModelScope.launch {
            repeat(3) { missionBots.forEach { it.link.send('S') }; delay(100) }
            missionLog.add("Аварійна зупинка")
            finishMission()
        }
    }

    private fun finishMission() {
        if (!missionRunning) return
        missionRunning = false
        val rs = missionReadings.toList()
        val avg = if (rs.isEmpty()) 0f else rs.map { it.p }.average().toFloat()
        val maxR = rs.maxByOrNull { it.p }
        val rec = CheckRecord(
            id = System.currentTimeMillis(), dateMs = System.currentTimeMillis(),
            location = form.location, typeLabel = form.typeLabel, reason = form.reason, note = form.note,
            lat = form.lat, lon = form.lon, robots = missionBots.map { it.link.name },
            avgP = avg, maxP = maxR?.p ?: 0, count = rs.size, durationS = missionElapsed,
            stopped = missionStopped, series = rs.groupBy { it.robot }.mapValues { e -> e.value.map { it.p } },
            hotspot = maxR?.pos
        )
        history.add(0, rec)
        store.saveHistory(history)
        result = rec
        backStack.clear()
        backStack.addLast(Screen.DEVICES)
        screen = Screen.RESULT
    }

    fun openRecord(r: CheckRecord) { result = r; go(Screen.RESULT) }

    fun newCheck() { form = form.copy(note = ""); go(Screen.FORM) }

    override fun onCleared() {
        robots.forEach { it.close() }
        scanner.release()
        super.onCleared()
    }
}

fun cmdName(c: Char) = when (c) {
    'F' -> "вперед"; 'B' -> "назад"; 'L' -> "ліворуч"; 'R' -> "праворуч"; 'S' -> "стоп"; else -> c.toString()
}
