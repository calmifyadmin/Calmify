# Calmify - Psychological Insights & Wellbeing Enhancement Plan

**Document Version**: 2.0 (Revised)
**Created**: 2025-01-19
**Revised**: 2025-01-19
**Status**: Ready for Implementation
**Estimated Timeline**: 10-12 weeks
**Approach**: Incremental MVP → Optimize → Scale

---

## 🎯 Executive Summary

This document outlines a **pragmatic, incremental approach** to adding psychological insights to Calmify. Unlike typical waterfall plans, we follow a **"build → test → optimize"** philosophy where features are implemented in working increments, and infrastructure (indexes, optimization) is added only when real usage demands it.

### Core Principles

1. **Working Software First**: Deploy functional features before optimizing
2. **Data-Driven Indexing**: Create Firestore indexes only after seeing real query patterns
3. **Incremental Cloud Functions**: Start with simple triggers, add complexity later
4. **User Feedback Loop**: Beta test each feature before building the next

### Implementation Phases

- **Phase 1 (Weeks 1-4)**: MVP - Basic features working end-to-end
- **Phase 2 (Weeks 5-8)**: AI Integration - Cloud Functions + Gemini
- **Phase 3 (Weeks 9-12)**: Optimization - Indexes, Vector Search, Performance

---

## 📚 Table of Contents

1. [Data Models](#1-data-models)
2. [Responsibility Matrix](#2-responsibility-matrix)
3. [Technical Architecture](#3-technical-architecture)
4. [AI Pipeline Design](#4-ai-pipeline-design)
5. [Implementation Roadmap (Revised)](#5-implementation-roadmap-revised)
6. [Firebase Infrastructure Strategy](#6-firebase-infrastructure-strategy)
7. [Ethical Considerations](#7-ethical-considerations)
8. [Testing Strategy](#8-testing-strategy)
9. [Cost Estimation](#9-cost-estimation)
10. [Appendices](#10-appendices)

---

## 1. Data Models

### 1.1 Enhanced Diary Entry (Existing Collection Extended)

**Firestore Collection**: `diaries` ✅ **Already exists**

```kotlin
data class Diary(
    @DocumentId
    var _id: String = "",
    var ownerId: String = "",
    var title: String = "",
    var description: String = "",
    var mood: String = Mood.Neutral.name,
    var images: List<String> = emptyList(),
    @ServerTimestamp
    var date: Date = Date.from(Instant.now()),

    // ✨ NEW: Psychological Metrics (10-second input)
    var emotionIntensity: Int = 5,          // 1-10 (how strongly felt)
    var stressLevel: Int = 5,               // 0-10 (0=none, 10=extreme)
    var energyLevel: Int = 5,               // 0-10 (0=exhausted, 10=energized)
    var calmAnxietyLevel: Int = 5,          // 0-10 (0=anxious, 10=calm)
    var primaryTrigger: Trigger = Trigger.NONE,
    var dominantBodySensation: BodySensation = BodySensation.NONE
)

enum class Trigger {
    NONE, WORK, FAMILY, HEALTH, FINANCE, SOCIAL, SELF, OTHER
}

enum class BodySensation {
    NONE, TENSION, LIGHTNESS, FATIGUE, HEAVINESS, AGITATION, RELAXATION
}
```

**Migration**: Backward compatible (defaults to 5/NONE for existing entries)

**Firestore Indexes**: Already exists (`ownerId` ASC, `date` DESC)

---

### 1.2 Wellbeing Snapshot (New Collection)

**Firestore Collection**: `wellbeing_snapshots` 🆕 **Created on first save**

```kotlin
data class WellbeingSnapshot(
    @DocumentId
    var id: String = UUID.randomUUID().toString(),
    var ownerId: String = "",
    @ServerTimestamp
    var timestamp: Date = Date.from(Instant.now()),

    // Core SDT (Self-Determination Theory) Dimensions
    var lifeSatisfaction: Int = 5,          // 0-10
    var workSatisfaction: Int = 5,          // 0-10
    var relationshipsQuality: Int = 5,      // 0-10

    // Psychological Constructs
    var mindfulnessScore: Int = 5,          // 0-10 (present awareness)
    var purposeMeaning: Int = 5,            // 0-10 (sense of direction)
    var gratitude: Int = 5,                 // 0-10 (appreciation)

    // SDT Pillars
    var autonomy: Int = 5,                  // 0-10 (control over life)
    var competence: Int = 5,                // 0-10 (mastery feeling)
    var relatedness: Int = 5,               // 0-10 (connection to others)

    // Risk Indicator
    var loneliness: Int = 5,                // 0-10 (0=connected, 10=isolated)

    // Open-ended
    var notes: String = "",                 // Optional reflection

    // Metadata
    var completionTime: Long = 0L,          // Milliseconds (UX metric)
    var wasReminded: Boolean = false        // Triggered by notification?
)
```

**Firestore Indexes**: ⏳ Created later when queries slow down (Phase 3)

---

### 1.3 AI-Generated Diary Insights (New Collection)

**Firestore Collection**: `diary_insights` 🆕 **Created by Cloud Function**

```kotlin
data class DiaryInsight(
    @DocumentId
    var id: String = UUID.randomUUID().toString(),
    var diaryId: String = "",              // Foreign key to diary entry
    var ownerId: String = "",
    @ServerTimestamp
    var generatedAt: Date = Date.from(Instant.now()),

    // Sentiment Analysis
    var sentimentPolarity: Float = 0f,     // -1.0 (negative) to +1.0 (positive)
    var sentimentMagnitude: Float = 0f,    // 0.0 (neutral) to infinity (strongly felt)

    // Content Analysis
    var topics: List<String> = emptyList(), // ["work stress", "family conflict"]
    var keyPhrases: List<String> = emptyList(), // ["feeling overwhelmed", "need rest"]

    // Cognitive Patterns (CBT-informed)
    var cognitivePatterns: List<CognitivePattern> = emptyList(),

    // Summary & Suggestions
    var summary: String = "",               // 1-2 sentence AI summary
    var suggestedPrompts: List<String> = emptyList(), // Reflective questions

    // Confidence & Explainability
    var confidence: Float = 0f,             // 0.0-1.0
    var modelUsed: String = "gemini-2.0-flash",
    var processingTime: Long = 0L,          // Milliseconds

    // User Feedback
    var userCorrection: String? = null,     // If user marks "incorrect"
    var userRating: Int? = null             // 1-5 stars (optional)
)

enum class CognitivePattern {
    CATASTROPHIZING,           // "This will end in disaster"
    ALL_OR_NOTHING,           // "I'm either perfect or a failure"
    OVERGENERALIZATION,       // "I always mess up"
    MIND_READING,             // "They think I'm incompetent"
    FORTUNE_TELLING,          // "I know it won't work"
    EMOTIONAL_REASONING,      // "I feel bad, so it must be bad"
    SHOULD_STATEMENTS,        // "I should be better"
    LABELING,                 // "I'm a loser"
    PERSONALIZATION,          // "It's all my fault"
    NONE                      // No patterns detected
}
```

**Firestore Indexes**: ⏳ Created in Phase 3 (after testing query patterns)

---

### 1.4 Psychological Profile (New Collection)

**Firestore Collection**: `psychological_profiles` 🆕 **Created by weekly Cloud Function**

```kotlin
data class PsychologicalProfile(
    @DocumentId
    var id: String = "${ownerId}_week_${weekNumber}_${year}",
    var ownerId: String = "",
    var weekNumber: Int = 0,                // ISO week number
    var year: Int = 2025,
    @ServerTimestamp
    var computedAt: Date = Date.from(Instant.now()),

    // Stress Dynamics
    var stressBaseline: Float = 5f,         // Weighted average
    var stressVolatility: Float = 0f,       // Standard deviation
    var stressPeaks: List<StressPeak> = emptyList(),

    // Mood Dynamics
    var moodBaseline: Float = 5f,
    var moodVolatility: Float = 0f,
    var moodTrend: Trend = Trend.STABLE,    // IMPROVING, STABLE, DECLINING

    // Resilience Metrics
    var resilienceIndex: Float = 0.5f,      // 0-1 (time to return to baseline)
    var recoverySpeed: Float = 0f,          // Days to bounce back

    // Clarity & Coherence
    var clarityTrend: Trend = Trend.STABLE,
    var narrativeCoherence: Float = 0.5f,   // 0-1 (story consistency)

    // Social & Purpose
    var socialSupportTrend: Trend = Trend.STABLE,
    var purposeTrend: Trend = Trend.STABLE,

    // Data Quality
    var diaryCount: Int = 0,                // Entries this week
    var snapshotCount: Int = 0,             // Wellbeing snapshots this week
    var confidence: Float = 0f              // 0-1 (based on data density)
)

data class StressPeak(
    val timestamp: Long,
    val level: Int,
    val trigger: Trigger?,
    val resolved: Boolean = false
)

enum class Trend {
    IMPROVING, STABLE, DECLINING, INSUFFICIENT_DATA
}
```

**Firestore Indexes**: ⏳ Created in Phase 3

---

## 2. Responsibility Matrix

| Data Type | Source | Frequency | User Effort |
|-----------|--------|-----------|-------------|
| **Emotion intensity, stress, energy, calm/anxiety, trigger, body sensation** | User (Diary Input) | Per entry | <10s |
| **Sentiment, topics, phrases, cognitive patterns, summary, prompts** | AI (Cloud Function) | On save | Automatic |
| **Life/work/relationships, mindfulness, purpose, gratitude, SDT, loneliness** | User (Snapshot) | Weekly | 2 min |
| **Stress baseline, volatility, resilience, mood trends, clarity** | AI (Weekly Batch) | Sunday 2 AM | Automatic |

**Key Principles**:
- ✅ User burden ≤ 5 min/week (10s per diary + 2 min snapshot)
- ✅ AI does heavy lifting (insights, profiling, suggestions)
- ✅ Progressive enhancement (app works without AI features)
- ✅ Smart defaults (neutral values, skip options)

---

## 3. Technical Architecture

### 3.1 System Overview (Simplified)

```
┌────────────────────────────────────────────┐
│         Android App (Kotlin)              │
│                                            │
│  Diary Screen → Wellbeing Screen          │
│       ↓               ↓                    │
│   Firestore SDK   Firestore SDK           │
└───────────┬────────────┬───────────────────┘
            │            │
            ↓            ↓
┌───────────────────────────────────────────┐
│        Firebase Firestore                 │
│  ┌──────────┬───────────┬──────────────┐  │
│  │ diaries  │ snapshots │ insights     │  │
│  └────┬─────┴───────────┴──────┬───────┘  │
└───────┼──────────────────────┼────────────┘
        │                      │
        │                      │
┌───────▼──────────────────────▼────────────┐
│       Cloud Functions (Node.js)           │
│  ┌────────────────┐  ┌──────────────────┐ │
│  │ onDiaryCreated │  │ computeProfiles  │ │
│  │  (Genkit flow) │  │  (Weekly cron)   │ │
│  └────────┬───────┘  └──────────────────┘ │
└───────────┼────────────────────────────────┘
            │
            ↓
┌───────────────────────────────────────────┐
│       Gemini 2.0 Flash API                │
│  - Text analysis                          │
│  - Sentiment detection                    │
│  - Pattern recognition                    │
└───────────────────────────────────────────┘
```

### 3.2 Data Flow (Diary → Insight)

```
1. User writes diary → Save to Firestore
2. Firestore onCreate trigger → Cloud Function
3. Cloud Function → Genkit flow → Gemini API
4. Gemini analyzes text → Returns JSON
5. Cloud Function saves insight → Firestore
6. Android app real-time listener → UI update
```

**Latency**: Target <5s end-to-end

---

## 4. AI Pipeline Design

### 4.1 Genkit Flow for Insight Generation

```typescript
// functions/src/genkit/insights.ts
import { genkit, z } from 'genkit';
import { gemini20Flash } from '@genkit-ai/googleai';

const ai = genkit({
  plugins: [googleAI()],
  model: gemini20Flash,
});

const InsightSchema = z.object({
  sentimentPolarity: z.number().min(-1).max(1),
  sentimentMagnitude: z.number().min(0),
  topics: z.array(z.string()).max(3),
  keyPhrases: z.array(z.string()).max(5),
  cognitivePatterns: z.array(z.enum([
    'CATASTROPHIZING', 'ALL_OR_NOTHING', 'OVERGENERALIZATION',
    'MIND_READING', 'FORTUNE_TELLING', 'EMOTIONAL_REASONING',
    'SHOULD_STATEMENTS', 'LABELING', 'PERSONALIZATION', 'NONE'
  ])),
  summary: z.string().max(200),
  suggestedPrompts: z.array(z.string()).min(2).max(3),
  confidence: z.number().min(0).max(1)
});

export const generateInsight = ai.defineFlow(
  {
    name: 'generateDiaryInsight',
    inputSchema: z.object({
      diaryText: z.string(),
      mood: z.string(),
      stressLevel: z.number()
    }),
    outputSchema: InsightSchema
  },
  async (input) => {
    const prompt = `
You are a compassionate AI therapist analyzing a journal entry.

Entry: "${input.diaryText}"
Mood: ${input.mood}
Stress: ${input.stressLevel}/10

Analyze for:
1. Sentiment (polarity -1 to +1, magnitude 0+)
2. Main topics (max 3 keywords)
3. Key phrases (exact quotes, max 5)
4. Cognitive distortions (CBT framework)
5. One-sentence compassionate summary
6. 2-3 reflective questions to deepen insight

CRITICAL RULES:
- NEVER diagnose ("You seem depressed" ❌)
- ALWAYS validate emotions ("It makes sense you feel..." ✅)
- Focus on patterns, not labels
- Be warm, non-judgmental, trauma-informed

Output as JSON matching the schema.
    `;

    const result = await ai.generate({
      prompt,
      output: { schema: InsightSchema }
    });

    return result.output;
  }
);
```

### 4.2 Cloud Function Trigger

```typescript
// functions/src/index.ts
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { generateInsight } from './genkit/insights';

export const onDiaryCreated = onDocumentCreated(
  'diaries/{diaryId}',
  async (event) => {
    const diary = event.data.data();

    // Skip if AI disabled for this user
    const userSettings = await getUserSettings(diary.ownerId);
    if (!userSettings.aiInsightsEnabled) return;

    // Generate insight
    const insight = await generateInsight({
      diaryText: diary.description,
      mood: diary.mood,
      stressLevel: diary.stressLevel
    });

    // Save to Firestore
    await event.data.ref.firestore
      .collection('diary_insights')
      .add({
        diaryId: event.params.diaryId,
        ownerId: diary.ownerId,
        ...insight,
        generatedAt: FieldValue.serverTimestamp()
      });

    // Send notification (optional)
    await sendFCM(diary.ownerId, {
      title: '💡 New Insight Available',
      body: insight.summary,
      data: { type: 'INSIGHT_READY', diaryId: event.params.diaryId }
    });
  }
);
```

---

## 5. Implementation Roadmap (Revised)

### 🎯 Phase 1: MVP - Working Features (Weeks 1-4)

**Goal**: Get basic features working end-to-end WITHOUT optimization

#### Week 1: Android - Enhanced Diary Input

**Tasks**:
- ✅ Extend `Diary` model with 6 new fields (backward compatible)
- ✅ Create `PsychologicalMetricsSheet` composable (sliders + buttons)
- ✅ Update `WriteViewModel` to handle new fields
- ✅ Integrate into `WriteScreen` after mood selection

**Deliverable**: Users can add psychological metrics to diary entries

**Testing**: Manual testing on device, save to Firestore, verify fields

**No Firebase Console work needed** - uses existing `diaries` collection

---

#### Week 2: Android - Wellbeing Snapshot

**Tasks**:
- ✅ Create `WellbeingSnapshot` data class
- ✅ Create `SnapshotScreen` (8-10 item questionnaire with sliders)
- ✅ Create `SnapshotViewModel` + `WellbeingRepository`
- ✅ Add navigation route
- ✅ Add entry point (Home FAB or Settings)

**Deliverable**: Users can complete weekly wellbeing snapshot

**Testing**: Complete snapshot, verify saves to Firestore

**Firebase Console**:
- Collection `wellbeing_snapshots` auto-created on first save
- **NO indexes needed yet** (queries are simple, data set small)

---

#### Week 3: Firebase Setup - Cloud Functions Basics

**Tasks** (Manual):
- ✅ Initialize Firebase Functions (`firebase init`)
- ✅ Install Genkit: `npm install genkit @genkit-ai/googleai`
- ✅ Set Gemini API key: `firebase functions:secrets:set GEMINI_API_KEY`
- ✅ Enable required APIs (Cloud Functions, Cloud Scheduler, Generative Language API)

**Deliverable**: Firebase Functions ready for development

**Testing**: Deploy a "hello world" function

---

#### Week 4: Cloud Functions - Insight Generation (MVP)

**Tasks**:
- ✅ Create `functions/src/genkit/insights.ts` (Genkit flow)
- ✅ Create `functions/src/index.ts` (onDiaryCreated trigger)
- ✅ Test locally with Firebase Emulator
- ✅ Deploy to Firebase

**Deliverable**: AI insights generated when diary saved

**Testing**:
- Create diary with meaningful content
- Wait 5-10 seconds
- Check `diary_insights` collection in Firestore
- Verify sentiment, topics, cognitive patterns populated

**Firebase Console**:
- Collection `diary_insights` auto-created by Cloud Function
- **NO indexes yet** - not needed for MVP

---

### 🚀 Phase 2: AI Integration & Polish (Weeks 5-8)

#### Week 5: Android - Insights Display UI

**Tasks**:
- ✅ Create `InsightScreen` composable (card-based UI)
- ✅ Create `InsightViewModel` + `InsightRepository`
- ✅ Display sentiment, topics, cognitive patterns, summary
- ✅ Add "This insight is incorrect" feedback button
- ✅ Add deep linking from notifications

**Deliverable**: Users can view AI-generated insights

**Testing**: Tap notification → opens InsightScreen → all data displays

---

#### Week 6: Cloud Functions - Weekly Profile Computation

**Tasks**:
- ✅ Create `functions/src/scheduler/compute-profiles.ts`
- ✅ Create `functions/src/utils/profile-calculator.ts` (algorithms)
- ✅ Implement metrics calculation (stress baseline, volatility, trends)
- ✅ Deploy as HTTP Cloud Function
- ✅ Create Cloud Scheduler job (Sunday 2 AM)

**Deliverable**: Weekly psychological profiles computed automatically

**Testing**:
- Manually trigger: `curl -X POST https://..../computeWeeklyProfiles`
- Check `psychological_profiles` collection
- Verify metrics calculated correctly

**Firebase Console**:
- Collection `psychological_profiles` auto-created
- **NO indexes yet**

---

#### Week 7: Android - Profile Dashboard UI

**Tasks**:
- ✅ Create `ProfileDashboard` composable (charts)
- ✅ Integrate charting library (Vico or Compose Canvas)
- ✅ Display 4-week rolling trends (stress, mood, energy)
- ✅ Show resilience score, stress peaks timeline
- ✅ Add navigation route

**Deliverable**: Users can visualize psychological trends

**Testing**: View dashboard, verify charts match Firestore data

---

#### Week 8: FCM Notifications & Reminders

**Tasks** (Android):
- ✅ Create notification channels (Insights, Reminders, Wellness)
- ✅ Create `CalmifyFirebaseMessagingService`
- ✅ Handle deep linking from notifications
- ✅ Save FCM tokens to Firestore (`users/{userId}/fcmToken`)

**Tasks** (Cloud Functions):
- ✅ Create `sendWeeklyReminders` (Cloud Scheduler triggered)
- ✅ Create `sendMonthlyThemesReminder`
- ✅ Implement adaptive logic (skip if high stress)

**Deliverable**: Automated notifications for weekly snapshot reminders

**Testing**: Manually trigger reminder function, verify notification received

---

### ⚡ Phase 3: Optimization & Scale (Weeks 9-12)

#### Week 9: Firestore Indexing (Now We Know What We Need!)

**Why now?** You have real data and real query patterns. Firebase has likely shown you error messages like:
```
FAILED_PRECONDITION: The query requires an index.
Create it here: https://console.firebase.google.com/...
```

**Tasks** (Manual - Firebase Console):
- ✅ Click error links OR manually create composite indexes:
  - `diary_insights`: (`ownerId` ASC, `generatedAt` DESC)
  - `wellbeing_snapshots`: (`ownerId` ASC, `timestamp` DESC)
  - `psychological_profiles`: (`ownerId` ASC, `year` DESC, `weekNumber` DESC)

**Deliverable**: Fast queries for all collections

**Testing**: Queries complete in <500ms

---

#### Week 10: Vector Search & RAG (Advanced)

**Prerequisites**:
- ✅ At least 50+ diaries in database (for meaningful similarity search)
- ✅ Vector index takes time to build (15-30 min)

**Tasks** (Manual - Firebase Console):
- ✅ Create vector index: `diaries.embedding` (768 dimensions, COSINE)

**Tasks** (Cloud Functions):
- ✅ Generate embeddings on diary create (Genkit flow)
- ✅ Create `getSimilarDiaries` callable function (vector search)

**Tasks** (Android):
- ✅ Update `ChatRepositoryImpl` with RAG integration
- ✅ Build personalized prompts with diary context

**Deliverable**: Chat AI references past diary entries

**Testing**: Send chat message, verify AI mentions relevant past diaries

---

#### Week 11: Ethical Safeguards & Privacy

**Tasks** (Android):
- ✅ Create consent screen (first-time AI features)
- ✅ Add privacy settings (disable insights, export data)
- ✅ Implement data export (GDPR compliance)
- ✅ Add explainability tooltips

**Tasks** (Cloud Functions):
- ✅ Crisis detection logic (suicidal keywords, sustained stress)
- ✅ Emergency notifications (988 Suicide Lifeline, etc.)

**Deliverable**: Ethical AI with user control

**Testing**: Trigger crisis detection, verify notification sent

---

#### Week 12: Testing & Beta Launch

**Tasks**:
- ✅ Integration tests (end-to-end flows)
- ✅ Load testing (Cloud Functions performance)
- ✅ Beta testing (5-10 users, 2 weeks)
- ✅ Bug fixes from beta feedback
- ✅ Deploy to Google Play Internal Track (10% rollout)

**Deliverable**: Production-ready feature

---

## 6. Firebase Infrastructure Strategy

### 6.1 Philosophy: "Just-In-Time Infrastructure"

**Problem with traditional approach**:
❌ Create all indexes upfront → Waste time on unused indexes
❌ Optimize before measuring → Premature optimization
❌ Complex setup → Delays MVP

**Our approach**:
✅ Start simple → Add complexity when needed
✅ Let Firebase tell us what's slow → Create indexes on demand
✅ MVP first → Optimize later

### 6.2 Infrastructure Timeline

| Week | Task | Why |
|------|------|-----|
| 1-4 | **No indexes** (except existing `diaries`) | Data set too small to matter |
| 5-8 | **Monitor query performance** | Identify slow queries |
| 9 | **Create indexes** based on Firebase error messages | Real usage patterns known |
| 10 | **Vector search** (only if 50+ diaries exist) | Meaningful similarity search |

### 6.3 How to Know When to Add an Index

Firebase will tell you! When a query is slow, you'll see:

**Firestore Console → Logs**:
```
FAILED_PRECONDITION: The query requires an index.
Create it here: https://console.firebase.google.com/project/.../indexes?create_composite=...
```

**Action**: Click the link → Firebase creates the index for you!

---

## 7. Ethical Considerations

### 7.1 Core Principles

**Based on APA Ethics Code + EU AI Act**

1. **Beneficence**: AI should help, not harm
   - ❌ No diagnostic claims ("You have depression")
   - ✅ Focus on patterns, not labels
   - ✅ Suggest professional help when needed

2. **Non-maleficence**: Do no psychological harm
   - ❌ No shame/blame language
   - ✅ Compassionate, validating tone
   - ✅ Recognize AI limitations (not a therapist)

3. **Autonomy**: User in control
   - ✅ Opt-out always available
   - ✅ Override AI suggestions
   - ✅ Export and delete data

4. **Justice**: Fair to all users
   - ✅ No demographic bias
   - ✅ Accessible UI (screen readers, large text)
   - ✅ Free tier includes core features

5. **Explainability**: Transparent AI
   - ✅ Show how insights were derived
   - ✅ Confidence scores visible
   - ✅ "Learn more" links

### 7.2 Crisis Detection Protocol

**Red Flags**:
- Keywords: "suicide", "harm myself", "end it all"
- Sustained severe stress: 9-10 for 7+ consecutive days
- Extreme isolation: loneliness 9-10 + no social support

**Response**:
```typescript
if (crisisLevel === CrisisLevel.HIGH) {
  await sendFCM(userId, {
    title: '🆘 We\'re here for you',
    body: 'If you\'re in crisis, please reach out:\n\n' +
          '988 Suicide & Crisis Lifeline (US)\n' +
          'Text HELLO to 741741 (Crisis Text Line)',
    priority: 'high',
    channelId: 'crisis'
  });
}
```

---

## 8. Testing Strategy

### 8.1 Unit Testing

**Cloud Functions**:
```typescript
describe('generateInsight', () => {
  it('detects cognitive patterns correctly', async () => {
    const input = {
      diaryText: 'I always mess up. I\'m a failure.',
      mood: 'Sad',
      stressLevel: 8
    };

    const result = await generateInsight(input);

    expect(result.cognitivePatterns).toContain('OVERGENERALIZATION');
    expect(result.cognitivePatterns).toContain('LABELING');
    expect(result.sentimentPolarity).toBeLessThan(0);
  });
});
```

**Android**:
```kotlin
@Test
fun `sendMessage updates UI state correctly`() = runTest {
    val viewModel = ChatViewModel(mockRepository)
    viewModel.sendMessage("Hello")

    viewModel.uiState.test {
        val state = awaitItem()
        assertEquals(1, state.messages.size)
    }
}
```

### 8.2 Integration Testing

**End-to-End Flow**:
1. Create diary on Android
2. Verify Cloud Function triggers
3. Verify insight saved to Firestore
4. Verify notification sent
5. Verify InsightScreen displays data

### 8.3 Load Testing

**Artillery.io** (functions/load-test.yml):
```yaml
config:
  target: 'https://us-central1-[PROJECT_ID].cloudfunctions.net'
  phases:
    - duration: 60
      arrivalRate: 10  # 10 requests/sec
```

**Acceptance Criteria**:
- P95 latency < 5s
- Error rate < 1%
- No cold starts under sustained load

---

## 9. Cost Estimation

### Monthly Costs (1,000 active users)

| Service | Usage | Cost |
|---------|-------|------|
| **Firestore** | 100K reads, 50K writes | $0.60 |
| **Cloud Functions** | 500K invocations | $10 |
| **Gemini API** | 100K requests | $15 |
| **Vector Search** | 10K queries | $1 |
| **Firebase Storage** | 10GB | $0.26 |
| **FCM** | Unlimited | $0 |
| **Cloud Scheduler** | 3 jobs × 4 runs/day | $0.30 |
| **Total** | | **~$27/month** |

**Per-User Cost**: $0.027/month ($0.32/year)

**Scalability**:
- 10K users: ~$270/month
- 100K users: ~$2,700/month (volume discounts apply)

---

## 10. Appendices

### Appendix A: Sample AI Prompts

See original document Section "Appendix C: Sample Prompts"

### Appendix B: Firestore Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isAuthenticated() {
      return request.auth != null;
    }

    function isOwner(ownerId) {
      return isAuthenticated() && request.auth.uid == ownerId;
    }

    // Diaries: Users can read/write their own
    match /diaries/{diaryId} {
      allow read: if isOwner(resource.data.ownerId);
      allow create: if isAuthenticated() && request.resource.data.ownerId == request.auth.uid;
      allow update, delete: if isOwner(resource.data.ownerId);
    }

    // Insights: Read own, Cloud Functions write
    match /diary_insights/{insightId} {
      allow read: if isOwner(resource.data.ownerId);
      allow write: if false; // Only Cloud Functions
    }

    // Wellbeing snapshots: Read/write own
    match /wellbeing_snapshots/{snapshotId} {
      allow read: if isOwner(resource.data.ownerId);
      allow create: if isAuthenticated() && request.resource.data.ownerId == request.auth.uid;
      allow update, delete: if isOwner(resource.data.ownerId);
    }

    // Profiles: Read own, Cloud Functions write
    match /psychological_profiles/{profileId} {
      allow read: if isOwner(resource.data.ownerId);
      allow write: if false; // Only Cloud Functions
    }
  }
}
```

### Appendix C: Quick Reference

**When to create Firestore indexes**:
- ❌ Week 1: NO (data set too small)
- ❌ Week 5: NO (still testing)
- ✅ Week 9: YES (real usage patterns known)

**When to enable Vector Search**:
- ❌ Before 50+ diaries: NO (not meaningful)
- ✅ After 100+ diaries: YES (useful similarity search)

**When to optimize Cloud Functions**:
- ❌ Week 4: NO (premature optimization)
- ✅ Week 10: YES (if latency >5s or cold starts problematic)

---

**End of Revised Plan**

*Revised by Jarvis AI - 2025-01-19*
*Philosophy: Build → Measure → Optimize*
*Status: Ready for Implementation*
