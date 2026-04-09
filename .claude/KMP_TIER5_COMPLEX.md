> **NOTA (2026-04-09)**: Questo file e' stato scritto PRIMA della Fase 1. Molte azioni sono gia' completate.
> Per lo stato attuale, vedi `.claude/KMP_MIGRATION_STATUS.md` (tracker aggiornato).
> Per i piani backend, vedi `.claude/BACKEND_*.md`.

# KMP TIER 5 — Complex (1 modulo + data layer, settimane)

> Dipendenze platform-native profonde. Serve architettura expect/actual seria.

---

## 1. features/humanoid (10 commonMain / 22 androidMain) — 2-3 settimane

### 3D Avatar Engine — Filament + CameraX + Media3

**commonMain (10 file — gia' KMP, pura logica):**

| File | Ruolo |
|------|-------|
| `animation/BlinkController.kt` | Timing blink naturale (matematica) |
| `animation/LookAtController.kt` | Eye tracking (geometria) |
| `api/HumanoidController.kt` | Interfaccia controllo avatar |
| `api/HumanoidIntegrationHelper.kt` | Utility integrazione |
| `data/vrm/VrmBlendShapeController.kt` | Gestione blend shapes |
| `data/vrm/VrmBlendShapePresets.kt` | Preset animazioni |
| `domain/model/AvatarState.kt` | State data class |
| `domain/model/Emotion.kt` | Enum emozioni |
| `domain/model/Viseme.kt` | Definizioni visemi |
| `lipsync/VisemeMapper.kt` | Audio → viseme mapping |

**androidMain (22 file) — categorizzati:**

### Filament 3D Rendering (13 file)
| File | Ruolo | KMP Strategy |
|------|-------|-------------|
| `rendering/FilamentRenderer.kt` | Core Filament engine | expect/actual Renderer |
| `rendering/PointCloudMaterialBuilder.kt` | Material creation | expect/actual |
| `data/vrm/VrmLoader.kt` | VRM model loading | expect/actual ModelLoader |
| `data/vrm/VrmModel.kt` | Loaded model state | Spostare in commonMain |
| `data/vrm/VrmHumanoidBoneMapper.kt` | Skeleton mapping | Spostare in commonMain (pura logica) |
| `data/vrm/GltfBoneOptimizer.kt` | Bone optimization | Spostare in commonMain (pura logica) |
| `animation/VrmaAnimationLoader.kt` | VRMA file loading | expect/actual |
| `animation/VrmaAnimationPlayer.kt` | Animation playback | expect/actual |
| `animation/VrmaAnimationPlayerFactory.kt` | Factory | expect/actual |
| `animation/IdlePoseController.kt` | Idle pose | expect/actual |
| `animation/IdleRotationController.kt` | Rotation | expect/actual |
| `animation/IdleAnimationController.kt` | Idle animation | expect/actual |
| `animation/AnimationCoordinator.kt` | Coordina tutte le animazioni | expect/actual |

### Camera (2 file)
| File | Ruolo | KMP Strategy |
|------|-------|-------------|
| `presentation/components/FilamentView.kt` | TextureView rendering | expect/actual |
| `api/HumanoidComposable.kt` | Compose wrapper | expect/actual |

### Lip Sync (2 file — potenzialmente commonMain)
| File | Ruolo | KMP Strategy |
|------|-------|-------------|
| `lipsync/LipSyncController.kt` | Viseme timing | Verificare se usa Android API |
| `lipsync/PhonemeConverter.kt` | Phoneme extraction | Verificare se usa Android API |

### Screen/Integration (5 file)
| File | Ruolo | KMP Strategy |
|------|-------|-------------|
| `presentation/HumanoidScreen.kt` | Full screen view | androidMain (navigation) |
| `presentation/HumanoidViewModel.kt` | ViewModel | Spostare (rimuovere SavedStateHandle) |
| `debug/AvatarDebugScreen.kt` | Debug UI | androidMain |
| `di/HumanoidKoinModule.kt` | DI setup | Parzialmente commonMain |

### Architettura expect/actual proposta

```kotlin
// commonMain — interfacce
expect class PlatformRenderer {
    fun initialize(width: Int, height: Int)
    fun loadModel(data: ByteArray): ModelHandle
    fun setBlendShape(handle: ModelHandle, name: String, weight: Float)
    fun playAnimation(handle: ModelHandle, animData: ByteArray)
    fun render()
    fun destroy()
}

expect class PlatformModelLoader {
    suspend fun loadVrm(path: String): ByteArray
}

// androidMain
actual class PlatformRenderer {
    private val engine = Engine.create()  // Filament
    private val renderer = Renderer(engine)
    // ... Filament implementation
}

// iosMain  
actual class PlatformRenderer {
    private val device = MTLCreateSystemDefaultDevice()  // Metal
    // ... Metal/SceneKit implementation
}

// jsMain (web)
actual class PlatformRenderer {
    // Three.js + @pixiv/three-vrm
    // JavaScript interop
}
```

### File spostabili in commonMain SUBITO (senza expect/actual)

1. `VrmModel.kt` — data class, se non ha import Filament
2. `VrmHumanoidBoneMapper.kt` — mapping logico
3. `GltfBoneOptimizer.kt` — ottimizzazione pura
4. `LipSyncController.kt` — se non usa Android API dirette
5. `PhonemeConverter.kt` — se non usa Android API dirette
6. `HumanoidViewModel.kt` — rimuovere SavedStateHandle

### Dipendenze androidMain

```gradle
// TUTTE platform-specific, restano in androidMain:
filament-android (1.68.2)
filament-utils (1.68.2)
gltfio-android (1.68.2)
filamat-android (1.68.2)
camerax-core, camerax-camera2, camerax-lifecycle, camerax-view (1.4.1)
media3-transformer
gson

// iOS equivalenti:
// Metal (built-in iOS)
// SceneKit o RealityKit
// AVCaptureSession

// Web equivalenti:
// Three.js + @pixiv/three-vrm
// WebGL
// getUserMedia
```

### Roadmap humanoid

```
FASE 1 (2 giorni): Spostare logica pura in commonMain
  - VrmModel, BoneMapper, BoneOptimizer, LipSync, PhonemeConverter
  - ViewModel (rimuovere SavedStateHandle)
  - Guadagno: +6 file commonMain

FASE 2 (3 giorni): Creare interfacce rendering in commonMain
  - PlatformRenderer, PlatformModelLoader, PlatformAnimationPlayer
  - Refactor FilamentRenderer per implementare interfaccia

FASE 3 (1 settimana): iOS Metal implementation
  - Metal backend per rendering
  - VRM loading nativo
  - Animation playback

FASE 4 (1 settimana): Web Three.js implementation
  - @pixiv/three-vrm per VRM
  - WebGL rendering
  - Kotlin/JS interop
```

---

## 2. data/mongo — Strategia Opzione C (Ibrida)

### Stato attuale
- **commonMain: 3 file** (Ktor clients)
- **androidMain: 40 file** (TUTTI i repository Firestore)
- **iosMain: 0 file** — non esiste

### I 40 repository Firestore in androidMain

Ogni file usa direttamente:
```kotlin
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
```

### Strategia Option C — Ibrida

**Approccio:**
- `commonMain`: interfacce (gia' in core/util) + implementazioni con GitLive Firebase SDK
- `androidMain`: GitLive wrappa Firebase Android SDK nativo (performance nativa, offline, real-time)
- `iosMain`: GitLive wrappa Firebase iOS SDK nativo (CocoaPods)
- `jsMain`: GitLive wrappa Firebase JS SDK
- `wasmJsMain` (futuro): Ktor REST API fallback

### Migrazione dei 40 repository

**Il trucco**: GitLive Firebase ha API quasi identiche a Firebase Android SDK.

```kotlin
// PRIMA (androidMain — Firebase Android diretto):
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreDiaryRepository : DiaryRepository {
    private val db = FirebaseFirestore.getInstance()
    
    override suspend fun getDiaries(userId: String): List<Diary> {
        return db.collection("users/$userId/diaries")
            .get().await()
            .documents.map { it.toObject(Diary::class.java)!! }
    }
}

// DOPO (commonMain — GitLive Firebase KMP):
import dev.gitlive.firebase.firestore.FirebaseFirestore

class FirestoreDiaryRepository(
    private val db: FirebaseFirestore
) : DiaryRepository {
    
    override suspend fun getDiaries(userId: String): List<Diary> {
        return db.collection("users/$userId/diaries")
            .get()
            .documents.map { it.data(Diary.serializer()) }
    }
}
```

### Differenze chiave GitLive vs Firebase Android

| Aspetto | Firebase Android | GitLive Firebase |
|---------|-----------------|------------------|
| Serialization | `toObject(Class)` | `data(KSerializer)` — usa kotlinx.serialization |
| Init | `FirebaseFirestore.getInstance()` | `Firebase.firestore` (inject via Koin) |
| Auth | `FirebaseAuth.getInstance()` | `Firebase.auth` |
| Logging | `android.util.Log` | `println` o KMP logger |
| Listeners | `addSnapshotListener` | `snapshots` Flow (coroutines-native) |

### Roadmap data/mongo

```
FASE 1 (1 giorno): Setup
  - Aggiungere GitLive Firebase deps in commonMain build.gradle
  - Creare FirebaseProvider in commonMain (init)
  - Aggiornare MongoKoinModule per inject Firebase instances

FASE 2 (3-4 giorni): Migrare i 40 repository
  - Batch 1: Repository semplici (CRUD) — 20 file
  - Batch 2: Repository con real-time listeners — 10 file
  - Batch 3: Repository complessi (billing, media upload) — 10 file
  - Per ogni file:
    1. Cambiare import da com.google.firebase → dev.gitlive.firebase
    2. Cambiare serialization da toObject → data(serializer)
    3. Cambiare Log.d → println/logger
    4. Spostare da androidMain → commonMain

FASE 3 (1 giorno): Cleanup
  - Rimuovere Firebase Android SDK deps da androidMain
  - Verificare compilazione iOS target
  - Test

FASE 4 (futuro): wasmJsMain fallback
  - Implementare repo via Ktor REST API per WASM
  - Solo se/quando serve target WASM
```

### Dipendenze build.gradle da cambiare

```gradle
// commonMain (NUOVO):
implementation libs.gitlive.firebase.auth
implementation libs.gitlive.firebase.firestore
implementation libs.gitlive.firebase.storage
// Gia' definiti in libs.versions.toml!

// androidMain (RIMUOVERE dopo migrazione):
// implementation libs.firebase.auth.kmp        ← rimosso
// implementation libs.firebase.firestore.kmp   ← rimosso
// implementation libs.firebase.storage.kmp     ← rimosso

// androidMain (RESTA):
implementation libs.sqldelight.android    // driver DB platform-specific
implementation libs.ktor.client.okhttp    // engine HTTP platform-specific
implementation libs.billing.ktx           // Play Billing (solo Android)
```

### Rischi e mitigazioni

| Rischio | Probabilita' | Mitigazione |
|---------|-------------|-------------|
| GitLive API breaking changes | Bassa | Versione 2.4.0 e' stabile |
| Performance regression | Bassa | GitLive wrappa SDK nativo |
| Offline persistence diversa | Media | Testare su iOS, verificare Firestore cache |
| JVM Desktop auth limitato | Media | Accettabile — Desktop non e' target primario |
| WASM non supportato | Alta | Ktor REST fallback (Fase 4) |

---

## Effort totale Tier 5

| Componente | Effort |
|-----------|--------|
| humanoid — logica pura → commonMain | 2 giorni |
| humanoid — interfacce rendering | 3 giorni |
| humanoid — iOS Metal impl | 1 settimana |
| humanoid — Web Three.js impl | 1 settimana |
| data/mongo — GitLive migration | 1 settimana |
| **Totale** | **3-4 settimane** |
