package com.qurafiz.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qurafiz.app.RecitationMatcher.MistakeType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciteScreen(vm: ReciteViewModel, onExit: () -> Unit) {
    val ui = vm.ui
    val ctx = LocalContext.current
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.toggleMic() }

    fun onMicClick() {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.toggleMic() else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (ui.reviewMode) "Review · ${ui.surahName}" else ui.surahName, fontWeight = FontWeight.SemiBold)
                        if (ui.queueSize > 0 && ui.phase != Phase.Finished)
                            Text("Ayah ${ui.ayahNumber} · ${ui.position + 1} of ${ui.queueSize}", fontSize = 12.sp)
                    }
                },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (ui.phase == Phase.Finished) { FinishedView(ui, Modifier.padding(pad), onExit); return@Scaffold }

        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (ui.queueSize > 0) {
                LinearProgressIndicator(
                    progress = { (ui.position + ui.confirmed.toFloat() / maxOf(1, ui.words.size)) / ui.queueSize },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(14.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) { AyahWords(ui, Modifier.padding(20.dp).fillMaxWidth()) }

                if (ui.showTranslation && ui.translation.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(ui.translation, textAlign = TextAlign.Center, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f))
                }
                Spacer(Modifier.height(10.dp))
                LiveHeard(ui)
                Spacer(Modifier.height(10.dp))
                StatusArea(ui, onTryAgain = vm::retry, onHear = vm::hearWord)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                FilterChip(ui.hideMode, vm::toggleHide, { Text("Hide text") })
                FilterChip(ui.strict, vm::toggleStrict, { Text("Strict") })
                FilterChip(ui.showTranslation, vm::toggleTranslation, { Text("Meaning") })
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = vm::listenToAyah, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.VolumeUp, "Hear this ayah") }
                MicButton(listening = ui.phase == Phase.Listening, onClick = ::onMicClick)
                FilledTonalIconButton(onClick = vm::skipAyah, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Skip ayah") }
            }
        }
    }
}

private enum class WState { Done, Current, Wrong, Upcoming }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AyahWords(ui: ReciteUi, modifier: Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ui.words.forEachIndexed { i, w ->
                val inMistake = ui.phase == Phase.Mistake || (ui.phase == Phase.PlayingAudio && ui.mistake != null)
                val state = when {
                    i < ui.confirmed -> WState.Done
                    i == ui.confirmed && inMistake -> WState.Wrong
                    i == ui.confirmed -> WState.Current
                    else -> WState.Upcoming
                }
                val attempt = ui.mistake?.attempt ?: 0
                val shown = when {
                    !ui.hideMode || state == WState.Done -> w.display
                    state == WState.Wrong && attempt >= 2 -> w.display             // hint 2: reveal the word
                    state == WState.Wrong && attempt == 1 -> w.display.take(2) + "…" // hint 1: first letter
                    else -> "•".repeat(minOf(w.norm.length, 6))
                }
                val color = when (state) {
                    WState.Done -> CorrectGreen
                    WState.Wrong -> ErrorRed
                    WState.Current -> MaterialTheme.colorScheme.onSurface
                    WState.Upcoming -> MaterialTheme.colorScheme.onSurface.copy(alpha = if (ui.hideMode) .35f else 1f)
                }
                Text(
                    shown, color = color, fontSize = 32.sp, lineHeight = 56.sp,
                    fontWeight = if (state == WState.Current) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (state == WState.Current && ui.phase == Phase.Listening) Gold.copy(alpha = .25f) else Color.Transparent)
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusArea(ui: ReciteUi, onTryAgain: () -> Unit, onHear: () -> Unit) {
    when (ui.phase) {
        Phase.Idle -> Hint(ui.message ?: "Tap the mic and recite. I'll follow you word by word.")
        Phase.Listening -> Hint("Listening… recite from where the gold highlight is.")
        Phase.AyahDone -> Text(ui.message ?: "", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = CorrectGreen)
        Phase.Mistake, Phase.PlayingAudio -> if (ui.mistake == null) Hint("Listen carefully, then recite it yourself.") else ui.mistake.let { m ->
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = .12f)),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (m.type == MistakeType.SKIPPED_WORD) "You skipped a word" else "That word didn't sound right",
                        fontWeight = FontWeight.Bold, color = ErrorRed, fontSize = 17.sp,
                    )
                    if (m.heard.isNotEmpty()) Text("I heard: ${m.heard}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            m.attempt >= 3 -> "Listen to the whole ayah, then I'll listen to you again."
                            else -> "Listen to the correct word, then say it. I'll start listening again automatically."
                        },
                        fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onTryAgain) { Text("Try again") }
                        OutlinedButton(onClick = onHear) { Text("Hear word") }
                    }
                }
            }
        }
        Phase.Finished -> Unit
    }
}

@Composable
private fun LiveHeard(ui: ReciteUi) {
    if (ui.phase != Phase.Listening && ui.liveHeard.isEmpty()) return
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth()) {
            Text("I heard", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    if (ui.liveHeard.isEmpty()) "…" else ui.liveHeard,
                    fontSize = 20.sp, modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, textAlign = TextAlign.Center, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .75f), modifier = Modifier.padding(horizontal = 8.dp))
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.14f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale",
    )
    Box(Modifier.size(100.dp), Alignment.Center) {
        if (listening) Box(Modifier.size(84.dp).scale(pulse).clip(CircleShape).background(ErrorRed.copy(alpha = .22f)))
        FilledIconButton(
            onClick = onClick, modifier = Modifier.size(78.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (listening) ErrorRed else MaterialTheme.colorScheme.primary),
        ) { Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, if (listening) "Stop" else "Start reciting", Modifier.size(36.dp), tint = Color.White) }
    }
}

@Composable
private fun FinishedView(ui: ReciteUi, modifier: Modifier, onExit: () -> Unit) {
    Column(modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("🌙", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (ui.queueSize == 0) "Nothing to review right now" else "Masha'Allah, session complete!",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        if (ui.queueSize > 0) Text("${ui.queueSize} ayat recited. Weak ones will return sooner for review.", textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        Button(onClick = onExit) { Text("Back to home") }
    }
}
