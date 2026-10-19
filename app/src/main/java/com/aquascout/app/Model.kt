package com.aquascout.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// ---------- форма перевірки ----------

enum class WaterType(val label: String) {
    LAKE("Озеро"), RIVER("Річка"), POND("Ставок"), SWAMP("Болото"), OTHER("Інше")
}

val REASONS = listOf(
    "Планова перевірка",
    "Змінився колір або запах води",
    "Мор риби",
    "Цвітіння води",
    "Шкільний проєкт / урок",
    "Інше"
)

data class CheckForm(
    val type: WaterType = WaterType.LAKE,
    val typeOther: String = "",
    val location: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val reason: String = REASONS[0],
    val note: String = ""
) {
    val typeLabel: String get() = if (type == WaterType.OTHER) typeOther.ifBlank { "Інша водойма" } else type.label
}

// ---------- зона і маршрут (усе в метрах) ----------
// x — вздовж берега (0..POND_W), d — відстань від берега (0..POND_D)

const val POND_W = 34f
const val POND_D = 30f
const val BT_RANGE_M = 10f

data class Pt(val x: Float, val d: Float)

/** x — лівий край зони, off — відступ від берега до ближнього краю, w — ширина, h — довжина вглиб */
data class Zone(val x: Float, val off: Float, val w: Float, val h: Float) {
    val farEdge: Float get() = off + h
    fun clamp(): Zone {
        val nw = w.coerceIn(0.5f, POND_W)
        val nh = h.coerceIn(0.5f, POND_D)
        val nx = x.coerceIn(0f, POND_W - nw)
        val noff = off.coerceIn(0f, POND_D - nh)
        return Zone(nx, noff, nw, nh)
    }
}

data class Step(val cmd: Char, val durS: Float, val from: Pt, val to: Pt)

data class Route(val points: List<Pt>, val steps: List<Step>, val lengthM: Float, val timeS: Float, val lanes: Int)

/** Змійка: від берега до ближнього краю, далі проходи вздовж берега з кроком spacing. */
fun planRoute(z: Zone, spacing: Float, speed: Float, turnS: Float): Route {
    val s = max(0.3f, spacing)
    val x0 = z.x
    val x1 = z.x + z.w
    val pts = mutableListOf(Pt(x0, 0f), Pt(x0, z.off))
    var d = z.off
    var dir = 1
    var lanes = 0
    while (true) {
        lanes++
        pts.add(Pt(if (dir > 0) x1 else x0, d))
        if (d + s > z.farEdge + 0.01f) break
        d += s
        pts.add(Pt(if (dir > 0) x1 else x0, d))
        dir = -dir
    }
    val steps = mutableListOf<Step>()
    var length = 0f
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        if (i > 1) {
            val k = i - 2
            val cmd = if (k == 0) 'R' else if (((k - 1) / 2) % 2 == 0) 'L' else 'R'
            steps.add(Step(cmd, turnS, a, a))
        }
        val dist = hypot(b.x - a.x, b.d - a.d)
        length += dist
        steps.add(Step('F', dist / speed, a, b))
    }
    return Route(pts, steps, length, steps.sumOf { it.durS.toDouble() }.toFloat(), lanes)
}

/** Ділить зону на смуги — по одній на робота. */
fun splitZone(z: Zone, n: Int): List<Zone> {
    if (n <= 1) return listOf(z)
    val w = z.w / n
    return List(n) { i -> Zone(z.x + i * w, z.off, w, z.h) }
}

/** Де робот у момент t (секунди від старту маршруту). */
fun positionAt(r: Route, t: Float): Pt {
    var acc = 0f
    for (st in r.steps) {
        if (t <= acc + st.durS) {
            val f = if (st.durS <= 0f) 1f else ((t - acc) / st.durS).coerceIn(0f, 1f)
            return Pt(st.from.x + (st.to.x - st.from.x) * f, st.from.d + (st.to.d - st.from.d) * f)
        }
        acc += st.durS
    }
    return r.points.last()
}

// ---------- оцінка води (провідність, датчик Keyestudio) ----------

data class Verdict(val title: String, val level: Int) // 0 — низька, 1 — помірна, 2 — висока

fun verdictFor(p: Float): Verdict = when {
    p < 30f -> Verdict("Низька провідність", 0)
    p < 60f -> Verdict("Помірна провідність", 1)
    else -> Verdict("Висока провідність", 2)
}

fun adviceFor(level: Int): List<String> = when (level) {
    0 -> listOf(
        "Розчинених солей і домішок мало — добрий знак.",
        "Повторюй перевірку раз на місяць і після сильних дощів.",
        "Провідність не показує бактерії: для купання чи пиття потрібен лабораторний аналіз."
    )
    1 -> listOf(
        "Повтори замір через 1–2 дні в тому самому місці.",
        "Оглянь берег біля найвищих значень: труби, канави, стоки з полів.",
        "Не скидай у воду траву й сміття, залиш смугу рослин уздовж берега."
    )
    else -> listOf(
        "Не купайся й не пускай у воду дітей і тварин, доки не з'ясуєш причину.",
        "Сфотографуй берег і воду, запиши дату й місце.",
        "Повідом Державну екологічну інспекцію або місцеву раду — додай графік з AquaScout.",
        "Здай пробу в лабораторію: там перевірять нітрати, бактерії й нафтопродукти."
    )
}

// ---------- історія ----------

data class Reading(val tS: Float, val robot: String, val w: Int, val p: Int, val pos: Pt)

data class CheckRecord(
    val id: Long,
    val dateMs: Long,
    val location: String,
    val typeLabel: String,
    val reason: String,
    val note: String,
    val lat: Double?,
    val lon: Double?,
    val robots: List<String>,
    val avgP: Float,
    val maxP: Int,
    val count: Int,
    val durationS: Float,
    val stopped: Boolean,
    val series: Map<String, List<Int>>,
    val hotspot: Pt?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("dateMs", dateMs); put("location", location); put("typeLabel", typeLabel)
        put("reason", reason); put("note", note)
        if (lat != null && lon != null) { put("lat", lat); put("lon", lon) }
        put("robots", JSONArray(robots)); put("avgP", avgP.toDouble()); put("maxP", maxP)
        put("count", count); put("durationS", durationS.toDouble()); put("stopped", stopped)
        put("series", JSONObject().apply { series.forEach { (k, v) -> put(k, JSONArray(v)) } })
        hotspot?.let { put("hx", it.x.toDouble()); put("hd", it.d.toDouble()) }
    }

    companion object {
        fun fromJson(o: JSONObject): CheckRecord {
            val robots = o.optJSONArray("robots")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            val ser = mutableMapOf<String, List<Int>>()
            o.optJSONObject("series")?.let { s ->
                s.keys().forEach { k -> val a = s.getJSONArray(k); ser[k] = List(a.length()) { a.getInt(it) } }
            }
            return CheckRecord(
                id = o.getLong("id"), dateMs = o.getLong("dateMs"),
                location = o.optString("location"), typeLabel = o.optString("typeLabel"),
                reason = o.optString("reason"), note = o.optString("note"),
                lat = if (o.has("lat")) o.getDouble("lat") else null,
                lon = if (o.has("lon")) o.getDouble("lon") else null,
                robots = robots, avgP = o.optDouble("avgP").toFloat(), maxP = o.optInt("maxP"),
                count = o.optInt("count"), durationS = o.optDouble("durationS").toFloat(),
                stopped = o.optBoolean("stopped"), series = ser,
                hotspot = if (o.has("hx")) Pt(o.getDouble("hx").toFloat(), o.getDouble("hd").toFloat()) else null
            )
        }
    }
}

// ---------- збереження на телефоні ----------

class Store(context: Context) {
    private val sp = context.getSharedPreferences("aquascout", Context.MODE_PRIVATE)

    var userEmail: String?
        get() = sp.getString("user", null)
        set(v) { sp.edit().putString("user", v).apply() }

    fun hasAccount(email: String) = sp.contains("pw_$email")
    fun createAccount(email: String, password: String) = sp.edit().putString("pw_$email", hash(password)).apply()
    fun checkPassword(email: String, password: String) = sp.getString("pw_$email", null) == hash(password)

    var speed: Float
        get() = sp.getFloat("speed", 0.3f)
        set(v) { sp.edit().putFloat("speed", v).apply() }
    var turnS: Float
        get() = sp.getFloat("turn", 1.5f)
        set(v) { sp.edit().putFloat("turn", v).apply() }

    fun loadHistory(): List<CheckRecord> = try {
        val a = JSONArray(sp.getString("history", "[]"))
        List(a.length()) { CheckRecord.fromJson(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    fun saveHistory(list: List<CheckRecord>) {
        val a = JSONArray(); list.take(100).forEach { a.put(it.toJson()) }
        sp.edit().putString("history", a.toString()).apply()
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

fun fmt1(v: Float): String = if (abs(v - v.toInt()) < 0.05f) v.toInt().toString() else String.format("%.1f", v).replace('.', ',')
fun mmss(s: Float): String { val t = max(0, s.toInt()); return "${t / 60}:${(t % 60).toString().padStart(2, '0')}" }
fun clampF(v: Float, lo: Float, hi: Float) = min(hi, max(lo, v))
