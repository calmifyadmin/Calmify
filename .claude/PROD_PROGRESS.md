# Calmify — Production Progress Tracker

> Stato di avanzamento verso il lancio su Play Store.
> Riferimento dettagliato: `PRODUCTION_READY_PLAN.md`
> Ultimo aggiornamento: 2026-04-19

## Milestones macro (2026-04-19)
- [x] **Level 1 KMP REST (2026-04-18)** — 36/36 repos migrate, server Cloud Run operational, smoke E2E 91/92 verdi
- [~] **Sprint i18n pre-Level 3 (~5-6 gg, Fase A + A' + A'' DONE 2026-04-19)** — EN default + 12 lingue ALL SCAFFOLDED (6 new: AR/ZH/JA/KO/HI/TH, baseline keys per ognuna) + Detekt + typed `Strings` facade. Next: Fase B (40 common actions × 12 lang). Vedi `memory/i18n_strategy.md`
- [ ] **Level 3 iOS + Web** — targets KMP aggiuntivi (stimato ~25 gg dopo sprint i18n). Vedi `memory/kmp_action_blocks.md`

## Legenda
- [ ] Da fare
- [~] In corso
- [x] Completato

---

## FASE 0: Pre-Alpha (Settimana 1-2)

### Build & Signing
- [ ] Generare keystore di produzione
- [ ] Configurare `signingConfigs.release` in `app/build.gradle`
- [ ] Iscriversi a Google Play App Signing
- [ ] Build AAB firmato (`.aab`)
- [x] ProGuard rules complete (Firebase, Koin, Serialization, Filament, Compose, OkHttp)
- [ ] Test release build su device fisico
- [x] Abilitare Crashlytics (plugin + dependency attivi)
- [ ] Verificare upload symbolication NDK (Oboe/Filament)

### Legal & Privacy
- [ ] Privacy Policy conforme GDPR (Art. 13-14)
- [ ] Terms of Service
- [ ] Hosting Privacy/Terms su URL pubblico (calmify.app o GitHub Pages)
- [x] Link Privacy/Terms in-app (Settings > Legale — Privacy Policy + ToS navigation items)
- [x] Prominent Disclosure in onboarding (step 3: dati benessere, diario, voce + checkbox consenso)
- [x] Consenso GDPR checkbox (non pre-spuntato) in onboarding step 3
- [x] "Elimina account" in Settings (Firestore + Storage + SQLDelight + Auth)
- [ ] Link web cancellazione account
- [x] Health disclaimer in-app (Settings > Informazioni + HealthDisclaimerCard)

### Sicurezza
- [x] Rimuovere `usesCleartextTraffic="true"` → Network Security Config (`network_security_config.xml`)
- [x] Verificare Firestore security rules per OGNI collection (16 top-level + subcollections)
- [ ] Verificare API key restrictions (package name + SHA-1)

### i18n
- [x] Estrarre stringhe hardcoded → Compose Multiplatform Resources
- [x] `core/ui/composeResources/values/strings.xml` (IT, default — 90+ stringhe)
- [x] `core/ui/composeResources/values-en/strings.xml` (EN — 90+ stringhe)
- [x] Wire stringhe nei composable: OnboardingScreen, PaywallScreen, WaitlistDialog, SubscriptionEntryPoint, SettingsScreen

### PRO Switch
- [x] `WaitlistSubscriptionRepository` implementato (commonMain)
- [x] `WaitlistDialog` (email capture UI con animated states)
- [x] Koin switch basato su `premium_enabled` flag (MongoKoinModule)
- [ ] Verificare `premium_enabled = false` in Firestore `config/flags`
- [x] Analytics events: `paywall_viewed`, `waitlist_signup`

---

## FASE 1: Alpha Chiusa (Settimana 3-4)

### Play Console
- [ ] Data Safety Section compilata
- [ ] Health Apps Declaration Form compilato
- [ ] Content Rating IARC completato
- [ ] App name verificato <= 30 char
- [ ] Store listing: icon 512x512, feature graphic 1024x500, 8 screenshot

### Testing
- [ ] Deploy su Google Play Internal Testing
- [ ] 10 tester interni: test funzionale completo
- [ ] Fix bug critici
- [ ] Analytics funnel completo (install → waitlist)
- [ ] Verificare billing NON attivo con `premium_enabled = false`
- [ ] Test paywall → waitlist flow end-to-end

---

## FASE 2: Beta Chiusa (Settimana 5-8)

- [ ] Google Play Closed Alpha: 50-100 utenti
- [ ] Form feedback strutturato
- [ ] Monitorare crash rate, ANR rate, retention D1/D7/D30
- [ ] Monitorare paywall views + waitlist signups
- [ ] Ottimizzare onboarding (drop-off data)
- [ ] Store listing finale (screenshot, video, descrizione + disclaimer)

---

## FASE 3: Beta Aperta + Validazione (Settimana 9-16)

- [ ] Google Play Open Beta: 500-1000 utenti
- [ ] Monitorare metriche validazione (waitlist>100, D7>25%, paywall>20%)
- [ ] Iterare su top 5 richieste utenti
- [ ] Landing page web (calmify.app)
- [ ] Audit sicurezza finale
- [ ] Audit GDPR finale
- [ ] **DECISIONE GO/NO-GO monetizzazione**

---

## FASE 4: Prep Monetizzazione (Settimana 17-18) — SOLO SE GO

- [ ] Aprire P.IVA forfettaria
- [ ] Verificare contratto lavoro (clausole non concorrenza)
- [ ] Configurare Google Play Billing (€5.99/mese, €49.99/anno)
- [ ] Purchase verification server-side (Cloud Function)
- [ ] Test billing end-to-end con test products
- [ ] Email template waitlist

---

## FASE 5: Lancio PRO (Settimana 19-20) — SOLO SE GO

- [ ] Flip `premium_enabled = true`
- [ ] Email waitlist con promo primo mese gratis
- [ ] Rilascio graduale: 20% → 50% → 100%
- [ ] Monitorare conversion, MRR, churn
- [ ] Supporto utenti attivo

---

## KMP Migration (parallelo)

### Completato
- [x] Convention plugins build-logic (calmify.kmp.library, calmify.kmp.compose)
- [x] 17/18 moduli KMP
- [x] Hilt → Koin 4.1.1
- [x] Room → SQLDelight 2.0.2
- [x] Navigation Compose → Decompose 3.4.0
- [x] MVI pattern su tutti i 18+ ViewModel
- [x] Firebase RemoteConfig → Firestore document
- [x] Firebase RTDB Presence → Firestore collection
- [x] Cloud Functions → Ktor HTTP (commonMain)
- [x] MediaCarousel unificato (expect/actual, M3 Expressive)
- [x] WriteViewModel → MediaUploadRepository (no Firebase diretto in UI)
- [x] Eliminati: MediaGrid, Gallery(), FirebaseImageHelper
- [x] java.time → kotlinx.datetime (parziale)
- [x] Coil 3.1.0 (ABI compatible con Kotlin 2.1.0)

### Da fare
- [ ] Fase 1: 91 file androidMain → commonMain (zero import Android)
- [ ] Fase 2: 28 file Firebase SDK → Ktor REST API (commonMain)
- [ ] Fase 3: 17 file expect/actual (Audio, Filament, Camera)

---

## Metriche Chiave

| Metrica | Attuale | Target |
|---------|---------|--------|
| commonMain files | 217 (61%) | 335 (~95%) |
| androidMain files | 137 (39%) | ~20 (~5%) |
| Build status | assembleDebug OK | bundleRelease OK |
| Crash-free rate | N/A | >99.5% |
| ProGuard | Full rules (150+) | Full rules |
| Crashlytics | Attivo | Attivo |
| Privacy Policy | Inesistente | Online + in-app |
| Data Safety | Non compilata | Compilata |
| Feature flags | Firestore `config/flags` | Verificato |
