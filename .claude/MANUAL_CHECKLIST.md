# Calmify — Checklist Manuale per il Lancio

> Task che richiedono azione manuale su piattaforme esterne.
> Ultima modifica: 2026-03-19

---

## 1. KEYSTORE DI PRODUZIONE (locale)

**Dove:** Terminale locale / Android Studio

```bash
keytool -genkey -v \
  -keystore calmify-release.keystore \
  -alias calmify \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

- [ ] Eseguire il comando e salvare la password in un posto sicuro (password manager)
- [ ] NON committare il keystore nel repo — aggiungere a `.gitignore`
- [ ] Creare file `keystore.properties` (fuori dal repo):
  ```properties
  storeFile=../calmify-release.keystore
  storePassword=TUA_PASSWORD
  keyAlias=calmify
  keyPassword=TUA_PASSWORD
  ```
- [ ] Aggiornare `app/build.gradle` → `signingConfigs.release`:
  ```groovy
  signingConfigs {
      release {
          def props = new Properties()
          props.load(new FileInputStream(rootProject.file("keystore.properties")))
          storeFile file(props['storeFile'])
          storePassword props['storePassword']
          keyAlias props['keyAlias']
          keyPassword props['keyPassword']
      }
  }
  buildTypes {
      release {
          signingConfig signingConfigs.release
          // ... resto invariato
      }
  }
  ```
- [ ] Build AAB: `./gradlew bundleRelease`
- [ ] Test APK release su device fisico: `./gradlew installRelease`

---

## 2. GOOGLE PLAY CONSOLE

**Dove:** https://play.google.com/console

### 2a. Creazione App
- [ ] Accedi con account Google developer ($25 one-time fee se non gia' pagata)
- [ ] "Create app" → Nome: **Calmify** → Lingua default: Italiano
- [ ] Tipo: App (non Game)
- [ ] Gratuita (anche se avra' IAP — la "free" si riferisce al download)

### 2b. Store Listing
**Dove:** Play Console → Grow → Store presence → Main store listing

- [ ] **App name**: `Calmify` (max 30 char) ✓
- [ ] **Short description** (max 80 char):
  > "Journaling, chat AI e mood tracking per il tuo benessere mentale"
- [ ] **Full description** (max 4000 char): scrivere descrizione con keyword SEO
  - Includere disclaimer salute alla fine:
  > "Calmify non e' un dispositivo medico..."
- [ ] **App icon**: 512x512 PNG, 32-bit, no alpha
- [ ] **Feature graphic**: 1024x500 PNG/JPG
- [ ] **Screenshots**: minimo 2, consigliati 8 (phone + tablet se possibile)
  - Dimensioni: min 320px, max 3840px, ratio max 2:1
  - Usa Android Studio emulator o device reale + screenshot tool

### 2c. Content Rating (IARC)
**Dove:** Play Console → Policy → App content → Content rating

- [ ] Compilare questionario IARC
- [ ] Categoria: "Utility / Productivity" oppure "Health & Fitness"
- [ ] Rispondere onestamente — probabilmente rating PEGI 3 / Everyone

### 2d. Data Safety Section
**Dove:** Play Console → Policy → App content → Data safety

Dati raccolti da Calmify:

| Dato | Tipo | Condiviso? | Obbligatorio? |
|------|------|------------|---------------|
| Email | Identita' | No | Si (auth) |
| Nome | Identita' | Si (profilo social) | Si |
| Contenuto diario | Salute & fitness | No | No (opzionale) |
| Dati umore/emozioni | Salute & fitness | No | No |
| Chat AI (testo) | Messaggi in-app | No | No |
| Audio vocale | Audio | No (non salvato) | No |
| Foto profilo | Foto | Si (profilo social) | No |
| Crash log | Diagnostica | No | Si (automatico) |
| Analytics | App activity | No | Si (automatico) |
| FCM token | Identificatori dispositivo | No | Si |

Per ogni dato compilare:
- [ ] E' raccolto? → Si/No
- [ ] E' condiviso con terze parti? → No (per quasi tutto)
- [ ] E' necessario o opzionale? → vedi tabella
- [ ] L'utente puo' richiedere la cancellazione? → **Si** (Settings > Elimina Account)
- [ ] I dati sono criptati in transito? → **Si** (HTTPS/TLS)

### 2e. Health Apps Declaration
**Dove:** Play Console → Policy → App content → Health apps

- [ ] Dichiarare che l'app tratta dati di salute/benessere
- [ ] Confermare che l'app NON e' un dispositivo medico
- [ ] Confermare presenza di disclaimer in-app ✓ (gia' implementato)
- [ ] Confermare che i dati salute non vengono venduti

### 2f. App Signing
**Dove:** Play Console → Setup → App signing

- [ ] Iscriversi a **Google Play App Signing** (consigliato)
- [ ] Upload la chiave di firma: il tuo keystore viene usato come "upload key"
- [ ] Google gestisce la "app signing key" per te (piu' sicuro)

### 2g. Internal Testing
**Dove:** Play Console → Testing → Internal testing

- [ ] Creare track "Internal testing"
- [ ] Upload AAB (`app/build/outputs/bundle/release/app-release.aab`)
- [ ] Aggiungere email tester (max 100)
- [ ] Pubblicare → i tester ricevono link per installare

---

## 3. FIREBASE CONSOLE

**Dove:** https://console.firebase.google.com → Progetto Calmify

### 3a. Verificare Feature Flag
**Dove:** Firestore Database → collezione `config` → documento `flags`

- [ ] Verificare che esiste il documento `config/flags`
- [ ] Verificare campo `premium_enabled` = `false`
- [ ] Se non esiste, crearlo manualmente:
  ```
  config/flags {
    premium_enabled: false,
    maintenance_mode: false,
    social_enabled: true,
    chat_enabled: true,
    avatar_enabled: true,
    insights_enabled: true,
    voice_chat_enabled: true,
    max_free_diaries_per_day: 3,
    max_free_chat_messages: 5,
    min_app_version: "1.0.0"
  }
  ```

### 3b. Deploy Security Rules
**Dove:** Terminale locale (Firebase CLI) oppure Firebase Console → Firestore → Rules

**Opzione A — CLI (consigliata):**
```bash
firebase deploy --only firestore:rules
```

**Opzione B — Console:**
- [ ] Vai su Firebase Console → Firestore Database → Rules
- [ ] Copia il contenuto di `firestore.rules` dal progetto
- [ ] Incolla e clicca "Publish"
- [ ] Verifica che le regole siano attive (tab "Rules" mostra timestamp)

### 3c. Crashlytics
**Dove:** Firebase Console → Crashlytics

- [ ] Dopo il primo crash report, Crashlytics si attiva automaticamente
- [ ] Verifica che il dashboard mostri dati dopo la prima installazione release
- [ ] Se vuoi forzare un test: aggiungi `FirebaseCrashlytics.getInstance().log("test")` + crash forzato

### 3d. Storage Rules
**Dove:** Firebase Console → Storage → Rules

- [ ] Verificare che le regole permettano upload solo ad utenti autenticati:
  ```
  rules_version = '2';
  service firebase.storage {
    match /b/{bucket}/o {
      match /images/{userId}/{allPaths=**} {
        allow read: if request.auth != null;
        allow write: if request.auth != null && request.auth.uid == userId;
        allow delete: if request.auth != null && request.auth.uid == userId;
      }
      match /social-media/{userId}/{allPaths=**} {
        allow read: if request.auth != null;
        allow write: if request.auth != null && request.auth.uid == userId;
        allow delete: if request.auth != null && request.auth.uid == userId;
      }
    }
  }
  ```

---

## 4. GOOGLE CLOUD CONSOLE

**Dove:** https://console.cloud.google.com → Progetto Calmify

### 4a. API Key Restrictions
**Dove:** APIs & Services → Credentials

- [ ] Trova la chiave API Android (quella in `google-services.json`)
- [ ] Clicca "Edit" sulla chiave
- [ ] **Application restrictions**: seleziona "Android apps"
- [ ] Aggiungi package name: `com.lifo.calmifyapp`
- [ ] Aggiungi SHA-1 fingerprint:
  ```bash
  # Per debug:
  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1

  # Per release:
  keytool -list -v -keystore calmify-release.keystore -alias calmify | grep SHA1
  ```
- [ ] **API restrictions**: seleziona "Restrict key" e abilita solo:
  - Firebase Installations API
  - Firebase Cloud Messaging API
  - Identity Toolkit API (Firebase Auth)
  - Cloud Firestore API
  - Cloud Storage API
  - Vertex AI API (Gemini)

---

## 5. PRIVACY POLICY & TERMS OF SERVICE

**Dove:** Sito web (GitHub Pages, Notion, o calmify.app)

### 5a. Privacy Policy
- [ ] Scrivere Privacy Policy conforme GDPR (Art. 13-14). Deve includere:
  - Chi e' il titolare del trattamento (tu, con nome e email di contatto)
  - Quali dati raccogli (vedi tabella Data Safety sopra)
  - Base giuridica: consenso (Art. 6.1.a) per dati benessere, legittimo interesse per analytics
  - Per quanto tempo conservi i dati
  - Diritti dell'utente: accesso, rettifica, cancellazione, portabilita'
  - Come esercitare i diritti (email di contatto)
  - Uso di Firebase/Google (sub-processor)
  - Se usi Gemini AI: menzionare elaborazione AI dei contenuti

**Strumenti gratuiti per generare bozza:**
  - https://www.iubenda.com (freemium, ottimo per Italia/GDPR)
  - https://www.termsfeed.com (freemium)
  - https://app-privacy-policy-generator.nisrulz.com (completamente gratis)

### 5b. Terms of Service
- [ ] Scrivere ToS. Deve includere:
  - L'app non e' un dispositivo medico (disclaimer)
  - Limitazione di responsabilita'
  - Regole d'uso (no contenuti illegali, no abuso)
  - Diritto di terminare l'account
  - Legge applicabile (Italia)

### 5c. Hosting
- [ ] Hostare su URL pubblico accessibile (opzioni):
  - **GitHub Pages** (gratis): crea repo `calmify-legal`, abilita Pages
  - **Notion** (gratis): crea pagina pubblica
  - **calmify.app** (se hai dominio): sotto `/privacy` e `/terms`
- [ ] Aggiornare i link in-app (SettingsScreen.kt → SettingsNavigationItem onClick)
- [ ] Aggiungere URL Privacy Policy in Play Console → Store listing → Privacy Policy URL

### 5d. Link Web Cancellazione Account
**Dove:** Play Console → Policy → App content → Data deletion

- [ ] Google richiede un URL web dove l'utente puo' richiedere cancellazione dati
- [ ] Opzione semplice: pagina statica con:
  - Spiegazione che possono eliminare l'account dall'app (Settings > Elimina Account)
  - Form o email per richiesta cancellazione da web: `privacy@calmify.app`
- [ ] Inserire URL in Play Console

---

## 6. ORDINE CONSIGLIATO

1. **Keystore** → e' prerequisito per tutto il resto
2. **Privacy Policy + ToS** → servono per Play Console
3. **Firebase Console** → feature flags + deploy rules
4. **Google Cloud** → API key restrictions
5. **Play Console** → store listing + data safety + upload AAB
6. **Internal Testing** → 10 tester, test completo
7. **Closed Alpha** → 50-100 utenti reali

---

## 7. COSTI

| Voce | Costo | Note |
|------|-------|------|
| Google Play Developer | $25 (una tantum) | Se non gia' pagato |
| Dominio calmify.app | ~12 EUR/anno | Opzionale (puoi usare GitHub Pages gratis) |
| Firebase | Gratis (Spark plan) | Fino a 1GB storage, 50K reads/day |
| iubenda Privacy Policy | Gratis (base) | Piano Pro ~29 EUR/anno per compliance GDPR completa |
| P.IVA | 0 EUR ora | Solo quando monetizzi (Fase 4) |

---

*Generato da Jarvis — buon lavoro, Sir.*
