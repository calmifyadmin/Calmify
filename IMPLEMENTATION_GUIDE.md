# Calmify - Psychological Insights Implementation Guide (v2.0)

**Purpose**: Pragmatic, step-by-step guide for implementing psychological insights by coordinating work between manual tasks (Firebase Console) and automated tasks (Claude Code).

**Philosophy**: Build → Test → Optimize (not optimize → build)

**For**: Project owner/developer
**Updated**: 2025-01-19

---

## 📋 Quick Start

### What This Guide Does

This guide divides implementation into **3 main phases** over 10-12 weeks:

1. **Phase 1 (Weeks 1-4)**: MVP - Get features working (NO optimization)
2. **Phase 2 (Weeks 5-8)**: AI Integration - Add Cloud Functions + Gemini
3. **Phase 3 (Weeks 9-12)**: Optimization - Indexes, Vector Search, Performance tuning

### Key Principle

**Firebase creates collections automatically** when you save the first document.

**Firebase tells you when indexes are needed** via error messages with direct links.

**No premature optimization** - build first, optimize later when you have real data.

---

## 🎯 How to Use This Guide

### Reading Instructions

- **⏸️ MANUAL TASK**: You do this manually (Firebase Console or terminal)
- **▶️ TRIGGER CLAUDE CODE**: Copy/paste the exact prompt to Claude Code
- **✅ VERIFY**: Check that work completed successfully
- **📍 CHECKPOINT**: Don't proceed unless ALL items are checked

### Golden Rules

1. **Follow phases sequentially** - Don't skip ahead
2. **Use exact trigger prompts** - Claude Code needs precise context
3. **Verify before proceeding** - Catch issues early
4. **Keep PSYCHOLOGICAL_INSIGHTS_PLAN.md open** - Claude Code references it constantly
5. **Don't create indexes until Phase 3** - Firebase will tell you when needed

---

## 📚 Reference Documents

| Document | Purpose | When Claude Code Uses It |
|----------|---------|-------------------------|
| `PSYCHOLOGICAL_INSIGHTS_PLAN.md` | Feature specification (data models, AI prompts) | **Every task** - primary reference |
| `PROJECT_TECHNICAL_REPORT.md` | Current codebase state | When integrating with existing features |
| `CLAUDE.md` (JARVIS.md) | Project conventions & build commands | For coding style, module structure |
| `IMPLEMENTATION_GUIDE.md` | This file - phase-by-phase workflow | To know current progress |

---

## 🚀 PHASE 1: MVP - Working Features (Weeks 1-4)

**Goal**: Get features working end-to-end with NO optimization

### Week 1: Enhanced Diary Input (Android)

#### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 1 from PSYCHOLOGICAL_INSIGHTS_PLAN.md: Enhanced Diary Input.

Extend the Diary model with 6 new fields (backward compatible with defaults):
- emotionIntensity: Int = 5
- stressLevel: Int = 5
- energyLevel: Int = 5
- calmAnxietyLevel: Int = 5
- primaryTrigger: Trigger = Trigger.NONE
- dominantBodySensation: BodySensation = BodySensation.NONE

Create enums: Trigger, BodySensation (see PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 1.1)

Create PsychologicalMetricsSheet composable:
- Material3 Sliders (0-10 range) for intensity/stress/energy/calm
- Preset buttons for Trigger enum (Work, Family, Health, etc.)
- Preset buttons for BodySensation enum
- Skip button (keeps defaults)
- Target completion: <10 seconds

Update WriteViewModel to handle new fields.
Integrate into WriteScreen after mood selection.

Reference existing WriteScreen patterns from PROJECT_TECHNICAL_REPORT.md.
```

#### ✅ VERIFY

**After Claude Code finishes**:

```bash
# Build project
cmd /c "gradlew.bat :features:write:build"

# Install on device
cmd /c "gradlew.bat installDebug"
```

**Manual Testing**:
1. Open app → Tap "New Diary"
2. Fill title, description, select mood
3. **NEW**: Should see psychological metrics sheet
4. Move sliders, select trigger/sensation
5. Tap Skip → defaults to 5/NONE
6. Save diary

**Check Firestore Console**:
- Go to https://console.firebase.google.com → Firestore
- Find your test diary in `diaries` collection
- Verify new fields exist: `emotionIntensity`, `stressLevel`, etc.
- Values should match what you selected

#### 📍 CHECKPOINT 1

Before proceeding to Week 2:
- [ ] Project builds successfully (`gradlew.bat build` passes)
- [ ] `Diary` model has 6 new fields with defaults
- [ ] `PsychologicalMetricsSheet` appears in UI
- [ ] Sliders work (0-10 range)
- [ ] Trigger/BodySensation buttons functional
- [ ] Skip button works (defaults to 5/NONE)
- [ ] Data saves to Firestore correctly
- [ ] Existing diaries still work (backward compatible)

---

### Week 2: Wellbeing Snapshot (Android)

#### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 2 from PSYCHOLOGICAL_INSIGHTS_PLAN.md: Wellbeing Snapshot.

Create WellbeingSnapshot data class as specified in Section 1.2:
- 10 metrics (lifeSatisfaction, workSatisfaction, relationshipsQuality, mindfulness, purpose, gratitude, autonomy, competence, relatedness, loneliness)
- All Int fields (0-10 range)
- notes: String (optional)
- completionTime: Long (track UX metric)
- wasReminded: Boolean

Create new screen or add to existing module:
1. SnapshotScreen.kt (8-10 item questionnaire with Material3 Sliders)
2. SnapshotViewModel.kt (save to Firestore)
3. WellbeingRepository.kt (Firestore operations)

Add navigation route.
Add entry point from Home screen (FAB action or Settings button).

Use Material3 design patterns from existing features.
```

#### ✅ VERIFY

```bash
cmd /c "gradlew.bat installDebug"
```

**Manual Testing**:
1. Open app → Navigate to Wellbeing Snapshot (new button)
2. Complete questionnaire (slide all 8-10 items)
3. Add optional note
4. Submit

**Check Firestore Console**:
- Collection: `wellbeing_snapshots` (should auto-create on first save)
- Verify document exists with all fields
- Check `completionTime` is populated (milliseconds)

#### 📍 CHECKPOINT 2

- [ ] `WellbeingSnapshot` data class created
- [ ] Snapshot screen accessible from Home
- [ ] All 10 questions display with sliders
- [ ] Optional notes field works
- [ ] Data saves to Firestore
- [ ] Collection `wellbeing_snapshots` auto-created
- [ ] No Firestore index errors (data set still small)

---

### Week 3: Firebase Setup - Cloud Functions

#### ⏸️ MANUAL TASKS

**Prerequisites**:
- Node.js 18+ installed: `node --version`
- Firebase CLI installed: `npm install -g firebase-tools`

**Terminal** (in project root):

```bash
# 1. Login to Firebase
firebase login

# 2. Initialize Firebase Functions
firebase init

# Select (Space to check, Enter to confirm):
# - Functions
# - Firestore (if not already initialized)

# Prompts:
# - Use existing project → Select your Calmify project
# - Language: TypeScript
# - ESLint: Yes
# - Install dependencies: Yes
```

This creates a `functions/` directory.

```bash
# 3. Install Genkit dependencies
cd functions
npm install genkit @genkit-ai/googleai
npm install firebase-admin zod @google-cloud/firestore
cd ..
```

```bash
# 4. Set Gemini API Key as secret
firebase functions:secrets:set GEMINI_API_KEY
# Paste your API key from https://aistudio.google.com/app/apikey
```

```bash
# 5. Verify secret
firebase functions:secrets:access GEMINI_API_KEY
# Should show redacted key
```

**Enable Google Cloud APIs**:

Go to https://console.cloud.google.com → Select your project

Navigate to: APIs & Services → Library

Search and **Enable** each:
- Cloud Functions API
- Cloud Scheduler API
- Cloud Build API
- Generative Language API
- Secret Manager API

#### ✅ VERIFY

```bash
# Verify Firebase CLI
firebase projects:list
# Should show your project

# Verify functions directory
ls -la functions/

# Verify Genkit installed
cd functions && npm list genkit && cd ..
```

#### 📍 CHECKPOINT 3

- [ ] Firebase CLI authenticated
- [ ] `functions/` directory created with TypeScript setup
- [ ] Genkit dependencies installed (`npm list genkit` shows version)
- [ ] `GEMINI_API_KEY` secret configured
- [ ] 5 Google Cloud APIs enabled

---

### Week 4: Cloud Functions - Insight Generation (MVP)

#### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 4 from PSYCHOLOGICAL_INSIGHTS_PLAN.md: Cloud Functions for AI Insight Generation.

Create Cloud Functions with Genkit as specified in Section 4:

1. Create functions/src/genkit/insights.ts:
   - Genkit flow: generateDiaryInsight
   - Use Gemini 2.0 Flash
   - Input schema: diaryText, mood, stressLevel
   - Output schema (Zod): InsightSchema with fields from Section 1.3
   - Use the exact AI prompt from Section 4.1 (compassionate, CBT-informed)

2. Create functions/src/index.ts:
   - onDocumentCreated trigger for 'diaries/{diaryId}'
   - Call generateInsight Genkit flow
   - Save result to 'diary_insights' collection
   - Include error handling

3. Create functions/src/utils/schemas.ts:
   - Zod schemas for all data models

Follow TypeScript patterns from existing Cloud Functions projects.
Test locally before deploying (I'll deploy manually).
```

#### ⏸️ MANUAL TASKS

**After Claude Code creates the functions**:

**Test with Firebase Emulator (Recommended)**:

```bash
# Start emulators
firebase emulators:start

# Open Emulator UI: http://localhost:4000
```

In Emulator UI:
1. Go to Firestore tab
2. Create test diary document manually
3. Watch Functions tab → `onDiaryCreated` should execute
4. Check `diary_insights` collection for new document

**Deploy to Firebase**:

```bash
# Build TypeScript
cd functions
npm run build
cd ..

# Deploy
firebase deploy --only functions

# Wait 2-5 minutes for deployment
```

**Verify deployment**:

```bash
firebase functions:log
# Should show "Function deployed successfully"
```

#### ✅ VERIFY

**End-to-End Test**:

1. Open Android app
2. Create diary with meaningful content:
   - Title: "Stressful day at work"
   - Description: "My boss criticized my project in front of everyone. I feel like a failure. I always mess things up."
   - Mood: Sad
   - Stress: 8/10
3. Save diary
4. **Wait 5-10 seconds**

**Check Firestore Console**:
- Collection: `diary_insights` (auto-created by Cloud Function)
- Find insight for your diary (match `diaryId`)
- Verify fields:
  - `sentimentPolarity` (should be negative, e.g., -0.7)
  - `topics` (e.g., ["work", "criticism", "self-worth"])
  - `cognitivePatterns` (should include "LABELING", "OVERGENERALIZATION")
  - `summary` (1-2 sentence compassionate summary)
  - `suggestedPrompts` (2-3 reflective questions)

**Check Logs**:

```bash
firebase functions:log --only onDiaryCreated
# Should show successful execution, no errors
```

#### 📍 CHECKPOINT 4 (END OF PHASE 1 MVP)

- [ ] Cloud Functions deployed successfully
- [ ] `onDiaryCreated` trigger fires when diary saved
- [ ] DiaryInsight generated within 10 seconds
- [ ] Sentiment analysis accurate (polarity matches diary tone)
- [ ] Topics detected correctly
- [ ] Cognitive patterns detected (CBT framework)
- [ ] Summary is compassionate and non-judgmental
- [ ] Collection `diary_insights` auto-created
- [ ] **NO Firestore index errors yet** (data set still small)
- [ ] No errors in `firebase functions:log`

**🎉 PHASE 1 COMPLETE**: You have a working MVP! Users can:
- Add psychological metrics to diaries
- Complete weekly wellbeing snapshots
- Get AI-generated insights automatically

---

## 🚀 PHASE 2: AI Integration & Polish (Weeks 5-8)

**Goal**: Polish UI, add profile computation, implement notifications

### Week 5: Insights Display UI (Android)

#### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 5 from PSYCHOLOGICAL_INSIGHTS_PLAN.md: Insights Display UI.

Create a screen to display AI-generated insights to users:

1. Create InsightScreen.kt (card-based Material3 UI):
   - Sentiment indicator (color-coded: green=positive, red=negative, gray=neutral)
   - Topics as Material3 chips
   - Cognitive patterns with info icons (explainability)
   - AI summary in expandable card
   - Suggested prompts as clickable cards
   - "This insight is incorrect" feedback button

2. Create InsightViewModel.kt:
   - Fetch insights from Firestore for a diary
   - Handle user corrections (save to Firestore)
   - State management with StateFlow

3. Create InsightRepository.kt:
   - Firestore query: get insights by diaryId
   - Save user correction

4. Add navigation route and deep linking (for FCM notifications later)

5. Add entry point (button on home feed)

Follow existing screen patterns from features/chat and features/write.
```

#### ✅ VERIFY

```bash
cmd /c "gradlew.bat installDebug"
```

**Manual Testing**:
1. Open app
2. View a diary that has an insight (created in Week 4 test)
3. Tap "View Insight" button
4. **Verify UI elements**:
   - [ ] Sentiment indicator visible (color matches polarity)
   - [ ] Topics displayed as chips
   - [ ] Cognitive patterns listed with info icons
   - [ ] Tap info icon → shows explainability tooltip
   - [ ] AI summary displayed
   - [ ] Suggested prompts shown (2-3 questions)
   - [ ] "This insight is incorrect" button visible
5. **Test correction**:
   - Tap "Incorrect" → Enter feedback → Submit
   - Check Firestore: insight document should have `userCorrection` field

#### 📍 CHECKPOINT 5

- [ ] Insights screen accessible from diary view
- [ ] All UI elements render correctly
- [ ] Sentiment indicator color matches data
- [ ] Topics and patterns display accurately
- [ ] User correction feature works
- [ ] Data updates in Firestore after correction

---

### Week 6: Weekly Profile Computation (Cloud Functions)

#### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 6 from PSYCHOLOGICAL_INSIGHTS_PLAN.md: Weekly Profile Computation.

Create Cloud Function to compute psychological profiles:

1. Create functions/src/scheduler/compute-profiles.ts:
   - HTTP Cloud Function (triggered by Cloud Scheduler)
   - Fetch all active users from Firestore
   - For each user:
     * Get diaries from past 7-14 days
     * Get wellbeing snapshots from past week
     * Calculate metrics (see Section 1.4):
       - stressBaseline (weighted average)
       - stressVolatility (standard deviation)
       - stressPeaks (detect peaks above threshold 7)
       - moodBaseline, moodVolatility, moodTrend
       - resilienceIndex (recovery time after peaks)
     * Save to 'psychological_profiles' collection

2. Create functions/src/utils/profile-calculator.ts:
   - calculateWeightedAverage(values) - exponential decay
   - calculateStdDev(values)
   - detectPeaks(values, threshold)
   - calculateResilience(values, peaks)
   - detectTrend(values) - returns IMPROVING/STABLE/DECLINING

3. Update functions/src/index.ts:
   - Export HTTP function: computeWeeklyProfiles

Include error handling, logging, and batch processing (max 60s per user).
```

#### ⏸️ MANUAL TASKS

**After Claude Code creates functions**:

```bash
# Deploy updated functions
cd functions
npm run build
cd ..
firebase deploy --only functions
```

**Create Cloud Scheduler Job**:

Go to https://console.cloud.google.com → Cloud Scheduler

Click **Create Job**:
- Name: `compute-weekly-profiles`
- Region: `us-central1` (match Functions region)
- Frequency: `0 2 * * 0` (Sunday 2 AM)
- Timezone: Your timezone
- Target: **HTTP**
- URL: `https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/computeWeeklyProfiles`
  - Replace `[YOUR_PROJECT_ID]` with your actual Firebase project ID
- HTTP method: **POST**
- Auth header: **Add OIDC token**
  - Service account: `[YOUR_PROJECT_ID]@appspot.gserviceaccount.com`
- Click **Create**

#### ✅ VERIFY

**Manual Trigger** (don't wait for Sunday):

```bash
# Manually trigger the job
curl -X POST https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/computeWeeklyProfiles \
  -H "Authorization: Bearer $(gcloud auth print-identity-token)"
```

Or click **"Run Now"** in Cloud Scheduler console.

**Check Firestore Console**:
- Collection: `psychological_profiles` (auto-created)
- Should have documents for all users with diaries
- Verify fields:
  - [ ] `stressBaseline` (float 0-10)
  - [ ] `stressVolatility` (float >= 0)
  - [ ] `stressPeaks` (array of peak objects)
  - [ ] `moodBaseline`, `moodTrend`
  - [ ] `resilienceIndex` (0-1)
  - [ ] `diaryCount`, `snapshotCount`
  - [ ] `confidence` (0-1)

**Check Logs**:

```bash
firebase functions:log --only computeWeeklyProfiles
# Should show successful execution for each user
```

#### 📍 CHECKPOINT 6

- [ ] `computeWeeklyProfiles` function deployed
- [ ] Cloud Scheduler job created
- [ ] Manual trigger works
- [ ] Profiles computed for all users
- [ ] All metrics calculated correctly
- [ ] Collection `psychological_profiles` auto-created
- [ ] **Still NO Firestore index errors** (queries are simple)

---

### Week 7: Profile Dashboard UI (Android)

#### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 7: Profile Dashboard UI.

Create a screen to visualize psychological profile trends:

1. Evaluate charting library options for Jetpack Compose:
   - Option A: Vico (recommended for Compose)
   - Option B: Compose Canvas (custom charts)
   - Choose the best option and explain why

2. Create ProfileDashboard.kt:
   - 4-week rolling line chart (X: weeks, Y: 0-10)
   - Three lines: stress baseline, mood baseline, energy (if available)
   - Stress peaks timeline (vertical markers on chart)
   - Resilience score (progress bar or gauge)
   - Trend indicators (↑ IMPROVING, → STABLE, ↓ DECLINING)
   - Handle empty state ("Not enough data yet")

3. Create ProfileViewModel.kt:
   - Fetch last 4 weeks of profiles
   - Transform data for chart display
   - State management (StateFlow)

4. Create ProfileRepository.kt:
   - Firestore query: get profiles for user (last 4 weeks)

5. Add navigation route
6. Add entry point (Settings screen or Home dashboard)

Use Material3 theming for chart colors.
```

#### ✅ VERIFY

```bash
cmd /c "gradlew.bat installDebug"
```

**Manual Testing**:
1. Open app → Navigate to Profile Dashboard
2. **Verify charts**:
   - [ ] 4-week line chart displays
   - [ ] X-axis shows week numbers
   - [ ] Y-axis shows 0-10 scale
   - [ ] Lines visible for stress/mood (different colors)
   - [ ] Stress peaks marked on timeline
3. **Test empty state**:
   - Create new test account with no data
   - Should show "Not enough data" message
4. **Test data accuracy**:
   - Compare chart values to Firestore data
   - Values should match `stressBaseline`, `moodBaseline` from profiles

#### 📍 CHECKPOINT 7

- [ ] Profile dashboard accessible
- [ ] Charting library integrated
- [ ] 4-week rolling chart renders correctly
- [ ] Trends match Firestore data
- [ ] Empty state handled gracefully
- [ ] UI follows Material3 design

---

### Week 8: FCM Notifications & Reminders

#### ▶️ TRIGGER CLAUDE CODE (Android)

```
Implement Week 8 (Android side): FCM Notification System.

1. Update MainActivity.kt:
   - Create notification channels (4 channels):
     * Insights: NotificationManager.IMPORTANCE_DEFAULT
     * Reminders: NotificationManager.IMPORTANCE_HIGH
     * Wellness: NotificationManager.IMPORTANCE_LOW
     * Crisis: NotificationManager.IMPORTANCE_HIGH
   - Request notification permission (Android 13+)

2. Create CalmifyFirebaseMessagingService.kt:
   - Extend FirebaseMessagingService
   - Override onMessageReceived()
   - Parse notification types (INSIGHT_READY, WEEKLY_SNAPSHOT, CRISIS)
   - Show notifications with correct channel
   - Handle deep linking (PendingIntent to screens)
   - Save FCM token to Firestore on token refresh

3. Update AndroidManifest.xml:
   - Register FirebaseMessagingService

4. Create repository function to save FCM token:
   - Save to Firestore: users/{userId}/fcmToken

Reference notification patterns from existing Android projects.
```

#### ▶️ TRIGGER CLAUDE CODE (Cloud Functions)

```
Implement Week 8 (Cloud Functions side): FCM Reminders.

1. Create functions/src/scheduler/send-reminders.ts:
   - Function: sendWeeklyReminders (HTTP, Cloud Scheduler triggered)
   - Query users who haven't completed snapshot this week
   - Check recent profiles for adaptive logic:
     * If stressBaseline > 7 → skip notification (give them a break)
     * If snapshotCount = 0 → send reminder
   - Send FCM notification via Firebase Admin SDK

2. Create functions/src/utils/fcm-helper.ts:
   - Helper: sendFCM(userId, notification)
   - Get user FCM token from Firestore
   - Send message via admin.messaging().send()

3. Update functions/src/index.ts:
   - Export HTTP function: sendWeeklyReminders

Include error handling for missing FCM tokens.
```

#### ⏸️ MANUAL TASKS

**After Claude Code finishes**:

```bash
# Deploy functions
cd functions
npm run build
cd ..
firebase deploy --only functions
```

**Create Cloud Scheduler Job** (Weekly Reminder):

Cloud Console → Cloud Scheduler → Create Job:
- Name: `send-weekly-reminders`
- Frequency: `0 21 * * 0` (Sunday 9 PM)
- Target: HTTP
- URL: `https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/sendWeeklyReminders`
- Method: POST
- Auth: OIDC token (same service account)

**Test FCM**:

```bash
# Manually trigger reminder
curl -X POST https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/sendWeeklyReminders \
  -H "Authorization: Bearer $(gcloud auth print-identity-token)"
```

#### ✅ VERIFY

**Test Notification Channels** (Android Settings):
1. Android Settings → Apps → Calmify → Notifications
2. Should see 4 channels:
   - Insights
   - Reminders
   - Wellness Checks
   - Crisis
3. Test toggling each on/off

**Test Notification Delivery**:
1. Trigger weekly reminder (curl command above)
2. Check device → Should receive notification
3. Tap notification → Should open Wellbeing Snapshot screen (deep link)

**Test Insight Notification**:
1. Create a new diary
2. Wait 5-10 seconds
3. Should receive "New Insight Available" notification
4. Tap → Should open InsightScreen

#### 📍 CHECKPOINT 8 (END OF PHASE 2)

- [ ] 4 notification channels created
- [ ] FCM tokens saved to Firestore (`users/{userId}/fcmToken`)
- [ ] Deep linking works for all notification types
- [ ] Weekly reminder function works
- [ ] Adaptive logic implemented (skip if high stress)
- [ ] Notifications received on device
- [ ] Tapping notifications opens correct screens

**🎉 PHASE 2 COMPLETE**: Full AI integration working!
- Users get insights automatically
- Weekly profiles computed
- Notifications for reminders
- Complete UX for viewing insights and trends

---

## ⚡ PHASE 3: Optimization & Scale (Weeks 9-12)

**Goal**: Optimize performance, add advanced features, prepare for production

### Week 9: Firestore Indexing (Finally!)

#### Why NOW and not earlier?

**You now have**:
- Real users (or extensive testing data)
- Real query patterns
- Firebase has shown you which queries are slow

**Firebase will tell you** which indexes to create via error messages like:
```
FAILED_PRECONDITION: The query requires an index.
Create it here: https://console.firebase.google.com/project/.../indexes?create_composite=...
```

#### ⏸️ MANUAL TASKS

**Approach 1: Click Firebase Error Links** (Recommended)

When you see a Firestore query error in logs or UI:
1. Firebase provides a direct link to create the index
2. Click the link
3. Firebase auto-fills the index configuration
4. Click "Create"
5. Wait 5-15 minutes for index to build

**Approach 2: Manually Create Indexes** (If no errors yet)

Go to https://console.firebase.google.com → Firestore → Indexes

**Create these composite indexes**:

**Index 1**: `diary_insights` collection
- Collection ID: `diary_insights`
- Fields:
  - `ownerId` (Ascending)
  - `generatedAt` (Descending)
- Query scope: Collection

**Index 2**: `wellbeing_snapshots` collection
- Collection ID: `wellbeing_snapshots`
- Fields:
  - `ownerId` (Ascending)
  - `timestamp` (Descending)

**Index 3**: `psychological_profiles` collection
- Collection ID: `psychological_profiles`
- Fields:
  - `ownerId` (Ascending)
  - `year` (Descending)
  - `weekNumber` (Descending)

**Note**: Indexes will show "Building" status. This takes 5-30 minutes.

#### ✅ VERIFY

**Check Index Status**:
- Firestore Console → Indexes
- All 3 indexes should show **"Enabled"**

**Test Query Performance**:
1. Open app → Load Profile Dashboard (queries profiles)
2. Load Insights screen (queries insights)
3. All queries should complete in <500ms (check with Logcat or Chrome DevTools)

#### 📍 CHECKPOINT 9

- [ ] All 3 composite indexes created
- [ ] All indexes show "Enabled" status
- [ ] Queries complete in <500ms
- [ ] No FAILED_PRECONDITION errors in logs

---

### Week 10: Vector Search & RAG (Advanced)

#### Prerequisites Check

**BEFORE proceeding, verify**:
- [ ] At least 50-100 diaries in database
- [ ] At least 5 different users (for diverse data)

**If you don't have enough data**: Skip this week, come back later when database grows.

#### ⏸️ MANUAL TASK (Firebase Console)

**Create Vector Index**:

Go to Firestore Console → Indexes → Single field tab

Click **Create Index**:
- Collection: `diaries`
- Field path: `embedding`
- Index type: **Vector**
- Dimensions: **768** (Gemini text-embedding-004)
- Distance measure: **COSINE**
- Click **Create**

**IMPORTANT**: Vector index takes 15-30 minutes to build (possibly longer if you have many diaries).

#### ▶️ TRIGGER CLAUDE CODE (Cloud Functions)

```
Implement Week 10: Vector Search & RAG.

1. Create functions/src/genkit/embeddings.ts:
   - Genkit flow: generateEmbedding
   - Use 'text-embedding-004' model
   - Input: text string
   - Output: vector (768 dimensions)

2. Update functions/src/index.ts:
   - Add to onDiaryCreated trigger:
     * Generate embedding for diary.description
     * Update Firestore document with embedding field
     * Store as FieldValue.vector(embedding)

3. Create functions/src/callable/get-similar-diaries.ts:
   - HTTPS callable function
   - Input: userMessage, userId, limit (default 5)
   - Generate query embedding
   - Perform Firestore vector search (COSINE distance)
   - Return top N similar diaries

Use Firestore vector search API (not external vector DB).
```

#### ▶️ TRIGGER CLAUDE CODE (Android)

```
Integrate RAG into Chat feature:

Update ChatRepositoryImpl.kt:
1. Add function getDiaryContext(userMessage: String):
   - Call Cloud Function getSimilarDiaries via HTTP
   - Parse response (list of similar diaries)

2. Update buildPersonalizedPrompt():
   - Include RAG context in AI prompt
   - Format: "Based on your past journal entries: [diary1 summary], [diary2 summary]..."
   - Add similar diaries BEFORE user message

Reference existing ChatRepositoryImpl patterns from PROJECT_TECHNICAL_REPORT.md.
```

#### ⏸️ MANUAL TASKS

```bash
# Deploy updated functions
cd functions
npm run build
cd ..
firebase deploy --only functions
```

#### ✅ VERIFY

**Test Embedding Generation**:
1. Create new diary with unique content
2. Wait 5-10 seconds
3. **Firestore Console** → `diaries` → Find your diary
4. Should have `embedding` field (array of 768 floats)

**Test Vector Search**:
1. Create 3-4 diaries with different topics:
   - Diary 1: "Anxious about public speaking presentation"
   - Diary 2: "Great workout, feeling energized"
   - Diary 3: "Worried about job interview tomorrow"
   - Diary 4: "Relaxing weekend with family"
2. **Open Chat**
3. Send message: "I'm nervous about giving a presentation"
4. **Expected**: AI should reference Diary 1 (similar topic)
5. AI response should mention "I see you've written before about presentation anxiety..."

**Check Logs**:

```bash
firebase functions:log --only getSimilarDiaries
# Should show vector search queries executed
```

#### 📍 CHECKPOINT 10

- [ ] Vector index created and **Enabled**
- [ ] Embeddings generated for all diaries
- [ ] `embedding` field exists in Firestore (768-dim vector)
- [ ] `getSimilarDiaries` callable function works
- [ ] Vector search returns relevant diaries
- [ ] Chat uses RAG context in responses
- [ ] AI mentions relevant past diary entries

---

### Week 11: Ethical Safeguards & Privacy

#### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 11: Ethical Safeguards & Privacy.

Android side:
1. Create ConsentScreen.kt:
   - First-time modal explaining AI features
   - Clear privacy information
   - "I understand" / "Disable AI features" buttons
   - Store consent in SharedPreferences

2. Update SettingsScreen.kt:
   - Add "Privacy & AI" section:
     * Toggle: "Enable AI Insights"
     * Toggle: "Enable Profile Computation"
     * Toggle: "Share Anonymized Data" (default OFF)
     * Button: "Export My Data" (GDPR compliance)
     * Button: "Delete All Data"

3. Create DataExportViewModel.kt:
   - Query all user data (diaries, insights, snapshots, profiles)
   - Export as JSON
   - Save to Downloads folder or share intent

4. Add explainability:
   - Info icons next to cognitive patterns in InsightScreen
   - Tap icon → show tooltip: "Detected based on phrases: '...'"

Cloud Functions side:
1. Update functions/src/genkit/insights.ts:
   - Add crisis detection logic:
     * Check for keywords: "suicide", "harm myself", "end it all"
     * Check for sustained high stress (stressLevel >= 9 for 7+ days)
   - Return crisisLevel: HIGH, MEDIUM, NONE

2. Update functions/src/index.ts (onDiaryCreated):
   - If crisisLevel === HIGH:
     * Send emergency notification with crisis resources
     * Log to 'crisis_logs' collection (for follow-up)

3. Create crisis notification template:
   - Title: "We're here for you"
   - Body: Crisis resources (988 Suicide Lifeline, Crisis Text Line)
   - Priority: HIGH
   - Channel: crisis

Follow PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 7 for ethical guidelines.
```

#### ✅ VERIFY

**Test Consent Flow**:
1. Uninstall app and reinstall (or clear app data)
2. On first launch → Should see consent screen
3. Tap "Disable AI" → AI features should be OFF
4. Reinstall again → Tap "I understand" → AI features enabled

**Test Privacy Settings**:
1. Settings → Privacy & AI
2. Toggle "Enable AI Insights" OFF
3. Create diary → NO insight notification
4. Toggle ON → Create diary → Insight notification received

**Test Data Export**:
1. Settings → "Export My Data"
2. Wait for export (5-10 seconds)
3. Check Downloads folder or share dialog
4. Open JSON file → Should contain:
   - `diaries`: array of all diaries
   - `insights`: array of all insights
   - `snapshots`: array of all snapshots
   - `profiles`: array of all profiles

**Test Crisis Detection**:
1. Create diary with crisis keywords:
   - "I want to end it all. There's no point in living."
2. Wait 5-10 seconds
3. **Should receive HIGH-priority notification**:
   - Title: "We're here for you"
   - Body: Crisis resources (988, Crisis Text Line)
   - Notification channel: Crisis
4. **Check Firestore**:
   - Collection: `crisis_logs`
   - Should have log entry with userId, diaryId, timestamp

#### 📍 CHECKPOINT 11

- [ ] Consent screen shows on first launch
- [ ] Privacy settings functional
- [ ] "Enable AI Insights" toggle works
- [ ] Data export works (valid JSON with all data)
- [ ] Crisis detection active
- [ ] Crisis notifications sent for HIGH level
- [ ] Explainability tooltips added to InsightScreen
- [ ] GDPR compliance features implemented

---

### Week 12: Testing & Beta Launch

#### ▶️ TRIGGER CLAUDE CODE

```
Create integration tests for critical flows:

1. Create test file: ChatInsightIntegrationTest.kt
   - Test: Create diary → Wait → Verify insight created
   - Test: Insight has correct sentimentPolarity
   - Test: Cognitive patterns detected

2. Create test file: ProfileComputationTest.kt (if possible to test Cloud Functions)
   - Test: Profile metrics calculated correctly
   - Test: Stress peaks detected

3. Create test file: CrisisDetectionTest.kt
   - Test: Crisis keywords trigger HIGH level
   - Test: Sustained stress triggers MEDIUM level

4. Create TESTING_CHECKLIST.md:
   - Pre-launch verification steps
   - Performance benchmarks
   - Edge cases to test

Reference PROJECT_TECHNICAL_REPORT.md Section 6 for testing patterns.
```

#### ⏸️ MANUAL TASKS

**Beta Testing Setup**:

1. **Recruit 5-10 beta testers** (friends, colleagues, Reddit community)

2. **Deploy to Firebase App Distribution**:

```bash
# Build release APK
cmd /c "gradlew.bat assembleRelease"

# Upload to App Distribution
firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
  --app [YOUR_FIREBASE_APP_ID] \
  --groups "beta-testers" \
  --release-notes "Beta v1.0 - Psychological Insights Feature Testing"
```

3. **Collect feedback** (2 weeks)
   - Use Google Forms or Typeform
   - Questions:
     * How helpful are AI insights? (1-5)
     * Were any insights inaccurate?
     * Is the wellbeing snapshot easy to complete?
     * Any bugs or crashes?
     * Feature requests?

4. **Fix critical bugs** based on feedback

**Performance Testing**:

Run load test on Cloud Functions:

```bash
# Install Artillery (if not installed)
npm install -g artillery

# Create load test config (artillery will be provided by Claude Code)
artillery run load-test.yml
```

**Acceptance Criteria**:
- P95 latency < 5s for insight generation
- Error rate < 1%
- No cold starts under sustained load (>10 req/sec)

**Cost Monitoring**:

1. Firebase Console → Usage and Billing
2. Monitor for 1 week
3. Verify costs within budget:
   - Target: <$30/month for 100 users
   - Check: Firestore reads/writes, Cloud Functions invocations, Gemini API calls

#### ✅ VERIFY

**Pre-Launch Checklist**:

- [ ] All unit tests passing (`gradlew.bat test`)
- [ ] Integration tests passing
- [ ] Beta testing complete (5-10 users, 2 weeks)
- [ ] Critical bugs fixed (no P0 or P1 bugs remaining)
- [ ] Performance benchmarks met (P95 < 5s)
- [ ] Cost within budget (<$30/month per 100 users)
- [ ] Privacy policy updated (mentions AI features)
- [ ] Google Play Store listing ready
- [ ] Screenshots & promotional materials prepared
- [ ] Rollout plan defined (10% → 50% → 100%)

#### 📍 CHECKPOINT 12 (LAUNCH READY)

- [ ] All tests passing
- [ ] Beta feedback addressed
- [ ] Performance optimized
- [ ] Cost under control
- [ ] Privacy/legal compliance verified
- [ ] App Store submission materials ready

**🎉 PHASE 3 COMPLETE**: Ready for production launch!

---

## 🚀 Production Launch (Week 13+)

### Staged Rollout (Manual - Google Play Console)

**Week 13: Internal Testing (10% rollout)**

1. **Google Play Console** → Release → Production
2. Create new release
3. Upload AAB:
   ```bash
   cmd /c "gradlew.bat bundleRelease"
   # AAB: app/build/outputs/bundle/release/app-release.aab
   ```
4. **Staged rollout**: 10%
5. Monitor for 7 days:
   - Crashlytics: Check for crashes
   - Firebase Analytics: Check feature usage
   - Function logs: Check for errors
   - User reviews: Check for complaints

**Week 14: Expand to 50%**

If no critical issues:
- Play Console → Release → Update rollout → 50%
- Monitor for 7 more days

**Week 15: Full Release (100%)**

If still stable:
- Play Console → Release → Update rollout → 100%

---

## 📊 Post-Launch Monitoring

### Daily (First Week)

```bash
# Check Crashlytics
# Firebase Console → Crashlytics → Dashboard

# Check function logs
firebase functions:log --limit 50

# Check costs
# Firebase Console → Usage and Billing → Dashboard
```

### Weekly

- Analyze usage metrics (Firebase Analytics)
- Review user feedback (Play Store reviews)
- Check insight accuracy (sample user corrections)
- Monitor costs vs. budget

### Monthly

- Review psychological profile trends (anonymized aggregate)
- Bias testing (ensure no demographic disparities)
- Cost optimization (identify expensive queries)
- Feature requests from users

---

## 🎯 Success Metrics (After 1 Month)

| Metric | Target | How to Measure |
|--------|--------|----------------|
| **Weekly Active Users completing Snapshot** | 70% | Firebase Analytics event: `wellbeing_snapshot_completed` |
| **Insight Satisfaction** | >80% | Firestore: user corrections <20% of total insights |
| **Crisis Response Time** | <5 min | Function logs: time from diary save to notification |
| **AI Response Latency** | <2s | Firebase Performance: custom trace |
| **Cost per 1K Users** | <$30/month | Firebase Console → Usage and Billing |
| **App Crash Rate** | <0.1% | Crashlytics → Dashboard |

---

## 🆘 Troubleshooting Guide

### Issue: "FAILED_PRECONDITION: The query requires an index"

**Solution**:
1. Click the link in the error message
2. Firebase auto-creates the index
3. Wait 5-15 minutes
4. Retry query

### Issue: Cloud Function deployment fails

**Solution**:
```bash
# Re-authenticate
firebase login --reauth

# Check permissions
gcloud projects get-iam-policy [YOUR_PROJECT_ID]

# Redeploy with verbose logging
firebase deploy --only functions --debug
```

### Issue: FCM notifications not received

**Solution**:
1. Verify `google-services.json` in `app/` directory
2. Check FCM token saved to Firestore (`users/{userId}/fcmToken`)
3. Test with Firebase Console → Cloud Messaging → Send test message
4. Check notification channels enabled on device (Android Settings → Apps → Calmify → Notifications)

### Issue: Vector search returns no results

**Solution**:
1. Verify embeddings stored as `FieldValue.vector()` (not plain array)
2. Check vector index status (Firestore Console → Indexes)
3. Ensure query embedding dimensions match (768)
4. Wait for index to fully build (can take 30 min+)

### Issue: AI insights inaccurate

**Solution**:
1. Review prompt in `functions/src/genkit/insights.ts`
2. Adjust temperature (lower = more deterministic):
   ```typescript
   temperature: 0.7  // Try 0.6 or 0.5
   ```
3. Add more examples to system prompt
4. Collect user corrections and analyze patterns

### Issue: Costs higher than expected

**Solution**:
1. Firebase Console → Usage and Billing → Detailed usage
2. Common culprits:
   - Too many Firestore reads (optimize queries, use limits)
   - Gemini API overuse (add caching for similar queries)
   - Function cold starts (increase min instances to 1)
3. Implement caching:
   ```typescript
   // Cache insights for 24 hours (reduce duplicate analysis)
   const cachedInsight = await cache.get(diaryId);
   if (cachedInsight) return cachedInsight;
   ```

---

## 📚 Learning Resources

**Stuck? Check these**:

- **Firebase Docs**: https://firebase.google.com/docs
- **Genkit Docs**: https://firebase.google.com/docs/genkit
- **Gemini API**: https://ai.google.dev/gemini-api/docs
- **Jetpack Compose**: https://developer.android.com/jetpack/compose
- **Firestore Vector Search**: https://firebase.google.com/docs/firestore/vector-search

**Stack Overflow Tags**:
- `[firebase]` + `[cloud-functions]`
- `[google-cloud-firestore]` + `[vector-search]`
- `[android-jetpack-compose]`
- `[gemini-api]`

---

## ✅ Final Checklist Before You Start

**Before Week 1**:
- [ ] Read entire IMPLEMENTATION_GUIDE.md (this file)
- [ ] Read PSYCHOLOGICAL_INSIGHTS_PLAN.md (data models, AI specs)
- [ ] Firebase project exists and Blaze plan enabled
- [ ] Android Studio project builds successfully
- [ ] `google-services.json` in `app/` directory
- [ ] Firebase CLI installed and authenticated
- [ ] Node.js 18+ installed (for Cloud Functions)

**Ready to begin? Start with Week 1!**

---

*Updated by Jarvis AI - 2025-01-19*
*Philosophy: Build → Test → Optimize (No Premature Optimization)*
*Version 2.0 - Pragmatic Approach*
