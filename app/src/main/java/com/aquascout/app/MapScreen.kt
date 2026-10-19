package com.aquascout.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Безкоштовна карта OpenStreetMap (стиль OpenFreeMap, без ключів). */
private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

@Composable
fun MapScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(vm.form.lat?.let { LatLng(it, vm.form.lon ?: 0.0) }) }
    var name by remember { mutableStateOf(vm.form.location) }
    var query by remember { mutableStateOf("") }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }

    fun setPin(map: MapLibreMap, ll: LatLng, zoom: Double? = null) {
        picked = ll
        map.clear()
        map.addMarker(MarkerOptions().position(ll))
        if (zoom != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, zoom))
    }

    val mapView = remember {
        MapLibre.getInstance(ctx)
        MapView(ctx).apply { onCreate(null) }
    }
    val lifecycle = (ctx as ComponentActivity).lifecycle
    DisposableEffect(lifecycle) {
        var destroyed = false
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> { if (!destroyed) { destroyed = true; mapView.onDestroy() } }
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            if (!destroyed) {
                destroyed = true
                mapView.onPause(); mapView.onStop(); mapView.onDestroy()
            }
        }
    }

    Page("Місце на карті", onBack = { vm.back() }, scroll = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Назва або координати") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val q = query.trim()
                val map = mapRef ?: return@TextButton
                val coord = Regex("^\\s*(-?\\d{1,2}[.,]\\d+)\\s*[,; ]\\s*(-?\\d{1,3}[.,]\\d+)\\s*$").find(q)
                if (coord != null) {
                    setPin(map, LatLng(coord.groupValues[1].replace(',', '.').toDouble(), coord.groupValues[2].replace(',', '.').toDouble()), 16.0)
                } else if (q.isNotEmpty()) {
                    scope.launch {
                        val res = searchPlace(q)
                        if (res == null) vm.message = "Нічого не знайдено. Спробуй інакше або встав координати."
                        else { setPin(map, res.first, 16.0); if (name.isBlank()) name = res.second }
                    }
                }
            }) { Text("Знайти") }
        }
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    mapRef = map
                    map.setStyle(MAP_STYLE)
                    val start = picked ?: LatLng(50.4501, 30.5234)
                    map.cameraPosition = CameraPosition.Builder().target(start).zoom(if (picked != null) 16.0 else 11.0).build()
                    picked?.let { setPin(map, it) }
                    map.addOnMapClickListener { ll -> setPin(map, ll); true }
                }
                mapView
            },
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))
        )
        Text("Торкнись карти там, де водойма, — з'явиться мітка.", color = Mist, fontSize = 13.sp)
        picked?.let { Text("📍 %.5f, %.5f".format(it.latitude, it.longitude), color = Sea, fontSize = 13.sp) }
        OutlinedTextField(name, { name = it }, label = { Text("Назва місця") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        PrimaryButton("Обрати це місце", enabled = picked != null) {
            val p = picked ?: return@PrimaryButton
            vm.form = vm.form.copy(lat = p.latitude, lon = p.longitude,
                location = name.ifBlank { vm.form.location.ifBlank { "Точка %.4f, %.4f".format(p.latitude, p.longitude) } })
            vm.back()
        }
        Note("Карта © учасники OpenStreetMap, стиль OpenFreeMap. Пошук — Nominatim. Потрібен інтернет.")
    }
}

/** Пошук місця за назвою через Nominatim (OpenStreetMap). */
private suspend fun searchPlace(q: String): Pair<LatLng, String>? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&accept-language=uk&countrycodes=ua&q=" + URLEncoder.encode(q, "UTF-8"))
        val c = url.openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "AquaScout/1.0 (school project)")
        c.connectTimeout = 8000
        c.readTimeout = 8000
        val body = c.inputStream.bufferedReader().use { it.readText() }
        val a = JSONArray(body)
        if (a.length() == 0) null else {
            val o = a.getJSONObject(0)
            LatLng(o.getString("lat").toDouble(), o.getString("lon").toDouble()) to
                o.optString("display_name").split(',').take(2).joinToString(",").trim()
        }
    } catch (e: Exception) { null }
}
