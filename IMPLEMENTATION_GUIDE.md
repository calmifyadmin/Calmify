# Calmify - Psychological Insights Implementation Guide

**Purpose**: Step-by-step guide for implementing the psychological insights feature by coordinating work between Firebase/Google Cloud Console (manual) and Claude Code (automated).

**For**: Project owner/developer
**Prerequisites**: Basic understanding of Firebase, Android development, terminal usage

---

## 📋 Overview

This guide divides the implementation into **12 phases** that alternate between:
- 🔧 **Manual Tasks** (Firebase/Cloud Console) - Tasks you perform manually
- 🤖 **Claude Code Tasks** (Automated) - Tasks delegated to Claude Code with simple prompts

Each phase has:
- Clear **STOP/START** markers
- Specific **trigger prompts** for Claude Code
- **Verification steps** to ensure everything works before proceeding

---

## 🎯 How to Use This Guide

### Reading Instructions

- **⏸️ STOP HERE**: Complete manual tasks before proceeding
- **▶️ TRIGGER CLAUDE CODE**: Use the exact prompt provided
- **✅ VERIFY**: Check that the work completed successfully
- **📍 CHECKPOINT**: Ensure all steps up to this point are complete

### Golden Rules

1. **Never skip checkpoints** - Each phase builds on the previous
2. **Use exact trigger prompts** - Claude Code relies on these for context
3. **Verify before proceeding** - Catch issues early
4. **Keep PSYCHOLOGICAL_INSIGHTS_PLAN.md open** - Claude Code references it constantly
5. **Mark checkboxes** in Appendix D (Platform Setup Checklist) as you complete tasks

---

## 📚 Reference Documents

Claude Code will use these as "bible" references during implementation:

| Document | Purpose | When Claude Code Uses It |
|----------|---------|-------------------------|
| `PSYCHOLOGICAL_INSIGHTS_PLAN.md` | Complete feature specification | **Every phase** - for data models, AI prompts, technical architecture |
| `PROJECT_TECHNICAL_REPORT.md` | Current codebase state | When integrating with existing features (Chat, Write, Home) |
| `CLAUDE.md` (aka JARVIS.md) | Project conventions & build commands | For coding style, module structure, build/deploy |
| `IMPLEMENTATION_GUIDE.md` | This file - phase-by-phase workflow | To know current progress and next steps |

---

## 🚀 Phase 0: Pre-Flight Checklist (15 minutes)

### ⏸️ STOP - Manual Tasks

**Location**: Your local machine + Firebase Console

- [ ] **Install Node.js 18+** (for Firebase CLI)
  ```bash
  node --version  # Should show v18.x or higher
  ```

- [ ] **Install Firebase CLI**
  ```bash
  npm install -g firebase-tools
  firebase --version
  ```

- [ ] **Login to Firebase**
  ```bash
  firebase login
  ```

- [ ] **Verify project exists**
  - Go to [Firebase Console](https://console.firebase.google.com)
  - Confirm `calmify` or `calmify-dev` project exists
  - Note the **Project ID** (you'll need this later)

- [ ] **Enable Blaze plan** (if not already)
  - Firebase Console → ⚙️ Settings → Usage and Billing
  - Upgrade to Blaze (pay-as-you-go)
  - Set budget alert: $50/month

### ✅ Verification

```bash
firebase projects:list
# Should show your project
```

### ▶️ TRIGGER CLAUDE CODE

```
Read PSYCHOLOGICAL_INSIGHTS_PLAN.md and PROJECT_TECHNICAL_REPORT.md to understand the current state of the project. Confirm you understand the enhancement plan and current architecture. No action needed yet, just acknowledge.
```

**Expected Response**: Claude Code will summarize the plan and confirm understanding of:
- Current tech stack (Kotlin 2.1, Compose BOM 2025.01, Firebase)
- Existing modules (app, core, data/mongo, features)
- Firestore setup (calmify-native database)
- Enhancement scope (psychological insights, wellbeing snapshots, AI analysis)

---

## 🔧 Phase 1: Firebase Platform Setup (2-3 hours)

### ⏸️ STOP - Manual Tasks

**Reference**: `PSYCHOLOGICAL_INSIGHTS_PLAN.md` → Appendix D: Platform Setup Checklist

**Tasks to Complete**:

#### 1.1 Firestore Configuration (20 min)

**Console**: [Firebase Console](https://console.firebase.google.com) → Your Project → Firestore

- [ ] **Create 4 composite indexes**:

  Navigate to: Firestore → Indexes → Composite tab → Add Index

  **Index 1**: `diaries` collection
  - Fields: `ownerId` (Ascending), `date` (Descending)

  **Index 2**: `diary_insights` collection
  - Fields: `ownerId` (Ascending), `generatedAt` (Descending)

  **Index 3**: `wellbeing_snapshots` collection
  - Fields: `ownerId` (Ascending), `timestamp` (Descending)

  **Index 4**: `psychological_profiles` collection
  - Fields: `ownerId` (Ascending), `year` (Descending), `weekNumber` (Descending)

  > **Note**: Indexes will show "Building" status. This can take 5-15 minutes. Continue with other tasks while waiting.

- [ ] **Enable Vector Search** (for RAG feature):

  Navigate to: Firestore → Indexes → Single field tab

  - Field path: `diaries.embedding`
  - Index type: **Vector**
  - Dimensions: **768**
  - Distance measure: **COSINE**
  - Click **Create**

  > **Note**: Vector index can take 15-30 minutes to build.

- [ ] **Update Firestore security rules**:

  Copy the rules from `PSYCHOLOGICAL_INSIGHTS_PLAN.md` → Appendix D → Section 1.2

  Navigate to: Firestore → Rules tab → Paste → **Publish**

  Test rules using Rules Playground (same tab):
  - Test read/write with matching/mismatched `ownerId`

#### 1.2 Cloud Functions Setup (45 min)

**Terminal**: Navigate to project root

- [ ] **Initialize Firebase Functions**:

  ```bash
  firebase init

  # Select (use Space to check, Enter to confirm):
  # - Functions
  # - Firestore (if not already initialized)

  # Prompts:
  # - Use existing project → Select your project
  # - Language: TypeScript
  # - ESLint: Yes
  # - Install dependencies: Yes
  ```

  This creates a `functions/` directory.

- [ ] **Install Genkit dependencies**:

  ```bash
  cd functions
  npm install genkit @genkit-ai/googleai
  npm install firebase-admin
  npm install zod
  npm install @google-cloud/firestore
  cd ..
  ```

- [ ] **Configure Gemini API Key**:

  1. Get API key from [Google AI Studio](https://aistudio.google.com/app/apikey)
  2. Set as secret:
     ```bash
     firebase functions:secrets:set GEMINI_API_KEY
     # Paste your API key when prompted
     ```
  3. Verify:
     ```bash
     firebase functions:secrets:access GEMINI_API_KEY
     # Should show redacted key
     ```

- [ ] **Create function directory structure**:

  ```bash
  mkdir -p functions/src/genkit
  mkdir -p functions/src/scheduler
  mkdir -p functions/src/utils
  ```

#### 1.3 Enable Google Cloud APIs (15 min)

**Console**: [Google Cloud Console](https://console.cloud.google.com)

- [ ] Select your Firebase project from dropdown (top left)
- [ ] Navigate to: APIs & Services → Library
- [ ] Search and **Enable** each of these:
  - Cloud Functions API
  - Cloud Scheduler API
  - Cloud Build API
  - Generative Language API
  - Vertex AI API
  - Secret Manager API

  > **Tip**: Use the search box and enable them one by one. Each takes ~10 seconds.

#### 1.4 Cloud Scheduler Jobs (15 min)

**Console**: [Google Cloud Console](https://console.cloud.google.com) → Cloud Scheduler

**Replace `[YOUR_PROJECT_ID]` with your actual Firebase project ID in URLs below**

- [ ] **Job 1: compute-weekly-profiles**
  - Name: `compute-weekly-profiles`
  - Region: `us-central1`
  - Frequency: `0 2 * * 0` (Sunday 2 AM)
  - Target: HTTP
  - URL: `https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/computeWeeklyProfiles`
  - HTTP method: POST
  - Auth: OIDC token
  - Service account: `[YOUR_PROJECT_ID]@appspot.gserviceaccount.com`

- [ ] **Job 2: send-weekly-reminders**
  - Name: `send-weekly-reminders`
  - Frequency: `0 21 * * 0` (Sunday 9 PM)
  - URL: `https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/sendWeeklyReminders`
  - (Same auth settings)

- [ ] **Job 3: monthly-themes-reminder**
  - Name: `monthly-themes-reminder`
  - Frequency: `0 10 1 * *` (1st of month 10 AM)
  - URL: `https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/sendMonthlyThemesReminder`
  - (Same auth settings)

  > **Note**: These jobs will fail until Cloud Functions are deployed. This is expected.

#### 1.5 Firebase Cloud Messaging (15 min)

**Console**: Firebase → Cloud Messaging

- [ ] **Enable FCM**:
  - Click "Send your first message"
  - This auto-enables the service

- [ ] **Copy Server Key** (for testing):
  - Firebase Console → ⚙️ Project Settings → Cloud Messaging
  - Copy **Server key** (under "Cloud Messaging API - Legacy")
  - Store in a secure note (don't commit to Git)

#### 1.6 Budget & Monitoring (15 min)

**Console**: [Google Cloud Console](https://console.cloud.google.com) → Billing → Budgets

- [ ] **Create budget alert**:
  - Name: `Calmify Monthly Budget`
  - Amount: $20 (testing) or $100 (production)
  - Alerts at: 50%, 90%, 100%
  - Email: Your email

**Console**: Cloud Console → Monitoring → Alerting

- [ ] **Create error alert**:
  - Resource: Cloud Function
  - Metric: Execution count
  - Filter: `status = error`
  - Threshold: > 10 errors/hour
  - Email: Your email

### ✅ Verification Checklist

Before proceeding, verify:

```bash
# 1. Firebase CLI authenticated
firebase projects:list

# 2. Functions directory created
ls -la functions/

# 3. Genkit installed
cd functions && npm list genkit && cd ..

# 4. Firestore indexes building/enabled
# Go to Firebase Console → Firestore → Indexes
# All 5 indexes should show "Building" or "Enabled"

# 5. Cloud Scheduler jobs created
# Go to Cloud Console → Cloud Scheduler
# Should see 3 jobs listed
```

**Firestore Indexes Status**:
- Go to Firebase Console → Firestore → Indexes
- All indexes should show **"Enabled"** (wait up to 30 min if still building)

### 📍 CHECKPOINT 1

**Before continuing**:
- ✅ All 5 Firestore indexes are **Enabled**
- ✅ `functions/` directory exists with TypeScript setup
- ✅ Genkit dependencies installed
- ✅ `GEMINI_API_KEY` secret configured
- ✅ 3 Cloud Scheduler jobs created
- ✅ FCM enabled
- ✅ Budget alert active

If ANY checkbox is unchecked, go back and complete it now.

---

## 🤖 Phase 2: Database Schema Extensions (Week 1)

### ▶️ TRIGGER CLAUDE CODE

```
Start Phase 1 (Week 1) of PSYCHOLOGICAL_INSIGHTS_PLAN.md implementation roadmap. Extend the database schema with the 4 new data models:

1. Enhanced Diary (add psychological metrics to existing Diary model)
2. WellbeingSnapshot entity
3. DiaryInsight entity
4. PsychologicalProfile entity

Also create LifeTheme entity (optional but include it).

Extend the existing Firestore data models in data/mongo module. Update the Diary model with backward-compatible defaults. Create new Kotlin data classes following the existing project patterns from PROJECT_TECHNICAL_REPORT.md.

No UI changes yet - just data layer.
```

**What Claude Code Will Do**:
1. Read `data/mongo/src/main/java/com/lifo/util/model/Diary.kt`
2. Add new fields to `Diary` data class:
   - `emotionIntensity: Int = 5`
   - `stressLevel: Int = 5`
   - `energyLevel: Int = 5`
   - `calmAnxietyLevel: Int = 5`
   - `primaryTrigger: Trigger = Trigger.NONE`
   - `dominantBodySensation: BodySensation = BodySensation.NONE`
3. Create new enums: `Trigger`, `BodySensation`
4. Create new files:
   - `data/mongo/src/main/java/com/lifo/mongo/database/entity/WellbeingSnapshotEntity.kt`
   - `data/mongo/src/main/java/com/lifo/mongo/database/entity/DiaryInsightEntity.kt`
   - `data/mongo/src/main/java/com/lifo/mongo/database/entity/PsychologicalProfileEntity.kt`
   - `data/mongo/src/main/java/com/lifo/mongo/database/entity/LifeThemeEntity.kt`
5. Create corresponding Firestore models in `core/util/src/main/java/com/lifo/util/model/`:
   - `WellbeingSnapshot.kt`
   - `DiaryInsight.kt`
   - `PsychologicalProfile.kt`
   - `LifeTheme.kt`
6. Update Room database version and add new DAOs if needed

### ✅ Verification

**After Claude Code finishes**:

```bash
# 1. Build the project
cmd /c "gradlew.bat clean build"

# Should compile successfully (ignore warnings)
```

**Manual Code Review**:
- [ ] Open `core/util/src/main/java/com/lifo/util/model/Diary.kt`
- [ ] Verify new fields added with default values
- [ ] Check `WellbeingSnapshot.kt`, `DiaryInsight.kt`, `PsychologicalProfile.kt` exist
- [ ] Verify data classes match specification in `PSYCHOLOGICAL_INSIGHTS_PLAN.md` Section 1

**Ask Claude Code**:
```
Show me the changes you made to the Diary model and list all new files created.
```

### 📍 CHECKPOINT 2

- ✅ Project builds successfully
- ✅ Diary model extended with 6 new fields
- ✅ 4 new entity classes created (Wellbeing, Insight, Profile, Theme)
- ✅ Enums created (Trigger, BodySensation, Trend, CognitivePattern)
- ✅ No compilation errors

---

## 🤖 Phase 3: Enhanced Diary Input UI (Week 2)

### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 2 of the roadmap: Enhanced Diary Input UI.

Create a new composable component called "PsychologicalMetricsSheet" that appears after mood selection in the Write screen.

Requirements from PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 1.1:
- Use Material3 Sliders for intensity/stress/energy/calm metrics (0-10 range)
- Preset buttons for Trigger enum (Work, Family, Health, etc.)
- Preset buttons for BodySensation enum
- Optional skip with defaults to 5/NONE
- Target completion time: <10 seconds
- Follow existing WriteScreen.kt patterns from PROJECT_TECHNICAL_REPORT.md

Update WriteViewModel to handle the new fields.
```

**What Claude Code Will Do**:
1. Create `features/write/src/main/java/com/lifo/write/components/PsychologicalMetricsSheet.kt`
2. Implement Material3 Sliders with emoji anchors
3. Add preset button rows for Trigger and BodySensation
4. Update `WriteViewModel.kt` to include new state fields
5. Integrate into `WriteScreen.kt` after mood selection
6. Add skip/default logic

### ✅ Verification

**Build and Test**:

```bash
# 1. Build the project
cmd /c "gradlew.bat :features:write:build"

# 2. Install on device/emulator
cmd /c "gradlew.bat installDebug"
```

**Manual Testing**:
1. Open app
2. Tap "New Diary" (FAB)
3. Fill title and description
4. Select a mood
5. **New**: Should see psychological metrics sheet
6. Move sliders, select trigger/body sensation
7. Save diary
8. Verify data saved correctly (check Firestore Console)

**Firebase Console Check**:
- Go to Firestore → `diaries` collection
- Find your test diary
- Verify new fields exist: `emotionIntensity`, `stressLevel`, etc.

### 📍 CHECKPOINT 3

- ✅ Write feature builds successfully
- ✅ PsychologicalMetricsSheet appears in UI
- ✅ Sliders work (0-10 range)
- ✅ Trigger/BodySensation buttons functional
- ✅ Data saves to Firestore with new fields
- ✅ Skip button defaults to 5/NONE

---

## 🤖 Phase 4: Wellbeing Snapshot UI (Week 3)

### ▶️ TRIGGER CLAUDE CODE

```
Create Week 3: Wellbeing Snapshot feature.

Create a new feature module (or add to existing structure) for the weekly wellbeing questionnaire.

Follow specification from PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 1.2:
- 8-10 item questionnaire (SDT-based)
- Adaptive logic: shorten to 4 items if user wrote 3+ diaries this week
- Sliders with emoji anchors (0-10)
- Save to Firestore wellbeing_snapshots collection
- Track completion time (UX metric)

Create:
1. SnapshotScreen composable
2. SnapshotViewModel
3. Add navigation route
4. Integrate with home screen (button or FAB)

Reference existing feature patterns from features/write and features/chat.
```

**What Claude Code Will Do**:
1. Decide whether to create `features/wellbeing` module or add to existing
2. Create `SnapshotScreen.kt` with:
   - 8-10 slider questions
   - Adaptive shortening logic
   - Completion time tracking
   - Optional notes field
3. Create `SnapshotViewModel.kt` with:
   - State management (StateFlow)
   - Firestore save logic
   - Diary count check for adaptive mode
4. Create repository: `WellbeingRepository.kt`
5. Update navigation in `app/navigation/NavGraph.kt`
6. Add entry point (button in HomeScreen or FAB action)

### ⏸️ STOP - Manual Task

**After Claude Code finishes, you need to**:

Create a **test notification** to verify FCM works before implementing automated reminders.

**Terminal**:

```bash
# Replace with your device FCM token (get from Logcat when app starts)
curl -X POST https://fcm.googleapis.com/fcm/send \
  -H "Authorization: key=[YOUR_FCM_SERVER_KEY]" \
  -H "Content-Type: application/json" \
  -d '{
    "to": "[DEVICE_FCM_TOKEN]",
    "notification": {
      "title": "Test: Weekly Snapshot Reminder",
      "body": "Tap to complete your 2-minute wellness check"
    }
  }'
```

**Verify**:
- Notification appears on device
- Tapping opens app (deep link will be added later)

### ✅ Verification

**Build and Test**:

```bash
cmd /c "gradlew.bat :features:wellbeing:build"
cmd /c "gradlew.bat installDebug"
```

**Manual Testing**:
1. Open app
2. Navigate to Wellbeing Snapshot (new button/FAB action)
3. Complete questionnaire (slide all 8-10 items)
4. Add optional note
5. Submit
6. **Check Firestore Console**:
   - Collection: `wellbeing_snapshots`
   - Verify document created with all fields
   - Check `completionTime` field is populated

**Test Adaptive Mode**:
1. Create 3+ diary entries this week
2. Open Wellbeing Snapshot again
3. Should show **4 items** instead of 10 (shortened mode)

### 📍 CHECKPOINT 4

- ✅ Wellbeing feature builds successfully
- ✅ Snapshot screen accessible from home
- ✅ All 8-10 questions display correctly
- ✅ Adaptive shortening works (3+ diaries → 4 questions)
- ✅ Data saves to Firestore `wellbeing_snapshots`
- ✅ `completionTime` tracked correctly
- ✅ FCM test notification received

---

## 🤖 Phase 5: Cloud Functions - Insight Generation (Week 4-5)

### ⏸️ STOP - Manual Preparation

Before triggering Claude Code, ensure:
- [ ] `functions/` directory initialized (done in Phase 1)
- [ ] Genkit installed (done in Phase 1)
- [ ] `GEMINI_API_KEY` secret configured (done in Phase 1)

### ▶️ TRIGGER CLAUDE CODE

```
Implement Weeks 4-5: Cloud Functions for AI Insight Generation.

Create the following Cloud Functions using Genkit as specified in PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 5:

1. Firestore onCreate trigger for diaries collection → Generate DiaryInsight
2. Genkit flow for insight generation (sentiment, topics, cognitive patterns)
3. Structured output parsing (Zod schema)
4. Save insight to diary_insights collection
5. Send FCM notification when insight ready

Use the exact prompt template from PSYCHOLOGICAL_INSIGHTS_PLAN.md Appendix C.

Create files in functions/src/:
- genkit/insights.ts (Genkit flow)
- index.ts (Cloud Function triggers)
- utils/schemas.ts (Zod schemas)

Test locally with Firebase Emulator before deploying.
```

**What Claude Code Will Do**:
1. Create `functions/src/genkit/insights.ts`:
   - Import Genkit and Gemini plugin
   - Define `InsightSchema` (Zod)
   - Implement `generateDiaryInsight` flow
   - Use ethical system prompt from plan
2. Create `functions/src/index.ts`:
   - Import Firebase Functions v2
   - Create `onDiaryCreated` trigger
   - Call Genkit flow
   - Save result to `diary_insights`
   - Send FCM notification
3. Create `functions/src/utils/schemas.ts`:
   - Zod schemas for all models
4. Update `functions/package.json` with dependencies
5. Provide deployment commands

### ⏸️ STOP - Manual Deployment

**After Claude Code creates the functions**:

#### Test with Emulator (Recommended)

```bash
# 1. Start Firebase Emulators
firebase emulators:start

# Open Emulator UI: http://localhost:4000
```

**In Emulator UI**:
1. Go to Firestore tab
2. Manually add a test diary document
3. Watch Functions tab - should see `onDiaryCreated` execute
4. Check `diary_insights` collection for new document

#### Deploy to Firebase

```bash
# 1. Build TypeScript
cd functions
npm run build

# 2. Deploy functions
cd ..
firebase deploy --only functions

# Wait for deployment (2-5 minutes)
```

**Verify Deployment**:

```bash
firebase functions:log
# Should show function deployed successfully
```

### ✅ Verification

**End-to-End Test**:

1. **Open Android app**
2. **Create a new diary** with meaningful content:
   - Title: "Stressful day at work"
   - Description: "My boss criticized my project in front of everyone. I feel like a failure and think I'm going to get fired. This always happens to me."
   - Mood: Sad
   - Stress: 8/10
3. **Save diary**
4. **Wait 5-10 seconds**
5. **Check notification**:
   - Should receive FCM notification: "New insight ready!"
6. **Check Firestore Console**:
   - Go to `diary_insights` collection
   - Find insight for your diary (check `diaryId` matches)
   - Verify fields:
     - `sentimentPolarity` (should be negative, e.g., -0.7)
     - `topics` (e.g., ["work", "criticism"])
     - `cognitivePatterns` (should include "LABELING", "FORTUNE_TELLING", "OVERGENERALIZATION")
     - `summary` (compassionate 1-2 sentence summary)
     - `suggestedPrompts` (2-3 reflective questions)

**Check Cloud Function Logs**:

```bash
firebase functions:log --only onDiaryCreated
# Should show successful execution, no errors
```

### 📍 CHECKPOINT 5

- ✅ Cloud Functions deployed successfully
- ✅ `onDiaryCreated` trigger fires when diary saved
- ✅ DiaryInsight generated within 5 seconds
- ✅ Sentiment analysis accurate
- ✅ Cognitive patterns detected correctly
- ✅ FCM notification sent and received
- ✅ No errors in function logs

---

## 🤖 Phase 6: Insights Display UI (Week 6)

### ▶️ TRIGGER CLAUDE CODE

```
Create Week 6: Insights Display UI.

Implement a new screen to display AI-generated diary insights to the user.

Requirements from PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 1.3:
- Create InsightScreen composable (card-based UI)
- Show sentiment visualization (positive/negative indicator)
- Display topics, key phrases, cognitive patterns
- Show AI summary and suggested prompts
- Add "This insight is incorrect" button (user override)
- Explainability: show text snippets that led to pattern detection
- Add opt-out setting in Settings screen
- Confidence score visualization

Create:
1. features/insights module (or add to existing structure)
2. InsightScreen.kt
3. InsightViewModel.kt
4. InsightRepository.kt
5. Navigation integration (deep link from FCM notification)

Follow Material3 design patterns from existing features.
```

**What Claude Code Will Do**:
1. Create insight display module/package
2. Create `InsightScreen.kt`:
   - Material3 Cards for each insight section
   - Sentiment indicator (color-coded)
   - Topics chips
   - Cognitive patterns with explainability tooltips
   - Suggested prompts as expandable cards
3. Create `InsightViewModel.kt`:
   - Fetch insights from Firestore
   - Handle user corrections
   - Manage opt-out preference
4. Create `InsightRepository.kt`:
   - Firestore queries for insights
   - Save user corrections
5. Update navigation for deep links
6. Add opt-out toggle in Settings screen

### ✅ Verification

**Build and Test**:

```bash
cmd /c "gradlew.bat :features:insights:build"
cmd /c "gradlew.bat installDebug"
```

**Manual Testing**:

1. **Open app**
2. **Create a diary** (to generate insight)
3. **Wait for notification** ("New insight ready!")
4. **Tap notification**
   - Should deep link to InsightScreen
   - Should show the insight for that diary
5. **Verify UI elements**:
   - [ ] Sentiment indicator visible (color matches polarity)
   - [ ] Topics displayed as chips
   - [ ] Cognitive patterns listed with info icons
   - [ ] AI summary displayed
   - [ ] Suggested prompts shown (expandable)
   - [ ] "This insight is incorrect" button visible
6. **Test correction**:
   - Tap "This insight is incorrect"
   - Enter correction text
   - Submit
   - Check Firestore: insight document should have `userCorrection` field
7. **Test opt-out**:
   - Go to Settings
   - Toggle "Enable AI Insights" OFF
   - Create new diary
   - Verify NO insight notification received

### 📍 CHECKPOINT 6

- ✅ Insights screen accessible from notification
- ✅ All UI elements render correctly
- ✅ Sentiment/topics/patterns display accurately
- ✅ User correction feature works
- ✅ Opt-out setting functional
- ✅ Deep linking from FCM works

---

## 🤖 Phase 7: Psychological Profile Computation (Week 7-8)

### ▶️ TRIGGER CLAUDE CODE

```
Implement Weeks 7-8: Psychological Profile Computation.

Create a Cloud Function that runs weekly to compute the PsychologicalProfile as specified in PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 1.4 and 5.3.

Requirements:
1. Cloud Scheduler triggered function (HTTP)
2. Compute profile metrics:
   - Stress baseline (weighted average)
   - Stress volatility (standard deviation)
   - Stress peaks detection
   - Mood trends (improving/stable/declining)
   - Resilience index (recovery time)
   - Language clarity (via Gemini)
3. Save to psychological_profiles collection
4. Process all active users

Create:
- functions/src/scheduler/compute-profiles.ts
- functions/src/utils/profile-calculator.ts (algorithms)
- Update functions/src/index.ts with HTTP trigger

Use the computation algorithms from PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 5.3.
```

**What Claude Code Will Do**:
1. Create `functions/src/scheduler/compute-profiles.ts`:
   - HTTP Cloud Function (triggered by Cloud Scheduler)
   - Fetch all active users
   - For each user:
     - Get diaries from past 7-14 days
     - Get wellbeing snapshots
     - Calculate metrics
     - Save profile
2. Create `functions/src/utils/profile-calculator.ts`:
   - `calculateWeightedAverage()` (exponential decay)
   - `calculateStdDev()`
   - `detectPeaks()` (threshold-based)
   - `calculateResilience()` (area under curve)
   - `detectTrend()` (linear regression)
3. Create Genkit flow for language clarity analysis
4. Update `functions/src/index.ts` with `computeWeeklyProfiles` export

### ⏸️ STOP - Manual Deployment

```bash
# Deploy updated functions
cd functions
npm run build
cd ..
firebase deploy --only functions
```

**Verify Cloud Scheduler Connection**:

1. Go to [Cloud Console](https://console.cloud.google.com) → Cloud Scheduler
2. Find `compute-weekly-profiles` job
3. Click **"Run Now"** (manual trigger)
4. Wait 1-2 minutes
5. Check function logs:
   ```bash
   firebase functions:log --only computeWeeklyProfiles
   ```
6. Check Firestore:
   - Collection: `psychological_profiles`
   - Should have documents for all active users

### ✅ Verification

**Check Profile Data**:

1. **Firestore Console** → `psychological_profiles`
2. Open your user's profile document
3. Verify fields:
   - [ ] `stressBaseline` (float 0-10)
   - [ ] `stressVolatility` (float >= 0)
   - [ ] `stressPeaks` (array of peak objects)
   - [ ] `moodBaseline` (float 0-10)
   - [ ] `moodTrend` (enum: IMPROVING/STABLE/DECLINING)
   - [ ] `resilienceIndex` (float 0-1)
   - [ ] `clarityTrend` (enum)
   - [ ] `diaryCount` (integer)
   - [ ] `snapshotCount` (integer)
   - [ ] `confidence` (float 0-1)

**Test Manual Trigger**:

```bash
# Manually trigger computation (don't wait for Sunday 2 AM)
curl -X POST https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/computeWeeklyProfiles \
  -H "Authorization: Bearer $(gcloud auth print-identity-token)"
```

### 📍 CHECKPOINT 7

- ✅ `computeWeeklyProfiles` function deployed
- ✅ Cloud Scheduler job runs successfully
- ✅ Profiles computed for all users
- ✅ All metrics calculated correctly
- ✅ Data saved to Firestore
- ✅ No errors in function logs

---

## 🤖 Phase 8: Profile Dashboard UI (Week 8)

### ▶️ TRIGGER CLAUDE CODE

```
Create Week 8: Profile Dashboard UI.

Build a ProfileDashboard screen to visualize the user's psychological profile trends.

Requirements from PSYCHOLOGICAL_INSIGHTS_PLAN.md:
- 4-week rolling view
- Trend charts (stress, mood, energy over time)
- Stress peaks timeline
- Resilience score visualization
- Clarity and social/purpose trends
- Export profile as PDF (optional)

Use Compose Canvas or integrate a charting library (suggest best option for Compose).

Create:
1. ProfileDashboard.kt (screen)
2. ProfileViewModel.kt
3. ProfileRepository.kt
4. Chart components (line charts, bar charts)
5. Add navigation entry point

Reference Material3 theming for chart colors.
```

**What Claude Code Will Do**:
1. Evaluate charting options:
   - Compose Canvas (custom charts)
   - Or suggest a library like `Vico` or `MPAndroidChart`
2. Create `ProfileDashboard.kt`:
   - 4-week rolling line chart (stress/mood/energy)
   - Stress peaks timeline (vertical markers)
   - Resilience score gauge/progress bar
   - Trend indicators (arrows up/down/stable)
3. Create `ProfileViewModel.kt`:
   - Fetch last 4 weeks of profiles
   - Transform data for chart display
   - Handle loading/error states
4. Create `ProfileRepository.kt`:
   - Firestore query: `psychological_profiles` collection
   - Filter by `ownerId` and last 4 weeks
   - Sort by `weekNumber` descending
5. Add navigation route
6. Add entry point (Settings screen or Home screen button)

### ✅ Verification

**Build and Test**:

```bash
cmd /c "gradlew.bat installDebug"
```

**Manual Testing**:

1. **Open app**
2. **Navigate to Profile Dashboard** (new menu item)
3. **Verify charts display**:
   - [ ] 4-week line chart (X-axis: weeks, Y-axis: 0-10)
   - [ ] Stress/mood/energy lines visible (different colors)
   - [ ] Stress peaks marked on timeline
   - [ ] Resilience score displayed (0-1 or 0-100%)
   - [ ] Trend arrows (↑ improving, → stable, ↓ declining)
4. **Test empty state**:
   - Create a new test account with no data
   - Should show "Not enough data" message
5. **Test data accuracy**:
   - Compare chart values to Firestore data
   - Verify trends match calculated values

### 📍 CHECKPOINT 8

- ✅ Profile dashboard accessible
- ✅ Charts render correctly
- ✅ 4-week rolling window works
- ✅ Trends calculated accurately
- ✅ Empty state handled gracefully
- ✅ UI follows Material3 design

---

## 🤖 Phase 9: FCM Notification System (Week 9)

### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 9: FCM Notification System.

Create Android notification handling and Cloud Functions to send scheduled reminders.

Requirements from PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 6.2:
1. Android side:
   - Create notification channels (Insights, Reminders, Wellness, Achievements)
   - Handle deep linking from notifications
   - CalmifyFirebaseMessagingService

2. Cloud Functions side:
   - sendWeeklyReminders (Cloud Scheduler triggered)
   - sendMonthlyThemesReminder
   - Adaptive notification logic (check stress peaks, skip if high stress)

Update MainActivity.kt to create notification channels.
Create FirebaseMessagingService to handle incoming FCM messages.
Create Cloud Functions to send notifications via FCM.
```

**What Claude Code Will Do**:

**Android Side**:
1. Update `MainActivity.kt`:
   - Create notification channels (4 channels)
   - Request notification permission (Android 13+)
2. Create `CalmifyFirebaseMessagingService.kt`:
   - Handle `onMessageReceived()`
   - Parse notification types (INSIGHT_READY, WEEKLY_SNAPSHOT, etc.)
   - Show notifications with correct channel
   - Handle deep linking (PendingIntent)
3. Update `AndroidManifest.xml`:
   - Register FirebaseMessagingService

**Cloud Functions Side**:
1. Create `functions/src/scheduler/send-reminders.ts`:
   - `sendWeeklyReminders()`:
     - Query users who haven't completed snapshot this week
     - Check recent profiles (adaptive logic)
     - Send FCM notification
   - `sendMonthlyThemesReminder()`:
     - Remind users to review life themes
2. Create `functions/src/utils/fcm-helper.ts`:
   - Helper functions to send FCM messages
   - Get user FCM tokens from Firestore
3. Update `functions/src/index.ts`:
   - Export HTTP triggers for Cloud Scheduler

### ⏸️ STOP - Manual Tasks

#### Update Firestore to Store FCM Tokens

**Add a `users` collection** (if not exists):

1. **Firestore Console** → Create collection: `users`
2. Structure:
   ```
   users/{userId}
   - fcmToken: string
   - lastSnapshotAt: timestamp
   - notificationsEnabled: boolean
   ```

#### Update Android to Save FCM Token

**Ask Claude Code**:
```
Update MainActivity or create a repository function to save the FCM token to Firestore when the app starts. Store it in users/{userId}/fcmToken.
```

#### Deploy Cloud Functions

```bash
cd functions
npm run build
cd ..
firebase deploy --only functions
```

#### Test Cloud Scheduler Jobs

```bash
# Manually trigger weekly reminder
curl -X POST https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/sendWeeklyReminders \
  -H "Authorization: Bearer $(gcloud auth print-identity-token)"
```

**Verify**:
- Check device for notification
- Check function logs for any errors

### ✅ Verification

**End-to-End Notification Test**:

1. **Create a diary** → Should receive "New insight ready!" notification
2. **Tap notification** → Should deep link to InsightScreen
3. **Manually trigger weekly reminder**:
   ```bash
   # From terminal
   curl -X POST https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/sendWeeklyReminders \
     -H "Authorization: Bearer $(gcloud auth print-identity-token)"
   ```
4. **Check device** → Should receive "Weekly Check-In" notification
5. **Tap notification** → Should open Wellbeing Snapshot screen

**Verify Notification Channels**:
1. **Android Settings** → Apps → Calmify → Notifications
2. Should see 4 channels:
   - Insights
   - Reminders
   - Wellness Checks
   - Achievements
3. Test toggling each channel on/off

### 📍 CHECKPOINT 9

- ✅ 4 notification channels created
- ✅ FCM tokens saved to Firestore
- ✅ Deep linking works for all notification types
- ✅ Weekly reminder function works
- ✅ Monthly reminder function works
- ✅ Adaptive logic (stress-aware) implemented
- ✅ All notifications received on device

---

## 🤖 Phase 10: Vector Search & RAG (Week 10)

### ⏸️ STOP - Manual Task

**Before triggering Claude Code**, verify Vector Search is enabled:

1. **Firestore Console** → Indexes → Single field tab
2. Find `diaries.embedding` vector index
3. Status should be **"Enabled"** (if "Building", wait until enabled)

### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 10: Vector Search for RAG (Retrieval-Augmented Generation).

Add embedding generation and vector search to enhance chat context with similar past diaries.

Requirements from PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 5.2:
1. Cloud Function: Generate embeddings when diary is created
   - Use Gemini text-embedding-004
   - Store vector in Firestore (diaries.embedding field)

2. Cloud Function: Callable function getSimilarDiaries
   - Input: user message, userId, limit
   - Generate query embedding
   - Perform vector search (COSINE distance)
   - Return top N similar diaries

3. Android: Update ChatRepositoryImpl
   - Call getSimilarDiaries before AI response
   - Build personalized prompt with RAG context
   - Enhanced prompt: "Based on your past journal entries about [topic]..."

Create:
- functions/src/genkit/embeddings.ts
- functions/src/callable/get-similar-diaries.ts
- Update ChatRepositoryImpl.kt with RAG integration
```

**What Claude Code Will Do**:

**Cloud Functions Side**:
1. Create `functions/src/genkit/embeddings.ts`:
   - Genkit flow: `generateEmbedding`
   - Use `text-embedding-004` model
2. Update `functions/src/index.ts`:
   - Add `onDiaryCreated` hook to generate embedding
   - Update Firestore document with embedding vector
3. Create `functions/src/callable/get-similar-diaries.ts`:
   - HTTPS callable function
   - Generate query embedding
   - Firestore vector search query
   - Return similar diaries (limit 5)

**Android Side**:
1. Update `ChatRepositoryImpl.kt`:
   - Add `getDiaryContext()` function
   - Call Cloud Function via HTTP
   - Parse response (list of similar diaries)
2. Update `buildPersonalizedPrompt()`:
   - Include RAG context in AI prompt
   - Format: "Recent similar journal entries: ..."

### ⏸️ STOP - Manual Deployment

```bash
# Deploy functions
cd functions
npm run build
cd ..
firebase deploy --only functions
```

### ✅ Verification

**Test Embedding Generation**:

1. **Create a new diary** with unique content:
   - Title: "Feeling anxious about presentation"
   - Description: "I have a big presentation tomorrow and I'm worried about public speaking."
2. **Wait 5-10 seconds**
3. **Firestore Console** → `diaries` → Find your diary
4. **Check for `embedding` field**:
   - Should be a `FieldValue.vector()` with 768 dimensions
   - Will appear as a long array of floats

**Test Vector Search**:

1. **Create 3-4 more diaries** with varied topics:
   - Diary 2: "Great workout today, feeling energized"
   - Diary 3: "Nervous about job interview"
   - Diary 4: "Relaxing weekend with family"
2. **Open Chat**
3. **Send message**: "I'm feeling anxious about public speaking"
4. **Expected**:
   - AI should reference your "presentation anxiety" diary
   - Response should include context like: "I see you've written before about presentation anxiety..."

**Verify RAG in Logs**:

```bash
firebase functions:log --only getSimilarDiaries
# Should show vector search query executed
```

### 📍 CHECKPOINT 10

- ✅ Embeddings generated for all diaries
- ✅ `embedding` field exists in Firestore
- ✅ `getSimilarDiaries` callable function works
- ✅ Vector search returns relevant diaries
- ✅ Chat uses RAG context in responses
- ✅ AI references past diary entries accurately

---

## 🤖 Phase 11: Ethical Safeguards & Polish (Week 11)

### ▶️ TRIGGER CLAUDE CODE

```
Implement Week 11: Ethical Safeguards and Polish.

Add ethical AI features from PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 8:
1. Consent screen (first-time AI features)
2. "Disable insights" setting
3. Data export (GDPR Article 20)
4. Privacy policy update screen
5. Explainability tooltips for all AI features
6. Insight correction feedback loop
7. Crisis detection and response protocol

Update Settings screen with privacy controls.
Create consent flow for new users.
Implement data export functionality.
Add crisis detection to insight generation.
```

**What Claude Code Will Do**:

**Android Side**:
1. Create `ConsentScreen.kt`:
   - First-time modal explaining AI features
   - Clear privacy information
   - "Agree" / "Decline" buttons
   - Store consent in SharedPreferences
2. Update `SettingsScreen.kt`:
   - Add "Privacy & AI" section
   - Toggle: "Enable AI Insights"
   - Toggle: "Enable Profile Computation"
   - Toggle: "Share Anonymized Data for Research"
   - Button: "Export My Data"
   - Button: "Delete All Data"
3. Create `DataExportViewModel.kt`:
   - Query all user data (diaries, insights, snapshots, profiles)
   - Export as JSON
   - Save to Downloads folder
4. Add explainability tooltips:
   - Info icons next to cognitive patterns
   - Tap to show: "This pattern was detected based on phrases like: '...'"

**Cloud Functions Side**:
1. Update `functions/src/genkit/insights.ts`:
   - Add crisis detection logic
   - Check for suicidal keywords
   - Check for sustained high stress (7+ consecutive days)
2. Create `functions/src/crisis/detect-crisis.ts`:
   - Crisis levels: HIGH, MEDIUM, NONE
   - Send emergency resources notification if HIGH
3. Update FCM notification templates:
   - Crisis resources (988 Suicide Lifeline, etc.)

### ✅ Verification

**Test Consent Flow**:
1. Uninstall and reinstall app (or clear app data)
2. On first launch, should see consent screen
3. Decline → AI features disabled
4. Accept → AI features enabled

**Test Privacy Settings**:
1. **Settings** → Privacy & AI
2. Toggle "Enable AI Insights" OFF
3. Create diary → No insight notification
4. Toggle ON → Create diary → Insight notification

**Test Data Export**:
1. **Settings** → "Export My Data"
2. Wait for export (5-10 seconds)
3. Check Downloads folder
4. Open JSON file
5. Verify contains all data:
   - Diaries
   - Insights
   - Wellbeing snapshots
   - Psychological profiles

**Test Crisis Detection**:
1. Create diary with crisis keywords:
   - "I want to end it all. No point in living anymore."
2. Wait for function to process
3. Should receive **high-priority notification** with crisis resources:
   - 988 Suicide & Crisis Lifeline
   - Text HELLO to 741741
4. Check Firestore:
   - Collection: `crisis_logs` (if implemented)
   - Should have log entry

### 📍 CHECKPOINT 11

- ✅ Consent screen shows on first launch
- ✅ Privacy settings functional
- ✅ Data export works (valid JSON)
- ✅ Data deletion works
- ✅ Crisis detection active
- ✅ Crisis notifications sent
- ✅ Explainability tooltips added

---

## 🧪 Phase 12: Testing & Launch Prep (Week 12)

### ▶️ TRIGGER CLAUDE CODE

```
Prepare for Week 12: Final testing and launch preparation.

Tasks:
1. Write integration tests for critical flows:
   - Diary save → Insight generation → Notification
   - Weekly snapshot completion → Profile computation
   - Chat with RAG context
   - Crisis detection

2. Performance testing:
   - Profile large data sets (100+ diaries)
   - Check Firestore query performance
   - Optimize if needed

3. Create a testing checklist document

4. Fix any critical bugs found during testing

Reference PROJECT_TECHNICAL_REPORT.md Section 6 for testing patterns.
```

**What Claude Code Will Do**:
1. Create integration tests:
   - `DiaryInsightIntegrationTest.kt`
   - `ProfileComputationTest.kt`
   - `RAGChatTest.kt`
   - `CrisisDetectionTest.kt`
2. Create `TESTING_CHECKLIST.md`:
   - Pre-launch verification steps
   - Performance benchmarks
   - Edge cases to test
3. Run local tests and report results
4. Suggest optimizations based on findings

### ⏸️ STOP - Manual Tasks

#### Beta Testing

1. **Recruit 5-10 beta testers**
2. **Deploy to Firebase App Distribution**:
   ```bash
   # Build release APK
   cmd /c "gradlew.bat assembleRelease"

   # Upload to App Distribution
   firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
     --app [YOUR_APP_ID] \
     --groups "beta-testers" \
     --release-notes "Beta v1.0 - Psychological Insights Feature"
   ```
3. **Collect feedback** (2 weeks)
4. **Fix critical issues**

#### Performance Testing

**Load test Cloud Functions**:

Use Artillery.io or similar:

```yaml
# load-test.yml (see PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 9.3)
config:
  target: 'https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net'
  phases:
    - duration: 60
      arrivalRate: 10
```

Run:
```bash
artillery run load-test.yml
```

**Acceptance Criteria**:
- P95 latency < 5s for insight generation
- Error rate < 1%
- No cold start issues under sustained load

#### Cost Monitoring

1. **Firebase Console** → Usage and Billing
2. Monitor for 1 week
3. Verify costs within budget ($20-100/month)
4. Check:
   - Firestore reads/writes
   - Cloud Functions invocations
   - Gemini API calls

### ✅ Verification

**Pre-Launch Checklist**:

- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] Beta testing complete (5-10 users, 2 weeks)
- [ ] Critical bugs fixed
- [ ] Performance benchmarks met (P95 < 5s)
- [ ] Cost within budget
- [ ] Privacy policy updated
- [ ] Google Play Store listing ready
- [ ] Screenshots & promotional materials prepared
- [ ] Rollout plan defined (10% → 50% → 100%)

### 📍 CHECKPOINT 12 - LAUNCH READY

- ✅ All tests passing
- ✅ Beta feedback addressed
- ✅ Performance optimized
- ✅ Cost under control
- ✅ Privacy/legal compliance verified
- ✅ App Store submission ready

---

## 🎉 Phase 13: Staged Rollout & Monitoring

### ⏸️ STOP - Manual Tasks Only

**This phase is entirely manual** - Claude Code cannot access Google Play Console.

#### Week 1: Internal Testing (10% rollout)

1. **Google Play Console** → Release → Production
2. Create new release
3. Upload AAB:
   ```bash
   cmd /c "gradlew.bat bundleRelease"
   # AAB location: app/build/outputs/bundle/release/app-release.aab
   ```
4. **Staged rollout**: 10%
5. Monitor for 7 days:
   - Crashlytics: Check for crashes
   - Firebase Analytics: Check feature usage
   - Function logs: Check for errors
   - User reviews: Check for complaints

#### Week 2: Expand to 50%

If no critical issues:
- **Play Console** → Release → Update rollout → 50%
- Monitor for 7 more days

#### Week 3: Full Release (100%)

If still stable:
- **Play Console** → Release → Update rollout → 100%

### ✅ Post-Launch Monitoring

**Daily (First Week)**:
- Check Crashlytics
- Review function logs
- Monitor costs

**Weekly**:
- Analyze usage metrics (Firebase Analytics)
- Review user feedback
- Check insight accuracy (sample reviews)
- Monitor costs vs. budget

**Monthly**:
- Review psychological profile trends (anonymized aggregate)
- Bias testing (ensure no demographic disparities)
- Cost optimization
- Feature requests from users

---

## 📊 Success Metrics

**After 1 Month**:

| Metric | Target | How to Measure |
|--------|--------|----------------|
| Weekly Active Users completing Wellbeing Snapshot | 70% | Firebase Analytics event: `wellbeing_snapshot_completed` |
| Insight Satisfaction (User Corrections <20%) | >80% | Firestore: count insights with `userCorrection` field / total insights |
| Crisis Detection Response Time | <5 min | Function logs: time from diary save to crisis notification |
| AI Response Latency | <2s | Firebase Performance Monitoring: custom trace |
| Cost per 1K users | <$30/month | Firebase Console → Usage and Billing |
| App Crash Rate | <0.1% | Crashlytics → Dashboard |

---

## 🆘 Troubleshooting Guide

### Common Issues & Solutions

#### Issue: Firestore indexes stuck in "Building"

**Solution**:
1. Wait 30 minutes
2. If still building, delete and recreate index
3. Check Firestore quota limits (Firebase Console → Quotas)

#### Issue: Cloud Function deployment fails

**Solution**:
```bash
# Re-authenticate
firebase login --reauth

# Check service account permissions
gcloud projects get-iam-policy [YOUR_PROJECT_ID]

# Redeploy with verbose logging
firebase deploy --only functions --debug
```

#### Issue: FCM notifications not received

**Solution**:
1. Verify `google-services.json` in `app/` directory
2. Check FCM token saved to Firestore (users collection)
3. Test with Firebase Console → Cloud Messaging → Send test message
4. Check notification channels enabled on device

#### Issue: Vector search returns no results

**Solution**:
1. Verify embeddings stored as `FieldValue.vector()` (not plain array)
2. Check vector index status (Firestore Console → Indexes)
3. Ensure query embedding dimensions match (768)
4. Check COSINE distance threshold

#### Issue: AI insights inaccurate

**Solution**:
1. Review prompt in `functions/src/genkit/insights.ts`
2. Adjust temperature (lower = more deterministic)
3. Add more examples to system prompt
4. Collect user corrections and retrain prompt

#### Issue: Costs higher than expected

**Solution**:
1. Firebase Console → Usage and Billing → View detailed usage
2. Common culprits:
   - Too many Firestore reads (optimize queries)
   - Gemini API overuse (add caching)
   - Function cold starts (increase min instances to 0)
3. Implement caching:
   - Cache insights for 24 hours
   - Cache profiles for 7 days

---

## 🎓 Learning Resources

**If you get stuck**, consult these resources:

- **Firebase Docs**: [firebase.google.com/docs](https://firebase.google.com/docs)
- **Genkit Docs**: [firebase.google.com/docs/genkit](https://firebase.google.com/docs/genkit)
- **Gemini API Docs**: [ai.google.dev/gemini-api/docs](https://ai.google.dev/gemini-api/docs)
- **Compose Docs**: [developer.android.com/jetpack/compose](https://developer.android.com/jetpack/compose)
- **Firestore Vector Search**: [firebase.google.com/docs/firestore/vector-search](https://firebase.google.com/docs/firestore/vector-search)

**Stack Overflow Tags**:
- `[firebase]` + `[cloud-functions]`
- `[google-cloud-firestore]` + `[vector-search]`
- `[android-jetpack-compose]`
- `[gemini-api]`

---

## 🔄 Iteration & Improvement

After launch, use this workflow for future enhancements:

### Weekly Iteration Cycle

1. **Monday**: Review metrics from past week
2. **Tuesday**: Prioritize bugs/features
3. **Wednesday-Thursday**:
   - **Trigger Claude Code** with new tasks
   - Follow same phase pattern (manual → automated → verify)
4. **Friday**: Deploy to beta testers
5. **Weekend**: Monitor

### Prompt Template for Iterations

```
Based on user feedback and metrics from FIREBASE_CONFIG.md, implement [FEATURE_NAME].

Current state:
- [Describe current implementation]
- [Issue or metric that triggered this]

Requirements:
- [Specific change requested]
- [Acceptance criteria]

Reference PSYCHOLOGICAL_INSIGHTS_PLAN.md Section [X] for context.
```

---

## 📝 Final Notes

### Key Takeaways

1. **Always verify before proceeding** - Each checkpoint catches issues early
2. **Use exact trigger prompts** - Claude Code relies on context from PSYCHOLOGICAL_INSIGHTS_PLAN.md
3. **Monitor costs daily** - Set budget alerts to avoid surprises
4. **Test crisis detection thoroughly** - This is a safety-critical feature
5. **Collect user feedback early** - Beta test with diverse users

### When to Ask Claude Code for Help

- ❌ **Don't ask**: "What should I do next?" (This guide tells you)
- ✅ **Do ask**: "The Diary model changes broke the build. Fix compilation errors."
- ✅ **Do ask**: "Optimize the profile computation function - it's taking >60s."
- ✅ **Do ask**: "Write a migration script to backfill embeddings for existing diaries."

### When to Do Manual Work

- Firebase/Cloud Console configuration (Claude Code has no access)
- Testing on physical devices
- Google Play Store submission
- Cost monitoring and optimization
- User feedback collection

---

**Good luck with your implementation, Sir!** 🚀

Follow this guide phase by phase, and you'll have the psychological insights feature live within 12 weeks.

---

*Generated by Jarvis AI - 2025-01-19*
*For: Calmify Psychological Insights Implementation*
*Companion to: PSYCHOLOGICAL_INSIGHTS_PLAN.md*
