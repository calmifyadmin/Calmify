# Full-Duplex Voice Chat Implementation

## Panoramica

Implementazione completa di un sistema vocale full-duplex "tipo app Gemini" per chat in tempo reale con l'API Gemini Live.

## Architettura

### 1. Connessione WebSocket (GeminiLiveWebSocketClient)
- **Endpoint**: `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent`
- **Modalità**: `responseModalities = ["AUDIO"]`
- **VAD Server**: Automatic Activity Detection abilitato
- **Lingua**: Italiano (it-IT) con voce Aoede

### 2. Audio Manager (GeminiLiveAudioManager)

#### Configurazione Microfono
- **Sorgente**: `VOICE_COMMUNICATION`
- **Formato**: 16 kHz, mono, PCM 16-bit
- **Chunk size**: 320 campioni (20ms)
- **Effetti abilitati**: AEC, Noise Suppression, AGC

#### Configurazione Playback
- **Formato**: 24 kHz, mono, PCM 16-bit
- **Attributi**: `USAGE_VOICE_COMMUNICATION` per migliorare AEC
- **Buffer**: 4x minimo per playback fluido

#### Sistema Audio Android
- **Modalità**: `MODE_IN_COMMUNICATION`
- **Routing**: Speaker di default
- **Bluetooth**: SCO preferito su A2DP (Android 12+)

### 3. Half-Duplex Base
- Audio utente bloccato quando AI parla (`aiSpeaking = true`)
- `audioStreamEnd` inviato automaticamente all'inizio TTS
- Reset automatico alla fine della riproduzione

### 4. Smart Barge-In
- **Soglia**: 0.08 di livello audio normalizzato
- **Trigger**: 4 frame consecutivi "caldi" (~80ms)
- **Azione**: Stop immediato TTS + ripresa audio utente
- **Feedback**: UI aggiornata istantaneamente

## Flusso Operativo

### Connessione
1. Configure audio routing e effetti
2. Connessione WebSocket con setup VAD
3. Avvio registrazione microfono always-on
4. Streaming chunk 20ms verso server

### Durante Conversazione
1. **Utente parla**: Audio chunks → server
2. **AI risponde**: 
   - `audioStreamEnd` → server
   - TTS playback locale
   - Microfono gated (no invio)
3. **Barge-in**:
   - Detection locale su livello audio
   - Stop immediato TTS
   - Ripresa invio microfono
   - Server gestisce interruzione

### Protezioni
- Queue overflow protection (50 chunks / 500KB max)
- Thread-safe audio operations
- Automatic AudioTrack re-initialization
- Error recovery e cleanup completo

## Benefici Implementazione

✅ **Latenza ridotta**: Chunk 20ms, no commit custom
✅ **Interruzioni immediate**: Barge-in locale + server VAD  
✅ **Audio pulito**: AEC/NS/AGC + attributi comunicazione
✅ **Routing intelligente**: SCO Bluetooth, speaker ottimizzato
✅ **Stabilità**: Overflow protection, error recovery
✅ **UX "app Gemini"**: Feedback visivo, turni chiari

## File Modificati

1. **GeminiLiveAudioManager.kt**
   - Configurazione audio full-duplex
   - Effetti audio (AEC/NS/AGC)
   - Smart barge-in detection
   - Bluetooth SCO support

2. **LiveChatViewModel.kt**  
   - Half-duplex gating logic
   - Smart barge-in handling
   - Audio state synchronization

3. **GeminiLiveWebSocketClient.kt** (già configurato)
   - Live API endpoint corretto
   - VAD server configuration
   - Italian TTS settings

## Testing

Per testare il sistema:
1. Avviare conversazione vocale
2. Verificare che l'audio utente si fermi durante TTS
3. Testare barge-in parlando durante risposta AI
4. Controllare che interruzione sia immediata
5. Verificare routing audio (speaker/bluetooth)