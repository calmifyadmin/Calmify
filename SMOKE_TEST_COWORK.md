# Calmify Server — Full E2E Smoke Test (Autonomous Cowork)

## Obiettivo

Eseguire lo smoke test E2E completo su **tutti i 25 route groups** del server Calmify deployato su Cloud Run. Verificare che ogni endpoint risponda con gli status code attesi. Produrre un report finale con pass/fail/skip.

## Contesto

Il server Calmify (`calmify-server`) e' stato appena deployato con le ultime modifiche (Phase 5: Environment, Garden, Ikigai, SocialGraph). Serve un test completo di tutti i 150+ endpoint, non solo dei 4 nuovi.

- **Server URL**: `https://calmify-server-23546263069.europe-west1.run.app`
- **API base**: `https://calmify-server-23546263069.europe-west1.run.app/api/v1`
- **Auth**: Firebase JWT token (header `Authorization: Bearer <token>`)
- **DB**: Firestore database `calmify-native`

## Prerequisiti

Prima di iniziare hai bisogno di un **Firebase ID token** valido. Chiedi all'utente di fornirlo come variabile d'ambiente `TOKEN`.

Se l'utente fornisce anche un `TARGET_ID` (userId di un secondo utente), potrai testare anche le mutation sociali (follow/unfollow, block/unblock, messaging conversation).

## Istruzioni di Esecuzione

### Step 1 — Verifica connettivita'

```bash
curl -s -o /dev/null -w "%{http_code}" https://calmify-server-23546263069.europe-west1.run.app/health
```

Deve tornare `200`. Se no, il server e' down — fermati e segnala.

### Step 2 — Esegui lo script smoke test

Lo script e' gia' pronto in `scripts/smoke-test-e2e.sh`. Eseguilo cosi':

```bash
TOKEN="<token-fornito-dall-utente>" bash scripts/smoke-test-e2e.sh
```

Oppure con target per test sociali completi:

```bash
TOKEN="<token>" TARGET_ID="<userId>" VERBOSE=1 bash scripts/smoke-test-e2e.sh
```

### Step 3 — Analizza i risultati

Lo script produce un summary finale con contatori PASS/FAIL/SKIP.

**Se ci sono FAIL:**

1. Per ogni endpoint fallito, analizza il codice HTTP ricevuto vs atteso
2. Leggi il body della risposta (ri-esegui con `VERBOSE=1` se necessario)
3. Identifica la causa root:
   - `401` → token scaduto o malformato. Chiedi un nuovo token.
   - `404` → route non registrata. Verifica `Routing.kt` e che il deploy sia andato a buon fine.
   - `500` → errore server. Leggi il body per il messaggio. Cause comuni:
     - Collection name sbagliato (server vs client mismatch)
     - Campo nullable in DTO Protobuf
     - `.get()` bloccante senza `withContext(Dispatchers.IO)`
     - Firestore permission denied (IAM o security rules)
   - `400` → body malformato. Verifica i DTO `@Serializable` nel server.
4. Se il fix e' evidente (< 5 righe, non architetturale), applicalo direttamente
5. Se il fix e' complesso, riporta il problema con: endpoint, codice ricevuto, body errore, file sorgente coinvolto

**Se tutti PASS:**

Riporta il summary e conferma che il server e' fully operational.

### Step 4 — Test di sicurezza critico (SocialGraph whitelist)

Lo script include gia' un test che invia `followerCount:99999` via `PATCH /profiles/me` e verifica che il server lo ignori. Se questo test FAIL, e' un **problema critico di sicurezza (IDOR)** — segnalalo immediatamente.

### Step 5 — Report finale

Produci un report strutturato cosi':

```
## Smoke Test Report — {data}

**Server**: https://calmify-server-23546263069.europe-west1.run.app
**Commit**: {ultimo commit hash}

### Risultati
- PASS: X
- FAIL: Y
- SKIP: Z

### Endpoint falliti (se presenti)
| # | Endpoint | Atteso | Ricevuto | Causa |
|---|----------|--------|----------|-------|
| 1 | ...      | ...    | ...      | ...   |

### Fix applicati (se presenti)
- {descrizione fix + file modificato}

### Note
- {eventuali osservazioni}
```

## I 25 Route Groups Testati

| # | Gruppo | Endpoint chiave | Note |
|---|--------|-----------------|------|
| 1 | Health | `GET /health` | No auth |
| 2 | Diary | CRUD completo | Create + get + update + delete |
| 3 | Chat | Session + messages | Create + send + delete |
| 4 | Insights | List | Read-only |
| 5 | Profile | Get + psychological | Read-only |
| 6 | Social | Feed + threads + like | CRUD + reactions |
| 7 | Dashboard | Home + feature flags | Flags = no auth |
| 8 | Notifications | List + unread + read-all | |
| 9 | Wellness | 13 tipi x (list + day) | gratitude, energy, sleep, meditation, habits, movement, reframe, wellbeing, awe, connection, recurring, block, values |
| 10 | AI | Usage stats only | SKIP generation (costa Gemini) |
| 11 | Sync | Get changes | |
| 12 | Search | Threads + users | |
| 13 | Presence | Online/offline cycle | |
| 14 | Moderation | Toxicity + sentiment + mood | |
| 15 | Waitlist | Signup | |
| 16 | Media | Upload URL + resolve | Presigned GCS V4 |
| 17 | Messaging | Conversations + WS check | Richiede TARGET_ID per mutations |
| 18 | Avatar | List only | SKIP create (pipeline Gemini) |
| 19 | Environment | GET + PUT + verify | Phase 5 NEW |
| 20 | Garden | Explored + favorite toggle | Phase 5 NEW |
| 21 | Ikigai | GET + PUT + DELETE | Phase 5 NEW |
| 22 | SocialGraph | Profile + whitelist + follow/block | Phase 5 NEW — test sicurezza incluso |
| 23 | Payments | Subscription state + products | SKIP checkout (crea sessioni Stripe) |
| 24 | Webhook | Reject unsigned | Verifica firma Stripe |
| 25 | GDPR | Export only | SKIP delete (distruttivo) |

## Endpoint deliberatamente SKIPPATI (non testare)

- `POST /ai/chat`, `POST /ai/insight`, `POST /ai/analyze` — costano token Gemini
- `POST /avatars` — triggera pipeline Gemini + Cloud Run VRM
- `POST /payments/checkout-session`, `POST /payments/portal-session` — crea sessioni Stripe
- `POST /gdpr/delete` — **DISTRUTTIVO**, cancella l'account

## File rilevanti per debug

| File | Ruolo |
|------|-------|
| `calmify-server/src/main/kotlin/com/lifo/server/plugins/Routing.kt` | Registrazione route |
| `calmify-server/src/main/kotlin/com/lifo/server/routing/*.kt` | Definizione endpoint |
| `calmify-server/src/main/kotlin/com/lifo/server/service/*.kt` | Business logic + Firestore |
| `calmify-server/src/main/kotlin/com/lifo/server/di/ServerModule.kt` | DI services |
| `data/network/src/commonMain/kotlin/com/lifo/network/repository/*.kt` | Client KMP repos |
| `app/src/main/java/com/lifo/calmifyapp/di/KoinModules.kt` | Koin bindings + BackendConfig flags |
| `scripts/smoke-test-e2e.sh` | Lo script di test |

## Regole di condotta

1. **Non modificare lo script di test** a meno che un endpoint abbia cambiato contratto
2. **Non eseguire endpoint distruttivi** (GDPR delete, account deletion)
3. **Non creare avatar** (pipeline costosa)
4. **Non generare AI content** (costo Gemini)
5. Se un test fallisce con 401, il token e' scaduto — chiedi un nuovo token prima di continuare
6. Se un test fallisce con 500, leggi il body e cerca la causa nel codice server prima di segnalare
7. Applica fix solo se sono evidenti e < 5 righe. Per fix architetturali, riporta e attendi istruzioni.
