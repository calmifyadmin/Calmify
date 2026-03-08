# Calmify — Feature Module Strategic Analysis

**Target User**: Self-improver, 25-40 anni. Gia' fa journaling, meditazione, legge libri di crescita personale. Vuole un sistema integrato per tracciare il suo percorso interiore.

**Product Thesis**: *"Calmify e' l'unico sistema che trasforma tutto cio' che scrivi, dici e senti in consapevolezza reale — non statistiche, ma comprensione."*

**Core Loop**: REGISTRA -> COMPRENDI -> CRESCI -> CONDIVIDI

---

## 0. NAVIGATION ARCHITECTURE — "Social come Layer"

### Decisione Strategica

Il social **non merita una tab dedicata** nella bottom nav. Il self-improver apre Calmify per se stesso, non per scrollare un feed. Il social e' un condimento, non il piatto principale.

### Navigazione Attuale (da superare)
```
Bottom Nav:  [Home]  [Feed]  [Chat]  [Profile]
                       ↑
                 Porta d'accesso a 6 moduli social
                 (feed, composer, search, notifications,
                  messaging, social-profile, thread-detail)
```

Problema: due app in una. Cliccando "Feed" l'utente entra in un mondo completamente diverso. Cambiano regole, contesto, interfaccia. Come passare da un diario privato a Twitter.

### Navigazione Target: "Social come Layer"
```
Bottom Nav:  [Home]  [Journal]  [AI Chat]  [Il Mio Percorso]
```

Il social **non scompare** — si integra nel flusso personale:

| Elemento Social | Dove Vive Ora | Dove Dovrebbe Vivere |
|-----------------|---------------|----------------------|
| **Feed** | Tab dedicata (bottom nav) | Sezione "Dalla community" nella Home (2-3 thread curati) + accesso completo dal drawer o dalla sezione community |
| **Composer** | Dentro il feed | Primariamente dal Journal ("Vuoi condividere?") + FAB globale |
| **Notifiche** | Dentro il feed (top bar) | Icona nella top bar GLOBALE dell'app (come Instagram) |
| **Search** | Dentro il feed (top bar) | Nella top bar della sezione community + ricerca globale (diari + thread + utenti) |
| **Messaging** | Dentro il feed | Accessibile dal profilo/notifiche o dal drawer |
| **Social Profile** | Click su utente nel feed | Invariato — navigazione da qualsiasi avatar/nome utente |
| **Thread Detail** | Click su thread nel feed | Invariato — navigazione da qualsiasi thread card |

### Come Funziona in Pratica

**Home Screen (rivisitata):**
```
┌─────────────────────────────┐
│ 🔔 Calmify           🔍 ⚙️  │  ← notifiche nella top bar globale
├─────────────────────────────┤
│                             │
│   Today's Reflection (AI)   │  ← card personale
│                             │
├─────────────────────────────┤
│  [✏️ Scrivi] [🎙️ Parla]     │  ← quick actions
├─────────────────────────────┤
│  Il tuo percorso  ───────>  │  ← mini-trend (tap = percorso completo)
│  ▁▂▃▅▆▅▃▄▅▇               │
├─────────────────────────────┤
│  Dalla community            │  ← 2-3 thread rilevanti
│  ┌─────────────────────┐   │
│  │ @Sara: "Ho scoperto  │   │
│  │ che meditare prima   │   │
│  │ di dormire..."       │   │
│  └─────────────────────┘   │
│  Vedi tutto →               │  ← apre feed completo
└─────────────────────────────┘
│ [Home] [Journal] [Chat] [Percorso] │  ← bottom nav, niente "Feed"
```

**Flusso "Condividi dal Journal":**
```
Scrivi diario → Salva → AI analizza →
  "Bella riflessione. Vuoi condividerla con la community?"
  [Si, condividi]  [No, resta privata]
       ↓
  Composer pre-compilato (testo editabile, mood auto-taggato)
       ↓
  Thread pubblicato nel feed
```

**Flusso "Esplora la Community":**
```
Home → "Dalla community" → "Vedi tutto" → Feed completo
  oppure
Drawer menu → "Community"
  oppure
Search globale → risultati thread + utenti
```

### Impatto sui Moduli

| Modulo | Azione Richiesta |
|--------|-----------------|
| **DecomposeApp.kt** | Rimuovere Feed dalla bottom nav. Aggiungere Journal e Percorso. |
| **RootComponent.kt** | Aggiungere navigazione a Community come child (non tab). Spostare notifiche nella top bar globale. |
| **RootDestination.kt** | Aggiungere `Community` come destination non-tab. Rinominare/riorganizzare le tab destinations. |
| **Home** | Aggiungere sezione "Dalla community" con 2-3 thread curati. Aggiungere icona notifiche nella top bar. |
| **Feed** | Resta identico come schermata — cambia solo come ci si arriva. |
| **Composer** | Aggiungere entry point da Journal (pre-compilato). |
| **Write** | Aggiungere CTA "Condividi" post-salvataggio. |
| **Notifications** | Estrarre il counter badge e renderlo disponibile globalmente (top bar). |

### Perche' Opzione A e Non le Altre

- **Opzione B** (tab "Community"): meglio di "Feed" come nome, ma comunque ruba una tab al percorso personale. Il self-improver usa il social 1-2 volte a settimana, non ogni sessione.
- **Opzione C** (fondere tutto): mischiare post pubblici e diari privati nella stessa timeline crea confusione e ansia. Il self-improver vuole uno spazio sicuro per scrivere senza pensare a chi legge.
- **Opzione A** (layer): il social c'e', e' raggiungibile, ma non compete per attenzione. L'app resta "il tuo spazio", con la community come estensione naturale.

---

## 1. AUTH — Login & Onboarding

### Stato Attuale
- Google Sign-In via Credential Manager (moderno, corretto)
- UI minimalista: logo Google + "Welcome back" + bottone
- MVI pulito, UseCase definiti ma non usati dal VM
- Solo Google come metodo auth

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Zero personalita' | La prima schermata dell'app dice "Welcome back" con un logo Google. Nessuna emozione, nessun brand. Il self-improver non si sente accolto. | CRITICO |
| Solo Google Auth | Esclude chi non ha Google o preferisce email/Apple | MEDIO |
| UseCase non usati | Il VM duplica la logica invece di delegare a `SignInWithGoogleUseCase` | BASSO |
| Nessun analytics su auth | Non sai quanti utenti falliscono il login | BASSO |

### Cosa Dovrebbe Essere
La prima schermata non e' un login — e' una **promessa**. Il self-improver deve sentire:
*"Questa app mi capisce gia'."*

### Improvement Plan

**P0 — Ridisegnare la Welcome Screen**
- Rimuovere il logo Google dal centro. Nessuno si emoziona vedendo il logo di Google.
- Hero: una frase potente. Non "Welcome back". Qualcosa come:
  - *"Conosci te stesso, un giorno alla volta"*
  - *"Il tuo specchio interiore"*
- Sottotitolo: *"Scrivi, parla, scopri chi sei davvero"*
- Visuale: l'avatar 3D (il differenziatore!) che saluta. Anche statico, fa gia' effetto.
- Bottone Google in basso, piccolo, funzionale. Non e' il protagonista.

**P1 — Aggiungere Apple Sign-In** (per futura espansione iOS KMP)

**P2 — Collegare UseCase al VM** (cleanup tecnico)

---

## 2. ONBOARDING — Prima Configurazione

### Stato Attuale
- 5 step wizard (info personali, salute mentale, lifestyle, obiettivi, riepilogo)
- Animazioni Lottie, slider custom per sonno, chip multi-select
- Calendar picker, validazione per step
- GDPR compliance nel riepilogo
- Buona qualita' tecnica complessiva

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Troppo lungo e clinico | 5 step con domande sulla salute mentale. Il self-improver non e' un paziente. Si sente interrogato. | CRITICO |
| Chiede troppo subito | Farmaci, terapia, storia clinica — al primo avvio. Trust non ancora costruita. | ALTO |
| Non spiega il "perche'" | L'utente non capisce a cosa servono queste info | MEDIO |
| Exit dialog non implementato | "TODO: Navigate back" — l'utente resta bloccato | MEDIO |

### Cosa Dovrebbe Essere
L'onboarding non e' un questionario medico — e' il momento in cui l'utente dice *"ok, questa app e' diversa"*.

### Improvement Plan

**P0 — Ridurre a 3 step, conversazionali**
1. **"Come ti chiami?"** + data di nascita (solo questo, leggero)
2. **"Cosa ti porta qui?"** — 4-5 card visive (non chip clinici):
   - "Voglio capirmi meglio" / "Sto attraversando un periodo difficile" / "Voglio crescere" / "Sono curioso"
   - Questo informa l'AI sulla modalita' di interazione
3. **"Come preferisci riflettere?"** — Scrivo / Parlo / Entrambi
   - Questo configura l'UX (journal-first vs chat-first)

**P1 — Spostare le domande cliniche DOPO**
- Le info su salute mentale, farmaci, terapia → raccoglierle gradualmente, nel tempo, quando l'utente ha costruito fiducia
- L'AI puo' chiedere *"Ti va di dirmi qualcosa in piu' su di te?"* dopo 1 settimana di uso

**P2 — Aggiungere un "welcome moment"**
- Dopo l'onboarding, l'avatar 3D saluta per nome: *"Ciao Marco. Sono qui per te."*
- Questo momento crea connessione emotiva immediata

---

## 3. HOME — Dashboard Principale

### Stato Attuale
- Hero con saluto time-based + pulse score (0-10)
- Quick actions: "Parla con Eve", "Snapshot", "Write"
- DailyInsightsChart (vertical pills settimanali)
- MoodDistributionCard (donut chart)
- CognitivePatternsCard (pattern CBT)
- TopicsCloudCard (word cloud)
- AchievementsRow (streak, entries, goal)
- Legacy diary list sotto

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Information overload | 7+ sezioni diverse. L'utente non sa dove guardare. | CRITICO |
| Metriche senza significato | "Pulse score 6.2" — e quindi? Cosa faccio? | CRITICO |
| Donut chart + pills + word cloud + patterns | Sembra un dashboard di analytics, non un'app di crescita personale | ALTO |
| Legacy diary list incoerente | Mix di vecchio e nuovo design | MEDIO |
| 30+ println() nel ViewModel | Debug logging in produzione | BASSO |
| Codice unifiedContent inutilizzato | Dead code che confonde | BASSO |

### Cosa Dovrebbe Essere
La home non e' un dashboard — e' una **conversazione quotidiana** con te stesso.

Il self-improver apre l'app e vede UNA cosa che conta oggi, non 7 grafici.

### Improvement Plan

**P0 — "Today's Reflection" come hero unico**
Sostituire TUTTI i grafici con UNA card AI-generated:

> *"Buongiorno Marco. Ieri hai scritto della tensione al lavoro — e' il terzo lunedi' consecutivo. Ma mercoledi' hai avuto un momento di chiarezza quando hai parlato del viaggio. Il tuo pattern: lo stress si accumula a inizio settimana e si scioglie quando ti dai spazio creativo. Oggi: prova a prenderti 10 minuti prima di pranzo."*

Questo e' il valore. Non un donut chart.

**P1 — Semplificare a 3 sezioni**
1. **Today's Reflection** (AI card, sopra)
2. **Quick Actions** (Scrivi / Parla / Esplora)
3. **Il tuo percorso** — UNA metrica visiva semplice (non 5 grafici):
   - Una linea continua che mostra il trend delle ultime 4 settimane
   - Colore che sfuma da rosso a verde (stress -> calma)
   - Tap per espandere dettagli

**P2 — Rimuovere CognitivePatternsCard e TopicsCloudCard dalla home**
- Spostare in una sezione "Deep Insights" accessibile dal profilo
- La home deve essere SEMPLICE

**P3 — Cleanup tecnico**
- Rimuovere println(), codice unifiedContent morto
- Centralizzare `Task<T>.await()` in core/util

---

## 4. WRITE — Journaling

### Stato Attuale
- Editor con titolo + descrizione + mood pager (6 mood)
- Psychological Metrics Wizard (7 step: emotion intensity, stress, energy, calm/anxiety, trigger, body sensation, completion)
- Gallery con upload Firebase (max 6 immagini, progress tracking, resumable)
- Date/time picker, delete con conferma
- Animazioni sofisticate nel wizard (forme geometriche, termometro, batteria, onde)

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Il wizard dei metriche e' troppo lungo | 7 step OGNI volta che scrivi. Il self-improver scrive 1-2 volte al giorno — dopo 3 giorni salta il wizard. | CRITICO |
| "Tell me about it" come placeholder | Freddo, generico. Non ispira a scrivere. | ALTO |
| Mood pager con 6 opzioni | Troppo poche e troppo binarie. Le emozioni sono sfumate. | MEDIO |
| Metriche di default a 5 | Se l'utente non tocca gli slider, tutti i dati sono 5/10. Rende le analytics inutili. | MEDIO |
| Nessun prompt AI | L'utente vede una pagina bianca. Il self-improver vuole una guida. | ALTO |

### Cosa Dovrebbe Essere
Il journaling non e' compilare un form — e' un **dialogo con se stessi**.

### Improvement Plan

**P0 — Eliminare il wizard obbligatorio, sostituire con "Smart Capture"**
Dopo che l'utente scrive, l'AI analizza il testo e INFERISCE automaticamente:
- Mood (dal sentiment del testo)
- Stress level (dalle parole usate)
- Energy (dal tono)
- Trigger (dai temi menzionati)

L'utente vede un mini-riepilogo: *"Sembra una giornata intensa. Ho colto stress medio e un trigger legato al lavoro. Corretto?"*
- Se si': salva
- Se no: mostra 2-3 slider per correggere (NON 7 step)

Questo e' 10x piu' veloce e 10x piu' accurato.

**P1 — Prompt giornaliero intelligente**
Invece di "Tell me about it", mostrare un prompt contestuale:
- Lunedi' mattina: *"Come ti senti all'inizio di questa settimana?"*
- Dopo 3 giorni senza scrivere: *"E' passato un po'. Cosa e' successo?"*
- Dopo un diario stressante: *"Ieri era pesante. Come stai oggi?"*
- Random: *"Qual e' stata la cosa piu' vera che hai pensato oggi?"*

**P2 — Espandere il mood a un modello dimensionale**
Invece di 6 mood discreti, usare 2 assi:
- **Valenza** (negativo ← → positivo)
- **Energia** (bassa ← → alta)

Questo cattura: Felice (positivo+alta), Sereno (positivo+bassa), Ansioso (negativo+alta), Triste (negativo+bassa) — e tutto lo spettro intermedio.

**P3 — Mantenere il wizard come OPZIONALE**
Chi vuole il deep-dive puo' aprirlo. Ma non deve essere il default.

---

## 5. CHAT — Conversazione AI

### Stato Attuale
- **Text Chat**: messaggi con Gemini API, streaming, TTS per messaggio, SpeechRecognizer
- **Live Chat**: full-duplex WebSocket con Gemini Live, VAD (Silero), AEC hardware, GLSL liquid visualizer
- Audio pipeline professionale: Oboe NDK, AudioQualityAnalyzer, ConversationContextManager
- Integrazione avatar (lip-sync slot)
- Camera preview opzionale

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| L'AI non ha personalita' | Parla come ChatGPT generico. Non conosce l'utente. | CRITICO |
| Nessun contesto dal journaling | L'AI non sa cosa l'utente ha scritto nel diario. E' scollegata. | CRITICO |
| Text chat e Live chat sono schermi separati | L'utente deve navigare per switchare. Dovrebbe essere un toggle fluido. | MEDIO |
| Nessuna gestione sessioni visibile | Le conversazioni si perdono. Non c'e' UI per storico. | MEDIO |
| Camera integration incompleta | Framework presente ma non wired | BASSO |
| Tool calling non renderizzato | Framework c'e' ma nessun UI per risultati | BASSO |

### Cosa Dovrebbe Essere
L'AI non e' un chatbot — e' **"la persona che ti conosce meglio"**.

Il differenziatore MASSIMO di Calmify: l'AI ha letto tutti i tuoi diari, conosce i tuoi pattern, sa quando sei stressato e perche'.

### Improvement Plan

**P0 — Context Engine: l'AI che ti conosce**
Prima di ogni conversazione, iniettare nel system prompt di Gemini:
- Ultimi 5 diari (riassunti)
- Pattern ricorrenti (dal profilo psicologico)
- Mood attuale (dall'ultimo check-in)
- Nome dell'utente, obiettivi (dall'onboarding)

Esempio di interazione risultante:
> Utente: "Oggi mi sento strano"
> AI: "Strano come martedi' scorso quando hai scritto che ti sentivi 'svuotato' dopo la riunione? O strano in modo diverso?"

QUESTO e' il valore. L'AI ricorda. L'AI connette.

**P1 — Dare una personalita' all'AI**
- L'AI ha un nome (Eve? O personalizzabile)
- Tono: caldo ma non sdolcinato, diretto ma non giudicante
- Esempio: *"Ti ascolto. Ma dimmi — questa sensazione la riconosci? L'hai gia' descritta 2 settimane fa."*

**P2 — Unificare text e voice in un unico flusso**
- Singola schermata con toggle text/voice
- La conversazione e' la stessa, il mezzo cambia
- L'avatar appare in modalita' voice (il differenziatore)

**P3 — Session management UI**
- Lista conversazioni passate accessibile
- Ogni conversazione ha un "tema" auto-generato dall'AI
- L'utente puo' riaprire e continuare

---

## 6. HUMANOID — Avatar 3D

### Stato Attuale
- Filament 1.68.2 con modelli VRM + animazioni VRMA
- Blend shape controller con priorita' (lip-sync > blink > emotion > idle)
- Blink realistico (15-20/min, double blink 15%, half blink 10%)
- Lip-sync text-based con coarticolazione + audio-driven sync
- 20+ animazioni (idle, gesture, emotion, action)
- Gesture + emotion via UI buttons
- Performance ~10-15% CPU, ~60-100MB RAM

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| L'avatar vive isolato | E' in un modulo separato, non integrato nell'esperienza. Devi navigare per vederlo. | CRITICO |
| UI di debug esposta | Bottoni emozioni, slider lip-sync, debug panel — sono tool di sviluppo, non UX | ALTO |
| Nessuna reazione autonoma | L'avatar non reagisce a cio' che dice l'utente. E' una marionetta. | ALTO |
| Spring bones non animati | Capelli/vestiti statici | BASSO |
| Single model only | Un solo avatar, non personalizzabile | MEDIO |

### Cosa Dovrebbe Essere
L'avatar non e' un demo 3D — e' **il volto della tua AI**.

E' la cosa che nessun competitor ha. Headspace non ce l'ha. Daylio non ce l'ha. ChatGPT non ce l'ha.

### Improvement Plan

**P0 — Integrare l'avatar nella chat voice**
Quando l'utente parla con l'AI in modalita' voice, l'avatar e' li'.
- Parla (lip-sync sincronizzato con l'audio dell'AI)
- Reagisce (sorride quando l'utente dice qualcosa di positivo, inclina la testa quando ascolta)
- Gesticola (annuisce, alza le sopracciglia)

Non deve essere perfetto. Deve essere **presente**.

**P1 — Emotion mapping automatico dal sentiment**
Collegare il sentiment analyzer al blend shape controller:
- L'utente scrive qualcosa di triste → avatar mostra empatia
- L'utente ride → avatar sorride
- Pausa lunga → avatar inclina la testa (curiosita')

**P2 — Rimuovere l'UI di debug**
- Eliminare i bottoni emozione, slider lip-sync, debug panel dalla release
- L'avatar non ha bisogno di controlli manuali — reagisce al contesto

**P3 — Avatar nella home (micro)**
- Un piccolo avatar (100dp) nella home che saluta e mostra l'emozione del giorno
- Non interattivo — solo presenza. Come un compagno che ti aspetta.

---

## 7. PROFILE — Profilo Psicologico

### Stato Attuale
- Dual-pill chart (mood viola + stress cyan invertito) per settimana
- Resilience card (progress bar 0-100%)
- Trend indicator (miglioramento/stabile/calo)
- Data quality footer (diari, affidabilita')
- 350 righe di ExpressiveLineChart mai usato (dead code)
- Navigazione settimane TODO (bottoni non funzionanti)
- Vico dependency dichiarata ma non usata

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Il grafico a pillole e' incomprensibile | Due barre per giorno, stress invertito, nessuna spiegazione. L'utente non sa cosa sta guardando. | CRITICO |
| Non racconta una storia | Mostra NUMERI, non SIGNIFICATO. Il self-improver vuole capire, non analizzare grafici. | CRITICO |
| Bottoni navigazione settimane TODO | L'utente non puo' scorrere la storia | ALTO |
| 350 righe di dead code | ExpressiveLineChart definito ma mai renderizzato | MEDIO |
| Dipendenza Vico inutilizzata | Peso extra nel build | BASSO |
| "15k / Obiettivo" hardcoded | Placeholder mai sostituito | MEDIO |

### Cosa Dovrebbe Essere
Non un "profilo psicologico" (clinico, freddo) ma un **"diario di crescita"** (personale, caldo).

### Improvement Plan

**P0 — Sostituire i grafici con "Weekly Reflection" AI**
Una card narrativa generata dall'AI:

> *"Settimana 10, 2026. Hai scritto 5 volte — il tuo ritmo migliore. Il tema dominante e' stato 'cambiamento': stai elaborando la decisione di cambiare lavoro. Lunedi' e martedi' erano tesi (stress 7/10), ma da mercoledi' c'e' stato un rilascio. La frase piu' significativa che hai scritto: 'Forse non devo avere tutto chiaro per iniziare'. La tua resilienza e' al 72% — in crescita da 3 settimane."*

QUESTO e' un profilo psicologico. Non due barre colorate.

**P1 — Una sola visualizzazione: Journey Line**
Una curva continua (non barre discrete) che mostra il benessere nel tempo.
- Colore gradiente (rosso → giallo → verde)
- Punti notevoli marcati con icone (picco stress, breakthrough, streak)
- Tap su un punto → mostra il diario di quel giorno
- Swipe orizzontale per navigare le settimane

**P2 — Rinominare la sezione**
Da "Il Tuo Profilo Psicologico" a "Il Tuo Percorso" o "La Tua Evoluzione"

**P3 — Cleanup tecnico**
- Rimuovere ExpressiveLineChart (350 righe morte)
- Rimuovere dipendenza Vico
- Implementare navigazione settimane o rimuovere i bottoni
- Rimuovere placeholder "15k"

---

## 8. HISTORY — Storico

### Stato Attuale
- Hub con 4 chat recenti + 4 diari recenti
- Full screen per chat history e diary history (separate)
- DockedSearchBar Material3
- Date formatting (Today/Yesterday/relativo)

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Separazione chat/diary innaturale | Per il self-improver, il percorso e' UNO. Non importa se ha scritto o parlato. | ALTO |
| Nessuna ricerca semantica | Posso cercare "lavoro" ma non "quella volta che ero stressato per il progetto" | MEDIO |
| Nessun delete/archive | Non posso gestire i miei dati | MEDIO |

### Improvement Plan

**P0 — Timeline unificata**
Un unico stream cronologico che mostra:
- Diari (icona penna)
- Conversazioni AI (icona chat)
- Mood check-in (icona cuore)
- Insight settimanali (icona lampadina)

L'utente scorre la sua storia come un feed personale e privato.

**P1 — Ricerca semantica**
"Cerca per emozione": Triste, Ansioso, Felice, Motivato
"Cerca per tema": Lavoro, Famiglia, Salute, Relazioni
Oltre alla ricerca testuale classica.

**P2 — Gestione dati**
- Elimina, archivia, esporta (PDF del diario settimanale)

---

## 9. INSIGHT — Analisi AI del Diario

### Stato Attuale
- Analisi sentiment (polarita'/magnitude) con barre
- Topic e key phrases come chip
- Pattern cognitivi (CBT) con evidenze
- Summary AI espandibile con confidence score
- Domande suggerite follow-up
- Feedback dialog

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Troppo clinico/accademico | "Cognitive Patterns", "Sentiment Polarity" — il self-improver non parla cosi' | ALTO |
| Isolato dal diario | E' una schermata separata. Dovrebbe essere parte dell'esperienza di scrittura. | MEDIO |
| Nessun collegamento con insight passati | Ogni analisi e' a se'. Non mostra evoluzione. | ALTO |

### Improvement Plan

**P0 — Rinominare tutto in linguaggio umano**
- "Sentiment Analysis" → "Come ti sentivi"
- "Cognitive Patterns" → "Schemi che ho notato"
- "Key Phrases" → "Le tue parole chiave"
- "Confidence Score" → non mostrarlo (e' un dato tecnico, non utile all'utente)

**P1 — Inline nel diario**
Dopo che l'utente salva un diario, mostrare l'insight SOTTO il testo, non in una schermata separata. Come un commento dell'AI al margine.

**P2 — Collegamento longitudinale**
*"Questo e' il terzo diario in cui menzioni 'controllo'. Vuoi esplorare questo tema con l'AI?"*

---

## 10. SOCIAL (Feed + Composer + Social Profile + Thread Detail)

### Stato Attuale
- Feed Threads-style con 2 tab (For You / Following)
- Composer con media (4), mood tag, visibility, thread chain
- Profilo social con tab (Threads/Replies/Reposts), follow/block, edit profile
- Thread detail con nested replies, sort, reply input
- Infinite scroll, pull-to-refresh, optimistic updates
- ThreadPostCard condiviso via core/social-ui

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| E' un clone di Threads generico | Non c'e' nulla che lo leghi al wellness. Potrebbe essere qualsiasi social. | CRITICO |
| Repost non implementato | Intent esiste ma nessuna logica | MEDIO |
| ThreadOptions tutto TODO | Save, Hide, Mute, Block, Report — vuoti | MEDIO |
| Nessun caching locale | Ogni apertura ricarica tutto da Firestore | MEDIO |
| Edit profile: avatar non uploadato | Salva URI locale, non funziona cross-device | MEDIO |
| Follow/follower list non navigabili | TODO nei click | BASSO |

### Cosa Dovrebbe Essere
Non un social generico — un **"circolo di crescita"**.

### Improvement Plan

**P0 — Ripensare il contenuto social**
Il feed non deve essere "pensieri random". Deve essere guidato:
- **"Scoperte"**: L'utente condivide un insight che ha avuto (*"Ho capito che il mio stress del lunedi' viene dalla domenica sera"*)
- **"Sfide"**: Mini-challenge settimanali (*"Questa settimana: scrivi 3 cose per cui sei grato ogni giorno"*)
- **"Domande"**: L'utente chiede alla community (*"Come gestite l'ansia pre-riunione?"*)

Ogni post ha un mood tag (gia' implementato!) ma deve essere obbligatorio — questo colora il feed di emozioni.

**P1 — "Share from Journal" come flusso primario**
Il modo principale di postare non e' il composer — e' dal diario.
Dopo aver scritto un diario, l'utente vede: *"Vuoi condividere questa riflessione?"*
- Il testo viene anonimizzato/editato prima della condivisione
- Il mood tag viene auto-taggato dal diario

**P2 — Implementare le azioni mancanti**
- Repost, Save, Hide, Mute, Block, Report
- Follow/follower list navigation
- Avatar upload reale nel profilo edit

**P3 — Caching locale con SQLDelight**
- Cache dei thread per offline e performance

---

## 11. MESSAGING — Chat Privata

### Stato Attuale
- Lista conversazioni con avatar, ultimo messaggio, unread badge
- Chat room con messaggi raggruppati, date separator, typing indicator
- Adaptive bubble corners (Threads-style)
- Read receipts
- Input con icone attachment/camera (non implementate)

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Attachment/camera non implementati | Bottoni presenti ma vuoti | MEDIO |
| User picker per nuova conversazione: TODO | Non si possono iniziare conversazioni | ALTO |
| Nessuna ricerca nei messaggi | | BASSO |
| No edit/delete messaggi | | BASSO |

### Improvement Plan

**P0 — Implementare user picker**
Senza questo, il messaging e' inutilizzabile per nuove conversazioni.

**P1 — Implementare attachment base**
Almeno immagini. GIF e file possono aspettare.

**P2 — Valutare se serve davvero**
Domanda strategica: il messaging privato e' core per il self-improver? O aggiunge complessita' senza valore?
- Se il social e' un "circolo di crescita", forse le conversazioni dovrebbero restare pubbliche (thread replies)
- Il DM potrebbe essere limitato a "contatti stretti" (premium feature)

---

## 12. NOTIFICATIONS — Centro Notifiche

### Stato Attuale
- Feed con filter tabs (All/Follows/Replies/Mentions)
- Mark all read
- Tipo-specific icons e colori
- Unread dot, relative timestamps
- Optimistic updates

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Click su notifica non naviga | L'intent c'e' ma non fa nulla | ALTO |
| Nessun raggruppamento | 10 like su un post = 10 notifiche separate | MEDIO |
| Nessun wellness reminder rendering | Il tipo WELLNESS_REMINDER esiste ma non ha UI speciale | MEDIO |

### Improvement Plan

**P0 — Implementare navigazione da notifica**
Click su "X ha risposto al tuo thread" → apri thread detail.

**P1 — Raggruppamento intelligente**
"Marco e altre 5 persone hanno apprezzato il tuo thread"

**P2 — Wellness Reminders come notifiche speciali**
Card differenziate per reminder dell'AI:
*"Non scrivi da 3 giorni. Va tutto bene?"*
Queste non sono notifiche social — sono il cuore dell'app.

---

## 13. SEARCH — Ricerca Globale

### Stato Attuale
- Ricerca thread + utenti in parallelo
- Filter chips (All/Threads/Users)
- Risultati compatti con stats

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Solo ricerca testuale | Non posso cercare per emozione o tema | MEDIO |
| Nessuna ricerca recente/suggerimenti | | MEDIO |
| Ricerca non live (solo su invio) | | BASSO |
| Stringhe miste italiano/inglese | | BASSO |

### Improvement Plan

**P0 — Aggiungere filtri per mood tag**
Il differenziatore: cerca thread per emozione. *"Mostrami thread di persone che si sentono motivate"*

**P1 — Ricerche suggerite**
"Trending topics", "Sfide della settimana", "Thread piu' apprezzati"

---

## 14. SETTINGS — Impostazioni

### Stato Attuale
- Profile card con pulse animation
- 4 navigation card (Personal, Health, Lifestyle, Goals)
- Privacy toggles
- Logout + Delete account
- Sub-schermate: stubbed (navigazione ok, contenuto incompleto)

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Sub-schermate incomplete | I form di dettaglio non sono implementati | MEDIO |
| Nessun export dati | GDPR richiede data portability | ALTO |
| Nessun tema/customizzazione | Il self-improver ama personalizzare | BASSO |

### Improvement Plan

**P0 — Implementare export dati** (GDPR compliance)

**P1 — Completare sub-schermate** con form funzionanti

**P2 — Aggiungere "AI Preferences"**
- Tono dell'AI (formale/amichevole/diretto)
- Frequenza reminder
- Temi da evitare

---

## 15. SUBSCRIPTION — Monetizzazione

### Stato Attuale
- Header animato con corona + shimmer
- Tabella comparativa Free/Premium/Pro
- Product card con glow border
- Restore purchases
- Billing flow: STUB (solo UI, nessuna logica reale)

### Problemi
| Problema | Impatto | Priorita' |
|----------|---------|-----------|
| Nessuna logica di acquisto reale | Il billing non funziona | CRITICO (per revenue) |
| 3 tier confusivi | Free/Premium/Pro — il self-improver non vuole scegliere tra 2 tier a pagamento | MEDIO |
| Feature comparison generica | Non mostra il valore reale | MEDIO |

### Improvement Plan

**P0 — Implementare BillingClient reale** (Google Play Billing Library 7.x)

**P1 — Semplificare a 2 tier: Free e Pro**
- Free: journaling illimitato, AI 5 msg/giorno, insight base
- Pro: AI illimitata + voce + avatar + insight profondi + social completo

**P2 — Paywall contestuale, non generica**
Non mostrare la paywall come schermata a se'. Mostrarla NEL MOMENTO del bisogno:
- L'utente ha usato 5 messaggi AI → *"Vuoi continuare? Con Pro hai conversazioni illimitate."*
- L'utente vede l'insight base → *"C'e' di piu'. Ecco un'anteprima del tuo pattern settimanale..."*

---

## PRIORITA' GLOBALE — Roadmap Suggerita

### Fase 1: "L'Anima" (2-3 settimane)
L'app deve avere un carattere. Senza questo, niente altro conta.

1. **Ristrutturare la navigazione** — Bottom nav: [Home] [Journal] [AI Chat] [Il Mio Percorso]. Social diventa layer, non tab.
2. Ridisegnare Welcome Screen (brand, non logo Google)
3. Semplificare Onboarding (3 step conversazionali)
4. Context Engine per l'AI (iniettare diari nel prompt)
5. Dare personalita' all'AI (nome, tono, memoria)
6. Ridisegnare Home (Today's Reflection AI + sezione "Dalla community")

### Fase 2: "Il Valore" (3-4 settimane)
Rendere ogni feature utile, non solo presente.

7. Smart Capture nel journaling (AI inferisce metriche)
8. Sostituire il profilo psicologico con Weekly Reflection AI + Journey Line
9. Integrare avatar nella chat voice
10. Timeline unificata nella History
11. CTA "Condividi" post-salvataggio diario → Composer pre-compilato
12. Implementare billing reale

### Fase 3: "La Community" (3-4 settimane)
Il social diventa un moltiplicatore di valore.

13. Ripensare il feed come "circolo di crescita" (accessibile da Home + drawer)
14. Notifiche nella top bar globale (non dentro il feed)
15. Implementare azioni social mancanti (repost, options, follow lists)
16. Raggruppamento notifiche intelligente
17. Filtri search per mood + ricerca globale (diari + thread + utenti)

### Fase 4: "Il Business" (2 settimane)
18. Paywall contestuale
19. Export dati (GDPR)
20. Analytics (Firebase/Mixpanel)
21. AI Preferences nelle settings

---

## METRICHE DI SUCCESSO

| Metrica | Target | Perche' |
|---------|--------|---------|
| **Retention D7** | >40% | Il self-improver torna se trova valore nella prima settimana |
| **Diari/settimana** | >3 per utente attivo | Indica engagement col core loop |
| **Conversioni Free->Pro** | >5% | Healthy per app wellness |
| **Sessioni AI/settimana** | >2 | L'AI e' il differenziatore, deve essere usata |
| **NPS** | >50 | Il self-improver raccomanda se l'esperienza e' profonda |

---

*"Sir, questa e' la mappa. La tecnologia c'e' tutta — ora serve il cuore."*
— Jarvis
