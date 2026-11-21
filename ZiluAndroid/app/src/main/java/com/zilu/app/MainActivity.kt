package com.zilu.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Task(val id: Long, val name: String, val targetMin: Int, val elapsedMs: Long, val runningSince: Long, val day: String) {
    fun currentElapsed(now: Long) = elapsedMs + if (runningSince > 0) (now - runningSince).coerceAtLeast(0) else 0
    fun completed(now: Long) = currentElapsed(now) >= targetMin * 60_000L
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureToday(this)
        scheduleMidnight(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF8BE9A5), background = Color(0xFF101418),
                surface = Color(0xFF1B2229), onBackground = Color(0xFFF2F5F7), onSurface = Color(0xFFF2F5F7)
            )) {
                var refresh by remember { mutableIntStateOf(0) }
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var showAdd by remember { mutableStateOf(false) }
                var taskName by remember { mutableStateOf("") }
                var minutes by remember { mutableStateOf("30") }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1000)
                        now = System.currentTimeMillis()
                        val before = todayKey()
                        ensureToday(this@MainActivity)
                        if (before != todayKey()) refresh++
                    }
                }
                val tasks = remember(refresh) { loadTasks(this@MainActivity) }
                Column(Modifier.fillMaxSize().background(Color(0xFF101418)).padding(20.dp)) {
                    Text("自律", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("把计划变成每天的行动", color = Color(0xFF9BA7B2), modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("今日任务", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showAdd = true }) { Text("+ 添加任务") }
                    }
                    val done = tasks.count { it.completed(now) }
                    Text("$done / ${tasks.size} 项已完成", color = Color(0xFF9BA7B2), modifier = Modifier.padding(bottom = 12.dp))
                    if (tasks.isEmpty()) {
                        Text("还没有任务，点击右上角添加你的第一个目标。", color = Color(0xFF9BA7B2), modifier = Modifier.padding(top = 24.dp))
                    } else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(tasks, key = { it.id }) { task ->
                            val elapsed = task.currentElapsed(now)
                            val complete = elapsed >= task.targetMin * 60_000L
                            val running = task.runningSince > 0
                            val cardColor = if (complete) Color(0xFF153D2B) else Color(0xFF421F25)
                            val statusColor = if (complete) Color(0xFF36C275) else Color(0xFFE05A65)
                            Card(colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(task.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Text(if (complete) "已完成" else if (running) "进行中" else "未完成",
                                            color = statusColor, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text("${formatMs(elapsed)} / ${task.targetMin} 分钟", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                    LinearProgressIndicator(
                                        progress = { (elapsed.toFloat() / (task.targetMin * 60_000f)).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                        color = statusColor, trackColor = Color(0xFF343B43)
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        if (!complete) {
                                            TextButton(onClick = {
                                                updateTask(this@MainActivity, task.copy(elapsedMs = elapsed, runningSince = if (running) 0 else now))
                                                refresh++
                                            }) { Text(if (running) "暂停" else if (elapsed > 0) "继续" else "开始") }
                                        } else Text("今日目标达成 ✓", color = Color(0xFF8BE9A5), modifier = Modifier.padding(12.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("每日 00:00 自动重置 · 历史数据功能待完善", color = Color(0xFF77838E), fontSize = 12.sp)
                }
                if (showAdd) AlertDialog(
                    onDismissRequest = { showAdd = false },
                    title = { Text("创建每日任务") },
                    text = {
                        Column {
                            OutlinedTextField(value = taskName, onValueChange = { taskName = it }, label = { Text("任务名称") }, singleLine = true)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("目标分钟数") }, singleLine = true)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val m = minutes.toIntOrNull()
                            if (taskName.isNotBlank() && m != null && m in 1..1440) {
                                addTask(this@MainActivity, Task(System.currentTimeMillis(), taskName.trim(), m, 0, 0, todayKey()))
                                taskName = ""; minutes = "30"; showAdd = false; refresh++
                            }
                        }) { Text("创建") }
                    },
                    dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ensureToday(this)
        scheduleMidnight(this)
    }
}

private const val PREF = "zilu_store"
private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}
private fun loadTasks(c: Context): List<Task> {
    val arr = JSONArray(c.getSharedPreferences(PREF, 0).getString("tasks", "[]"))
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        Task(o.optLong("id"), o.optString("name"), o.optInt("target"), o.optLong("elapsed"), o.optLong("running"), o.optString("day"))
    }
}
private fun saveTasks(c: Context, tasks: List<Task>) {
    val arr = JSONArray()
    tasks.forEach { t -> arr.put(JSONObject().put("id", t.id).put("name", t.name).put("target", t.targetMin).put("elapsed", t.elapsedMs).put("running", t.runningSince).put("day", t.day)) }
    c.getSharedPreferences(PREF, 0).edit().putString("tasks", arr.toString()).apply()
}
private fun addTask(c: Context, t: Task) = saveTasks(c, loadTasks(c) + t)
private fun updateTask(c: Context, changed: Task) = saveTasks(c, loadTasks(c).map { if (it.id == changed.id) changed else it })
private fun ensureToday(c: Context) {
    val today = todayKey()
    val old = loadTasks(c)
    if (old.any { it.day != today }) {
        val now = System.currentTimeMillis()
        val history = JSONArray(c.getSharedPreferences(PREF, 0).getString("history", "[]"))
        old.filter { it.day != today }.forEach { task ->
            val elapsedAtReset = task.currentElapsed(now).coerceAtMost(task.targetMin * 60_000L)
            history.put(JSONObject()
                .put("date", task.day)
                .put("name", task.name)
                .put("targetMin", task.targetMin)
                .put("elapsedMs", elapsedAtReset)
                .put("completed", elapsedAtReset >= task.targetMin * 60_000L))
        }
        c.getSharedPreferences(PREF, 0).edit().putString("history", history.toString()).apply()
        saveTasks(c, old.map { it.copy(elapsedMs = 0, runningSince = 0, day = today) })
    }
}
private fun scheduleMidnight(c: Context) {
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val intent = PendingIntent.getBroadcast(c, 101, Intent(c, MidnightResetReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, intent) }
    catch (_: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, intent) }
}
