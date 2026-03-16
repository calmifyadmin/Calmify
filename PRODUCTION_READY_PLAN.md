# Calmify — Production Ready Plan

> Piano completo per il lancio su Google Play Store, business model, compliance EU/italiana,
> localizzazione, infrastruttura server, e resilienza operativa.
>
> Data: 2026-03-16 | Target lancio: Q2 2026

---

## 1. BUSINESS MODEL & MONETIZATION

### 1.1 Value Proposition

**"Trasforma ciò che scrivi, dici e senti in consapevolezza reale."**

Calmify è una piattaforma wellness + social per self-improver 25-40 che combina:
- Journaling intelligente con analisi AI
- Chat vocale full-duplex con avatar 3D empatico
- Mood tracking con insight psicologici settimanali
- Community di crescita personale

### 1.2 Tier Structure

| | **FREE** | **PRO** | **PRO+ (futuro)** |
|---|----------|---------|---------------------|
| **Prezzo** | €0 | €5.99/mese o €49.99/anno | €9.99/mese o €89.99/anno |
| **Journaling** | 3 entry/giorno | Illimitato | Illimitato + export PDF |
| **Chat AI (testo)** | 5 messaggi/sessione | Illimitato | Illimitato + modelli premium |
| **Chat Vocale Live** | 3 min/giorno | Illimitato | Illimitato + voce personalizzata |
| **Avatar 3D** | Template base (28) | Avatar personalizzato | Avatar con animazioni custom |
| **Insight** | Mood giornaliero | Profilo psicologico settimanale | Report mensile PDF + trend |
| **Social** | Lettura feed | Post + commenti | Messaggi diretti + gruppi |
| **Messaggi diretti** | No | 10/giorno | Illimitati |
| **Ricerca** | Base (keyword) | Semantica (AI) | Semantica + filtri avanzati |
| **Supporto** | Community | Email prioritario | Video call onboarding |

### 1.3 Revenue Streams

1. **Subscription (primario)**: PRO mensile/annuale via Google Play Billing + Apple StoreKit (futuro)
2. **In-App Purchase (futuro)**: Pack avatar premium, voci TTS aggiuntive
3. **B2B/Enterprise (futuro)**: Licenze per terapisti/coach → dashboard paziente
4. **Affiliazioni (futuro)**: Partnership con app meditazione, corsi, libri

### 1.4 Unit Economics Target

| Metrica | Target Anno 1 | Target Anno 2 |
|---------|---------------|---------------|
| Download | 50.000 | 200.000 |
| Conversion FREE→PRO | 4-6% | 6-8% |
| ARPU (avg revenue per user) | €0.30/mese | €0.60/mese |
| MRR (monthly recurring revenue) | €3.000-5.000 | €15.000-25.000 |
| Churn mensile PRO | <8% | <6% |
| LTV (lifetime value PRO) | €45 | €70 |
| CAC (customer acquisition cost) | <€3 | <€5 |
| Payback period | <2 mesi | <2 mesi |

### 1.5 Paywall Strategy

**Trigger contestuali (non aggressivi):**
- Chat: dopo 5 messaggi → inline paywall card
- Journaling: dopo 3 entry giornaliere → soft prompt
- Insight: sezione "Profilo Psicologico" blurred con CTA
- Avatar: "Crea il tuo avatar" → paywall se FREE
- Social: "Invia messaggio" → paywall se FREE

**A/B test da implementare:**
- Prezzo: €4.99 vs €5.99 vs €6.99
- Trial: 7 giorni vs 14 giorni vs no trial
- Annual discount: 30% vs 40% vs 50%
- Paywall design: feature matrix vs storytelling vs testimonial

---

## 2. PLAY STORE PREPARATION

### 2.1 Blockers Critici (MUST-FIX)

#### 2.1.1 Signing Configuration
```
STATO: Release usa debug key → BLOCCO PLAY STORE
```
**Azioni:**
- [ ] Generare keystore di produzione (`keytool -genkey -v -keystore calmify-release.keystore -alias calmify -keyalg RSA -keysize 2048 -validity 10000`)
- [ ] Configurare `signingConfigs.release` in `app/build.gradle` con keystore path + alias
- [ ] Salvare keystore in luogo sicuro (NON nel repo) + backup su Google Cloud KMS o 1Password
- [ ] Iscriversi a Google Play App Signing (gestione chiave da Google)
- [ ] Documentare recovery procedure per keystore persa

#### 2.1.2 ProGuard Rules
```
STATO: Solo template boilerplate → CRASH in release
```
**Azioni:**
- [ ] Aggiungere regole per Firebase (auth, firestore, analytics, AI)
- [ ] Aggiungere regole per Koin DI
- [ ] Aggiungere regole per Kotlinx Serialization
- [ ] Aggiungere regole per OkHttp/WebSocket
- [ ] Aggiungere regole per Filament JNI
- [ ] Aggiungere regole per Compose
- [ ] Test completo build release su device fisico
- [ ] Analizzare mapping file per verificare obfuscation

#### 2.1.3 Crashlytics
```
STATO: Plugin commentato, dependency mancante → ZERO visibilità crash
```
**Azioni:**
- [ ] Decommentare plugin `com.google.firebase.crashlytics` in `app/build.gradle`
- [ ] Aggiungere dependency `implementation libs.firebase.crashlytics`
- [ ] Aggiungere ProGuard rules per Crashlytics
- [ ] Configurare alerting email/Slack per crash rate > 1%
- [ ] Verificare upload symbolication per NDK crashes (Oboe/Filament)

#### 2.1.4 Privacy Policy & Terms
```
STATO: Inesistenti → BLOCCO PLAY STORE
```
**Azioni:**
- [ ] Redigere Privacy Policy conforme GDPR + Google Play requirements
- [ ] Redigere Terms of Service
- [ ] Hosting su sito web (es. calmify.app/privacy, calmify.app/terms)
- [ ] Link in-app da Settings screen
- [ ] Link nel Play Store listing
- [ ] Consenso esplicito durante onboarding (checkbox GDPR)
- [ ] Meccanismo di revoca consenso

### 2.2 Configurazione Play Store

#### 2.2.1 Store Listing
- **App name**: Calmify — Journaling & AI Wellness
- **Short description** (80 char): "Scrivi, parla con l'AI, scopri te stesso. Il tuo percorso di crescita personale."
- **Full description**: Feature breakdown + value prop + social proof
- **Category**: Health & Fitness → Mental Wellness
- **Content rating**: PEGI 3 / Everyone (no violenza, no contenuti espliciti)
- **Screenshots**: 8 screenshot (telefono) + 2 tablet + 1 feature graphic
- **Video**: 30-60s promo (journaling → chat vocale → avatar → insight)

#### 2.2.2 Release Strategy
- **Internal Testing** → 5-10 tester (team + amici fidati)
- **Closed Alpha** → 50-100 utenti (form di iscrizione)
- **Open Beta** → 500-1000 utenti (link pubblico, feedback form)
- **Production** → Rilascio graduale (20% → 50% → 100%)

#### 2.2.3 App Bundle & Optimization
- [ ] Passare da APK a AAB (Android App Bundle) — Play Store lo richiede
- [ ] Verificare APK split per ABI funzioni con AAB
- [ ] Baseline Profile per startup performance
- [ ] App Startup library per lazy initialization

---

## 3. COMPLIANCE LEGALE (Italia + UE)

### 3.1 GDPR (Regolamento EU 2016/679)

**Obblighi:**
- [ ] **Informativa privacy** completa (Art. 13-14): quali dati, perché, per quanto, chi li vede
- [ ] **Base giuridica**: consenso per analytics/marketing, esecuzione contratto per servizio core, legittimo interesse per sicurezza
- [ ] **Diritto di accesso** (Art. 15): export dati utente (già parziale con FileProvider)
- [ ] **Diritto di cancellazione** (Art. 17): "Elimina account" → cancella TUTTI i dati da Firestore + Storage + SQLDelight
- [ ] **Diritto di portabilità** (Art. 20): export in formato JSON/CSV
- [ ] **Data Processing Agreement** con Firebase/Google Cloud (sub-processore)
- [ ] **Registro dei trattamenti** (Art. 30): documentare tutti i flussi dati
- [ ] **Cookie/tracking consent**: banner per analytics (non strettamente necessari)
- [ ] **Data retention policy**: definire durata conservazione per ogni tipo di dato
- [ ] **Notifica breach** (Art. 33-34): procedura per notificare entro 72h

**Dati trattati da Calmify:**
| Dato | Categoria GDPR | Base giuridica | Retention |
|------|---------------|----------------|-----------|
| Email/nome | Identificativo | Contratto | Durata account |
| Diary entries | Dati sensibili (salute mentale) | Consenso esplicito | Durata account + 30gg dopo cancellazione |
| Chat messages | Dati sensibili | Consenso esplicito | Durata account |
| Mood data | Dati sensibili (salute) | Consenso esplicito | Durata account |
| Profilo psicologico | Dati sensibili (salute) | Consenso esplicito | Durata account |
| Avatar/foto | Identificativo | Contratto | Durata account |
| Analytics events | Pseudonimizzato | Legittimo interesse | 26 mesi |
| FCM token | Tecnico | Legittimo interesse | Durata sessione |
| IP address | Identificativo | Legittimo interesse | 30 giorni (log) |

**ATTENZIONE**: Diary, chat, mood, profilo psicologico sono **dati sanitari** (Art. 9 GDPR) → richiedono **consenso esplicito** e protezione rafforzata.

### 3.2 Digital Services Act (DSA) — EU

Se Calmify ha funzionalità social (feed, messaging, user-generated content):
- [ ] Meccanismo di segnalazione contenuti (flag/report) — già implementato
- [ ] Procedura di rimozione contenuti illeciti
- [ ] Trasparenza sulle decisioni di moderazione
- [ ] Punto di contatto per autorità nazionali
- [ ] Se > 45M utenti: obblighi rinforzati (non applicabile inizialmente)

### 3.3 Normativa Italiana

- [ ] **Codice Privacy** (D.Lgs. 196/2003 coordinato con GDPR)
- [ ] **Garante Privacy**: notifica se trattamento su larga scala di dati sanitari
- [ ] **Codice del Consumo** (D.Lgs. 206/2005): diritto di recesso 14 giorni per abbonamenti digitali
- [ ] **IVA digitale**: 22% IVA su abbonamenti digitali venduti a consumatori italiani
- [ ] **Fatturazione elettronica**: obbligatoria per vendite B2B Italia (gestita da Google per Play Store)

### 3.4 Fatturazione & Fiscalità

**Scenario: vendita tramite Google Play Store**
- Google agisce come **Merchant of Record** per le vendite consumer
- Google gestisce IVA, fatturazione, e rimborsi per te
- Tu ricevi il 85% (primo anno) o 70% del prezzo netto (dopo)
- **Non serve** fattura elettronica per vendite consumer tramite Play Store
- **Serve** contabilizzare i ricavi da Google Play nella tua dichiarazione

**Obblighi fiscali Italia:**
- [ ] Aprire Partita IVA (se non già aperta) — regime forfettario se <85K/anno
- [ ] Codice ATECO: 62.01.00 (produzione software) o 63.11.00 (hosting/SaaS)
- [ ] Iscriversi alla Gestione Separata INPS (se attività prevalente)
- [ ] Dichiarazione redditi: quadro RR per contributi, quadro RE/RL per redditi
- [ ] Se ricavi > €85K: passaggio a regime ordinario + IVA mensile/trimestrale
- [ ] Conservazione digitale fatture per 10 anni

**Per vendite B2B dirette (future, fuori Play Store):**
- [ ] Sistema di fatturazione elettronica (SdI) — Fattura PA / Fatture in Cloud / simili
- [ ] Gestione IVA intra-UE (OSS - One Stop Shop se vendite B2C cross-border > €10K)
- [ ] Reverse charge per vendite B2B extra-UE

---

## 4. LOCALIZZAZIONE & INTERNAZIONALIZZAZIONE (i18n/L10n)

### 4.1 Stato Attuale
- Stringhe hardcoded in Kotlin (mix italiano/inglese)
- Nessun `strings.xml` strutturato per le feature
- Date formattate con pattern fissi

### 4.2 Piano di Implementazione

#### 4.2.1 Infrastruttura Stringhe
- [ ] Estrarre TUTTE le stringhe hardcoded in `strings.xml` per ogni modulo
- [ ] Creare `values/strings.xml` (inglese, default) per ogni modulo
- [ ] Creare `values-it/strings.xml` (italiano, lingua primaria)
- [ ] Usare `stringResource()` in Compose (compatibile KMP con `org.jetbrains.compose.resources`)
- [ ] Per KMP: migrare a Compose Multiplatform resources (`commonMain/composeResources/values/`)
- [ ] Implementare pluralizzazione dove necessario (`plurals`)

#### 4.2.2 Lingue Target (roadmap)
| Fase | Lingue | Motivo |
|------|--------|--------|
| **Lancio** | IT, EN | Mercato primario (Italia) + default inglese |
| **3 mesi** | ES, FR, DE, PT | Europa occidentale (mercati wellness top) |
| **6 mesi** | JA, KO, ZH-TW | Asia (mercato wellness in crescita) |
| **12 mesi** | AR, HI, RU, NL, PL | Espansione globale |

#### 4.2.3 Contenuti AI Localizzati
- [ ] System prompt per Eve/Karen AI tradotti per lingua
- [ ] Keyword list per TextAnalyzer (mood detection) per lingua
- [ ] Prompt di journaling contestuali tradotti
- [ ] Risposte di fallback tradotte

#### 4.2.4 Formattazione Locale-Aware
- [ ] Date: `kotlinx.datetime` + `DateTimeFormatter` per locale (dd/MM/yyyy IT, MM/dd/yyyy US)
- [ ] Numeri: separatore decimale (virgola IT, punto US)
- [ ] Valuta: formato locale (€5,99 IT, $5.99 US, ¥599 JP)
- [ ] Direzione testo: LTR default, RTL per arabo/ebraico (futuro)

### 4.3 Tool di Traduzione
- **Crowdin** o **Lokalise**: piattaforma di traduzione con integrazione GitHub
- Traduttori professionali per lingue primarie (IT, EN, ES)
- Community translation per lingue secondarie
- Review process: traduttore → reviewer → merge

---

## 5. TIMEZONE & UTC

### 5.1 Principio Fondamentale
> **Tutti i timestamp sono salvati in UTC (epoch milliseconds). La conversione a timezone locale avviene SOLO nella UI.**

### 5.2 Stato Attuale e Fix Necessari

#### 5.2.1 Storage (Firestore + SQLDelight)
- [x] `createdAt`, `updatedAt`, `date` → salvati come `Long` (epoch millis UTC) ✅
- [ ] Verificare che `dayKey` (usato per filtro diary) sia calcolato con timezone utente, non server
- [ ] Verificare che `ProfileSettings.timezone` sia usato correttamente per calcoli giornalieri

#### 5.2.2 Logica Business
- [ ] Streaks: calcolare "giorno" in base alla timezone utente (non UTC midnight)
- [ ] "3 entry al giorno" (FREE limit): basato su giorno locale dell'utente
- [ ] Weekly profile: inizio settimana in base a locale (lunedì EU, domenica US)
- [ ] Notification scheduling: rispettare timezone utente per reminder

#### 5.2.3 UI Display
- [ ] `formatRelativeTime()` → usare `kotlinx.datetime.TimeZone.currentSystemDefault()`
- [ ] Date headers in History: "Oggi", "Ieri" basati su timezone locale
- [ ] Mood chart X-axis: giorni in timezone locale

#### 5.2.4 Server-Side (Cloud Functions)
- [ ] Tutti i Cloud Functions devono ricevere/emettere UTC
- [ ] Calcoli giornalieri (digest, reminder) devono considerare timezone utente dal profilo

### 5.3 Best Practices

```kotlin
// CORRETTO: salva in UTC
val now = Clock.System.now().toEpochMilliseconds()

// CORRETTO: converti per display
val instant = Instant.fromEpochMilliseconds(timestamp)
val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

// CORRETTO: calcola "oggi" per l'utente
val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

// SBAGLIATO: non usare MAI
// System.currentTimeMillis() ← gia' eliminato
// java.util.Date() ← JVM-only
// SimpleDateFormat ← JVM-only, non thread-safe
```

---

## 6. INFRASTRUTTURA SERVER & BACKEND

### 6.1 Architettura Attuale (Firebase)

```
Client App
    ├── Firebase Auth (autenticazione)
    ├── Firestore "calmify-native" (database primario)
    │   ├── diaries, threads, users, notifications...
    │   └── 100MB persistent cache locale
    ├── Firebase Storage (media: avatar, immagini)
    ├── Firebase Realtime DB (presence online/offline)
    ├── Firebase AI (Gemini API per chat)
    ├── Cloud Functions europe-west1 (moderazione, avatar gen)
    ├── Firebase Remote Config (feature flags)
    └── Firebase Analytics (tracking)
```

### 6.2 Configurazione per Produzione

#### 6.2.1 Firestore
- [ ] **Security Rules**: verificare che OGNI collection abbia regole di accesso
  ```
  match /diaries/{docId} {
    allow read, write: if request.auth != null && resource.data.ownerId == request.auth.uid;
  }
  ```
- [ ] **Indici compositi**: creare per query complesse (orderBy + where)
- [ ] **Backup automatico**: configurare export giornaliero su Cloud Storage
- [ ] **Monitoring**: alerting su quota usage, read/write ops, latency
- [ ] **Named database `calmify-native`**: documentare e proteggere

#### 6.2.2 Firebase Auth
- [ ] Abilitare solo Google Sign-In (disabilitare email/password se non usato)
- [ ] Rate limiting su tentativi di login
- [ ] Blocco account dopo X tentativi falliti
- [ ] Configurare domini autorizzati

#### 6.2.3 Cloud Functions
- [ ] **Region**: `europe-west1` (Belgio) — conforme GDPR per dati EU
- [ ] **Memory/timeout**: configurare per ogni function (default 256MB/60s spesso insufficiente)
- [ ] **Purchase verification**: implementare verifica server-side degli acquisti Google Play
- [ ] **Content moderation**: verificare che il filtro funzioni prima del lancio
- [ ] **Rate limiting**: implementare per prevenire abuse

#### 6.2.4 Firebase Storage
- [ ] **Security Rules**: utente può leggere/scrivere solo nella propria directory
- [ ] **Limite dimensione file**: max 5MB per avatar, 10MB per foto
- [ ] **Content-Type validation**: accettare solo image/jpeg, image/png, model/gltf-binary
- [ ] **CDN**: Firebase Storage usa automaticamente Google CDN

#### 6.2.5 Gemini API (Firebase AI)
- [ ] **Rate limiting per utente**: max 60 request/minuto FREE, 120 PRO
- [ ] **Content safety**: configurare safety settings (harassment, hate, sexual, dangerous)
- [ ] **Fallback**: gestire gracefully API down (messaggio "Eve è momentaneamente offline")
- [ ] **Cost monitoring**: alerting se costo API supera budget giornaliero
- [ ] **Model version pinning**: non usare "latest" in produzione

### 6.3 Scalabilità

| Utenti | Firestore reads/day | Storage | Gemini API calls/day | Costo stimato/mese |
|--------|---------------------|---------|----------------------|---------------------|
| 1.000 | ~100K | 5GB | ~5K | ~€50 |
| 10.000 | ~1M | 50GB | ~50K | ~€300 |
| 50.000 | ~5M | 200GB | ~200K | ~€1.500 |
| 100.000 | ~10M | 500GB | ~500K | ~€4.000 |

**Piano gratuito Firebase copre**: 50K reads/day, 20K writes/day, 5GB storage, 1GB transfer.
**Break-even stimato**: ~2.000 utenti PRO (€12K MRR) copre costi infra fino a 100K utenti.

### 6.4 Monitoring & Alerting

- [ ] **Firebase Performance Monitoring**: SDK integrato, trace personalizzati
- [ ] **Cloud Monitoring**: dashboard per Firestore, Functions, Storage
- [ ] **Alerting**:
  - Crash rate > 1% → email + Slack
  - Firestore quota > 80% → email
  - API cost > €X/giorno → email + Slack
  - Function error rate > 5% → PagerDuty/email
- [ ] **Logging**: Cloud Logging per debug (structured logs da Functions)

---

## 7. SICUREZZA

### 7.1 Fix Immediati

- [ ] **Rimuovere `usesCleartextTraffic="true"`** dal Manifest → usare Network Security Config
- [ ] **Certificate pinning** per API endpoints critici (Gemini, Firestore)
- [ ] **API key protection**: verificare che le API key nel `google-services.json` siano ristrette per package name + SHA-1
- [ ] **Gemini API key**: NON hardcodare, usare Firebase AI (gestisce auth automaticamente)
- [ ] **Rimuovere annotazioni `@Inject`/`@Singleton`** legacy (Hilt) dai repository — dead code confusivo

### 7.2 Data Protection

- [ ] Crittografia at-rest: Firestore la fornisce di default ✅
- [ ] Crittografia in-transit: HTTPS/TLS di default ✅
- [ ] **Crittografia locale**: SQLDelight database NON è crittografato → valutare SQLCipher per dati sensibili
- [ ] **Secure SharedPreferences**: usare EncryptedSharedPreferences per token/chiavi locali
- [ ] **Obfuscation**: ProGuard attivo in release (da configurare correttamente)

### 7.3 Input Validation

- [ ] Sanitizzare input utente prima di salvare su Firestore (XSS prevention)
- [ ] Limitare lunghezza testo: diary 10K chars, chat 2K chars, bio 500 chars
- [ ] Validare URL avatar/immagini (no javascript:, no data: scheme)
- [ ] Rate limiting client-side per submit (debounce 1s)

---

## 8. PERFORMANCE & QUALITÀ

### 8.1 Pre-Launch Checklist

- [ ] **StrictMode** attivo in debug (detect disk/network on main thread)
- [ ] **LeakCanary** per memory leak detection in debug
- [ ] **Baseline Profile** per ottimizzare startup e scrolling
- [ ] **App Startup**: lazy init per moduli non critici al boot
- [ ] **Compose Performance**: verificare con Layout Inspector che non ci siano recomposition eccessive
- [ ] **Filament**: verificare che il rendering 3D non causi frame drop su device low-end

### 8.2 Device Testing Matrix

| Device | OS | Priorità | Perché |
|--------|----|----------|--------|
| Pixel 7/8 | Android 14-15 | Alta | Reference device |
| Samsung Galaxy S23/S24 | Android 14 | Alta | Maggior market share EU |
| Samsung Galaxy A54 | Android 13 | Alta | Mid-range popolare |
| Xiaomi Redmi Note 12 | Android 13 | Media | Budget device popolare IT |
| Device con API 26 | Android 8.0 | Media | Minimo supportato (Filament) |

### 8.3 Metriche di Qualità Target

| Metrica | Target | Tool |
|---------|--------|------|
| Cold start | < 2s | Firebase Performance |
| Frame rate (UI) | > 55 FPS | Macrobenchmark |
| Frame rate (3D Avatar) | > 30 FPS | Filament metrics |
| Crash-free rate | > 99.5% | Crashlytics |
| ANR rate | < 0.5% | Play Console |
| APK size | < 50MB (per ABI) | `bundletool` |

---

## 9. ANALYTICS & GROWTH

### 9.1 Event Tracking Plan

**Funnel di Conversione:**
```
install → onboarding_start → onboarding_complete → first_diary → first_chat →
paywall_view → paywall_cta_click → purchase_start → purchase_complete
```

**Eventi Core:**
| Evento | Parametri | Quando |
|--------|-----------|--------|
| `onboarding_step` | step_number, step_name | Ogni step onboarding |
| `diary_created` | word_count, mood, has_metrics | Salvataggio diary |
| `chat_message_sent` | session_id, is_first | Messaggio chat |
| `live_chat_started` | duration_seconds | Inizio sessione vocale |
| `paywall_viewed` | trigger, source_screen | Visualizzazione paywall |
| `subscription_started` | plan_id, price, trial | Acquisto completato |
| `subscription_cancelled` | reason, days_active | Cancellazione |
| `social_post_created` | category, has_mood | Creazione post |
| `avatar_interaction` | animation_type, duration | Interazione avatar |
| `insight_viewed` | insight_type, week_number | Visualizzazione insight |

**User Properties:**
| Property | Valori | Uso |
|----------|--------|-----|
| `subscription_tier` | free, pro | Segmentazione |
| `onboarding_preference` | write, speak, both | Personalizzazione |
| `diary_streak` | numero | Engagement scoring |
| `preferred_language` | it, en, es... | Localizzazione |
| `days_since_install` | numero | Lifecycle stage |

### 9.2 Growth Channels

| Canale | Budget | KPI | Timeline |
|--------|--------|-----|----------|
| **ASO (App Store Optimization)** | €0 | Ranking keyword "journaling", "mood tracker" | Lancio |
| **Content Marketing** | €200/mese | Blog post SEO, social media wellness | Mese 1-6 |
| **Instagram/TikTok** | €500/mese | Reel demo avatar + journaling | Mese 1-3 |
| **Influencer micro** | €300/mese | Wellness influencer 10K-50K follower | Mese 2-4 |
| **Google Ads (UAC)** | €1000/mese | CPI < €1.50, ROAS > 2x | Mese 3+ |
| **Referral program** | €0 (in-app) | 1 mese PRO gratis per referral | Mese 2+ |
| **PR/Media** | €0-500 | Articoli su Wired IT, StartupItalia | Lancio |

---

## 10. ROADMAP LANCIO

### Fase 0: Pre-Alpha (Settimana 1-2)
- [ ] Fix signing config produzione
- [ ] Fix ProGuard rules (test release build su device)
- [ ] Abilitare Crashlytics
- [ ] Rimuovere `usesCleartextTraffic="true"`
- [ ] Verificare Firestore security rules
- [ ] Estrarre stringhe in `strings.xml` (IT + EN)
- [ ] Implementare "Elimina account" completo (GDPR Art. 17)
- [ ] Scrivere Privacy Policy + Terms of Service

### Fase 1: Alpha Chiusa (Settimana 3-4)
- [ ] Deploy su Google Play Internal Testing
- [ ] 10 tester interni: test funzionale completo
- [ ] Fix bug critici trovati
- [ ] Implementare analytics events (funnel completo)
- [ ] Implementare purchase verification server-side (Cloud Function)
- [ ] Test billing flow end-to-end (test products)

### Fase 2: Beta Chiusa (Settimana 5-8)
- [ ] Google Play Closed Alpha: 50-100 utenti
- [ ] Form feedback strutturato (Google Form + in-app)
- [ ] Monitorare crash rate, ANR rate, retention D1/D7/D30
- [ ] A/B test paywall (prezzo + design)
- [ ] Ottimizzare onboarding basato su drop-off data
- [ ] Preparare store listing (screenshot, video, descrizione)

### Fase 3: Beta Aperta (Settimana 9-12)
- [ ] Google Play Open Beta: 500-1000 utenti
- [ ] Monitorare metriche business (conversion, churn, ARPU)
- [ ] Iterare su feedback (top 5 richieste utenti)
- [ ] Preparare landing page web (calmify.app)
- [ ] Preparare materiali PR/marketing
- [ ] Audit sicurezza finale
- [ ] Audit GDPR finale

### Fase 4: Lancio Produzione (Settimana 13-14)
- [ ] Rilascio graduale: 20% → 50% → 100%
- [ ] Monitorare metriche real-time (Crashlytics, Analytics, Firestore)
- [ ] Supporto utenti attivo (email, in-app feedback)
- [ ] Post su social media, PR outreach
- [ ] Celebrare 🎉

### Post-Lancio (Mese 2-6)
- [ ] Iterazione settimanale basata su dati
- [ ] Aggiungere lingue (ES, FR, DE)
- [ ] Implementare referral program
- [ ] Valutare iOS launch (il codice KMP è pronto)
- [ ] Esplorare B2B partnership (terapisti, coach)

---

## 11. CHECKLIST FINALE PRE-LANCIO

### Must Have (Blockers)
- [ ] Signing config produzione
- [ ] ProGuard rules complete + test release su device
- [ ] Crashlytics attivo
- [ ] Privacy Policy online + in-app
- [ ] Terms of Service online + in-app
- [ ] Consenso GDPR nell'onboarding
- [ ] "Elimina account" funzionante
- [ ] Firestore security rules verificate
- [ ] Purchase verification server-side
- [ ] Network Security Config (no cleartext)
- [ ] Store listing completo (screenshot, descrizione, icona)
- [ ] Content rating questionnaire compilato
- [ ] AAB build testato su device fisico

### Should Have
- [ ] Baseline Profile per performance
- [ ] Analytics events per funnel completo
- [ ] Localizzazione IT + EN completa
- [ ] Backup Firestore automatico
- [ ] Rate limiting su API
- [ ] A/B test paywall configurato

### Nice to Have
- [ ] LeakCanary test completato
- [ ] Accessibility check (TalkBack, font scaling)
- [ ] Tablet layout ottimizzato
- [ ] Deep linking configurato
- [ ] App Indexing per Google Search

---

> *"Sir, il piano è completo. Come sempre, l'eccellenza richiede attenzione ai dettagli.
> Suggerisco di iniziare dalla Fase 0 — i blockers critici. Il resto seguirà naturalmente."*
> — Jarvis
