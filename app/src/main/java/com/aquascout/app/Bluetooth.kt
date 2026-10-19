package com.aquascout.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

/** Стандартний UUID послідовного порту (SPP) — саме його використовує HC-05. */
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

data class LiveReading(val w: Int, val p: Int, val atMs: Long)

/** Одне з'єднання з одним роботом. Роботів може бути кілька одночасно. */
@SuppressLint("MissingPermission")
class RobotLink(
    val device: BluetoothDevice,
    val color: Color,
    private val scope: CoroutineScope
) {
    val address: String = device.address
    val name: String = device.safeName()
    val shortName: String get() = name.replace("AquaScout-", "#")

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _last = MutableStateFlow<LiveReading?>(null)
    val last: StateFlow<LiveReading?> = _last

    private val _readings = MutableSharedFlow<LiveReading>(extraBufferCapacity = 64)
    val readings: SharedFlow<LiveReading> = _readings

    private val _log = MutableStateFlow(listOf("Готово до команд."))
    val log: StateFlow<List<String>> = _log

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    private val writeLock = Mutex()

    suspend fun connect(adapter: BluetoothAdapter?): Result<Unit> = withContext(Dispatchers.IO) {
        try { adapter?.cancelDiscovery() } catch (_: Exception) {}
        val first = runCatching { open(device.createRfcommSocketToServiceRecord(SPP_UUID)) }
        if (first.isSuccess) return@withContext first
        // Деякі телефони з HC-05 підключаються лише через канал 1 — запасний спосіб.
        runCatching {
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            open(m.invoke(device, 1) as BluetoothSocket)
        }
    }

    private fun open(s: BluetoothSocket) {
        s.connect()
        socket = s
        out = s.outputStream
        _connected.value = true
        addLog("Підключено")
        scope.launch(Dispatchers.IO) { readLoop(s) }
    }

    private fun readLoop(s: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(s.inputStream))
            while (true) {
                val line = reader.readLine() ?: break
                parse(line.trim())
            }
        } catch (_: Exception) {
        } finally {
            if (socket === s) {
                _connected.value = false
                addLog("Зв'язок втрачено")
            }
        }
    }

    /** Прошивка надсилає рядки виду W:512;P:43 */
    private fun parse(line: String) {
        if (line.startsWith("STOP")) { addLog("Робот зупинився: немає команд"); return }
        if (!line.contains("W:")) return
        var w: Int? = null
        var p: Int? = null
        line.split(';').forEach { part ->
            val kv = part.split(':')
            if (kv.size == 2) when (kv[0].trim()) {
                "W" -> w = kv[1].trim().toIntOrNull()
                "P" -> p = kv[1].trim().toIntOrNull()
            }
        }
        val ww = w ?: return
        val r = LiveReading(ww, p ?: 0, System.currentTimeMillis())
        _last.value = r
        _readings.tryEmit(r)
    }

    /** Надіслати один символ: F, B, L, R, S, 0..9 або ? */
    suspend fun send(c: Char): Boolean = withContext(Dispatchers.IO) {
        writeLock.withLock {
            try {
                val o = out ?: return@withLock false
                o.write(c.code)
                o.flush()
                true
            } catch (e: Exception) {
                _connected.value = false
                false
            }
        }
    }

    fun sendAsync(c: Char) { scope.launch { send(c) } }

    fun addLog(s: String) { _log.value = (_log.value + s).takeLast(40) }

    fun close() {
        try { out?.write('S'.code); out?.flush() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        out = null
        _connected.value = false
    }
}

/** Пошук Bluetooth-пристроїв: уже спарені + знайдені поруч. */
@SuppressLint("MissingPermission")
class BtScanner(private val context: Context) {
    val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _found = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val found: StateFlow<List<BluetoothDevice>> = _found

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (dev != null && _found.value.none { it.address == dev.address }) {
                        _found.value = _found.value + dev
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
            }
        }
    }

    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun bonded(): List<BluetoothDevice> = try { adapter?.bondedDevices?.toList() ?: emptyList() } catch (e: SecurityException) { emptyList() }

    fun start() {
        val a = adapter ?: return
        if (!registered) {
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        _found.value = emptyList()
        try {
            if (a.isDiscovering) a.cancelDiscovery()
            _scanning.value = a.startDiscovery()
        } catch (e: SecurityException) {
            _scanning.value = false
        }
    }

    fun stop() {
        try { adapter?.cancelDiscovery() } catch (_: Exception) {}
        _scanning.value = false
    }

    fun release() {
        stop()
        if (registered) { try { context.unregisterReceiver(receiver) } catch (_: Exception) {}; registered = false }
    }
}

@SuppressLint("MissingPermission")
fun BluetoothDevice.safeName(): String = try { name ?: address } catch (e: SecurityException) { address }

fun BluetoothDevice.looksLikeRobot(): Boolean {
    val n = safeName().lowercase()
    return n.contains("aquascout") || n.contains("hc-05") || n.contains("hc-06")
}
