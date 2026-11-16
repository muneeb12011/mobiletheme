package com.muneeb.fsociety

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

val Bg = Color(0xFF0B0B0C)
val Fg = Color(0xFFE6E6E6)
val Dim = Color(0xFF7A7A7A)
val Red = Color(0xFFE11D1D)
val Cyan = Color(0xFF22D3EE)
val Mono = FontFamily.Monospace

data class App(val label: String, val pkg: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Launcher() }
    }
}

fun loadApps(pm: PackageManager): List<App> {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.pkg != "com.muneeb.fsociety" }
        .sortedBy { it.label.lowercase() }
}

fun bootLines(ctx: Context, appCount: Int): List<Pair<String, String>> {
    val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0
    val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    val mi = ActivityManager.MemoryInfo()
    (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
    val fs = StatFs(Environment.getDataDirectory().path)
    val gb = 1024.0 * 1024 * 1024
    val up = SystemClock.elapsedRealtime() / 60000
    return listOf(
        "OK" to "host ${Build.MODEL.lowercase()} / android ${Build.VERSION.RELEASE}",
        "OK" to "power ${lvl * 100 / scale}%${if (charging) " [charging]" else ""}",
        "OK" to "mem ${"%.1f".format((mi.totalMem - mi.availMem) / gb)}/${"%.1f".format(mi.totalMem / gb)} GB",
        "OK" to "disk ${"%.0f".format(fs.availableBytes / gb)} GB free of ${"%.0f".format(fs.totalBytes / gb)}",
        "OK" to "uptime ${up / 60}h ${up % 60}m",
        "OK" to "$appCount binaries indexed",
        "!!" to "they are watching. stay quiet."
    )
}

@Composable
fun Launcher() {
    val ctx = LocalContext.current
    val apps = remember { loadApps(ctx.packageManager) }
    var query by remember { mutableStateOf("") }
    val shown = apps.filter { it.label.contains(query, ignoreCase = true) }

    val clock by produceState("") {
        val f = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) { value = f.format(Date()); delay(1000) }
    }
    val date = remember { SimpleDateFormat("yyyy.MM.dd // EEE", Locale.getDefault()).format(Date()).uppercase() }

    // glitch: RGB split that fires at random
    var gx by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2500, 6000))
            repeat(5) { gx = Random.nextInt(-14, 15); delay(55) }
            gx = 0
        }
    }

    // boot log, typed once
    val lines = remember { bootLines(ctx, apps.size) }
    val done = remember { mutableStateListOf<Pair<String, String>>() }
    var typing by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) {
        for ((tag, msg) in lines) {
            for (n in 1..msg.length) { typing = tag to msg.take(n); delay(14) }
            done.add(tag to msg); typing = null; delay(90)
        }
    }

    fun launch(a: App) {
        ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let { ctx.startActivity(it) }
        query = ""
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 16.dp)) {
            Text("FSOCIETY // 00.00.0", color = Red, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(10.dp))
            Box {
                Text(clock, color = Red.copy(alpha = if (gx != 0) 0.8f else 0f), fontFamily = Mono, fontWeight = FontWeight.Light,
                    fontSize = 84.sp, modifier = Modifier.offset { IntOffset(gx, 0) })
                Text(clock, color = Cyan.copy(alpha = if (gx != 0) 0.8f else 0f), fontFamily = Mono, fontWeight = FontWeight.Light,
                    fontSize = 84.sp, modifier = Modifier.offset { IntOffset(-gx, 0) })
                Text(clock, color = Fg, fontFamily = Mono, fontWeight = FontWeight.Light, fontSize = 84.sp)
            }
            Text(date, color = Dim, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(18.dp))
            Column {
                done.forEach { (t, m) -> LogLine(t, m) }
                typing?.let { (t, m) -> LogLine(t, m) }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(Modifier.weight(1f), reverseLayout = true) {
                itemsIndexed(shown.reversed()) { idx, a ->
                    val n = shown.size - 1 - idx
                    Row(Modifier.fillMaxWidth().clickable { launch(a) }.padding(vertical = 8.dp)) {
                        Text("%02X".format(n), color = Red, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.width(34.dp))
                        Text(a.label.lowercase(), color = Fg, fontFamily = Mono, fontSize = 17.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("~# ", color = Red, fontFamily = Mono, fontSize = 15.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Fg, fontFamily = Mono, fontSize = 15.sp),
                    cursorBrush = SolidColor(Red),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let { launch(it) } }),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // scanlines + vignette overlay (not touchable)
        Canvas(Modifier.fillMaxSize()) {
            var y = 0f
            while (y < size.height) {
                drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), 2f)
                y += 6f
            }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                center = center, radius = size.maxDimension * 0.75f))
        }
    }
}

@Composable
fun LogLine(tag: String, msg: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text("[$tag] ", color = if (tag == "!!") Red else Dim, fontFamily = Mono, fontSize = 11.sp)
        Text(msg, color = if (tag == "!!") Red else Dim, fontFamily = Mono, fontSize = 11.sp)
    }
}
