package com.aquascout.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

// Палітра «вода»: синій і білий
val Sea = Color(0xFF0E5FA8)
val Wave = Color(0xFF2E9BDB)
val Foam = Color(0xFFEAF5FC)
val Ink = Color(0xFF0B2540)
val Mist = Color(0xFF5E7790)
val Line = Color(0xFFD3E6F3)
val Good = Color(0xFF1D8F63)
val Warn = Color(0xFFB9740F)
val Bad = Color(0xFFC0392F)

fun levelColor(level: Int) = when (level) { 0 -> Good; 1 -> Warn; else -> Bad }

/** Дозволи Bluetooth залежать від версії Android. */
fun btPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Sea, onPrimary = Color.White, secondary = Wave,
                    background = Color.White, surface = Color.White, onSurface = Ink, onBackground = Ink,
                    surfaceVariant = Foam, outline = Line, error = Bad
                )
            ) {
                AquaApp()
            }
        }
    }
}

@Composable
fun AquaApp(vm: AppViewModel = viewModel()) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    BackHandler(enabled = vm.screen != Screen.LOGIN && vm.screen != Screen.DEVICES) { vm.back() }

    val tabs = vm.screen in listOf(Screen.DEVICES, Screen.HISTORY, Screen.PROFILE, Screen.ROBOT)
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (tabs) NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = vm.screen == Screen.DEVICES || vm.screen == Screen.ROBOT, onClick = { vm.tab(Screen.DEVICES) },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Пристрої") })
                NavigationBarItem(selected = false, onClick = { vm.go(Screen.SCAN) },
                    icon = { Icon(Icons.Filled.Add, null) }, label = { Text("Додати") })
                NavigationBarItem(selected = vm.screen == Screen.HISTORY, onClick = { vm.tab(Screen.HISTORY) },
                    icon = { Icon(Icons.Filled.List, null) }, label = { Text("Історія") })
                NavigationBarItem(selected = vm.screen == Screen.PROFILE, onClick = { vm.tab(Screen.PROFILE) },
                    icon = { Icon(Icons.Filled.Person, null) }, label = { Text("Профіль") })
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (vm.screen) {
                Screen.LOGIN -> LoginScreen(vm)
                Screen.DEVICES -> DevicesScreen(vm)
                Screen.SCAN -> ScanScreen(vm)
                Screen.ROBOT -> RobotScreen(vm)
                Screen.FORM -> FormScreen(vm)
                Screen.MAP -> MapScreen(vm)
                Screen.ZONE -> ZoneScreen(vm)
                Screen.MISSION -> MissionScreen(vm)
                Screen.RESULT -> ResultScreen(vm)
                Screen.HISTORY -> HistoryScreen(vm)
                Screen.PROFILE -> ProfileScreen(vm)
            }
        }
    }
}
