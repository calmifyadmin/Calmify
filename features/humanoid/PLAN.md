# 🤖 Amica Android - Piano di Sviluppo Completo

**Architettura**: Unity (UniVRM) + Kotlin/Jetpack Compose + AI Services

---

## 📋 Indice

1. [Overview Architettura](#overview-architettura)
2. [PARTE 1: Setup Unity](#parte-1-setup-unity)
3. [PARTE 2: Android Studio Setup](#parte-2-android-studio-setup)
4. [PARTE 3: Integrazione Unity-Android](#parte-3-integrazione-unity-android)
5. [PARTE 4: Trigger per Claude Code](#parte-4-trigger-per-claude-code)

---

## Overview Architettura

```
┌─────────────────────────────────────────────────────────────────┐
│                        APP ANDROID                               │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐    ┌─────────────────────────────────┐ │
│  │   KOTLIN/COMPOSE    │    │         UNITY MODULE            │ │
│  │                     │    │                                 │ │
│  │  • UI Chat          │◄──►│  • VRM Rendering                │ │
│  │  • LLM Client       │    │  • Blendshapes/Espressioni      │ │
│  │  • TTS/STT          │    │  • Spring Bones                 │ │
│  │  • VAD              │    │  • Lip Sync Receiver            │ │
│  │  • Camera/Vision    │    │  • Eye Tracking                 │ │
│  │  • Audio Manager    │    │  • Animazioni                   │ │
│  │                     │    │                                 │ │
│  │  AvatarBridge.kt ───┼────┼─► AvatarController.cs           │ │
│  └─────────────────────┘    └─────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

# PARTE 1: Setup Unity

## 1.1 Requisiti Software

- **Unity Hub** (ultima versione)
- **Unity 2022.3 LTS** o superiore (con Android Build Support)
- **Android SDK** e **NDK** (configurati in Unity)

## 1.2 Creazione Progetto Unity

### Step 1: Nuovo Progetto
1. Apri Unity Hub → New Project
2. Template: **3D (URP)** - Universal Render Pipeline
3. Nome progetto: `AmicaVRMModule`
4. Posizione: cartella dedicata (es. `~/Projects/AmicaVRMModule`)

### Step 2: Configurazione Player Settings
```
Edit → Project Settings → Player → Android Tab:

✓ Company Name: tuodominio
✓ Product Name: AmicaVRM
✓ Package Name: com.tuodominio.amicavrm
✓ Minimum API Level: Android 8.0 (API 26)
✓ Target API Level: Android 14 (API 34)
✓ Scripting Backend: IL2CPP
✓ Target Architectures: ✓ ARM64 (disabilita ARMv7 se non necessario)
✓ Graphics APIs: Vulkan, OpenGLES3
```

### Step 3: URP Settings per Mobile
```
Edit → Project Settings → Quality:

1. Crea nuovo profilo "Mobile_High"
2. Assegna URP Asset ottimizzato per mobile:
   - Shadow Resolution: 1024
   - Shadow Cascade: 2
   - HDR: Off (per performance)
   - Anti-aliasing: 2x MSAA
```

## 1.3 Installazione UniVRM

### Metodo 1: Via UPM (Consigliato)
```
Window → Package Manager → + → Add package from git URL:

1. https://github.com/vrm-c/UniVRM.git?path=/Assets/VRMShaders#v0.127.2
2. https://github.com/vrm-c/UniVRM.git?path=/Assets/UniGLTF#v0.127.2  
3. https://github.com/vrm-c/UniVRM.git?path=/Assets/VRM#v0.127.2
4. https://github.com/vrm-c/UniVRM.git?path=/Assets/VRM10#v0.127.2
```

### Metodo 2: Via .unitypackage
1. Scarica da: https://github.com/vrm-c/UniVRM/releases
2. Assets → Import Package → Custom Package
3. Importa tutto

### Verifica Installazione
- Menu "VRM0" e "VRM1" devono apparire nella barra menu
- Nessun errore rosso in Console

## 1.4 Setup Scena Avatar

### Struttura Scena
```
Hierarchy:
├── Main Camera
├── Directional Light
├── AvatarRoot (Empty GameObject)
│   └── [VRM Model caricato a runtime]
├── AvatarController (Script)
└── EventSystem
```

### Configurazione Camera
```csharp
// Camera Settings per avatar VRM
Position: (0, 1.2, 1.5)
Rotation: (0, 180, 0)
Field of View: 35
Clear Flags: Solid Color
Background: #1a1a2e (o colore UI app)
```

### Configurazione Luce
```
Directional Light:
- Rotation: (50, -30, 0)
- Intensity: 1.0
- Shadow Type: Soft Shadows
- Color: #FFF5E6 (warm)

Opzionale - Rim Light:
- Tipo: Point Light
- Position: dietro avatar
- Intensity: 0.3
- Color: #E6F0FF
```

## 1.5 Script AvatarController.cs (con supporto VRMA)

Crea `Assets/Scripts/AvatarController.cs`:

```csharp
using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Threading.Tasks;
using UnityEngine;
using UniGLTF;
using UniVRM10;

public class AvatarController : MonoBehaviour
{
    // === SINGLETON ===
    public static AvatarController Instance { get; private set; }
    
    // === VRM 1.0 REFERENCES ===
    private Vrm10Instance currentVrmInstance;
    private GameObject currentAvatarRoot;
    
    // === VRMA ANIMATIONS ===
    private Dictionary<string, Vrm10AnimationInstance> loadedAnimations = new();
    private Vrm10AnimationInstance currentAnimation;
    private string currentAnimationName = "";
    private bool isAnimationPlaying = false;
    
    // === LIP SYNC ===
    [Header("Lip Sync Settings")]
    [Range(0f, 2f)] public float lipSyncSensitivity = 1.5f;
    [Range(0f, 0.3f)] public float lipSyncSmoothing = 0.1f;
    private float currentMouthOpen = 0f;
    private float targetMouthOpen = 0f;
    
    // === EMOTIONS ===
    private string currentEmotion = "neutral";
    private float emotionIntensity = 1f;
    
    // === BLINKING ===
    [Header("Auto Blink")]
    public bool enableAutoBlink = true;
    public float blinkInterval = 3f;
    public float blinkDuration = 0.1f;
    private float blinkTimer;
    private bool isBlinking = false;
    
    // === ANIMATION NAMES ===
    public static class Animations
    {
        public const string Idle = "idle_loop";
        public const string Greeting = "greeting";
        public const string Dance = "dance";
        public const string PeaceSign = "peaceSign";
        public const string Shoot = "shoot";
        public const string Spin = "spin";
        public const string Squat = "squat";
        public const string ShowFullBody = "showFullBody";
        public const string ModelPose = "modelPose";
    }

    void Awake()
    {
        if (Instance == null)
        {
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }
        else
        {
            Destroy(gameObject);
        }
    }

    void Start()
    {
        // Pre-carica tutte le animazioni
        _ = PreloadAnimationsAsync();
    }

    void Update()
    {
        UpdateLipSync();
        UpdateAutoBlink();
    }

    // ==========================================
    // PRELOAD ANIMAZIONI VRMA
    // ==========================================

    private async Task PreloadAnimationsAsync()
    {
        string animationsPath = Path.Combine(Application.streamingAssetsPath, "Animations");
        
        string[] animationFiles = {
            "idle_loop.vrma",
            "greeting.vrma", 
            "dance.vrma",
            "peaceSign.vrma",
            "shoot.vrma",
            "spin.vrma",
            "squat.vrma",
            "showFullBody.vrma",
            "modelPose.vrma"
        };

        foreach (var file in animationFiles)
        {
            string fullPath = Path.Combine(animationsPath, file);
            
            // Su Android, StreamingAssets richiede UnityWebRequest
            #if UNITY_ANDROID && !UNITY_EDITOR
            fullPath = await CopyStreamingAssetToCache(file);
            #endif
            
            if (File.Exists(fullPath))
            {
                try
                {
                    var animInstance = await LoadVrmaAsync(fullPath);
                    string animName = Path.GetFileNameWithoutExtension(file);
                    loadedAnimations[animName] = animInstance;
                    Debug.Log($"[AvatarController] Loaded animation: {animName}");
                }
                catch (Exception e)
                {
                    Debug.LogError($"[AvatarController] Failed to load {file}: {e.Message}");
                }
            }
        }
        
        Debug.Log($"[AvatarController] Preloaded {loadedAnimations.Count} animations");
    }

    private async Task<Vrm10AnimationInstance> LoadVrmaAsync(string path)
    {
        using var data = new GlbFileParser(path).Parse();
        using var loader = new VrmAnimationImporter(data);
        var instance = await loader.LoadAsync(new RuntimeOnlyAwaitCaller());
        return instance.GetComponent<Vrm10AnimationInstance>();
    }

    #if UNITY_ANDROID && !UNITY_EDITOR
    private async Task<string> CopyStreamingAssetToCache(string fileName)
    {
        string sourcePath = Path.Combine(Application.streamingAssetsPath, "Animations", fileName);
        string destPath = Path.Combine(Application.temporaryCachePath, fileName);
        
        using (var www = UnityEngine.Networking.UnityWebRequest.Get(sourcePath))
        {
            await www.SendWebRequest();
            if (www.result == UnityEngine.Networking.UnityWebRequest.Result.Success)
            {
                File.WriteAllBytes(destPath, www.downloadHandler.data);
            }
        }
        return destPath;
    }
    #endif

    // ==========================================
    // METODI CHIAMABILI DA ANDROID (via UnitySendMessage)
    // ==========================================

    /// <summary>
    /// Carica un modello VRM 1.0 da path file
    /// Chiamata: UnitySendMessage("AvatarController", "LoadVRM", "/path/to/model.vrm")
    /// </summary>
    public async void LoadVRM(string vrmPath)
    {
        try
        {
            // Cleanup previous avatar
            if (currentAvatarRoot != null)
            {
                Destroy(currentAvatarRoot);
            }

            Debug.Log($"[AvatarController] Loading VRM from: {vrmPath}");

            // Load VRM 1.0 (or migrate from 0.x)
            // showMeshes: true mostra automaticamente le mesh
            var vrm10Instance = await Vrm10.LoadPathAsync(
                path: vrmPath,
                canLoadVrm0X: true,  // Permette migrazione automatica da VRM 0.x
                showMeshes: true,
                awaitCaller: new RuntimeOnlyAwaitCaller(),
                materialGenerator: null,
                vrmMetaInformationCallback: OnVrmMetaLoaded
            );

            currentVrmInstance = vrm10Instance;
            currentAvatarRoot = vrm10Instance.gameObject;
            currentAvatarRoot.transform.SetParent(transform);
            currentAvatarRoot.transform.localPosition = Vector3.zero;
            currentAvatarRoot.transform.localRotation = Quaternion.identity;

            // Start idle animation
            PlayAnimation(Animations.Idle);

            Debug.Log("[AvatarController] VRM loaded successfully!");
            SendMessageToAndroid("VRM_LOADED", "success");
        }
        catch (Exception e)
        {
            Debug.LogError($"[AvatarController] Error loading VRM: {e.Message}");
            SendMessageToAndroid("VRM_ERROR", e.Message);
        }
    }

    // Callback per metadati VRM (firma corretta con 3 parametri)
    private void OnVrmMetaLoaded(Texture2D thumbnail, UniGLTF.Extensions.VRMC_vrm.Meta vrm10Meta, UniVRM10.Migration.Vrm0Meta vrm0Meta)
    {
        if (vrm10Meta != null)
        {
            Debug.Log($"[AvatarController] VRM 1.0 Meta - Name: {vrm10Meta.Name}");
        }
        else if (vrm0Meta != null)
        {
            Debug.Log($"[AvatarController] VRM 0.x Meta - Title: {vrm0Meta.Title}");
        }
    }

    /// <summary>
    /// Riproduce un'animazione VRMA
    /// Chiamata: UnitySendMessage("AvatarController", "PlayAnimation", "greeting")
    /// Animazioni: idle_loop, greeting, dance, peaceSign, shoot, spin, squat, showFullBody, modelPose
    /// </summary>
    public void PlayAnimation(string animationName)
    {
        if (currentVrmInstance == null)
        {
            Debug.LogWarning("[AvatarController] No VRM loaded");
            return;
        }

        if (!loadedAnimations.TryGetValue(animationName, out var animation))
        {
            Debug.LogWarning($"[AvatarController] Animation not found: {animationName}");
            return;
        }

        // Ferma animazione corrente
        if (currentAnimation != null && isAnimationPlaying)
        {
            StopCurrentAnimation();
        }

        // Collega la nuova animazione al VRM
        currentVrmInstance.Runtime.VrmAnimation = animation;
        currentAnimation = animation;
        currentAnimationName = animationName;
        isAnimationPlaying = true;

        // Play
        var animationComponent = animation.GetComponent<Animation>();
        if (animationComponent != null)
        {
            animationComponent.Play();
        }

        // Per animazioni non-loop, torna a idle alla fine
        bool isLoop = animationName == Animations.Idle || animationName == Animations.Dance;
        if (!isLoop)
        {
            StartCoroutine(ReturnToIdleAfterAnimation(animation));
        }

        Debug.Log($"[AvatarController] Playing animation: {animationName}");
        SendMessageToAndroid("ANIMATION_STARTED", animationName);
    }

    /// <summary>
    /// Ferma l'animazione corrente
    /// </summary>
    public void StopAnimation(string _)
    {
        StopCurrentAnimation();
        PlayAnimation(Animations.Idle);
    }

    private void StopCurrentAnimation()
    {
        if (currentAnimation != null)
        {
            var animationComponent = currentAnimation.GetComponent<Animation>();
            if (animationComponent != null)
            {
                animationComponent.Stop();
            }
        }
        isAnimationPlaying = false;
    }

    private IEnumerator ReturnToIdleAfterAnimation(Vrm10AnimationInstance animation)
    {
        var animComponent = animation.GetComponent<Animation>();
        if (animComponent != null && animComponent.clip != null)
        {
            yield return new WaitForSeconds(animComponent.clip.length);
        }
        else
        {
            yield return new WaitForSeconds(2f); // Default duration
        }
        
        if (currentAnimation == animation && isAnimationPlaying)
        {
            PlayAnimation(Animations.Idle);
        }
    }

    /// <summary>
    /// Imposta espressione emotiva
    /// Chiamata: UnitySendMessage("AvatarController", "SetEmotion", "happy|0.8")
    /// Formato: "emotionName|intensity"
    /// Emotions: neutral, happy, sad, angry, surprised, relaxed
    /// </summary>
    public void SetEmotion(string emotionData)
    {
        if (currentVrmInstance == null) return;

        string[] parts = emotionData.Split('|');
        string emotion = parts[0].ToLower();
        float intensity = parts.Length > 1 ? float.Parse(parts[1]) : 1f;
        
        currentEmotion = emotion;
        emotionIntensity = Mathf.Clamp01(intensity);
        
        ApplyEmotion();
    }

    /// <summary>
    /// Imposta apertura bocca per lip sync
    /// Chiamata: UnitySendMessage("AvatarController", "SetMouthOpen", "0.7")
    /// Range: 0.0 - 1.0
    /// </summary>
    public void SetMouthOpen(string value)
    {
        targetMouthOpen = Mathf.Clamp01(float.Parse(value));
    }

    /// <summary>
    /// Imposta visemi per lip sync avanzato
    /// Chiamata: UnitySendMessage("AvatarController", "SetViseme", "aa|0.5")
    /// Visemi VRM 1.0: aa, ih, ou, ee, oh
    /// </summary>
    public void SetViseme(string visemeData)
    {
        if (currentVrmInstance == null) return;
        
        string[] parts = visemeData.Split('|');
        string viseme = parts[0].ToLower();
        float weight = parts.Length > 1 ? float.Parse(parts[1]) : 0f;
        
        var expression = currentVrmInstance.Runtime.Expression;
        
        // Reset all visemes
        expression.SetWeight(ExpressionKey.Aa, 0);
        expression.SetWeight(ExpressionKey.Ih, 0);
        expression.SetWeight(ExpressionKey.Ou, 0);
        expression.SetWeight(ExpressionKey.Ee, 0);
        expression.SetWeight(ExpressionKey.Oh, 0);
        
        // Set target viseme
        ExpressionKey key = viseme switch
        {
            "aa" or "a" => ExpressionKey.Aa,
            "ih" or "i" => ExpressionKey.Ih,
            "ou" or "u" => ExpressionKey.Ou,
            "ee" or "e" => ExpressionKey.Ee,
            "oh" or "o" => ExpressionKey.Oh,
            _ => ExpressionKey.Aa
        };
        
        expression.SetWeight(key, weight);
    }

    /// <summary>
    /// Imposta direzione sguardo (yaw/pitch)
    /// Chiamata: UnitySendMessage("AvatarController", "SetLookAt", "10.0|-5.0")
    /// Formato: "yaw|pitch" in gradi
    /// </summary>
    public void SetLookAt(string targetData)
    {
        if (currentVrmInstance?.Runtime?.LookAt == null) return;
        
        string[] parts = targetData.Split('|');
        if (parts.Length >= 2)
        {
            float yaw = float.Parse(parts[0]);
            float pitch = float.Parse(parts[1]);
            currentVrmInstance.Runtime.LookAt.SetYawPitchManually(yaw, pitch);
        }
    }

    /// <summary>
    /// Guarda dritto (reset look at)
    /// </summary>
    public void LookAtCamera(string _)
    {
        if (currentVrmInstance?.Runtime?.LookAt != null)
        {
            currentVrmInstance.Runtime.LookAt.SetYawPitchManually(0, 0);
        }
    }

    /// <summary>
    /// Triggera animazione di saluto
    /// </summary>
    public void PlayGreeting(string _)
    {
        PlayAnimation(Animations.Greeting);
    }

    /// <summary>
    /// Resetta avatar a stato neutrale
    /// </summary>
    public void ResetAvatar(string _)
    {
        currentEmotion = "neutral";
        emotionIntensity = 1f;
        targetMouthOpen = 0f;
        ApplyEmotion();
        LookAtCamera("");
        PlayAnimation(Animations.Idle);
    }

    // ==========================================
    // IMPLEMENTAZIONI INTERNE
    // ==========================================

    private void ApplyEmotion()
    {
        if (currentVrmInstance == null) return;
        
        var expression = currentVrmInstance.Runtime.Expression;
        
        // Reset all emotion expressions
        expression.SetWeight(ExpressionKey.Happy, 0);
        expression.SetWeight(ExpressionKey.Angry, 0);
        expression.SetWeight(ExpressionKey.Sad, 0);
        expression.SetWeight(ExpressionKey.Surprised, 0);
        expression.SetWeight(ExpressionKey.Relaxed, 0);
        
        // Apply current emotion
        ExpressionKey? emotionKey = currentEmotion switch
        {
            "happy" or "joy" => ExpressionKey.Happy,
            "sad" or "sorrow" => ExpressionKey.Sad,
            "angry" => ExpressionKey.Angry,
            "surprised" => ExpressionKey.Surprised,
            "relaxed" => ExpressionKey.Relaxed,
            "neutral" => null,
            _ => null
        };
        
        if (emotionKey.HasValue)
        {
            expression.SetWeight(emotionKey.Value, emotionIntensity);
        }
        
        SendMessageToAndroid("EMOTION_SET", $"{currentEmotion}|{emotionIntensity}");
    }

    private void UpdateLipSync()
    {
        if (currentVrmInstance == null) return;
        
        // Smooth interpolation
        currentMouthOpen = Mathf.Lerp(currentMouthOpen, targetMouthOpen, Time.deltaTime / lipSyncSmoothing);
        
        // Apply to mouth blendshape (Aa is most common for open mouth)
        float adjustedValue = Mathf.Clamp01(currentMouthOpen * lipSyncSensitivity);
        currentVrmInstance.Runtime.Expression.SetWeight(ExpressionKey.Aa, adjustedValue);
    }

    private void UpdateAutoBlink()
    {
        if (!enableAutoBlink || currentVrmInstance == null || isBlinking) return;
        
        blinkTimer -= Time.deltaTime;
        
        if (blinkTimer <= 0)
        {
            StartCoroutine(BlinkCoroutine());
            blinkTimer = blinkInterval + UnityEngine.Random.Range(-0.5f, 1f);
        }
    }

    private IEnumerator BlinkCoroutine()
    {
        isBlinking = true;
        
        // Close eyes
        currentVrmInstance.Runtime.Expression.SetWeight(ExpressionKey.Blink, 1f);
        yield return new WaitForSeconds(blinkDuration);
        
        // Open eyes
        currentVrmInstance.Runtime.Expression.SetWeight(ExpressionKey.Blink, 0f);
        
        isBlinking = false;
    }

    // ==========================================
    // COMUNICAZIONE CON ANDROID
    // ==========================================

    private void SendMessageToAndroid(string eventType, string data)
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        try
        {
            using (AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            {
                AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
                activity.Call("onUnityMessage", eventType, data);
            }
        }
        catch (Exception e)
        {
            Debug.LogError($"[AvatarController] Error sending message to Android: {e.Message}");
        }
#endif
        Debug.Log($"[AvatarController] Event: {eventType} - Data: {data}");
    }
}
```

### Script Helper: RuntimeOnlyAwaitCaller.cs

Crea `Assets/Scripts/RuntimeOnlyAwaitCaller.cs`:

```csharp
using System;
using System.Threading.Tasks;
using UniGLTF;

public class RuntimeOnlyAwaitCaller : IAwaitCaller
{
    public Task NextFrame()
    {
        return Task.Delay(16); // ~60fps
    }

    public Task Run(Action action)
    {
        return Task.Run(action);
    }

    public Task<T> Run<T>(Func<T> action)
    {
        return Task.Run(action);
    }
}
```

### ⚠️ Note Importanti API UniVRM

**Versione testata**: UniVRM v0.127.0+

> Se usi una versione diversa, l'API potrebbe variare leggermente. Controlla sempre la documentazione ufficiale: https://vrm.dev/api/

**Errori comuni e soluzioni:**

1. **VrmMetaInformationCallback** - Accetta 3 parametri:
   ```csharp
   // Firma corretta:
   void Callback(Texture2D thumbnail, 
                 UniGLTF.Extensions.VRMC_vrm.Meta vrm10Meta, 
                 UniVRM10.Migration.Vrm0Meta vrm0Meta)
   
   // Se ricevi errore "non accetta argomenti", controlla di usare questa firma
   ```

2. **ShowMeshes** - È un parametro di `LoadPathAsync`, non un metodo:
   ```csharp
   // ✅ Corretto
   await Vrm10.LoadPathAsync(path, showMeshes: true);
   
   // ❌ Sbagliato  
   vrm10Instance.ShowMeshes(); // Questo metodo NON esiste!
   ```

3. **ExpressionKey** - Usa le proprietà statiche, non confrontare con `!=`:
   ```csharp
   // ✅ Corretto - usa nullable
   ExpressionKey? key = emotion == "happy" ? ExpressionKey.Happy : null;
   if (key.HasValue) expression.SetWeight(key.Value, weight);
   
   // ❌ Sbagliato - non compila!
   if (emotionKey != ExpressionKey.Neutral) // ExpressionKey non supporta !=
   ```

4. **LookAtTargetType** - Non esiste più, usa `SetYawPitchManually`:
   ```csharp
   // ✅ Corretto
   currentVrmInstance.Runtime.LookAt.SetYawPitchManually(yaw, pitch);
   
   // ❌ Sbagliato - proprietà non esiste!
   vrm.Vrm.LookAt.LookAtTargetType = ...
   ```

5. **Se il callback Meta dà ancora errori**, prova senza callback:
   ```csharp
   var vrm10Instance = await Vrm10.LoadPathAsync(
       path: vrmPath,
       canLoadVrm0X: true,
       showMeshes: true
       // Ometti vrmMetaInformationCallback se dà problemi
   );
   ```
```

## 1.6 Animazioni VRMA di Amica

### Animazioni Disponibili da Amica

Le animazioni da copiare dal repository Amica (`public/animations/`):

| File | Tipo | Descrizione | Uso |
|------|------|-------------|-----|
| `idle_loop.vrma` | Loop | Respirazione e micro-movimenti idle | Animazione default |
| `greeting.vrma` | One-shot | Saluto con mano | Inizio conversazione |
| `dance.vrma` | Loop | Ballo completo | Su richiesta utente |
| `peaceSign.vrma` | One-shot | Segno della pace | Espressione positiva |
| `shoot.vrma` | One-shot | Gesto "pistola" con dita | Gesto scherzoso |
| `spin.vrma` | One-shot | Giravolta su se stessa | Su richiesta |
| `squat.vrma` | One-shot | Squat/inchino | Gesto rispetto |
| `showFullBody.vrma` | One-shot | Mostra corpo intero | Cambio inquadratura |
| `modelPose.vrma` | One-shot | Posa da modella | Su richiesta |

### Setup Cartella Animazioni in Unity

```
Assets/
├── StreamingAssets/
│   └── Animations/
│       ├── idle_loop.vrma
│       ├── greeting.vrma
│       ├── dance.vrma
│       ├── peaceSign.vrma
│       ├── shoot.vrma
│       ├── spin.vrma
│       ├── squat.vrma
│       ├── showFullBody.vrma
│       └── modelPose.vrma
```

> ⚠️ **IMPORTANTE**: I file `.vrma` devono essere in `StreamingAssets` per essere accessibili a runtime su Android!

### Copia le Animazioni

```bash
# Clona Amica se non l'hai già fatto
git clone https://github.com/semperai/amica.git

# Copia le animazioni nel progetto Unity
cp amica/public/animations/*.vrma /path/to/UnityProject/Assets/StreamingAssets/Animations/
```

### Configurazione VRM 1.0

> ⚠️ **NOTA CRITICA**: Le animazioni VRMA funzionano SOLO con modelli **VRM 1.0**. Se il tuo modello è VRM 0.x, UniVRM può migrarlo automaticamente a 1.0.

Per verificare/convertire:
1. In Unity Editor: `VRM1 → Import from VRM 0.x`
2. Oppure a runtime con `Vrm10.MigrateAsync()`

## 1.7 Build Unity as a Library

### Step 1: Export Settings
```
File → Build Settings:

✓ Platform: Android
✓ Export Project: ✓ (IMPORTANTE!)
✓ Development Build: ✓ (per debug)

Player Settings → Other Settings:
✓ Scripting Backend: IL2CPP
✓ Api Compatibility Level: .NET Standard 2.1
```

### Step 2: Export
1. Click "Export"
2. Seleziona cartella: `AmicaVRMModule_Export`
3. Unity genera progetto Gradle completo

### Step 3: Struttura Generata
```
AmicaVRMModule_Export/
├── unityLibrary/
│   ├── build.gradle
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/
│   │   └── jniLibs/
│   └── libs/
├── launcher/
└── gradle/
```

---

# PARTE 2: Android Studio Setup

## 2.1 Requisiti

- Android Studio Hedgehog (2023.1.1) o superiore
- JDK 17
- Kotlin 1.9+
- Gradle 8.2+

## 2.2 Nuovo Progetto Android

```
New Project:
- Template: Empty Activity (Compose)
- Name: AmicaAndroid
- Package: com.tuodominio.amica
- Language: Kotlin
- Minimum SDK: API 26 (Android 8.0)
- Build configuration: Kotlin DSL
```

## 2.3 Struttura Progetto Target

```
AmicaAndroid/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/tuodominio/amica/
│       │   ├── MainActivity.kt
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   ├── screens/
│       │   │   │   ├── ChatScreen.kt
│       │   │   │   └── SettingsScreen.kt
│       │   │   └── components/
│       │   │       ├── ChatBubble.kt
│       │   │       ├── VoiceButton.kt
│       │   │       └── AvatarView.kt
│       │   ├── unity/
│       │   │   ├── UnityPlayerActivity.kt
│       │   │   └── AvatarBridge.kt
│       │   ├── ai/
│       │   │   ├── LLMClient.kt
│       │   │   ├── TTSEngine.kt
│       │   │   ├── STTEngine.kt
│       │   │   └── VADProcessor.kt
│       │   ├── audio/
│       │   │   ├── AudioCapture.kt
│       │   │   ├── AudioPlayer.kt
│       │   │   └── LipSyncAnalyzer.kt
│       │   ├── data/
│       │   │   ├── models/
│       │   │   ├── repository/
│       │   │   └── preferences/
│       │   └── utils/
│       ├── res/
│       └── AndroidManifest.xml
├── unityLibrary/ (importato da Unity)
├── settings.gradle.kts
└── build.gradle.kts
```

## 2.4 Dipendenze Gradle (app/build.gradle.kts)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.tuodominio.amica"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tuodominio.amica"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // === COMPOSE ===
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // === NETWORKING ===
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0") // Server-Sent Events
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // === AUDIO ===
    implementation("com.google.oboe:oboe:1.8.0") // Low-latency audio
    
    // === AI/ML ===
    // Whisper locale (opzionale)
    // implementation("com.github.nicholasyager:whisper-android:0.6.0")
    
    // === DI ===
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // === COROUTINES ===
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // === DATASTORE ===
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // === UNITY ===
    implementation(project(":unityLibrary"))
    
    // === DEBUG ===
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

---

# PARTE 3: Integrazione Unity-Android

## 3.1 Importare Unity Module

### Step 1: Copia unityLibrary
```bash
cp -r /path/to/AmicaVRMModule_Export/unityLibrary /path/to/AmicaAndroid/
```

### Step 2: settings.gradle.kts
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        flatDir {
            dirs("unityLibrary/libs")
        }
    }
}

rootProject.name = "AmicaAndroid"

include(":app")
include(":unityLibrary")
project(":unityLibrary").projectDir = file("unityLibrary")
```

### Step 3: Modifica unityLibrary/build.gradle

Cerca e modifica:
```groovy
// Da:
apply plugin: 'com.android.application'
// A:
apply plugin: 'com.android.library'

// Rimuovi:
// applicationId "com.tuodominio.amicavrm"
```

## 3.2 MainActivity con Unity

```kotlin
// MainActivity.kt
package com.tuodominio.amica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tuodominio.amica.ui.theme.AmicaTheme
import com.tuodominio.amica.unity.UnityBridge
import com.unity3d.player.UnityPlayer
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var unityPlayer: UnityPlayer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inizializza Unity
        unityPlayer = UnityPlayer(this).also {
            UnityBridge.initialize(it)
        }
        
        setContent {
            AmicaTheme {
                AmicaApp(unityPlayer = unityPlayer)
            }
        }
    }

    // Callback da Unity
    fun onUnityMessage(eventType: String, data: String) {
        runOnUiThread {
            UnityBridge.handleUnityEvent(eventType, data)
        }
    }

    override fun onResume() {
        super.onResume()
        unityPlayer?.resume()
    }

    override fun onPause() {
        super.onPause()
        unityPlayer?.pause()
    }

    override fun onDestroy() {
        unityPlayer?.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        unityPlayer?.windowFocusChanged(hasFocus)
    }
}
```

## 3.3 AvatarBridge.kt (con supporto Animazioni VRMA)

```kotlin
// unity/AvatarBridge.kt
package com.tuodominio.amica.unity

import com.unity3d.player.UnityPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge per comunicazione Android ↔ Unity
 * Gestisce avatar VRM, animazioni VRMA, espressioni e lip sync
 */
object AvatarBridge {
    
    private var unityPlayer: UnityPlayer? = null
    
    // === ANIMAZIONI DISPONIBILI (da Amica) ===
    object Animations {
        const val IDLE = "idle_loop"
        const val GREETING = "greeting"
        const val DANCE = "dance"
        const val PEACE_SIGN = "peaceSign"
        const val SHOOT = "shoot"
        const val SPIN = "spin"
        const val SQUAT = "squat"
        const val SHOW_FULL_BODY = "showFullBody"
        const val MODEL_POSE = "modelPose"
    }
    
    // === EMOZIONI SUPPORTATE ===
    object Emotions {
        const val NEUTRAL = "neutral"
        const val HAPPY = "happy"
        const val SAD = "sad"
        const val ANGRY = "angry"
        const val SURPRISED = "surprised"
        const val RELAXED = "relaxed"
    }
    
    // Eventi da Unity
    private val _unityEvents = MutableStateFlow<UnityEvent?>(null)
    val unityEvents: StateFlow<UnityEvent?> = _unityEvents
    
    // Stato avatar
    private val _isAvatarLoaded = MutableStateFlow(false)
    val isAvatarLoaded: StateFlow<Boolean> = _isAvatarLoaded
    
    // Stato animazione corrente
    private val _currentAnimation = MutableStateFlow(Animations.IDLE)
    val currentAnimation: StateFlow<String> = _currentAnimation
    
    // Stato emozione corrente
    private val _currentEmotion = MutableStateFlow(Emotions.NEUTRAL)
    val currentEmotion: StateFlow<String> = _currentEmotion
    
    fun initialize(player: UnityPlayer) {
        unityPlayer = player
    }
    
    // === METODI VRM ===
    
    fun loadVRM(path: String) {
        sendMessage("LoadVRM", path)
    }
    
    // === METODI ANIMAZIONI VRMA ===
    
    /**
     * Riproduce un'animazione VRMA
     * @param animation Una delle costanti di Animations
     */
    fun playAnimation(animation: String) {
        _currentAnimation.value = animation
        sendMessage("PlayAnimation", animation)
    }
    
    fun playIdle() = playAnimation(Animations.IDLE)
    fun playGreeting() = playAnimation(Animations.GREETING)
    fun playDance() = playAnimation(Animations.DANCE)
    fun playPeaceSign() = playAnimation(Animations.PEACE_SIGN)
    fun playShoot() = playAnimation(Animations.SHOOT)
    fun playSpin() = playAnimation(Animations.SPIN)
    fun playSquat() = playAnimation(Animations.SQUAT)
    fun playShowFullBody() = playAnimation(Animations.SHOW_FULL_BODY)
    fun playModelPose() = playAnimation(Animations.MODEL_POSE)
    
    fun stopAnimation() {
        sendMessage("StopAnimation", "")
    }
    
    // === METODI ESPRESSIONI ===
    
    /**
     * Imposta emozione dell'avatar
     * @param emotion Una delle costanti di Emotions
     * @param intensity Intensità da 0.0 a 1.0
     */
    fun setEmotion(emotion: String, intensity: Float = 1f) {
        _currentEmotion.value = emotion
        sendMessage("SetEmotion", "$emotion|${intensity.coerceIn(0f, 1f)}")
    }
    
    fun setHappy(intensity: Float = 1f) = setEmotion(Emotions.HAPPY, intensity)
    fun setSad(intensity: Float = 1f) = setEmotion(Emotions.SAD, intensity)
    fun setAngry(intensity: Float = 1f) = setEmotion(Emotions.ANGRY, intensity)
    fun setSurprised(intensity: Float = 1f) = setEmotion(Emotions.SURPRISED, intensity)
    fun setRelaxed(intensity: Float = 1f) = setEmotion(Emotions.RELAXED, intensity)
    fun setNeutral() = setEmotion(Emotions.NEUTRAL, 1f)
    
    // === METODI LIP SYNC ===
    
    /**
     * Imposta apertura bocca per lip sync semplice
     * @param value Valore da 0.0 (chiusa) a 1.0 (aperta)
     */
    fun setMouthOpen(value: Float) {
        sendMessage("SetMouthOpen", value.coerceIn(0f, 1f).toString())
    }
    
    /**
     * Imposta visema specifico per lip sync avanzato
     * @param viseme Visema: "aa", "ih", "ou", "ee", "oh"
     * @param weight Peso da 0.0 a 1.0
     */
    fun setViseme(viseme: String, weight: Float) {
        sendMessage("SetViseme", "$viseme|${weight.coerceIn(0f, 1f)}")
    }
    
    // === METODI LOOK AT ===
    
    /**
     * Imposta direzione sguardo
     * @param yaw Rotazione orizzontale in gradi (-30 a 30)
     * @param pitch Rotazione verticale in gradi (-30 a 30)
     */
    fun setLookAt(yaw: Float, pitch: Float) {
        sendMessage("SetLookAt", "$yaw|$pitch")
    }
    
    fun lookAtCamera() {
        sendMessage("LookAtCamera", "")
    }
    
    // === RESET ===
    
    fun resetAvatar() {
        _currentAnimation.value = Animations.IDLE
        _currentEmotion.value = Emotions.NEUTRAL
        sendMessage("ResetAvatar", "")
    }
    
    // === COMUNICAZIONE ===
    
    private fun sendMessage(method: String, param: String) {
        UnityPlayer.UnitySendMessage("AvatarController", method, param)
    }
    
    // Chiamato da MainActivity
    fun handleUnityEvent(eventType: String, data: String) {
        when (eventType) {
            "VRM_LOADED" -> {
                _isAvatarLoaded.value = true
                _currentAnimation.value = Animations.IDLE
            }
            "VRM_ERROR" -> {
                _isAvatarLoaded.value = false
            }
            "ANIMATION_STARTED" -> {
                _currentAnimation.value = data
            }
            "EMOTION_SET" -> {
                val parts = data.split("|")
                if (parts.isNotEmpty()) {
                    _currentEmotion.value = parts[0]
                }
            }
        }
        _unityEvents.value = UnityEvent(eventType, data)
    }
}

data class UnityEvent(
    val type: String,
    val data: String
)

/**
 * Extension per mappare emozioni LLM → Avatar
 */
fun String.toAvatarEmotion(): String = when (this.lowercase()) {
    "happy", "excited", "joyful", "pleased", "delighted" -> AvatarBridge.Emotions.HAPPY
    "sad", "disappointed", "unhappy", "melancholy" -> AvatarBridge.Emotions.SAD
    "angry", "frustrated", "annoyed", "irritated" -> AvatarBridge.Emotions.ANGRY
    "surprised", "shocked", "amazed", "astonished" -> AvatarBridge.Emotions.SURPRISED
    "relaxed", "calm", "peaceful", "content" -> AvatarBridge.Emotions.RELAXED
    else -> AvatarBridge.Emotions.NEUTRAL
}

/**
 * Extension per suggerire animazioni basate su contesto
 */
fun suggestAnimationForContext(context: String): String = when {
    context.contains("hello", ignoreCase = true) || 
    context.contains("hi", ignoreCase = true) ||
    context.contains("ciao", ignoreCase = true) -> AvatarBridge.Animations.GREETING
    
    context.contains("dance", ignoreCase = true) ||
    context.contains("balla", ignoreCase = true) -> AvatarBridge.Animations.DANCE
    
    context.contains("peace", ignoreCase = true) ||
    context.contains("victory", ignoreCase = true) -> AvatarBridge.Animations.PEACE_SIGN
    
    context.contains("spin", ignoreCase = true) ||
    context.contains("turn around", ignoreCase = true) ||
    context.contains("gira", ignoreCase = true) -> AvatarBridge.Animations.SPIN
    
    context.contains("full body", ignoreCase = true) ||
    context.contains("corpo intero", ignoreCase = true) -> AvatarBridge.Animations.SHOW_FULL_BODY
    
    context.contains("pose", ignoreCase = true) ||
    context.contains("model", ignoreCase = true) -> AvatarBridge.Animations.MODEL_POSE
    
    else -> AvatarBridge.Animations.IDLE
}
```

## 3.4 LipSync Analyzer

```kotlin
// audio/LipSyncAnalyzer.kt
package com.tuodominio.amica.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sqrt

class LipSyncAnalyzer {
    
    companion object {
        private const val SMOOTHING_FACTOR = 0.3f
        private const val NOISE_GATE = 0.02f
        private const val MAX_AMPLITUDE = 0.8f
    }
    
    private var previousValue = 0f
    
    /**
     * Analizza audio buffer e restituisce valore apertura bocca (0-1)
     */
    fun analyze(audioBuffer: ShortArray): Float {
        val rms = calculateRMS(audioBuffer)
        val normalized = normalizeValue(rms)
        val smoothed = smooth(normalized)
        return smoothed
    }
    
    /**
     * Flow per analisi continua durante playback TTS
     */
    fun analyzeStream(audioStream: Flow<ShortArray>): Flow<Float> = flow {
        audioStream.collect { buffer ->
            emit(analyze(buffer))
        }
    }
    
    private fun calculateRMS(buffer: ShortArray): Float {
        if (buffer.isEmpty()) return 0f
        
        var sum = 0.0
        for (sample in buffer) {
            val normalized = sample / 32768.0
            sum += normalized * normalized
        }
        return sqrt(sum / buffer.size).toFloat()
    }
    
    private fun normalizeValue(rms: Float): Float {
        // Applica noise gate
        if (rms < NOISE_GATE) return 0f
        
        // Normalizza nel range 0-1
        val adjusted = (rms - NOISE_GATE) / (MAX_AMPLITUDE - NOISE_GATE)
        return adjusted.coerceIn(0f, 1f)
    }
    
    private fun smooth(value: Float): Float {
        previousValue = previousValue + SMOOTHING_FACTOR * (value - previousValue)
        return previousValue
    }
    
    fun reset() {
        previousValue = 0f
    }
}
```

---

# PARTE 4: Trigger per Claude Code

Questi sono prompt strutturati da copiare e incollare in Claude Code per completare lo sviluppo.

---

## 🎯 TRIGGER 1: Setup Progetto Base

```
Crea un progetto Android completo per un'app stile Amica con:

STRUTTURA:
- Package name: com.example.amica
- Min SDK: 26
- Target SDK: 34
- Compose + Material3
- Hilt per DI
- Navigation Compose

MODULI:
1. :app - applicazione principale
2. :core:network - networking con OkHttp + Retrofit  
3. :core:audio - cattura e playback audio
4. :feature:chat - UI chat con Compose

FILE DA CREARE:
- build.gradle.kts (root e app)
- settings.gradle.kts
- MainActivity.kt con Scaffold base
- AmicaApplication.kt con Hilt
- Navigation setup

Usa Kotlin DSL per Gradle e includi tutte le dipendenze necessarie per Compose, coroutines, serialization.
```

---

## 🎯 TRIGGER 2: UI Chat Screen

```
Crea una ChatScreen completa in Jetpack Compose con:

LAYOUT:
- TopBar con nome avatar e stato connessione
- Lista messaggi scrollabile (LazyColumn)
- Ogni messaggio: bolla, timestamp, indicatore typing
- Input area in basso: TextField + bottone invio + bottone mic
- FAB per voice mode

COMPONENTI:
1. ChatScreen.kt - schermata principale
2. ChatBubble.kt - bolla messaggio (user/assistant)
3. VoiceButton.kt - bottone registrazione con animazione
4. TypingIndicator.kt - animazione "sta scrivendo..."
5. ChatViewModel.kt - gestione stato con StateFlow

STATO:
- Lista messaggi (id, role, content, timestamp)
- isRecording: Boolean
- isTyping: Boolean  
- inputText: String

USA:
- Material3 theming
- Animazioni per transizioni
- remember + mutableStateOf dove appropriato
- LaunchedEffect per scroll automatico

Stile visivo pulito, moderno, dark mode friendly.
```

---

## 🎯 TRIGGER 3: LLM Client OpenAI

```
Implementa un client completo per OpenAI Chat Completions API:

FILE: ai/LLMClient.kt

REQUISITI:
- Interfaccia astratta LLMClient per supportare più backend
- Implementazione OpenAIClient
- Streaming response con Server-Sent Events
- Gestione conversation history
- System prompt configurabile
- Emotion extraction dal response (JSON mode)

FUNZIONALITÀ:
1. sendMessage(message: String): Flow<StreamChunk>
2. setSystemPrompt(prompt: String)
3. clearHistory()
4. setModel(model: String)

STREAMING CHUNK:
data class StreamChunk(
    val text: String,
    val isComplete: Boolean,
    val emotion: String? = null,
    val usage: TokenUsage? = null
)

GESTIONE ERRORI:
- Timeout
- Rate limiting
- Network errors
- Invalid API key

USA OkHttp con SSE per streaming.
Includi anche mock client per testing.
```

---

## 🎯 TRIGGER 4: Text-to-Speech Engine

```
Implementa un TTS engine per Android con supporto multiplo:

FILE: ai/TTSEngine.kt

BACKENDS SUPPORTATI:
1. Android TTS nativo (fallback)
2. OpenAI TTS API
3. ElevenLabs API (opzionale)

INTERFACCIA:
interface TTSEngine {
    suspend fun speak(text: String): Flow<AudioChunk>
    fun stop()
    fun setVoice(voiceId: String)
    fun setSpeed(speed: Float)
    var onSpeakingStateChanged: ((Boolean) -> Unit)?
}

AUDIO CHUNK (per lip sync):
data class AudioChunk(
    val pcmData: ShortArray,
    val sampleRate: Int,
    val isLast: Boolean
)

IMPLEMENTAZIONI:
1. AndroidTTSEngine - usa android.speech.tts.TextToSpeech
2. OpenAITTSEngine - streaming da API, decode MP3 to PCM
3. CompositeEngine - fallback chain

REQUISITI:
- Streaming audio per lip sync real-time
- Coda di riproduzione per frasi multiple
- Cancellazione in corso
- Callback per stato (speaking/idle)

Gestisci il lifecycle correttamente con CoroutineScope.
```

---

## 🎯 TRIGGER 5: Speech-to-Text Engine

```
Implementa STT engine con Voice Activity Detection:

FILES:
- ai/STTEngine.kt
- ai/VADProcessor.kt
- audio/AudioCapture.kt

STT BACKENDS:
1. Google Speech Recognition (on-device)
2. OpenAI Whisper API
3. Whisper locale (opzionale)

INTERFACCIA STT:
interface STTEngine {
    fun startListening(): Flow<STTResult>
    fun stopListening()
    var onPartialResult: ((String) -> Unit)?
}

data class STTResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float
)

VAD PROCESSOR:
- Silero VAD o algoritmo RMS-based
- Detect inizio/fine parlato
- Timeout configurabile
- Evita false trigger

AUDIO CAPTURE:
- AudioRecord con buffer circolare
- Sample rate: 16000Hz
- Mono, 16-bit PCM
- Permessi RECORD_AUDIO gestiti

FLOW COMPLETO:
1. User preme bottone mic
2. AudioCapture inizia
3. VAD detecta voce → STT attivo
4. VAD detecta silenzio → STT finalizza
5. Risultato emesso via Flow

Gestisci permessi runtime con ActivityResultContracts.
```

---

## 🎯 TRIGGER 6: Audio Player con Lip Sync

```
Crea AudioPlayer che emette dati per lip sync:

FILE: audio/AudioPlayer.kt

REQUISITI:
- Playback PCM a bassa latenza (Oboe o AudioTrack)
- Emissione buffer per analisi lip sync
- Queue per audio chunks
- Controlli play/pause/stop
- Callback completion

INTERFACCIA:
class AudioPlayer(context: Context) {
    
    val audioBufferFlow: Flow<ShortArray>  // Per lip sync
    val isPlaying: StateFlow<Boolean>
    
    fun enqueue(chunk: AudioChunk)
    fun play()
    fun pause()
    fun stop()
    fun clear()
    
    var onPlaybackComplete: (() -> Unit)?
}

INTEGRAZIONE LIP SYNC:
- Ogni frame audio (~20ms) emetti buffer
- LipSyncAnalyzer processa → valore 0-1
- AvatarBridge.setMouthOpen(value)

PERFORMANCE:
- Buffer size ottimale per latenza
- Thread dedicato per playback
- Evita allocazioni nel loop audio

Usa coroutines per flow, ma audio loop su thread nativo.
```

---

## 🎯 TRIGGER 7: Unity Integration Layer + Animazioni VRMA

```
Completa l'integrazione Unity as a Library con supporto animazioni VRMA di Amica:

FILES:
- unity/UnityBridge.kt (già parziale, espandi)
- unity/UnityPlayerFragment.kt
- unity/AnimationController.kt
- ui/components/AvatarView.kt
- ui/components/AnimationPicker.kt

ANIMAZIONI VRMA DISPONIBILI (da Amica):
- idle_loop.vrma - Loop idle con respirazione
- greeting.vrma - Saluto con mano
- dance.vrma - Ballo loop
- peaceSign.vrma - Segno della pace
- shoot.vrma - Gesto pistola
- spin.vrma - Giravolta
- squat.vrma - Squat/inchino
- showFullBody.vrma - Mostra corpo intero
- modelPose.vrma - Posa da modella

UNITY PLAYER FRAGMENT:
- Fragment che wrappa UnityPlayer
- Gestione lifecycle corretta
- Resize handling
- Touch forwarding

ANIMATION CONTROLLER:
class AnimationController(private val avatarBridge: AvatarBridge) {
    
    val currentAnimation: StateFlow<String>
    val availableAnimations: List<AnimationInfo>
    
    fun playAnimation(name: String)
    fun playAnimationForContext(text: String) // Auto-detect da testo
    fun stopAnimation()
    fun playIdleLoop()
    
    // Trigger automatici
    fun onConversationStart() // → greeting
    fun onConversationEnd() // → idle
    fun onUserRequest(request: String) // Parse e play
}

data class AnimationInfo(
    val name: String,
    val displayName: String,
    val isLoop: Boolean,
    val duration: Float? // null se loop
)

ANIMATION PICKER (Compose):
@Composable
fun AnimationPicker(
    animations: List<AnimationInfo>,
    currentAnimation: String,
    onAnimationSelected: (String) -> Unit
)
- Horizontal scrollable chips
- Visual feedback animazione attiva
- Tooltip con nome

AVATAR VIEW (Compose):
@Composable
fun AvatarView(
    modifier: Modifier,
    unityPlayer: UnityPlayer?,
    emotion: String,
    mouthOpen: Float,
    currentAnimation: String,
    onAnimationRequest: (String) -> Unit
)
- Usa AndroidView per embeddare Unity
- Binding reattivo con emotion e mouthOpen
- Long press per mostrare AnimationPicker
- Doppio tap per greeting

EMOTION MAPPING:
Mappa emozioni LLM → blendshape VRM 1.0:
- "happy", "excited" → ExpressionKey.Happy
- "sad", "disappointed" → ExpressionKey.Sad
- "angry", "frustrated" → ExpressionKey.Angry
- "surprised", "shocked" → ExpressionKey.Surprised
- default → ExpressionKey.Neutral

CONTEXT-AWARE ANIMATION:
Quando LLM risponde, analizza il testo per trigger animazioni:
- Saluti (ciao, hello, hi) → greeting
- Richiesta ballo → dance
- Richiesta gira → spin
- Richiesta pose → modelPose

SINCRONIZZAZIONE:
- Quando LLM risponde con emotion, chiama setEmotion
- Durante TTS, lip sync continuo su idle_loop
- Fine TTS → bocca chiusa graduale
- Richieste speciali → animazione one-shot → torna a idle

Gestisci race conditions con mutex o StateFlow.
```

---

## 🎯 TRIGGER 8: Settings & Preferences

```
Crea sistema di settings persistenti:

FILES:
- data/preferences/UserPreferences.kt
- data/preferences/PreferencesRepository.kt
- ui/screens/SettingsScreen.kt
- ui/viewmodels/SettingsViewModel.kt

PREFERENCES (DataStore):
data class UserPreferences(
    // AI
    val llmBackend: LLMBackend = LLMBackend.OPENAI,
    val openaiApiKey: String = "",
    val openaiModel: String = "gpt-4o-mini",
    val systemPrompt: String = DEFAULT_PROMPT,
    
    // TTS
    val ttsBackend: TTSBackend = TTSBackend.OPENAI,
    val ttsVoice: String = "alloy",
    val ttsSpeed: Float = 1.0f,
    
    // STT
    val sttBackend: STTBackend = STSBackend.GOOGLE,
    val whisperApiKey: String = "",
    
    // Avatar
    val currentVrmPath: String? = null,
    val lipSyncSensitivity: Float = 1.0f,
    val enableAutoBlink: Boolean = true,
    
    // UI
    val darkMode: Boolean = true,
    val hapticFeedback: Boolean = true
)

SETTINGS SCREEN:
- Sezioni collassabili per categoria
- TextField per API keys (password mask)
- Slider per sensitivity/speed
- Switch per toggles
- Dropdown per backend selection
- Pulsante test connessione

Usa Proto DataStore per type safety.
Valida API keys prima di salvare.
```

---

## 🎯 TRIGGER 9: VRM File Picker & Manager

```
Implementa gestione file VRM:

FILES:
- data/VRMManager.kt
- ui/components/VRMPicker.kt
- ui/screens/AvatarGalleryScreen.kt

VRM MANAGER:
class VRMManager(context: Context) {
    
    val availableAvatars: Flow<List<VRMInfo>>
    
    suspend fun importVRM(uri: Uri): Result<VRMInfo>
    suspend fun deleteVRM(id: String): Result<Unit>
    fun getVRMPath(id: String): String?
    suspend fun setActiveAvatar(id: String)
}

data class VRMInfo(
    val id: String,
    val name: String,
    val thumbnailPath: String?,
    val filePath: String,
    val fileSize: Long,
    val importDate: Long
)

VRM PICKER:
- Usa SAF (Storage Access Framework)
- ActivityResultContract per ACTION_OPEN_DOCUMENT
- MIME type: application/octet-stream, .vrm
- Copia file in app internal storage
- Genera thumbnail (opzionale, via Unity)

AVATAR GALLERY:
- Grid di avatar disponibili
- Card con thumbnail, nome, size
- Long press per delete
- Tap per selezionare
- FAB per importare nuovo

Gestisci permessi storage per Android 10+.
```

---

## 🎯 TRIGGER 10: Conversation Flow Orchestrator

```
Crea orchestratore che coordina tutto il flusso conversazionale:

FILE: ConversationOrchestrator.kt

RESPONSABILITÀ:
- Coordina STT → LLM → TTS → Avatar
- Gestisce stati: idle, listening, thinking, speaking
- Handles interruzioni
- Emotion pipeline

STATI:
sealed class ConversationState {
    object Idle : ConversationState()
    object Listening : ConversationState()
    object Processing : ConversationState()
    data class Speaking(val emotion: String) : ConversationState()
    data class Error(val message: String) : ConversationState()
}

FLOW PRINCIPALE:
1. User attiva mic → stato Listening
2. STT produce testo → stato Processing
3. LLM genera risposta (streaming) 
   - Estrai emotion dal primo chunk
   - Imposta emotion su avatar
4. TTS converte in audio (streaming)
   - Avvia playback
   - Lip sync attivo
5. Fine audio → stato Idle

INTERRUZIONE:
- User parla durante Speaking → stop TTS, torna a Listening
- User preme stop → cancella tutto, torna a Idle

IMPLEMENTAZIONE:
@HiltViewModel
class ConversationOrchestrator @Inject constructor(
    private val sttEngine: STTEngine,
    private val llmClient: LLMClient,
    private val ttsEngine: TTSEngine,
    private val audioPlayer: AudioPlayer,
    private val lipSyncAnalyzer: LipSyncAnalyzer,
    private val avatarBridge: AvatarBridge
) : ViewModel() {

    val state: StateFlow<ConversationState>
    val messages: StateFlow<List<ChatMessage>>
    
    fun startConversation()
    fun stopConversation()
    fun sendTextMessage(text: String)
    private fun processResponse(response: Flow<StreamChunk>)
}

Usa SupervisorJob per gestire errori senza crashare tutto.
```

---

## 🎯 TRIGGER 11: Error Handling & Logging

```
Implementa sistema robusto di error handling:

FILES:
- utils/Result.kt
- utils/ErrorHandler.kt
- utils/Logger.kt
- ui/components/ErrorDialog.kt

RESULT WRAPPER:
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val error: AppError) : AppResult<Nothing>()
    object Loading : AppResult<Nothing>()
}

sealed class AppError {
    data class Network(val code: Int?, val message: String) : AppError()
    data class Api(val service: String, val message: String) : AppError()
    data class Audio(val type: AudioErrorType, val message: String) : AppError()
    data class Unity(val message: String) : AppError()
    data class Storage(val message: String) : AppError()
    object Unknown : AppError()
}

ERROR HANDLER:
object ErrorHandler {
    fun handle(error: AppError): UserMessage
    fun shouldRetry(error: AppError): Boolean
    fun getRecoveryAction(error: AppError): RecoveryAction?
}

LOGGER:
- Timber o custom logger
- Log levels: DEBUG, INFO, WARN, ERROR
- File logging per crash reports
- Sanitize sensitive data (API keys)

ERROR UI:
- Snackbar per errori transient
- Dialog per errori bloccanti
- Retry button dove appropriato
- Messaggi user-friendly

Integra con Firebase Crashlytics (opzionale).
```

---

## 🎯 TRIGGER 12: Testing Setup

```
Configura testing completo:

DIPENDENZE TEST (build.gradle.kts):
testImplementation("junit:junit:4.13.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("io.mockk:mockk:1.13.9")
testImplementation("app.cash.turbine:turbine:1.0.0")

androidTestImplementation("androidx.compose.ui:ui-test-junit4")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
androidTestImplementation("com.google.dagger:hilt-android-testing:2.50")

TEST FILES DA CREARE:

1. LLMClientTest.kt
   - Test streaming response parsing
   - Test error handling
   - Test conversation history

2. LipSyncAnalyzerTest.kt
   - Test RMS calculation
   - Test normalization
   - Test smoothing

3. ConversationOrchestratorTest.kt
   - Test state transitions
   - Test interruption handling
   - Test error recovery

4. ChatScreenTest.kt (UI test)
   - Test message display
   - Test input handling
   - Test voice button states

5. FakeImplementations.kt
   - FakeLLMClient
   - FakeTTSEngine
   - FakeSTTEngine

USA:
- Turbine per testing Flow
- MockK per mocking
- runTest per coroutines
- ComposeTestRule per UI

Crea anche script per test coverage report.
```

---

## 🎯 TRIGGER 13: Sistema Animazioni VRMA Completo

```
Implementa sistema completo per gestione animazioni VRMA di Amica:

FILES:
- animation/AnimationManager.kt
- animation/AnimationTriggerDetector.kt
- animation/AnimationQueue.kt
- ui/components/AnimationSelector.kt

ANIMATION MANAGER:
@Singleton
class AnimationManager @Inject constructor(
    private val avatarBridge: AvatarBridge
) {
    // Stato
    val currentAnimation: StateFlow<AnimationInfo>
    val isAnimating: StateFlow<Boolean>
    val animationQueue: StateFlow<List<String>>
    
    // Animazioni disponibili
    val animations = listOf(
        AnimationInfo("idle_loop", "Idle", true, null),
        AnimationInfo("greeting", "Saluto", false, 2.5f),
        AnimationInfo("dance", "Ballo", true, null),
        AnimationInfo("peaceSign", "Peace", false, 1.8f),
        AnimationInfo("shoot", "Bang!", false, 1.5f),
        AnimationInfo("spin", "Gira", false, 2.0f),
        AnimationInfo("squat", "Squat", false, 2.2f),
        AnimationInfo("showFullBody", "Full Body", false, 3.0f),
        AnimationInfo("modelPose", "Posa", false, 2.5f)
    )
    
    // Metodi
    suspend fun play(name: String)
    suspend fun playSequence(names: List<String>)
    fun queue(name: String)
    fun stop()
    fun returnToIdle()
    
    // Auto-return to idle dopo one-shot
    private suspend fun scheduleIdleReturn(duration: Float)
}

data class AnimationInfo(
    val name: String,
    val displayName: String,
    val isLoop: Boolean,
    val duration: Float?
)

ANIMATION TRIGGER DETECTOR:
class AnimationTriggerDetector {
    
    // Pattern per rilevare richieste animazione nel testo
    private val patterns = mapOf(
        "greeting" to listOf("ciao", "hello", "hi", "hey", "salve", "buongiorno"),
        "dance" to listOf("balla", "dance", "dancing", "ballare"),
        "spin" to listOf("gira", "spin", "ruota", "turn around", "giravolta"),
        "peaceSign" to listOf("peace", "vittoria", "victory", "v sign"),
        "shoot" to listOf("bang", "pew", "pistola", "shoot"),
        "squat" to listOf("squat", "inchino", "bow"),
        "showFullBody" to listOf("full body", "corpo intero", "mostrami tutto"),
        "modelPose" to listOf("pose", "posa", "modella", "model")
    )
    
    fun detectAnimation(text: String): String?
    fun detectFromLLMResponse(response: String): String?
    fun detectFromUserMessage(message: String): String?
}

ANIMATION QUEUE:
- Gestisce sequenze di animazioni
- Priority system (interrupt vs queue)
- Smooth transitions

ANIMATION SELECTOR UI:
@Composable
fun AnimationSelector(
    animations: List<AnimationInfo>,
    currentAnimation: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Grid o lista di bottoni per ogni animazione
    // Icone custom per ogni tipo
    // Feedback visivo per animazione attiva
    // Preview opzionale
}

ICONE SUGGERITE (Material Icons):
- idle_loop → "self_improvement" o "accessibility"
- greeting → "waving_hand"
- dance → "music_note" o "nightlife"
- peaceSign → "sign_language"
- shoot → "sports_esports"
- spin → "360" o "sync"
- squat → "fitness_center"
- showFullBody → "person" o "accessibility_new"
- modelPose → "photo_camera"

INTEGRAZIONE CON CONVERSAZIONE:
Nel ConversationOrchestrator, aggiungi:
1. Prima di rispondere: check user message per trigger
2. Durante risposta LLM: check per emotion + animation hints
3. Fine risposta: torna a idle se era one-shot

USA:
- Regex per pattern matching
- Coroutines per timing animazioni
- StateFlow per stato reattivo
```

---

## 📝 Note Finali per lo Sviluppo

### Ordine di Implementazione Consigliato

1. **Setup base** (Trigger 1)
2. **UI Chat** (Trigger 2)
3. **Settings** (Trigger 8)
4. **LLM Client** (Trigger 3)
5. **TTS** (Trigger 4)
6. **STT + VAD** (Trigger 5)
7. **Audio Player** (Trigger 6)
8. **Unity Integration + Animazioni** (Trigger 7)
9. **VRM Manager** (Trigger 9)
10. **Orchestrator** (Trigger 10)
11. **Error Handling** (Trigger 11)
12. **Testing** (Trigger 12)
13. **Sistema Animazioni Avanzato** (Trigger 13)

---

## 🎬 Guida Rapida Animazioni VRMA

### Tabella Riassuntiva Animazioni Amica

| Animazione | Trigger Keywords | Tipo | Uso Consigliato |
|------------|------------------|------|-----------------|
| `idle_loop` | - | Loop | Default, sempre attiva |
| `greeting` | ciao, hello, hi | One-shot | Inizio conversazione |
| `dance` | balla, dance | Loop | Su richiesta utente |
| `peaceSign` | peace, vittoria | One-shot | Risposta positiva |
| `shoot` | bang, pew | One-shot | Risposta scherzosa |
| `spin` | gira, turn | One-shot | Su richiesta |
| `squat` | squat, inchino | One-shot | Ringraziamento |
| `showFullBody` | full body | One-shot | Cambio vista |
| `modelPose` | pose, posa | One-shot | Su richiesta |

### Flow Animazioni Durante Conversazione

```
┌──────────────────────────────────────────────────────────────┐
│                    CONVERSATION FLOW                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  [User apre app] ──► idle_loop                               │
│         │                                                    │
│         ▼                                                    │
│  [User dice "Ciao"] ──► greeting ──► idle_loop               │
│         │                                                    │
│         ▼                                                    │
│  [Avatar risponde] ──► idle_loop + lip sync + emotion        │
│         │                                                    │
│         ▼                                                    │
│  [User dice "Balla!"] ──► dance (loop finché non stop)       │
│         │                                                    │
│         ▼                                                    │
│  [User dice "Stop"] ──► idle_loop                            │
│         │                                                    │
│         ▼                                                    │
│  [Risposta happy] ──► peaceSign ──► idle_loop                │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Checklist Pre-Build Unity (con Animazioni)

- [ ] UniVRM 0.127+ importato (supporto VRMA)
- [ ] AvatarController.cs compilato senza errori
- [ ] Cartella `Assets/StreamingAssets/Animations/` creata
- [ ] Tutti i 9 file .vrma copiati da Amica
- [ ] Scena configurata con AvatarController GameObject
- [ ] Build settings: Android, Export Project ✓
- [ ] Player settings: IL2CPP, ARM64
- [ ] Test in Editor con VRM 1.0 di prova
- [ ] Test ogni animazione manualmente in Editor

### Checklist Pre-Release Android

- [ ] Permessi manifest: INTERNET, RECORD_AUDIO, READ_EXTERNAL_STORAGE
- [ ] ProGuard rules per Unity classes
- [ ] API keys non hardcoded (usa BuildConfig o encrypted prefs)
- [ ] Test su device fisico ARM64
- [ ] Memory profiling con avatar caricato
- [ ] Battery usage check
- [ ] Test tutte le animazioni su device
- [ ] Test lip sync durante TTS
- [ ] Test transizioni animazione smooth

### Risorse Utili

- **UniVRM GitHub**: https://github.com/vrm-c/UniVRM
- **VRM Spec**: https://vrm.dev/en/
- **Amica GitHub**: https://github.com/semperai/amica
- **VRMA Docs**: https://vrm.dev/en/vrma/

---

**Buon sviluppo! 🚀**