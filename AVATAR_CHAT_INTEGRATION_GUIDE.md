# Avatar Chat Integration - Implementation Guide

## 🎯 Obiettivo
Integrare il modulo Humanoid con Chat per creare l'AvatarChatScreen, dove l'avatar VRM è l'interfaccia visiva del chatbot.

## ⚠️ Problema Risolto: Dipendenza Circolare

**Problema originale:**
- `features:chat` dipendeva da `features:humanoid` → ERRORE: circular dependency
- Gradle non permette dipendenze circolari tra moduli

**Soluzione implementata:**
- L'integrazione avviene a livello **app module**
- `features:chat` e `features:humanoid` restano indipendenti
- L'app module ha accesso a entrambi e orchestra l'integrazione

## 📁 Struttura File Implementata

### ✅ Modulo Humanoid (Completato)

**API Pubbliche** (`features/humanoid/src/main/java/com/lifo/humanoid/api/`)
```
HumanoidController.kt        - Interface per controllo esterno
HumanoidControllerImpl.kt    - Implementazione
HumanoidComposable.kt         - Composable riutilizzabile + extension
```

**Questi file sono pronti e funzionanti.**

### 📋 Da Implementare nell'App Module

Creare questi file in `app/src/main/java/com/lifo/app/`:

#### 1. **EmotionBridge.kt**

Mappa emozioni Chat → VRM blend shapes.

```kotlin
package com.lifo.app.integration

import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.humanoid.api.HumanoidController
import com.lifo.humanoid.domain.model.Emotion

class EmotionBridge(
    private val humanoidController: HumanoidController
) {
    fun applyChatEmotion(chatEmotion: GeminiNativeVoiceSystem.Emotion) {
        val vrmEmotion = when (chatEmotion) {
            GeminiNativeVoiceSystem.Emotion.NEUTRAL -> Emotion.Neutral
            GeminiNativeVoiceSystem.Emotion.HAPPY -> Emotion.Happy(1.0f)
            GeminiNativeVoiceSystem.Emotion.SAD -> Emotion.Sad(1.0f)
            GeminiNativeVoiceSystem.Emotion.ANGRY -> Emotion.Angry(1.0f)
            GeminiNativeVoiceSystem.Emotion.SURPRISED -> Emotion.Surprised(0.5f)
            GeminiNativeVoiceSystem.Emotion.THINKING -> Emotion.Thinking(1.0f)
            GeminiNativeVoiceSystem.Emotion.EXCITED -> Emotion.Excited(1.0f)
            else -> Emotion.Neutral
        }

        humanoidController.setEmotion(vrmEmotion)
    }
}
```

#### 2. **TTSLipSyncAdapter.kt**

Sincronizza TTS con lip-sync.

```kotlin
package com.lifo.app.integration

import com.lifo.humanoid.api.HumanoidController

class TTSLipSyncAdapter(
    private val humanoidController: HumanoidController
) {
    fun speakWithLipSync(text: String, durationMs: Long) {
        val cleanText = cleanTextForLipSync(text)
        val duration = if (durationMs > 0) durationMs else estimateDuration(cleanText)

        humanoidController.speakText(cleanText, duration)
    }

    fun stopLipSync() {
        humanoidController.stopSpeaking()
    }

    fun cleanTextForLipSync(text: String): String {
        return text
            .replace(Regex("""\[.*?\]"""), "") // Remove [emotion] tags
            .replace(Regex("""[*_~`]"""), "") // Remove markdown
            .trim()
    }

    private fun estimateDuration(text: String): Long {
        val wordCount = text.split(Regex("\\s+")).size
        return (wordCount * 400L * 1.1).toLong() // ~150 WPM + 10% buffer
    }
}
```

#### 3. **AvatarChatScreen.kt**

Screen principale con integrazione.

```kotlin
package com.lifo.app.presentation.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.app.integration.EmotionBridge
import com.lifo.app.integration.TTSLipSyncAdapter
import com.lifo.chat.presentation.components.ChatBubble
import com.lifo.chat.presentation.components.ChatInput
import com.lifo.chat.presentation.viewmodel.ChatViewModel
import com.lifo.humanoid.api.HumanoidAvatarView
import com.lifo.humanoid.api.asHumanoidController
import com.lifo.humanoid.presentation.HumanoidViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarChatScreen(
    navigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
    chatViewModel: ChatViewModel = hiltViewModel(),
    humanoidViewModel: HumanoidViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()

    // Chat state
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by chatViewModel.voiceState.collectAsStateWithLifecycle()
    val isVoiceActive by chatViewModel.isVoiceActive.collectAsStateWithLifecycle()
    val voiceEmotion by chatViewModel.voiceEmotion.collectAsStateWithLifecycle()

    // UI state
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Integration components
    val humanoidController = remember(humanoidViewModel) {
        humanoidViewModel.asHumanoidController()
    }

    val emotionBridge = remember(humanoidController) {
        EmotionBridge(humanoidController)
    }

    val lipSyncAdapter = remember(humanoidController) {
        TTSLipSyncAdapter(humanoidController)
    }

    // Load session
    LaunchedEffect(sessionId) {
        sessionId?.let { chatViewModel.loadExistingSession(it) }
    }

    // Auto-scroll
    LaunchedEffect(chatState.messages.size) {
        if (showHistory && chatState.messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    // EVENT BRIDGE: Emotion sync
    LaunchedEffect(voiceEmotion) {
        emotionBridge.applyChatEmotion(voiceEmotion)
    }

    // EVENT BRIDGE: Lip-sync sync
    LaunchedEffect(isVoiceActive) {
        if (isVoiceActive) {
            val message = chatState.messages.lastOrNull { !it.isUser }?.content ?: ""
            if (message.isNotEmpty()) {
                lipSyncAdapter.speakWithLipSync(message, 0L)
            }
        } else {
            lipSyncAdapter.stopLipSync()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    ) {
        // LAYER 1: Avatar VRM
        HumanoidAvatarView(
            modifier = Modifier.fillMaxSize(),
            viewModel = humanoidViewModel,
            blurAmount = if (showHistory) 8f else 0f
        )

        // LAYER 2: Chat History
        AnimatedVisibility(
            visible = showHistory,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .statusBarsPadding()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp, bottom = 100.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chatState.messages) { message ->
                        ChatBubble(
                            message = message,
                            isSpeaking = isVoiceActive,
                            voiceEmotion = voiceEmotion.name,
                            voiceLatency = voiceState.latencyMs,
                            onSpeak = { chatViewModel.speakMessage(message.id) },
                            onDelete = { chatViewModel.deleteMessage(message.id) },
                            onCopy = { /* TODO */ }
                        )
                    }
                }
            }
        }

        // LAYER 3: TopBar
        TopAppBar(
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter),
            title = { Text("Amica") },
            navigationIcon = {
                IconButton(onClick = {
                    chatViewModel.stopSpeaking()
                    navigateBack()
                }) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = { showHistory = !showHistory }) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = if (showHistory)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (showHistory)
                    MaterialTheme.colorScheme.surface
                else
                    Color.Transparent
            )
        )

        // LAYER 4: ChatInput
        ChatInput(
            text = chatState.inputText,
            onTextChange = { chatViewModel.updateInputText(it) },
            onSend = { chatViewModel.sendMessage(it) },
            onVoiceClick = { /* TODO */ },
            enabled = !isVoiceActive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}
```

#### 4. **AvatarChatNavigation.kt**

```kotlin
package com.lifo.app.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lifo.app.presentation.screen.AvatarChatScreen
import com.lifo.util.Screen

@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.avatarChatRoute(navigateBack: () -> Unit) {
    composable(
        route = Screen.AvatarChat.route,
        enterTransition = { fadeIn(tween(400)) + scaleIn(0.95f, tween(400)) },
        exitTransition = { fadeOut(tween(300)) + scaleOut(0.95f, tween(300)) }
    ) {
        AvatarChatScreen(navigateBack = navigateBack)
    }

    composable(
        route = "${Screen.AvatarChat.route}/{sessionId}",
        arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        enterTransition = { fadeIn(tween(400)) + scaleIn(0.95f, tween(400)) },
        exitTransition = { fadeOut(tween(300)) + scaleOut(0.95f, tween(300)) }
    ) { backStackEntry ->
        val sessionId = backStackEntry.arguments?.getString("sessionId")
        AvatarChatScreen(navigateBack = navigateBack, sessionId = sessionId)
    }
}

fun NavController.navigateToAvatarChat(sessionId: String? = null) {
    val route = sessionId?.let { "${Screen.AvatarChat.route}/$it" }
        ?: Screen.AvatarChat.route

    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}
```

#### 5. **Aggiornare NavGraph.kt**

Nel file `app/src/main/java/com/lifo/app/navigation/NavGraph.kt`, aggiungere:

```kotlin
import com.lifo.app.navigation.avatarChatRoute

// Nel NavHost:
avatarChatRoute(navigateBack = { navController.popBackStack() })
```

## 🎯 Architettura Finale

```
┌────────────────────────────────────────────┐
│           APP MODULE                       │
│  ┌──────────────────────────────────────┐  │
│  │      AvatarChatScreen                │  │
│  │  ┌────────────┐  ┌────────────────┐ │  │
│  │  │ChatViewModel│  │HumanoidViewModel│ │  │
│  │  └─────┬──────┘  └────────┬───────┘ │  │
│  │        │                  │         │  │
│  │        ▼                  ▼         │  │
│  │  EmotionBridge   TTSLipSyncAdapter │  │
│  └──────────────────────────────────────┘  │
├────────────────────────────────────────────┤
│  features:chat     features:humanoid       │
│  (independent)     (independent)           │
└────────────────────────────────────────────┘
```

## ✅ Vantaggi

1. **Nessuna dipendenza circolare**: i moduli restano indipendenti
2. **Modularity**: Chat e Humanoid possono essere usati separatamente
3. **Clean Architecture**: integrazione orchestrata a livello app
4. **Testabilità**: ogni modulo testabile indipendentemente

## 🧪 Testing

Dopo aver implementato i file sopra:

```bash
# Build
.\gradlew.bat :app:compileDebugKotlin

# Installare ed eseguire
.\gradlew.bat installDebug

# Navigare a Screen.AvatarChat per testare
```

## 📚 Note Tecniche

- **Emotion Sync**: Automatico via LaunchedEffect su voiceEmotion
- **Lip Sync**: Text-based (evita complessità audio real-time)
- **Blur Effect**: History mode applica blur(8.dp) all'avatar
- **State Preservation**: Chat logic rimane intatta

## ⚠️ Troubleshooting

**Errore: "Unresolved reference HumanoidViewModel"**
- Verificare che app module abbia dependencies su entrambi features

**Avatar non si muove:**
- Verificare che blendShapeWeights vengano aggiornati (loop in HumanoidAvatarView)

**Lip-sync non funziona:**
- Verificare che TTSLipSyncAdapter.speakWithLipSync() venga chiamato quando isVoiceActive=true

---

*Implementazione JARVIS-style: Elegante, modulare, funzionale.*
