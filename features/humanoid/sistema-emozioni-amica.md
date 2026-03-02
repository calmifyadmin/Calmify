# Sistema di Emozioni di Amica - Analisi Tecnica

## Panoramica

Il sistema di emozioni di Amica permette al personaggio virtuale VRM di esprimere 14 diverse emozioni attraverso espressioni facciali, creando un'esperienza di chatbot coinvolgente e realistica. Il sistema interpreta tag emozionali inseriti nel testo generato dal modello linguistico (LLM) e li traduce in espressioni facciali sul modello VRM.

## Emozioni Disponibili

Le 14 emozioni supportate sono definite in [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/messages.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/messages.ts#L27-L30):

```typescript
export const emotions =
["neutral", "happy", "angry", "sad", "relaxed", "Surprised",
"Shy", "Jealous", "Bored", "Serious", "Suspicious", "Victory",
"Sleep", "Love"]
```

## Flusso di Processo del Sistema

### 1. Generazione del Testo con Tag Emozionali

Il sistema utilizza un **system prompt** specifico che istruisce il modello linguistico a includere tag emozionali nel formato `[emozione]` nelle sue risposte. Questo è definito in [docs/overview/emotion-system.md](docs/overview/emotion-system.md#L18-L51).

**Esempio di output LLM:**
```
[happy] I just had the most amazing idea for our next adventure!
[angry] Why aren't you as excited as I am?
```

### 2. Streaming e Parsing della Risposta

Il processo avviene in [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/chat.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/chat.ts#L536-L636):

**a) Ricezione dello Stream**
- Il metodo `handleChatResponseStream()` riceve lo stream di risposta dal LLM
- Lo stream viene letto carattere per carattere (linee 562-620)

**b) Processamento Incrementale**
- La funzione `processResponse()` in [C:/Users/lifoe/Desktop/amica-master/amica-master/src/utils/processResponse.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/utils/processResponse.ts#L17-L106) elabora il testo in arrivo
- Estrae i tag emozionali usando regex: `/^\[(.*?)\]/` (linea 47)
- Divide il testo in frasi utilizzando pattern di punteggiatura (linee 61-69)

### 3. Conversione in Screenplay

La funzione `textsToScreenplay()` in [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/messages.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/messages.ts#L54-L88) converte il testo con tag in oggetti strutturati:

**Processo:**
1. **Estrazione del Tag** (linea 62): usa regex `text.match(/\[(.*?)\]/)`
2. **Normalizzazione** (linee 69-75): converte il tag usando `userInputToSystem()` per gestire maiuscole/minuscole
3. **Validazione** (linee 71-75): verifica che il tag sia nell'elenco delle emozioni valide
4. **Creazione Screenplay** (linee 77-84): crea un oggetto con:
   - `expression`: l'emozione da mostrare
   - `talk`: informazioni per la sintesi vocale (TTS)
   - `text`: il testo originale

**Codice chiave:**
```typescript
if (emotions.includes(systemTag as any)) {
  console.log("Emotion detect :", systemTag);
  expression = systemTag;
  prevExpression = systemTag;
}
```

### 4. Enqueuing per TTS e Riproduzione

In [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/chat.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/chat.ts#L593-L596):
- Lo screenplay viene inserito nella coda `ttsJobs`
- La coda viene processata da `processTtsJobs()` (linee 204-226)
- Viene generato l'audio tramite TTS
- L'audio viene inserito nella coda `speakJobs`

### 5. Riproduzione con Espressione Facciale

Il metodo `processSpeakJobs()` in [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/chat.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/chat.ts#L228-L260) gestisce la riproduzione:

**a) Applicazione dell'Espressione** (linea 253):
```typescript
await this.viewer!.model?.speak(speak.audioBuffer, speak.screenplay);
```

**b) Il metodo `speak()` in [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/vrmViewer/model.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/vrmViewer/model.ts#L416-L423):**
```typescript
public async speak(buffer: ArrayBuffer, screenplay: Screenplay) {
  this.emoteController?.playEmotion(screenplay.expression);
  await new Promise((resolve) => {
    this._lipSync?.playFromArrayBuffer(buffer, () => {
      resolve(true);
    });
  });
}
```

### 6. Controllo delle Espressioni VRM

La gerarchia di controllo delle espressioni:

**a) EmoteController** ([C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/emoteController.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/emoteController.ts)):
- Classe wrapper che delega al `ExpressionController`
- Metodo `playEmotion()` (linee 16-18)

**b) ExpressionController** ([C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/expressionController.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/expressionController.ts)):
- Gestisce le espressioni del modello VRM
- `playEmotion()` (linee 55-79):

**Processo dettagliato:**
1. **Normalizzazione** (linea 56): converte prima lettera in maiuscolo
2. **Controllo duplicati** (linee 58-60): evita di riapplicare la stessa emozione
3. **Reset emozione precedente** (linee 62-64): riporta l'emozione corrente a 0
4. **Gestione neutral** (linee 66-70): riabilita il blinking automatico
5. **Calcolo valore** (linea 72):
   - Emozioni preset: valore 1.0 (Surprised: 0.5)
   - Emozioni custom: valore 0.5
6. **Disabilitazione blinking** (linea 74): ferma il lampeggiamento automatico
7. **Applicazione espressione** (linee 76-78): imposta il valore del blendshape VRM

**Codice chiave:**
```typescript
public playEmotion(preset: VRMExpressionPresetName | string) {
  const normalizedPreset = `${preset.charAt(0).toUpperCase()}${preset.slice(1)}`;

  if (this._currentEmotion != "neutral") {
    this._expressionManager?.setValue(this._currentEmotion, 0);
  }

  const value = (normalizedPreset in VRMExpressionPresetName)
    ? (normalizedPreset === "Surprised" ? 0.5 : 1)
    : 0.5;

  this._expressionManager?.setValue(preset, value);
}
```

### 7. Applicazione ai Blendshapes VRM

Il `VRMExpressionManager` della libreria `@pixiv/three-vrm`:
- Mappa le emozioni ai blendshapes del modello VRM
- Applica i valori ai morph targets del mesh 3D
- I blendshapes devono essere configurati correttamente nel file VRM

## File Coinvolti

### File Principali

| File | Funzione | Linee Chiave |
|------|----------|--------------|
| [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/messages.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/messages.ts) | Definizione emozioni e conversione screenplay | 27-30, 54-88 |
| [C:/Users/lifoe/Desktop/amica-master/amica-master/src/utils/processResponse.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/utils/processResponse.ts) | Parsing tag emozionali dallo stream | 46-51, 90-94 |
| [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/chat.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/chat.ts) | Gestione stream, TTS e riproduzione | 536-636, 204-260 |
| [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/vrmViewer/model.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/vrmViewer/model.ts) | Interfaccia modello VRM | 416-423 |
| [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/emoteController.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/emoteController.ts) | Controller principale emozioni | 9-27 |
| [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/expressionController.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/expressionController.ts) | Gestione espressioni VRM | 55-104 |

### File di Supporto

| File | Funzione |
|------|----------|
| [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/autoBlink.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/autoBlink.ts) | Gestione lampeggiamento automatico |
| [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/autoLookAt.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/emoteController/autoLookAt.ts) | Gestione direzione dello sguardo |
| [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/lipSync/lipSync.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/lipSync/lipSync.ts) | Sincronizzazione labiale |

## Sistema Subconcious (Emozioni Automatiche)

Il file [C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/amicaLife/eventHandler.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/amicaLife/eventHandler.ts) gestisce eventi automatici che possono generare emozioni:

### Eventi Idle

**handleSubconsciousEvent** (linee 132-231):
- Analizza la conversazione passata
- Genera pensieri subconsci usando LLM
- Decide un'emozione appropriata (linee 175-181)
- Applica l'emozione al personaggio

**Processo in 4 step:**
1. **Step 1**: Genera diario mentale dalla conversazione
2. **Step 2**: Descrive le emozioni dal diario in terza persona
3. **Step 3**: Crea dialogo con tag emozionali appropriati
4. **Step 4**: Comprime e salva nella memoria

**handleSleepEvent** (linee 113-128):
- Applica l'emozione "Sleep" quando il personaggio dorme
- Usa direttamente `playEmotion("Sleep")` (linea 121)

## Diagramma di Flusso

```
LLM Response Stream
       ↓
[Tag Extraction] (processResponse.ts)
  ↓            ↓
[Tag]      [Text]
  ↓            ↓
[textsToScreenplay] (messages.ts)
       ↓
[Screenplay Object]
  {expression, talk, text}
       ↓
[TTS Queue] → [Audio Generation]
       ↓
[Speak Queue]
       ↓
[Model.speak()] (model.ts)
       ↓
[EmoteController.playEmotion()] (emoteController.ts)
       ↓
[ExpressionController.playEmotion()] (expressionController.ts)
       ↓
[VRMExpressionManager.setValue()]
       ↓
[VRM Blendshapes / Morph Targets]
       ↓
[Visual Expression on 3D Model]
```

## Caratteristiche Tecniche Importanti

### 1. Gestione delle Maiuscole
Il sistema usa `userInputToSystem()` in [messages.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/messages.ts#L33-L41) per normalizzare:
- `[suspicious]` → `[Suspicious]`
- `[sleep]` → `[Sleep]`
- `[shy]` → `[Shy]`

### 2. Persistenza delle Emozioni
- L'emozione persiste tra le frasi se non specificato un nuovo tag
- Variabile `prevExpression` mantiene l'ultima emozione (messages.ts:58, 74)

### 3. Sistema di Blinking Automatico
- Disabilitato durante le emozioni attive
- Riabilitato solo per `neutral` (expressionController.ts:67)

### 4. Mapping TTS
La funzione `emotionToTalkStyle()` in [messages.ts](C:/Users/lifoe/Desktop/amica-master/amica-master/src/features/chat/messages.ts#L90-L101) mappa alcune emozioni a stili vocali TTS:
- `angry` → stile vocale arrabbiato
- `happy` → stile vocale felice
- `sad` → stile vocale triste
- Altri → stile normale

### 5. Valori di Intensità
- Emozioni standard: 1.0 (100%)
- Surprised: 0.5 (50%)
- Emozioni custom: 0.5 (50%)

## Creazione di Modelli VRM Personalizzati

Per creare un modello VRM compatibile con Amica:

1. **Creare Blendshapes** per ogni emozione:
   - Usare software 3D (Blender, VRoid Studio, etc.)
   - Creare 14 blendshapes corrispondenti ai nomi delle emozioni

2. **Configurare il file VRM**:
   - Esportare il modello in formato VRM
   - Assicurarsi che i nomi dei blendshapes corrispondano esattamente alle emozioni

3. **Testare nel sistema**:
   - Caricare il modello in Amica
   - Verificare che tutte le espressioni funzionino correttamente

## Espansione Futura

Secondo [emotion-system.md](docs/overview/emotion-system.md#L78-L81), il sistema sarà espanso per lavorare con **sub-routine subconscie**, probabilmente migliorando il sistema già presente in `eventHandler.ts`.

## Conclusione

Il sistema di emozioni di Amica è un'architettura multi-layer sofisticata che:
1. Istruisce il LLM a generare tag emozionali
2. Estrae e valida i tag in tempo reale dallo stream
3. Converte i tag in strutture dati screenplay
4. Sincronizza le espressioni facciali VRM con la sintesi vocale
5. Gestisce automaticamente transizioni e stati delle espressioni

Questo approccio crea un'esperienza conversazionale naturale e emotivamente espressiva che va oltre i chatbot tradizionali basati solo su testo.
