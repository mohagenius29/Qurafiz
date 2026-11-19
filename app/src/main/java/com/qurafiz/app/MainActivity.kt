package com.qurafiz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QurafizTheme { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val loaded by produceState(false) {
        withContext(Dispatchers.IO) { Quran.ensureLoaded(ctx) }
        value = true
    }
    if (!loaded) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val vm: ReciteViewModel = viewModel()
    var reciting by rememberSaveable { mutableStateOf(false) }
    BackHandler(reciting) { vm.leave(); reciting = false }

    if (reciting) {
        ReciteScreen(vm, onExit = { vm.leave(); reciting = false })
    } else {
        HomeScreen(
            store = vm.store,
            onStart = { s, a -> vm.startSurah(s, a); reciting = true },
            onReview = { vm.startReview(); reciting = true },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(store: ProgressStore, onStart: (Int, Int) -> Unit, onReview: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<Surah?>(null) }
    var version by remember { mutableIntStateOf(0) } // bump to refresh after returning
    LaunchedEffect(Unit) { version++ }
    val surahs = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) Quran.surahs
        else Quran.surahs.filter { it.name.lowercase().contains(q) || it.arabicName.contains(q) || it.number.toString() == q || it.meaning.lowercase().contains(q) }
    }
    val dueCount = remember(version) { store.due().size }
    val today = store.todayCount()
    val goal = store.dailyGoal

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).statusBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("السلام عليكم", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Qurafiz · memorize with your voice", color = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f))
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Today", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                                Text("$today / $goal ayat", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("🔥 ${store.streak()}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                Text("day streak", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (today.toFloat() / goal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Gold, trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .25f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Card(
                    onClick = onReview, enabled = dueCount > 0,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Replay, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (dueCount > 0) "Review due: $dueCount ayat" else "Nothing to review right now", fontWeight = FontWeight.SemiBold)
                            Text("Revise before you forget", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Search surah by name or number") },
                )
            }
            items(surahs, key = { it.number }) { s ->
                val learned = store.learnedIn(s.number)
                Card(
                    onClick = { pick = s },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                            Alignment.Center,
                        ) { Text("${s.number}", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.SemiBold)
                            Text("${s.meaning} · ${s.ayat.size} ayat", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                            if (learned > 0) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { learned.toFloat() / s.ayat.size },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = CorrectGreen,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(s.arabicName, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
        }
    }

    pick?.let { s ->
        var text by remember(s) { mutableStateOf(store.firstUnlearned(s.number).toString()) }
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text(s.name) },
            text = {
                Column {
                    Text("Start from ayah (1–${s.ayat.size})")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.filter(Char::isDigit).take(3) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onStart(s.number, text.toIntOrNull() ?: 1); pick = null }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Cancel") } },
        )
    }
}
