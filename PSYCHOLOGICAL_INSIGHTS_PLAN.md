# Calmify - Psychological Insights & Wellbeing Enhancement Plan

**Document Version**: 1.0
**Created**: 2025-01-19
**Status**: Planning Phase
**Estimated Timeline**: 8-12 weeks
**Complexity**: High (AI/ML, Cloud Functions, Ethical Considerations)

---

## Executive Summary

This document outlines a comprehensive enhancement to Calmify that transforms it from a simple journaling app into an **intelligent mental health companion** with evidence-based psychological insights, while maintaining ethical standards and user autonomy.

### Core Objectives

1. **Minimal User Burden**: Enhance diary entries with quick 10-second inputs
2. **AI-Powered Insights**: Automatic analysis of journal content for patterns and trends
3. **Holistic Wellbeing Tracking**: Weekly snapshot questionnaire (2 minutes)
4. **Predictive Profiling**: Rolling psychological profile for personalized support
5. **Ethical AI**: Transparent, correctable, and privacy-respecting insights

### Key Metrics

- **User Engagement**: Target 70% weekly active users completing wellbeing snapshot
- **Insight Accuracy**: >80% user satisfaction with AI-generated insights
- **Privacy Compliance**: 100% GDPR/HIPAA-ready data handling
- **Performance**: <2s latency for insight generation, <500ms for profile queries

---

## Table of Contents

1. [Data Model Extensions](#1-data-model-extensions)
2. [Responsibility Matrix](#2-responsibility-matrix)
3. [Frequency & Triggers](#3-frequency--triggers)
4. [Technical Architecture](#4-technical-architecture)
5. [AI Pipeline Design](#5-ai-pipeline-design)
6. [Firebase Tooling](#6-firebase-tooling)
7. [Implementation Roadmap](#7-implementation-roadmap)
8. [Ethical Considerations](#8-ethical-considerations)
9. [Testing Strategy](#9-testing-strategy)
10. [Future Enhancements](#10-future-enhancements)

---

## 1. Data Model Extensions

### 1.1 Enhanced Diary Entry (Lightweight Additions)

**Firestore Collection**: `diaries` (existing, extended)

```kotlin
data class DiaryEntry(
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

**UI/UX Implementation**:
- **Preset buttons** + **horizontal sliders** (Material3 Slider)
- **Optional skip** (defaults to 5/neutral)
- **Time budget**: <10 seconds total
- **Placement**: After mood selection, before save

**Migration Strategy**:
```kotlin
// Existing entries default to neutral (5/NONE)
// No data loss, backward compatible
```

---

### 1.2 Wellbeing Snapshot (Weekly Questionnaire)

**Firestore Collection**: `wellbeing_snapshots`

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

    // SDT Pillars (optional but powerful)
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

**Questionnaire Design**:
- **8-10 items** total (adaptive: shorten if 3+ diaries this week)
- **Format**: 0-10 sliders with emoji anchors
- **Time**: 2 minutes target (tracked via `completionTime`)
- **Trigger**: Weekly notification (customizable day/time)

**Evidence Base**:
- **SDT** (Deci & Ryan): autonomy, competence, relatedness
- **Mindfulness**: attention regulation (Brown & Ryan, 2003)
- **Purpose**: meaning-making (Steger et al., 2006)
- **Loneliness**: UCLA scale simplified (Russell, 1996)

---

### 1.3 AI-Generated Diary Insights

**Firestore Collection**: `diary_insights`

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
    var processingTime: Long = 0L           // Milliseconds
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

**Generation Strategy**:
- **Trigger**: On diary save (Cloud Function)
- **Model**: Gemini 2.0 Flash (fast, cost-effective)
- **Prompt Engineering**:
  ```
  Analyze this journal entry for:
  1. Sentiment (polarity & magnitude)
  2. Main topics (max 3)
  3. Key phrases (max 5)
  4. Cognitive distortions (CBT framework)
  5. One-sentence summary
  6. 2-3 reflective prompts

  Be compassionate, non-judgmental, evidence-based.
  Output as JSON.
  ```

**Ethical Safeguards**:
- ✅ **User override**: "This insight is incorrect" button
- ✅ **Explainability**: Show text snippets that led to pattern detection
- ✅ **Opt-out**: Disable insights entirely in settings
- ✅ **Privacy**: Insights stored client-side only (optional cloud backup)

---

### 1.4 Dynamic Psychological Profile (Rolling Window)

**Firestore Collection**: `psychological_profiles`

```kotlin
data class PsychologicalProfile(
    @DocumentId
    var id: String = "${ownerId}_week_${weekNumber}", // Composite key
    var ownerId: String = "",
    var weekNumber: Int = 0,                // ISO week number
    var year: Int = 2025,
    @ServerTimestamp
    var computedAt: Date = Date.from(Instant.now()),

    // Stress Dynamics
    var stressBaseline: Float = 5f,         // Weighted average (recent = heavier)
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
    var clarityTrend: Trend = Trend.STABLE, // Derived from language complexity
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

**Computation Strategy**:
- **Trigger**: Weekly Cloud Scheduler (Sunday 2 AM)
- **Algorithm**:
  1. Fetch all diary entries + insights from past 7-14 days
  2. Calculate weighted averages (exponential decay: recent = more weight)
  3. Detect peaks/troughs in stress/mood
  4. Measure volatility (std dev)
  5. Calculate resilience (area under curve after negative event)
  6. Analyze language patterns (clarity via Gemini)
  7. Store profile in Firestore
- **Privacy**: Aggregate data only, no raw text stored

**Use Cases**:
- Personalized notification timing (low energy → skip morning reminder)
- Adaptive AI tone (high stress → more empathetic responses)
- Early warning system (declining trend + high volatility → suggest support)
- Progress visualization (4-week trend charts)

---

### 1.5 Life Themes & Goals (Optional)

**Firestore Collection**: `life_themes`

```kotlin
data class LifeTheme(
    @DocumentId
    var id: String = UUID.randomUUID().toString(),
    var ownerId: String = "",
    @ServerTimestamp
    var createdAt: Date = Date.from(Instant.now()),
    var lastReviewedAt: Date = Date.from(Instant.now()),

    var label: String = "",                 // "Career", "Family", "Health"
    var priority: Int = 3,                  // 1-5 (1=highest)
    var goal: String = "",                  // "Get promoted", "Improve work-life balance"
    var progress: Int = 0,                  // 0-100%

    var relatedDiaryIds: List<String> = emptyList(), // Auto-tagged by AI
    var milestones: List<Milestone> = emptyList(),

    var isActive: Boolean = true
)

data class Milestone(
    val description: String,
    val targetDate: Date?,
    val completedAt: Date?,
    val isCompleted: Boolean = false
)
```

**AI Assistance**:
- Auto-suggest themes from diary topic analysis
- Remind user to review monthly
- Connect diary entries to themes via embedding similarity

---

## 2. Responsibility Matrix

| Data Type | Source | Frequency | Effort |
|-----------|--------|-----------|--------|
| **Emotion intensity, stress, energy, calm/anxiety, trigger, body sensation** | User (Diary Input) | Per entry (event-driven) | <10s |
| **Sentiment, topics, phrases, cognitive patterns, summary, prompts** | AI (DiaryInsight) | On save or batch (1-5s) | Automatic |
| **Life/work/relationships satisfaction, mindfulness, purpose, gratitude, SDT, loneliness** | User (Wellbeing Snapshot) | Weekly (notification) | 2 min |
| **Stress baseline, volatility, resilience, mood trends, clarity, social/purpose trends** | AI/Derived (PsyProfile) | Weekly (cron job) | Automatic |
| **Life themes, goals, milestones** | User (with AI suggestions) | Monthly (optional) | 3-5 min |

**Key Principles**:
- ✅ **User burden ≤ 5 min/week** (10s per diary + 2 min snapshot)
- ✅ **AI does heavy lifting** (insights, profiling, suggestions)
- ✅ **Progressive enhancement** (app works perfectly without extras)
- ✅ **Smart defaults** (neutral values, skip options)

---

## 3. Frequency & Triggers

### 3.1 Event-Driven (Passive)

| Event | Trigger | Action | Latency |
|-------|---------|--------|---------|
| Diary saved | Firestore onCreate | Generate DiaryInsight (Cloud Function) | <5s |
| Snapshot completed | Firestore onCreate | Update weekly profile preview | <1s |
| Stress peak detected | Profile computation | Send compassionate notification | Next day |

### 3.2 Time-Based (Active)

| Schedule | Trigger | Action | Rationale |
|----------|---------|--------|-----------|
| **Sunday 9 PM** | Cloud Scheduler | Send weekly snapshot reminder (FCM) | Prep for new week |
| **Sunday 2 AM** | Cloud Scheduler | Compute PsychologicalProfile (batch) | Low traffic time |
| **1st of month** | Cloud Scheduler | Suggest life themes review | Monthly reflection |
| **7 days no diary** | Cloud Scheduler | Gentle check-in notification | Re-engagement |

### 3.3 Adaptive Logic

```kotlin
// If user wrote ≥3 diaries this week → shorten snapshot
val snapshotLength = if (diaryCountThisWeek >= 3) {
    SnapshotMode.SHORT // 4 items (2 min → 1 min)
} else {
    SnapshotMode.FULL  // 8-10 items (2 min)
}

// If stress peak detected → skip energetic notifications
if (profile.stressBaseline > 7 && profile.moodTrend == Trend.DECLINING) {
    notificationTone = NotificationTone.COMPASSIONATE
    notificationFrequency = NotificationFrequency.REDUCED
}
```

---

## 4. Technical Architecture

### 4.1 System Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                      Android App (Kotlin)                        │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐│
│  │   Diary    │  │ Wellbeing  │  │  Insights  │  │  Profile   ││
│  │   Screen   │  │  Snapshot  │  │   Screen   │  │  Dashboard ││
│  └──────┬─────┘  └──────┬─────┘  └──────┬─────┘  └──────┬─────┘│
│         │                │                │                │      │
│         └────────────────┴────────────────┴────────────────┘      │
│                              │                                    │
│                    ┌─────────▼─────────┐                         │
│                    │  Firestore SDK    │                         │
│                    └─────────┬─────────┘                         │
└──────────────────────────────┼──────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Firebase Cloud     │
                    └──────────┬──────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
┌───────▼────────┐  ┌─────────▼─────────┐  ┌────────▼────────┐
│  Firestore     │  │ Cloud Functions   │  │ Cloud Scheduler │
│  (Database)    │  │ (Compute)         │  │ (Cron Jobs)     │
└───────┬────────┘  └─────────┬─────────┘  └────────┬────────┘
        │                     │                      │
        │           ┌─────────▼─────────┐           │
        │           │   Genkit (AI)     │           │
        │           │ - Insight Gen     │           │
        │           │ - Profile Compute │           │
        │           │ - RAG (Vector)    │           │
        │           └─────────┬─────────┘           │
        │                     │                      │
        │           ┌─────────▼─────────┐           │
        │           │  Gemini 2.0 Flash │           │
        │           │  - Text Analysis  │           │
        │           │  - Embeddings     │           │
        │           └───────────────────┘           │
        │                                            │
        └────────────────┬───────────────────────────┘
                         │
                ┌────────▼────────┐
                │   FCM (Push)    │
                │  - Reminders    │
                │  - Insights     │
                └─────────────────┘
```

### 4.2 Data Flow Examples

#### A) Diary Save with Insight Generation

```
User writes diary → App saves to Firestore → Firestore onCreate trigger
→ Cloud Function (insight-generator) → Genkit prompt Gemini
→ Parse JSON response → Save DiaryInsight to Firestore
→ FCM notification "New insight ready!" → User opens app
```

#### B) Weekly Profile Computation

```
Sunday 2 AM → Cloud Scheduler → HTTP trigger Cloud Function (profile-computer)
→ Query Firestore (diaries + snapshots from past 7 days)
→ Genkit aggregation pipeline → Calculate metrics
→ Save PsychologicalProfile → Update cache → Done
```

#### C) Weekly Snapshot Reminder

```
Sunday 9 PM → Cloud Scheduler → HTTP trigger Cloud Function (reminder-sender)
→ Query users who haven't done snapshot this week
→ Send FCM notification (data payload: snapshotMode)
→ User taps → Open snapshot screen with pre-filled mode
```

---

### 4.3 Module Structure (Android)

**New Modules**:

```
features/
├── insights/                   # NEW
│   ├── InsightsScreen.kt      # View AI-generated insights
│   ├── InsightsViewModel.kt   # Fetch from Firestore
│   └── InsightCard.kt         # UI component
│
├── wellbeing/                  # NEW
│   ├── SnapshotScreen.kt      # Weekly questionnaire
│   ├── SnapshotViewModel.kt   # Save to Firestore
│   └── ProfileDashboard.kt    # Rolling profile visualization
│
└── chat/                       # EXISTING (enhanced)
    └── ChatRepositoryImpl.kt  # Add getDiariesForRAG()

data/mongo/                     # EXISTING (extended)
├── database/entity/
│   ├── WellbeingSnapshotEntity.kt  # NEW (Room cache)
│   └── LifeThemeEntity.kt          # NEW (Room cache)
│
└── repository/
    ├── InsightRepository.kt        # NEW (Firestore)
    ├── WellbeingRepository.kt      # NEW (Firestore)
    └── ProfileRepository.kt        # NEW (Firestore)
```

**Dependencies**:
- No new external dependencies needed!
- Use existing Firebase SDK (Firestore, Functions, FCM)
- Genkit runs on Cloud Functions (Node.js)

---

## 5. AI Pipeline Design

### 5.1 Genkit Architecture (Cloud Functions)

**Why Genkit?**
- ✅ Built by Firebase team for AI workflows
- ✅ First-class Gemini integration
- ✅ Structured output parsing (JSON schemas)
- ✅ Observability & debugging tools
- ✅ RAG (Vector Search) support

**Genkit Flows**:

```typescript
// functions/src/genkit/insights.ts
import { genkit, z } from 'genkit';
import { gemini20Flash } from '@genkit-ai/googleai';

const ai = genkit({
  plugins: [googleAI()],
  model: gemini20Flash,
});

// Schema for structured output
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

      Entry:
      "${input.diaryText}"

      Mood: ${input.mood}
      Stress: ${input.stressLevel}/10

      Analyze for:
      1. Sentiment (polarity -1 to +1, magnitude 0+)
      2. Main topics (max 3 keywords)
      3. Key phrases (exact quotes, max 5)
      4. Cognitive distortions (CBT framework)
      5. One-sentence compassionate summary
      6. 2-3 reflective questions to deepen insight

      Be warm, non-judgmental, evidence-based.
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

**Cloud Function Trigger**:

```typescript
// functions/src/index.ts
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { generateInsight } from './genkit/insights';

export const onDiaryCreated = onDocumentCreated(
  'diaries/{diaryId}',
  async (event) => {
    const diary = event.data.data();

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

    // Send notification
    await sendFCM(diary.ownerId, 'New insight ready!');
  }
);
```

---

### 5.2 Vector Search for RAG (Diary Context)

**Use Case**: When AI responds in chat, retrieve **similar past diaries** for context.

**Setup**:

```typescript
// Firestore Vector Search (built-in)
import { VectorQuery } from '@google-cloud/firestore';

// 1. Generate embeddings on diary save
export const onDiaryCreatedEmbedding = onDocumentCreated(
  'diaries/{diaryId}',
  async (event) => {
    const diary = event.data.data();

    // Generate embedding via Gemini
    const embedding = await ai.embed({
      content: diary.description,
      model: 'text-embedding-004'
    });

    // Store vector in Firestore
    await event.data.ref.update({
      embedding: FieldValue.vector(embedding)
    });
  }
);

// 2. Query similar diaries in chat
export const getSimilarDiaries = ai.defineFlow(
  {
    name: 'getSimilarDiaries',
    inputSchema: z.object({
      userMessage: z.string(),
      userId: z.string(),
      limit: z.number().default(5)
    })
  },
  async (input) => {
    // Embed query
    const queryEmbedding = await ai.embed({
      content: input.userMessage,
      model: 'text-embedding-004'
    });

    // Vector search
    const results = await db.collection('diaries')
      .where('ownerId', '==', input.userId)
      .findNearest('embedding', queryEmbedding, {
        limit: input.limit,
        distanceMeasure: 'COSINE'
      })
      .get();

    return results.docs.map(doc => doc.data());
  }
);
```

**Integration with ChatRepository**:

```kotlin
// In ChatRepositoryImpl.kt
private suspend fun getDiaryContext(userMessage: String): List<Diary> {
    // Call Cloud Function with HTTP
    val response = httpClient.post("https://us-central1-calmify.cloudfunctions.net/getSimilarDiaries") {
        contentType(ContentType.Application.Json)
        setBody(json {
            "userMessage" to userMessage
            "userId" to currentUserId
            "limit" to 5
        })
    }

    return response.body<List<Diary>>()
}

// Update AI prompt with RAG context
private fun buildPersonalizedPrompt(
    userMessage: String,
    similarDiaries: List<Diary>
): String {
    return """
        User: $userMessage

        Recent similar journal entries:
        ${similarDiaries.joinToString("\n") { "- ${it.title}: ${it.description.take(100)}" }}

        Respond with empathy and specific references to their past reflections.
    """.trimIndent()
}
```

---

### 5.3 Profile Computation Pipeline

**Cloud Scheduler → HTTP Function**:

```typescript
// functions/src/scheduler/compute-profiles.ts
import { onSchedule } from 'firebase-functions/v2/scheduler';

export const computeWeeklyProfiles = onSchedule(
  'every sunday 02:00',
  async (event) => {
    const users = await db.collection('users').get();

    for (const userDoc of users.docs) {
      const userId = userDoc.id;

      // Fetch data
      const diaries = await getDiariesLastWeek(userId);
      const snapshots = await getSnapshotsLastWeek(userId);

      // Compute metrics
      const profile = await computeProfile({
        userId,
        diaries,
        snapshots
      });

      // Save
      await db.collection('psychological_profiles').add(profile);
    }
  }
);

async function computeProfile(data: {
  userId: string,
  diaries: Diary[],
  snapshots: WellbeingSnapshot[]
}) {
  // Calculate stress baseline (exponential weighted average)
  const stressValues = data.diaries.map(d => ({
    value: d.stressLevel,
    timestamp: d.date.getTime()
  }));

  const stressBaseline = calculateWeightedAverage(stressValues);
  const stressVolatility = calculateStdDev(stressValues.map(s => s.value));

  // Detect peaks
  const stressPeaks = detectPeaks(stressValues, threshold: 7);

  // Calculate resilience (recovery time after peaks)
  const resilienceIndex = calculateResilience(stressValues, stressPeaks);

  // Mood trend
  const moodTrend = detectTrend(data.diaries.map(d => moodToNumber(d.mood)));

  // Language clarity (via Gemini)
  const clarityScore = await analyzeLanguageClarity(
    data.diaries.map(d => d.description).join('\n')
  );

  return {
    ownerId: data.userId,
    weekNumber: getISOWeekNumber(new Date()),
    year: new Date().getFullYear(),
    stressBaseline,
    stressVolatility,
    stressPeaks,
    moodBaseline: calculateAverage(data.diaries.map(d => moodToNumber(d.mood))),
    moodVolatility: calculateStdDev(data.diaries.map(d => moodToNumber(d.mood))),
    moodTrend,
    resilienceIndex,
    clarityTrend: clarityScore > 0.7 ? 'IMPROVING' : 'STABLE',
    diaryCount: data.diaries.length,
    snapshotCount: data.snapshots.length,
    confidence: calculateConfidence(data.diaries.length, data.snapshots.length),
    computedAt: FieldValue.serverTimestamp()
  };
}
```

---

## 6. Firebase Tooling

### 6.1 Cloud Functions for Firebase

**Setup**:
```bash
firebase init functions
# Choose TypeScript
# Install Genkit: npm install genkit @genkit-ai/googleai
```

**Function Types**:

| Function | Trigger | Purpose | Timeout |
|----------|---------|---------|---------|
| `onDiaryCreated` | Firestore onCreate | Generate insight | 60s |
| `onDiaryEmbedding` | Firestore onCreate | Generate vector | 30s |
| `computeWeeklyProfiles` | Cloud Scheduler (Sunday 2 AM) | Aggregate metrics | 540s |
| `sendWeeklyReminders` | Cloud Scheduler (Sunday 9 PM) | FCM notifications | 60s |
| `getSimilarDiaries` | HTTPS callable | RAG query | 10s |

**Cost Optimization**:
- Use **2nd gen functions** (cheaper cold starts)
- Set **min instances = 0** (scale to zero)
- Use **regional deployment** (us-central1)
- Cache Gemini responses (Firestore TTL)

---

### 6.2 Firebase Cloud Messaging (FCM)

**Notification Channels**:

```kotlin
// Android setup
enum class NotificationChannel(val id: String, val importance: Int) {
    INSIGHTS("insights", NotificationManager.IMPORTANCE_DEFAULT),
    REMINDERS("reminders", NotificationManager.IMPORTANCE_HIGH),
    WELLNESS_CHECK("wellness", NotificationManager.IMPORTANCE_LOW),
    ACHIEVEMENTS("achievements", NotificationManager.IMPORTANCE_LOW)
}

// Create channels in MainActivity
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    createNotificationChannels()
}

private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel.values().forEach { channel ->
            val notificationChannel = NotificationChannel(
                channel.id,
                channel.name,
                channel.importance
            ).apply {
                description = channel.description
            }
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}
```

**Message Types**:

```typescript
// Cloud Function
import { getMessaging } from 'firebase-admin/messaging';

async function sendInsightNotification(userId: string, diaryId: string) {
  const message = {
    notification: {
      title: '💡 New Insight Available',
      body: 'Your journal entry has been analyzed',
      imageUrl: 'https://...'  // Optional
    },
    data: {
      type: 'INSIGHT_READY',
      diaryId: diaryId,
      clickAction: 'OPEN_INSIGHT_SCREEN'
    },
    android: {
      channelId: 'insights',
      priority: 'default'
    },
    token: await getUserFCMToken(userId)
  };

  await getMessaging().send(message);
}

async function sendWeeklyReminder(userId: string, snapshotMode: string) {
  const message = {
    notification: {
      title: '🌟 Weekly Check-In',
      body: snapshotMode === 'SHORT'
        ? 'Quick 1-minute wellness check (you wrote 3+ diaries!)'
        : 'Your 2-minute weekly wellness snapshot'
    },
    data: {
      type: 'WEEKLY_SNAPSHOT',
      snapshotMode: snapshotMode,
      clickAction: 'OPEN_SNAPSHOT_SCREEN'
    },
    android: {
      channelId: 'reminders',
      priority: 'high'
    },
    token: await getUserFCMToken(userId)
  };

  await getMessaging().send(message);
}
```

**Android Handler**:

```kotlin
class CalmifyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "INSIGHT_READY" -> {
                val diaryId = message.data["diaryId"]
                showNotification(
                    channelId = NotificationChannel.INSIGHTS.id,
                    title = message.notification?.title,
                    body = message.notification?.body,
                    pendingIntent = createDeepLink("calmify://insights/$diaryId")
                )
            }

            "WEEKLY_SNAPSHOT" -> {
                val snapshotMode = message.data["snapshotMode"]
                showNotification(
                    channelId = NotificationChannel.REMINDERS.id,
                    title = message.notification?.title,
                    body = message.notification?.body,
                    pendingIntent = createDeepLink("calmify://wellbeing/snapshot?mode=$snapshotMode")
                )
            }
        }
    }
}
```

---

### 6.3 Firestore Vector Search

**Enable in Firebase Console**:
1. Go to Firestore → Indexes
2. Create vector index on `diaries` collection
3. Field: `embedding`, Dimension: 768 (Gemini embeddings), Distance: COSINE

**Cost**:
- Embedding generation: ~$0.00001 per diary (via Gemini API)
- Vector search: ~$0.001 per 1000 queries
- Storage: Negligible (768-dim vector ≈ 3KB)

---

### 6.4 BigQuery Export + Looker Studio

**Purpose**: Long-term analytics and research insights (optional)

**Setup**:
1. Firebase Console → Project Settings → Integrations → BigQuery
2. Enable daily export of Firestore collections
3. Create scheduled queries in BigQuery:

```sql
-- Example: Weekly stress trends across all users (anonymized)
SELECT
  EXTRACT(WEEK FROM TIMESTAMP_MILLIS(createdAt)) AS week,
  AVG(stressLevel) AS avg_stress,
  STDDEV(stressLevel) AS stress_volatility,
  COUNT(*) AS entry_count
FROM `calmify.firestore_export.diaries`
WHERE createdAt >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 12 WEEK)
GROUP BY week
ORDER BY week DESC
```

**Looker Studio Dashboard**:
- Line chart: Stress trends over time
- Heatmap: Trigger frequency (work, family, health, etc.)
- Funnel: Snapshot completion rate
- Cohort retention: Weekly active users

**Privacy**:
- Aggregate only (no user-identifiable data)
- GDPR compliance: export opt-out setting
- Data retention: 90 days max

---

## 7. Implementation Roadmap

### Phase 1: Foundation (Weeks 1-3)

**Week 1: Database Schema**
- [ ] Extend `Diary` model with psychological metrics
- [ ] Create `WellbeingSnapshot` entity (Firestore + Room)
- [ ] Create `DiaryInsight` entity (Firestore + Room)
- [ ] Create `PsychologicalProfile` entity (Firestore only)
- [ ] Add Firestore indexes (ownerId, date, weekNumber)
- [ ] Write migration script (defaults for existing entries)

**Week 2: Android UI - Enhanced Diary**
- [ ] Design psychological metrics input UI (sliders + presets)
- [ ] Implement `PsychologicalMetricsSheet` composable
- [ ] Add to `WriteScreen` after mood selection
- [ ] Add skip/default logic
- [ ] Unit test: metrics validation
- [ ] UI test: metrics input flow

**Week 3: Android UI - Wellbeing Snapshot**
- [ ] Create `features/wellbeing` module
- [ ] Design `SnapshotScreen` (8-10 item questionnaire)
- [ ] Implement adaptive logic (short vs full)
- [ ] Add `SnapshotViewModel` with Firestore save
- [ ] Add navigation route
- [ ] UI test: snapshot completion flow

### Phase 2: AI Insights (Weeks 4-6)

**Week 4: Cloud Functions Setup**
- [ ] Initialize Firebase Functions (TypeScript)
- [ ] Install Genkit + Gemini plugin
- [ ] Create `onDiaryCreated` trigger function
- [ ] Implement insight generation flow (Genkit)
- [ ] Add error handling + retry logic
- [ ] Deploy to Firebase (test project)

**Week 5: Insight Generation**
- [ ] Design prompt for Gemini (sentiment, topics, patterns)
- [ ] Implement structured output parsing (Zod schema)
- [ ] Add cognitive pattern detection (CBT framework)
- [ ] Test with sample diary entries
- [ ] Optimize for latency (<5s target)
- [ ] Add confidence scoring

**Week 6: Android - Insights Screen**
- [ ] Create `features/insights` module
- [ ] Design `InsightScreen` UI (card-based)
- [ ] Implement `InsightViewModel` (Firestore listener)
- [ ] Add "Incorrect insight" override button
- [ ] Add explainability view (show text snippets)
- [ ] Add opt-out setting
- [ ] Navigation integration

### Phase 3: Psychological Profiling (Weeks 7-8)

**Week 7: Profile Computation**
- [ ] Create Cloud Scheduler job (weekly)
- [ ] Implement profile computation pipeline:
  - [ ] Stress baseline + volatility
  - [ ] Mood trends
  - [ ] Resilience index
  - [ ] Language clarity (Gemini)
- [ ] Test with mock data (100+ entries)
- [ ] Optimize performance (<60s per user)
- [ ] Add confidence calculation

**Week 8: Android - Profile Dashboard**
- [ ] Design `ProfileDashboard` screen
- [ ] Implement trend charts (MPAndroidChart or Compose Canvas)
- [ ] Add 4-week rolling view
- [ ] Show stress peaks timeline
- [ ] Add resilience score visualization
- [ ] Export profile as PDF (optional)

### Phase 4: Notifications & RAG (Weeks 9-10)

**Week 9: FCM Integration**
- [ ] Set up Cloud Messaging in Android
- [ ] Create notification channels
- [ ] Implement `CalmifyFirebaseMessagingService`
- [ ] Add deep linking (insights, snapshot)
- [ ] Create Cloud Function for reminders
- [ ] Test notification delivery (emulator + device)

**Week 10: Vector Search (RAG)**
- [ ] Enable Firestore Vector Search
- [ ] Create embedding generation function
- [ ] Implement `getSimilarDiaries` callable function
- [ ] Update `ChatRepositoryImpl` with RAG
- [ ] Test similarity accuracy (manual eval)
- [ ] Optimize embedding storage (compression)

### Phase 5: Polish & Testing (Weeks 11-12)

**Week 11: Ethical Safeguards**
- [ ] Add consent screen (first-time AI features)
- [ ] Implement "Disable insights" setting
- [ ] Add data export (GDPR compliance)
- [ ] Create privacy policy update
- [ ] Add explainability tooltips
- [ ] Implement insight correction feedback loop

**Week 12: Testing & Launch**
- [ ] Integration testing (end-to-end flows)
- [ ] Load testing (100 concurrent users)
- [ ] Beta testing (20 users, 2 weeks)
- [ ] Fix critical bugs
- [ ] Performance optimization
- [ ] Staged rollout (10% → 50% → 100%)

---

## 8. Ethical Considerations

### 8.1 Privacy & Data Protection

**Principles**:
- ✅ **Data minimization**: Collect only what's needed
- ✅ **User control**: Easy opt-out, delete, export
- ✅ **Transparency**: Explain how AI works
- ✅ **Security**: Encrypted at rest and in transit

**Implementation**:

```kotlin
// Settings screen
data class PrivacySettings(
    val enableAIInsights: Boolean = true,
    val enableProfileComputation: Boolean = true,
    val shareAnonymizedData: Boolean = false, // For research
    val dataSharingConsent: Boolean = false
)

// Data export (GDPR Article 20)
suspend fun exportAllUserData(userId: String): File {
    val data = mapOf(
        "diaries" to diaryRepository.getAllDiaries(userId),
        "insights" to insightRepository.getAllInsights(userId),
        "snapshots" to wellbeingRepository.getAllSnapshots(userId),
        "profiles" to profileRepository.getAllProfiles(userId)
    )

    val json = Json.encodeToString(data)
    return File(context.filesDir, "calmify_export_$userId.json").apply {
        writeText(json)
    }
}

// Data deletion (GDPR Article 17)
suspend fun deleteAllUserData(userId: String) {
    // Delete from Firestore
    firestoreBatch {
        delete("diaries", userId)
        delete("diary_insights", userId)
        delete("wellbeing_snapshots", userId)
        delete("psychological_profiles", userId)
    }

    // Delete from Room (local)
    appDatabase.clearAllTables()

    // Delete Firebase Auth account
    FirebaseAuth.getInstance().currentUser?.delete()
}
```

---

### 8.2 Ethical AI Guidelines

**Principles** (based on APA Ethics Code + EU AI Act):

1. **Beneficence**: AI should help, not harm
   - No diagnostic claims (e.g., avoid "You have depression")
   - Focus on patterns, not labels
   - Suggest professional help when needed

2. **Non-maleficence**: Do no psychological harm
   - Avoid reinforcing negative patterns
   - No shame/blame language
   - Recognize limitations (AI ≠ therapist)

3. **Autonomy**: User in control
   - Opt-out always available
   - Override AI suggestions
   - No forced compliance

4. **Justice**: Fair to all users
   - No demographic bias (test on diverse samples)
   - Accessible UI (screen readers, large text)
   - Free tier includes core features

5. **Explainability**: Transparent AI
   - Show how insights were derived
   - Confidence scores visible
   - "Learn more" links to methodology

**Prompt Engineering for Ethics**:

```typescript
const ethicalSystemPrompt = `
You are Lifo, a compassionate AI companion for mental health journaling.

CRITICAL RULES:
1. NEVER diagnose mental health conditions (e.g., "You seem depressed")
2. NEVER give medical advice (defer to professionals)
3. ALWAYS use "I notice patterns..." not "You are..."
4. ALWAYS validate emotions ("It makes sense you feel...")
5. ALWAYS suggest professional help if severe distress detected
6. ALWAYS be warm, non-judgmental, trauma-informed
7. NEVER use stigmatizing language (crazy, broken, damaged)
8. ALWAYS focus on strengths + growth, not just problems

Example GOOD:
"I notice you've written about work stress 3 times this week.
It seems this is weighing on you. Have you considered talking
to a counselor or trusted friend?"

Example BAD:
"You show signs of work-related anxiety disorder.
You should seek treatment immediately."

Your role: supportive friend, not therapist.
`;
```

---

### 8.3 Crisis Detection & Response

**Red Flags** (automated detection):
- Keywords: "suicide", "harm myself", "no point living", "end it all"
- Patterns: Severe stress (9-10) for 7+ consecutive days
- Isolation: Loneliness score 9-10 + no social support

**Response Protocol**:

```typescript
async function detectCrisis(diary: Diary): Promise<CrisisLevel> {
  const suicidalKeywords = ['suicide', 'kill myself', 'end it all', 'no reason to live'];
  const containsSuicidalContent = suicidalKeywords.some(kw =>
    diary.description.toLowerCase().includes(kw)
  );

  if (containsSuicidalContent) {
    return CrisisLevel.HIGH;
  }

  // Check severe sustained stress
  const recentDiaries = await getLastNDiaries(diary.ownerId, 7);
  const avgStress = average(recentDiaries.map(d => d.stressLevel));

  if (avgStress >= 9) {
    return CrisisLevel.MEDIUM;
  }

  return CrisisLevel.NONE;
}

// Cloud Function trigger
export const onCrisisDetected = onDocumentCreated(
  'diaries/{diaryId}',
  async (event) => {
    const diary = event.data.data();
    const crisisLevel = await detectCrisis(diary);

    if (crisisLevel === CrisisLevel.HIGH) {
      // Immediate intervention
      await sendFCM(diary.ownerId, {
        title: '🆘 We\'re here for you',
        body: 'If you\'re in crisis, please reach out:\n\n' +
              '988 Suicide & Crisis Lifeline (US)\n' +
              'Text HELLO to 741741 (Crisis Text Line)',
        priority: 'high',
        channelId: 'crisis'
      });

      // Log for follow-up (with user consent)
      await db.collection('crisis_logs').add({
        userId: diary.ownerId,
        diaryId: event.params.diaryId,
        level: crisisLevel,
        timestamp: FieldValue.serverTimestamp()
      });
    } else if (crisisLevel === CrisisLevel.MEDIUM) {
      // Gentle suggestion
      await sendFCM(diary.ownerId, {
        title: 'Taking care of yourself',
        body: 'I notice you\'ve been under a lot of stress. ' +
              'Consider talking to a counselor or trusted friend.',
        priority: 'default',
        channelId: 'wellness'
      });
    }
  }
);
```

**Disclaimer in App**:
> **Calmify is not a replacement for professional mental health care.**
> If you're in crisis, please contact:
> - **988 Suicide & Crisis Lifeline** (US)
> - **116 123 Samaritans** (UK)
> - Your local emergency services

---

## 9. Testing Strategy

### 9.1 Unit Testing

**Android (Kotlin)**:

```kotlin
// InsightViewModelTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class InsightViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: InsightRepository
    private lateinit var viewModel: InsightViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        viewModel = InsightViewModel(repository)
    }

    @Test
    fun `loadInsights emits success state`() = runTest {
        // Given
        val mockInsights = listOf(
            DiaryInsight(id = "1", diaryId = "d1", summary = "Test")
        )
        coEvery { repository.getInsightsForDiary("d1") } returns flowOf(
            RequestState.Success(mockInsights)
        )

        // When
        viewModel.loadInsights("d1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(mockInsights, state.insights)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }
    }

    @Test
    fun `correctInsight updates Firestore`() = runTest {
        // Given
        val insightId = "insight123"
        val correction = "This is incorrect"
        coEvery { repository.markInsightIncorrect(insightId, correction) } returns
            RequestState.Success(Unit)

        // When
        viewModel.correctInsight(insightId, correction)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { repository.markInsightIncorrect(insightId, correction) }
    }
}
```

**Cloud Functions (TypeScript)**:

```typescript
// insights.test.ts
import { generateInsight } from '../src/genkit/insights';
import { mockFirestore } from './mocks';

describe('generateInsight', () => {
  it('should generate valid insight from diary text', async () => {
    const input = {
      diaryText: 'Had a terrible day at work. Boss criticized my project in front of everyone. Feel like a failure.',
      mood: 'Sad',
      stressLevel: 8
    };

    const result = await generateInsight(input);

    expect(result.sentimentPolarity).toBeLessThan(0); // Negative
    expect(result.topics).toContain('work');
    expect(result.cognitivePatterns).toContain('LABELING'); // "I'm a failure"
    expect(result.summary).toBeTruthy();
    expect(result.suggestedPrompts).toHaveLength(2);
    expect(result.confidence).toBeGreaterThan(0.7);
  });

  it('should handle positive diary entries', async () => {
    const input = {
      diaryText: 'Amazing presentation today! Team loved it. Feeling proud.',
      mood: 'Happy',
      stressLevel: 2
    };

    const result = await generateInsight(input);

    expect(result.sentimentPolarity).toBeGreaterThan(0); // Positive
    expect(result.cognitivePatterns).toEqual(['NONE']);
  });
});
```

---

### 9.2 Integration Testing

**Scenario: End-to-End Insight Generation**

```kotlin
@RunWith(AndroidJUnit4::class)
class InsightGenerationIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        // Mock Firebase Auth
        FirebaseAuth.getInstance().signInWithEmailAndPassword(
            "test@example.com",
            "password123"
        ).await()
    }

    @Test
    fun userWritesDiary_insightIsGenerated_notificationReceived() {
        // 1. Navigate to Write screen
        composeTestRule.onNodeWithText("New Diary").performClick()

        // 2. Fill in diary
        composeTestRule.onNodeWithTag("title_input")
            .performTextInput("Stressful day")
        composeTestRule.onNodeWithTag("description_input")
            .performTextInput("Boss was very demanding. Felt overwhelmed.")

        // 3. Select mood
        composeTestRule.onNodeWithText("Sad").performClick()

        // 4. Set psychological metrics
        composeTestRule.onNodeWithTag("stress_slider")
            .performGesture { swipeRight() } // Set to 8

        // 5. Save
        composeTestRule.onNodeWithText("Save").performClick()

        // 6. Wait for Cloud Function (max 10s)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            // Check for notification
            notificationManager.activeNotifications.any {
                it.notification.channelId == "insights"
            }
        }

        // 7. Tap notification
        val notification = notificationManager.activeNotifications
            .first { it.notification.channelId == "insights" }
        notification.notification.contentIntent.send()

        // 8. Verify Insight screen opened
        composeTestRule.onNodeWithText("Diary Insight").assertIsDisplayed()
        composeTestRule.onNodeWithText("work").assertExists() // Topic
        composeTestRule.onNodeWithTag("sentiment_indicator")
            .assertExists() // Negative sentiment
    }
}
```

---

### 9.3 Load Testing (Cloud Functions)

**Artillery.io Config**:

```yaml
# load-test.yml
config:
  target: 'https://us-central1-calmify.cloudfunctions.net'
  phases:
    - duration: 60
      arrivalRate: 10  # 10 requests/sec
      name: "Warm up"
    - duration: 120
      arrivalRate: 50  # 50 requests/sec
      name: "Sustained load"
    - duration: 60
      arrivalRate: 100 # 100 requests/sec
      name: "Spike test"
  processor: "./functions.js"

scenarios:
  - name: "Generate Insight"
    flow:
      - post:
          url: "/generateInsight"
          json:
            diaryText: "{{ $randomString() }}"
            mood: "{{ $randomMood() }}"
            stressLevel: "{{ $randomInt(0, 10) }}"
          capture:
            - json: "$.id"
              as: "insightId"
      - think: 2
```

**Acceptance Criteria**:
- ✅ P95 latency < 5s
- ✅ Error rate < 1%
- ✅ No cold starts under sustained load
- ✅ Auto-scaling to 100 instances

---

### 9.4 Ethical Bias Testing

**Test Data**: Diverse demographic samples (age, gender, culture, language)

**Metrics**:
- Sentiment accuracy across demographics (±5% max deviation)
- Pattern detection fairness (no systematic bias)
- Suggestion appropriateness (cultural sensitivity)

**Example**:
```typescript
// Test that AI doesn't misinterpret cultural expressions
const testCases = [
  {
    culture: 'Western',
    input: 'Feeling blue today',
    expectedSentiment: 'NEGATIVE'
  },
  {
    culture: 'Asian',
    input: '今天心情不太好', // Chinese: "Mood not great today"
    expectedSentiment: 'NEGATIVE'
  },
  {
    culture: 'Latinx',
    input: 'Estoy un poco triste', // Spanish: "A bit sad"
    expectedSentiment: 'NEGATIVE'
  }
];

for (const test of testCases) {
  const result = await generateInsight({ diaryText: test.input });
  expect(result.sentimentPolarity).toBeLessThan(0);
}
```

---

## 10. Future Enhancements

### Phase 2 (Q2 2025)

1. **Voice Journaling with Emotion Detection**
   - Real-time speech-to-text (Gemini Live API)
   - Prosody analysis (tone, pitch, pace → emotional state)
   - Auto-tag diary entries with vocal emotion

2. **Social Features (Opt-In)**
   - Anonymous peer support groups
   - Therapist collaboration mode (share selected entries)
   - Accountability partners (share goals only)

3. **Advanced Visualizations**
   - 3D mood timeline (time × stress × energy)
   - Network graph of life themes + diary connections
   - Predictive stress forecast (ML model)

4. **Integrations**
   - Apple Health / Google Fit (sleep, activity → energy correlation)
   - Calendar sync (detect stressors from events)
   - Spotify (music mood tracking)

### Phase 3 (Q3 2025)

1. **Therapist Dashboard (B2B)**
   - HIPAA-compliant portal for therapists
   - Client progress tracking
   - Homework assignments via app
   - Billing integration

2. **Research Platform**
   - Anonymized data donation (IRB-approved studies)
   - Contribution to mental health research
   - User feedback loop (research results)

3. **Custom AI Personalities**
   - User-trainable Lifo (adjust tone, depth, humor)
   - Specialized modes (CBT coach, mindfulness guide, grief support)
   - Multi-agent system (different AI for different needs)

---

## Appendix A: Data Privacy Impact Assessment (DPIA)

**GDPR Compliance Checklist**:

- [x] **Lawful basis**: Consent + Legitimate Interest
- [x] **Data minimization**: Only essential data collected
- [x] **Purpose limitation**: Clear use cases documented
- [x] **Storage limitation**: 2-year retention (configurable)
- [x] **Accuracy**: User can correct AI insights
- [x] **Integrity & confidentiality**: AES-256 encryption
- [x] **Accountability**: Audit logs for AI decisions
- [x] **Right to access**: Export all data (JSON)
- [x] **Right to erasure**: Delete account + all data
- [x] **Right to portability**: Standard JSON format
- [x] **Right to object**: Opt-out of AI features

**Risk Assessment**:

| Risk | Severity | Mitigation |
|------|----------|------------|
| **Data breach** | High | Firestore security rules, encryption, MFA |
| **AI bias** | Medium | Diverse training data, fairness testing |
| **Over-reliance on AI** | Medium | Disclaimers, suggest professional help |
| **Privacy violation** | High | Minimal data collection, user control |
| **Emotional harm** | Medium | Ethical prompt engineering, crisis detection |

---

## Appendix B: Cost Estimation

**Monthly Costs (1,000 active users)**:

| Service | Usage | Cost |
|---------|-------|------|
| **Firestore** | 100K reads, 50K writes | $0.60 |
| **Cloud Functions** | 500K invocations, 200GB-sec | $10 |
| **Gemini API** | 100K requests (insight gen) | $15 |
| **Vector Search** | 10K queries | $1 |
| **Firebase Storage** | 10GB | $0.26 |
| **FCM** | Unlimited | $0 |
| **Cloud Scheduler** | 3 jobs × 4 runs/day | $0.30 |
| **Total** | | **~$27/month** |

**Per-User Cost**: $0.027/month ($0.32/year)

**Scalability**:
- 10K users: ~$270/month
- 100K users: ~$2,700/month (volume discounts apply)

**Monetization Strategy**:
- Free tier: Core features (diary, chat, weekly snapshot)
- Premium ($4.99/month): Advanced insights, unlimited history, therapist collab
- Break-even: ~600 premium users

---

## Appendix C: Sample Prompts

### Insight Generation Prompt (Full)

```
You are Lifo, a compassionate AI companion for mental health journaling.

CONTEXT:
- User's diary entry: "{diaryText}"
- Selected mood: {mood}
- Stress level: {stressLevel}/10
- Energy level: {energyLevel}/10
- Recent diary topics: {recentTopics}

TASK:
Analyze this entry and provide:

1. **Sentiment Analysis**
   - Polarity: -1 (very negative) to +1 (very positive)
   - Magnitude: 0 (neutral) to 5+ (intense emotion)

2. **Content Themes** (max 3 keywords)
   - What is this entry primarily about?
   - Examples: work, relationships, health, self-reflection

3. **Key Phrases** (max 5 exact quotes)
   - Most emotionally significant phrases
   - Quotes that capture the essence

4. **Cognitive Patterns** (CBT framework)
   - Identify any cognitive distortions:
     * Catastrophizing: "This will be a disaster"
     * All-or-nothing: "I'm either perfect or worthless"
     * Overgeneralization: "I always fail"
     * Mind reading: "They think I'm incompetent"
     * Fortune telling: "I know it won't work"
     * Emotional reasoning: "I feel bad, so it must be bad"
     * Should statements: "I should be better"
     * Labeling: "I'm a loser"
     * Personalization: "It's all my fault"
   - If none detected, return "NONE"

5. **Summary** (1-2 sentences)
   - Compassionate, validating summary
   - Acknowledge emotions without judgment
   - Example: "It sounds like today was really challenging at work, and you're feeling the weight of that criticism. It's understandable to feel hurt when your efforts aren't recognized."

6. **Reflective Prompts** (2-3 questions)
   - Open-ended questions to deepen insight
   - Examples:
     * "What would you say to a friend going through this?"
     * "What strengths did you use to get through today?"
     * "Is there a small step you could take tomorrow to feel more in control?"

ETHICAL GUIDELINES:
- NEVER diagnose (e.g., "You have depression")
- NEVER give medical advice
- ALWAYS validate emotions
- ALWAYS suggest professional help if severe distress
- ALWAYS be warm, non-judgmental, trauma-informed
- Focus on patterns, not labels

OUTPUT FORMAT (JSON):
{
  "sentimentPolarity": -0.6,
  "sentimentMagnitude": 3.2,
  "topics": ["work", "criticism", "self-worth"],
  "keyPhrases": [
    "Boss criticized my project",
    "Feel like a failure",
    "Everyone saw me get humiliated"
  ],
  "cognitivePatterns": ["LABELING", "CATASTROPHIZING"],
  "summary": "It sounds like today was really challenging at work...",
  "suggestedPrompts": [
    "What would you say to a friend going through this?",
    "What strengths did you use to get through today?"
  ],
  "confidence": 0.85
}
```

### Profile Computation Prompt (Language Clarity)

```
Analyze the following journal entries and rate the author's **language clarity** on a 0-1 scale.

ENTRIES:
{diaryTexts}

CRITERIA:
- 0.0-0.3: Incoherent, fragmented, difficult to follow
- 0.4-0.6: Some coherence, occasional tangents, readable
- 0.7-0.9: Clear, organized, easy to follow
- 1.0: Exceptionally clear, structured, purposeful

Consider:
- Sentence structure (simple, complex, run-on?)
- Logical flow (does one thought connect to the next?)
- Word choice (varied, repetitive, vague?)
- Emotional regulation reflected in writing (chaotic vs. composed)

OUTPUT (JSON):
{
  "clarityScore": 0.72,
  "reasoning": "The entries show clear sentence structure and logical progression. Some emotional intensity, but overall coherent and reflective."
}
```

---

**End of Document**

*Next Steps*:
1. Review and approve this plan
2. Set up Firebase project (test environment)
3. Create Jira/Linear tickets from roadmap
4. Kick off Week 1 (Database Schema)

**Questions for Product Team**:
- Should we prioritize weekly snapshot over insights in Phase 1?
- What's the target beta user count (20? 50? 100?)?
- Do we need ethics board review before launch?

---

## Appendix D: Platform Setup Checklist (Manual Tasks)

> **Purpose**: This section documents all Firebase/Google Cloud Console tasks that must be performed manually by you (the developer), as Claude Code cannot access cloud platforms. Complete these setup tasks BEFORE starting the implementation roadmap.

---

### Prerequisites

- [ ] Google account with billing enabled
- [ ] Node.js 18+ installed locally (for Firebase CLI)
- [ ] Android Studio configured with project
- [ ] Basic familiarity with Firebase Console

---

### Phase 0: Firebase Project Setup (Estimated Time: 30 minutes)

#### 0.1 Create/Configure Firebase Project

**Console**: [https://console.firebase.google.com](https://console.firebase.google.com)

- [ ] **Create new Firebase project** (or use existing `calmify` project)
  - Name: `Calmify` (Production) or `Calmify-Dev` (Testing)
  - Enable Google Analytics: **Yes** (recommended)
  - Select Analytics location: Your region
  - Accept terms and create project

- [ ] **Enable Blaze (Pay-as-you-go) plan**
  - Navigate to: ⚙️ Project Settings → Usage and Billing → Modify Plan
  - Required for: Cloud Functions, Genkit, Vector Search
  - Set budget alert: $50/month (safety)

- [ ] **Add Android app to Firebase**
  - Click "Add app" → Android icon
  - Package name: `com.lifo.calmifyapp` (must match your build.gradle)
  - App nickname: `Calmify Android`
  - SHA-1 certificate: Get with `keytool -list -v -keystore ~/.android/debug.keystore` (password: `android`)
  - Download `google-services.json`
  - **ACTION**: Place file in `app/` directory

- [ ] **Enable required Firebase services**:
  - ✅ Authentication (already enabled)
  - ✅ Firestore Database (already enabled)
  - ✅ Cloud Storage (already enabled)
  - ✅ Cloud Functions
  - ✅ Cloud Messaging (FCM)

---

### Phase 1: Firestore Configuration (Estimated Time: 20 minutes)

#### 1.1 Configure Firestore Native Mode Database

**Console**: Firebase → Firestore Database

- [ ] **Verify existing database**: `calmify-native`
  - Location: Already set (cannot change after creation)
  - Mode: Native mode ✓

- [ ] **Create composite indexes** (required for queries):

  Navigate to: Firestore → Indexes → Composite tab → Add Index

  **Index 1: Diaries by user and date**
  - Collection ID: `diaries`
  - Fields:
    - `ownerId` (Ascending)
    - `date` (Descending)
  - Query scope: Collection
  - Status: Wait until "Enabled" (5-10 min)

  **Index 2: Insights by user**
  - Collection ID: `diary_insights`
  - Fields:
    - `ownerId` (Ascending)
    - `generatedAt` (Descending)

  **Index 3: Wellbeing snapshots by user**
  - Collection ID: `wellbeing_snapshots`
  - Fields:
    - `ownerId` (Ascending)
    - `timestamp` (Descending)

  **Index 4: Psychological profiles by user and week**
  - Collection ID: `psychological_profiles`
  - Fields:
    - `ownerId` (Ascending)
    - `year` (Descending)
    - `weekNumber` (Descending)

- [ ] **Enable Vector Search** (for RAG feature):

  Navigate to: Firestore → Indexes → Single field tab

  - Field path: `diaries.embedding`
  - Index type: **Vector**
  - Vector configuration:
    - Dimensions: **768** (Gemini text-embedding-004)
    - Distance measure: **COSINE**
    - Similarity threshold: Default
  - Click Create

  **Note**: Vector indexing may take 15-30 minutes for initial build.

#### 1.2 Configure Security Rules

**Console**: Firestore → Rules tab

- [ ] **Replace default rules** with the following:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Helper functions
    function isAuthenticated() {
      return request.auth != null;
    }

    function isOwner(ownerId) {
      return isAuthenticated() && request.auth.uid == ownerId;
    }

    // Diaries: Users can only read/write their own
    match /diaries/{diaryId} {
      allow read: if isOwner(resource.data.ownerId);
      allow create: if isAuthenticated() && request.resource.data.ownerId == request.auth.uid;
      allow update, delete: if isOwner(resource.data.ownerId);
    }

    // Diary insights: Users can only read their own (Cloud Functions write)
    match /diary_insights/{insightId} {
      allow read: if isOwner(resource.data.ownerId);
      allow write: if false; // Only Cloud Functions can write
    }

    // Wellbeing snapshots: Users can read/write their own
    match /wellbeing_snapshots/{snapshotId} {
      allow read: if isOwner(resource.data.ownerId);
      allow create: if isAuthenticated() && request.resource.data.ownerId == request.auth.uid;
      allow update, delete: if isOwner(resource.data.ownerId);
    }

    // Psychological profiles: Users can read their own (Cloud Functions write)
    match /psychological_profiles/{profileId} {
      allow read: if isOwner(resource.data.ownerId);
      allow write: if false; // Only Cloud Functions can write
    }

    // Life themes: Users can read/write their own
    match /life_themes/{themeId} {
      allow read: if isOwner(resource.data.ownerId);
      allow create: if isAuthenticated() && request.resource.data.ownerId == request.auth.uid;
      allow update, delete: if isOwner(resource.data.ownerId);
    }
  }
}
```

- [ ] Click **Publish**
- [ ] **Test rules** using Rules Playground (same tab):
  - Auth UID: (any test UID)
  - Path: `/diaries/test123`
  - Test read/write with matching/mismatched ownerId

---

### Phase 2: Cloud Functions Setup (Estimated Time: 45 minutes)

#### 2.1 Initialize Firebase Functions Locally

**Terminal** (run in project root):

```bash
# Install Firebase CLI globally (if not already installed)
npm install -g firebase-tools

# Login to Firebase
firebase login

# Initialize Firebase in project
firebase init

# Select:
# - Functions (Space to select, Enter to confirm)
# - Use existing project → Select your Calmify project
# - Language: TypeScript
# - ESLint: Yes
# - Install dependencies: Yes
```

This creates a `functions/` directory with boilerplate.

#### 2.2 Install Genkit Dependencies

**Terminal** (inside `functions/` directory):

```bash
cd functions

# Install Genkit and Google AI plugin
npm install genkit @genkit-ai/googleai

# Install Firebase Admin SDK (if not already installed)
npm install firebase-admin

# Install other dependencies
npm install zod  # For schema validation
npm install @google-cloud/firestore  # For Vector Search types

# Return to project root
cd ..
```

#### 2.3 Configure Gemini API Key in Firebase

**Console**: Firebase → ⚙️ Project Settings → Service Accounts

- [ ] **Get Gemini API key**:
  - Navigate to: [https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)
  - Create API key (or use existing)
  - Copy key

- [ ] **Set environment variable** for Cloud Functions:

**Terminal**:

```bash
firebase functions:secrets:set GEMINI_API_KEY
# Paste your API key when prompted
```

- [ ] **Verify secret**:

```bash
firebase functions:secrets:access GEMINI_API_KEY
# Should show your API key (redacted in console)
```

#### 2.4 Create Basic Cloud Function Structure

**Terminal**:

```bash
# Create Genkit flows directory
mkdir -p functions/src/genkit

# Create scheduler directory
mkdir -p functions/src/scheduler

# Create shared utilities directory
mkdir -p functions/src/utils
```

**ACTION**: Claude Code will provide the actual function code during implementation. For now, just verify the directory structure is created.

#### 2.5 Enable Required Google Cloud APIs

**Console**: [https://console.cloud.google.com](https://console.cloud.google.com)

- [ ] Select your Firebase project from dropdown
- [ ] Navigate to: APIs & Services → Library
- [ ] Search and **Enable** the following APIs:
  - **Cloud Functions API** (should already be enabled)
  - **Cloud Scheduler API** (for cron jobs)
  - **Cloud Build API** (for function deployment)
  - **Generative Language API** (for Gemini)
  - **Vertex AI API** (for embeddings)
  - **Secret Manager API** (for GEMINI_API_KEY)

---

### Phase 3: Cloud Scheduler (Cron Jobs) (Estimated Time: 15 minutes)

#### 3.1 Create Weekly Profile Computation Job

**Console**: Google Cloud Console → Cloud Scheduler

- [ ] Click **Create Job**

**Job 1: Compute Weekly Profiles**
- Name: `compute-weekly-profiles`
- Region: `us-central1` (must match Functions region)
- Description: `Compute psychological profiles every Sunday at 2 AM`
- Frequency: `0 2 * * 0` (Cron format: every Sunday at 2:00 AM)
- Timezone: Your timezone (e.g., `America/New_York`)
- Target type: **HTTP**
- URL: `https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/computeWeeklyProfiles`
  - Replace `[YOUR_PROJECT_ID]` with your Firebase project ID
- HTTP method: **POST**
- Auth header: **Add OIDC token**
  - Service account: `[YOUR_PROJECT_ID]@appspot.gserviceaccount.com`
- Click **Create**

**Job 2: Send Weekly Reminders**
- Name: `send-weekly-reminders`
- Frequency: `0 21 * * 0` (every Sunday at 9:00 PM)
- Target URL: `https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/sendWeeklyReminders`
- (Same auth settings as above)

**Job 3: Monthly Life Themes Review**
- Name: `monthly-themes-reminder`
- Frequency: `0 10 1 * *` (1st of month at 10:00 AM)
- Target URL: `https://us-central1-[YOUR_PROJECT_ID].cloudfunctions.net/sendMonthlyThemesReminder`

**Note**: These jobs will fail until the actual Cloud Functions are deployed (Phase 2 of implementation roadmap).

---

### Phase 4: Firebase Cloud Messaging (FCM) (Estimated Time: 20 minutes)

#### 4.1 Configure FCM in Firebase

**Console**: Firebase → Cloud Messaging

- [ ] **Enable Cloud Messaging API**:
  - Navigate to: Cloud Messaging → Send your first message
  - Click "Let's get started" (if first time)
  - This auto-enables FCM

- [ ] **Upload APNs certificate** (if planning iOS version):
  - Skip for Android-only (current scope)

#### 4.2 Configure Notification Channels (Android-side)

**ACTION**: Claude Code will implement this in `MainActivity.kt` during Phase 4 of roadmap.

**Manual verification** (after implementation):
- [ ] Build and install app on test device
- [ ] Open app → Settings → Notifications
- [ ] Verify channels exist:
  - ✅ Insights
  - ✅ Reminders
  - ✅ Wellness Checks
  - ✅ Achievements

#### 4.3 Get FCM Server Key (for testing)

**Console**: Firebase → ⚙️ Project Settings → Cloud Messaging

- [ ] Copy **Server key** (under Cloud Messaging API - Legacy)
  - Used for manual testing via Postman/curl
  - **Store securely** (don't commit to Git)

**Test notification** (optional, via curl):

```bash
curl -X POST https://fcm.googleapis.com/fcm/send \
  -H "Authorization: key=[YOUR_SERVER_KEY]" \
  -H "Content-Type: application/json" \
  -d '{
    "to": "[DEVICE_FCM_TOKEN]",
    "notification": {
      "title": "Test from Console",
      "body": "FCM is working!"
    }
  }'
```

---

### Phase 5: BigQuery Export (Optional) (Estimated Time: 10 minutes)

#### 5.1 Enable Firestore → BigQuery Export

**Console**: Firebase → ⚙️ Project Settings → Integrations

- [ ] Find **BigQuery** card → Click **Link**
- [ ] Select collections to export:
  - ✅ `diaries`
  - ✅ `wellbeing_snapshots`
  - ✅ `psychological_profiles` (for long-term analytics)
- [ ] Choose dataset location: Same as Firestore (e.g., `us-central1`)
- [ ] Enable daily export: **Yes**
- [ ] Click **Link to BigQuery**

**Note**: First export happens at next UTC midnight. Historical data NOT backfilled.

#### 5.2 Create BigQuery Scheduled Queries (Optional)

**Console**: [https://console.cloud.google.com/bigquery](https://console.cloud.google.com/bigquery)

- [ ] Navigate to: Scheduled Queries → Create Scheduled Query
- [ ] Paste this sample query:

```sql
-- Weekly Stress Trends (Anonymized)
SELECT
  EXTRACT(WEEK FROM TIMESTAMP_MILLIS(CAST(date AS INT64))) AS week_number,
  EXTRACT(YEAR FROM TIMESTAMP_MILLIS(CAST(date AS INT64))) AS year,
  AVG(stressLevel) AS avg_stress,
  STDDEV(stressLevel) AS stress_volatility,
  COUNT(*) AS entry_count
FROM `[YOUR_PROJECT_ID].firestore_export.diaries_raw_latest`
WHERE date IS NOT NULL
  AND stressLevel IS NOT NULL
  AND TIMESTAMP_MILLIS(CAST(date AS INT64)) >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 12 WEEK)
GROUP BY week_number, year
ORDER BY year DESC, week_number DESC
```

- [ ] Schedule: **Weekly** (Sunday at 3 AM)
- [ ] Destination table: `calmify_analytics.weekly_stress_trends`
- [ ] Save

---

### Phase 6: Monitoring & Alerts (Estimated Time: 15 minutes)

#### 6.1 Enable Firebase Crashlytics (Optional but Recommended)

**Console**: Firebase → Crashlytics

- [ ] Click **Set up Crashlytics**
- [ ] Follow Android setup instructions:
  - Add plugin to `app/build.gradle`
  - Rebuild app
  - Force a test crash

**Claude Code will integrate** this during implementation if requested.

#### 6.2 Create Budget Alerts

**Console**: Google Cloud Console → Billing → Budgets & Alerts

- [ ] Click **Create Budget**
- [ ] Budget name: `Calmify Monthly Budget`
- [ ] Projects: Select your Calmify project
- [ ] Budget amount:
  - **Testing phase**: $20/month
  - **Production**: $100/month (adjust based on usage)
- [ ] Set threshold alerts:
  - 50% ($10 or $50)
  - 90% ($18 or $90)
  - 100% ($20 or $100)
- [ ] Alert emails: Your email
- [ ] Click **Finish**

#### 6.3 Set Up Cloud Function Error Alerts

**Console**: Google Cloud Console → Monitoring → Alerting

- [ ] Click **Create Policy**
- [ ] Condition:
  - Resource type: **Cloud Function**
  - Metric: **Execution count**
  - Filter: `status = error`
  - Condition: Above threshold
  - Threshold value: **10** (errors per hour)
- [ ] Notification channels:
  - Email: Your email
  - (Optional) Slack webhook
- [ ] Alert name: `Cloud Functions Error Spike`
- [ ] Save

---

### Phase 7: Deployment Configuration (Estimated Time: 10 minutes)

#### 7.1 Configure Firebase Hosting (Optional - for web version)

**Skip for Android-only** (current scope).

If adding web dashboard later:

```bash
firebase init hosting
firebase deploy --only hosting
```

#### 7.2 Set Function Deployment Defaults

**Edit**: `functions/.firebaserc` (auto-created during init)

```json
{
  "projects": {
    "default": "your-project-id"
  }
}
```

**Edit**: `firebase.json` (auto-created during init)

```json
{
  "functions": {
    "source": "functions",
    "runtime": "nodejs18",
    "predeploy": [
      "npm --prefix functions run build"
    ]
  },
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  }
}
```

- [ ] **Verify configuration**:

```bash
firebase functions:config:get
# Should show your project configuration
```

#### 7.3 Create Deployment Script

**Create**: `deploy.sh` (in project root)

```bash
#!/bin/bash

# Calmify Cloud Functions Deployment Script

set -e  # Exit on error

echo "🚀 Starting Calmify deployment..."

# Build TypeScript
echo "📦 Building TypeScript..."
cd functions
npm run build
cd ..

# Deploy Firestore rules
echo "🔒 Deploying Firestore rules..."
firebase deploy --only firestore:rules

# Deploy Cloud Functions
echo "☁️ Deploying Cloud Functions..."
firebase deploy --only functions

# Deploy Firestore indexes
echo "📊 Deploying Firestore indexes..."
firebase deploy --only firestore:indexes

echo "✅ Deployment complete!"
echo "📝 Check console: https://console.firebase.google.com/project/$(firebase use)/overview"
```

- [ ] **Make executable**:

```bash
chmod +x deploy.sh
```

- [ ] **Test** (dry run):

```bash
firebase deploy --only functions --dry-run
```

---

### Phase 8: Testing Environment Setup (Estimated Time: 10 minutes)

#### 8.1 Create Firebase Test Project (Recommended)

**Console**: [https://console.firebase.google.com](https://console.firebase.google.com)

- [ ] Click **Add project**
- [ ] Name: `Calmify-Dev` or `Calmify-Test`
- [ ] Repeat **all steps from Phases 0-7** for test project
- [ ] Keep separate API keys and configurations

#### 8.2 Configure Firebase Emulator Suite (Local Testing)

**Terminal**:

```bash
firebase init emulators

# Select:
# - Firestore
# - Functions
# - Authentication
# - Storage
# - Hosting (optional)
```

**Edit**: `firebase.json` (add emulators section)

```json
{
  "emulators": {
    "functions": {
      "port": 5001
    },
    "firestore": {
      "port": 8080
    },
    "auth": {
      "port": 9099
    },
    "ui": {
      "enabled": true,
      "port": 4000
    }
  }
}
```

- [ ] **Start emulators**:

```bash
firebase emulators:start
```

- [ ] **Access Emulator UI**: [http://localhost:4000](http://localhost:4000)

**Configure Android app** to use emulators (dev build variant):

```kotlin
// In MainActivity.kt (dev variant only)
if (BuildConfig.DEBUG) {
    FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
    FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
}
```

---

### Phase 9: Security & Compliance (Estimated Time: 20 minutes)

#### 9.1 Configure Firebase App Check (Anti-abuse)

**Console**: Firebase → App Check

- [ ] Click **Get started**
- [ ] Register app: Select `Calmify Android`
- [ ] Provider: **Play Integrity** (for production) or **Debug** (for testing)
- [ ] Click **Save**

**For Debug Provider** (testing):

```bash
# Get debug token
firebase appcheck:debug:create --app <YOUR_APP_ID>
# Copy token and paste in App Check console → Debug tokens
```

**For Play Integrity** (production):

- [ ] Requires app published to Google Play (internal/alpha track OK)
- [ ] Configure in Play Console → App Integrity

#### 9.2 Enable Firestore Audit Logs

**Console**: Google Cloud Console → IAM & Admin → Audit Logs

- [ ] Find **Cloud Firestore API**
- [ ] Enable:
  - ✅ Admin Read
  - ✅ Data Read
  - ✅ Data Write
- [ ] Click **Save**

**Note**: Logs viewable in Cloud Logging → Logs Explorer.

#### 9.3 Review IAM Permissions

**Console**: Google Cloud Console → IAM & Admin → IAM

- [ ] Verify service accounts:
  - `[PROJECT_ID]@appspot.gserviceaccount.com` (App Engine default)
    - Roles: **Editor** (auto-assigned)
  - `firebase-adminsdk-xxxxx@[PROJECT_ID].iam.gserviceaccount.com`
    - Roles: **Firebase Admin SDK** (auto-assigned)

- [ ] **Add custom role** for Cloud Functions (least privilege):
  - Click **Add** → New principals
  - Email: `cloud-functions@[PROJECT_ID].iam.gserviceaccount.com`
  - Roles:
    - **Cloud Datastore User** (Firestore read/write)
    - **Cloud Functions Invoker** (call other functions)
    - **Secret Manager Secret Accessor** (read GEMINI_API_KEY)

---

### Phase 10: Documentation & Handoff (Estimated Time: 5 minutes)

#### 10.1 Document Your Configuration

**Create**: `FIREBASE_CONFIG.md` (in project root)

```markdown
# Calmify Firebase Configuration

**Project ID**: `your-project-id`
**Region**: `us-central1`
**Database**: `calmify-native` (Firestore Native Mode)

## Environment URLs

- **Firebase Console**: https://console.firebase.google.com/project/your-project-id
- **Cloud Console**: https://console.cloud.google.com/home/dashboard?project=your-project-id
- **Functions**: https://console.firebase.google.com/project/your-project-id/functions
- **Firestore**: https://console.firebase.google.com/project/your-project-id/firestore

## Secrets

- `GEMINI_API_KEY`: Stored in Secret Manager (Cloud Functions)
- FCM Server Key: ⚙️ Project Settings → Cloud Messaging

## Deployment

```bash
./deploy.sh
```

## Emulators (Local Testing)

```bash
firebase emulators:start
```

**Emulator UI**: http://localhost:4000

## Cron Jobs

| Job | Schedule | URL |
|-----|----------|-----|
| compute-weekly-profiles | Sunday 2 AM | https://us-central1-[PROJECT_ID].cloudfunctions.net/computeWeeklyProfiles |
| send-weekly-reminders | Sunday 9 PM | https://us-central1-[PROJECT_ID].cloudfunctions.net/sendWeeklyReminders |
| monthly-themes-reminder | 1st of month 10 AM | https://us-central1-[PROJECT_ID].cloudfunctions.net/sendMonthlyThemesReminder |

## Monitoring

- **Budget Alert**: $20/month (testing) or $100/month (production)
- **Error Alert**: >10 function errors/hour → Email
- **Crashlytics**: https://console.firebase.google.com/project/your-project-id/crashlytics

## Backup & Recovery

- Firestore: Automated daily backups (built-in)
- BigQuery export: Daily at midnight UTC
- Point-in-time recovery: 7 days (Firestore)

## Last Updated

2025-01-19 - Initial setup complete
```

- [ ] **Fill in your project-specific values**
- [ ] **Commit to Git** (exclude secrets!)

#### 10.2 Mark Tasks Complete

**Update this checklist** in `PSYCHOLOGICAL_INSIGHTS_PLAN.md`:

- [ ] Add checkmarks ✅ to completed sections
- [ ] Note any deviations or custom configurations
- [ ] Save file and commit to Git

---

### Summary Checklist (Quick Reference)

**Core Setup** (Must complete before implementation):
- [ ] Firebase project created with Blaze plan
- [ ] `google-services.json` downloaded and placed in `app/`
- [ ] Firestore composite indexes created (4 indexes)
- [ ] Firestore vector index created (768-dim, COSINE)
- [ ] Firestore security rules deployed
- [ ] Firebase CLI installed and logged in
- [ ] Cloud Functions initialized (`functions/` directory)
- [ ] Genkit dependencies installed
- [ ] `GEMINI_API_KEY` secret configured
- [ ] Required Google Cloud APIs enabled (7 APIs)
- [ ] Cloud Scheduler jobs created (3 jobs)
- [ ] FCM enabled and tested

**Optional but Recommended**:
- [ ] Test Firebase project created
- [ ] Firebase Emulator Suite configured
- [ ] BigQuery export enabled
- [ ] Budget alerts set ($20-$100/month)
- [ ] Error monitoring alerts configured
- [ ] App Check enabled
- [ ] Firestore audit logs enabled
- [ ] `FIREBASE_CONFIG.md` created and committed

**Verification**:
- [ ] Run `firebase projects:list` → See your project
- [ ] Run `firebase emulators:start` → Emulators run successfully
- [ ] Check Firestore indexes → All show "Enabled" status
- [ ] Check Cloud Scheduler → Jobs created (will fail until functions deployed)
- [ ] Build Android app → No `google-services.json` errors

---

### Troubleshooting Common Issues

**Issue**: "Billing account not configured"
- **Fix**: Enable Blaze plan in Firebase Console → Usage and Billing

**Issue**: Firestore index stuck in "Building"
- **Fix**: Wait 10-15 minutes. If >1 hour, delete and recreate.

**Issue**: Cloud Function deployment fails with "Permission denied"
- **Fix**: Run `firebase login --reauth` and re-deploy.

**Issue**: Vector index returns empty results
- **Fix**: Ensure embeddings are stored as `FieldValue.vector()`, not plain arrays.

**Issue**: FCM notifications not received on device
- **Fix**:
  1. Verify `google-services.json` is in `app/`
  2. Check FCM token is being sent to Firestore
  3. Test with Firebase Console → Cloud Messaging → Send test message

**Issue**: Genkit functions timeout
- **Fix**: Increase timeout in `functions/src/index.ts`:
```typescript
export const generateInsight = onDocumentCreated({
  timeoutSeconds: 120,  // Default is 60s
  memory: '512MB'       // Increase if needed
}, async (event) => { ... });
```

---

### Next Steps After Platform Setup

Once ALL checklist items are complete:

1. ✅ **Notify Claude Code**: "Platform setup complete. Ready for implementation."
2. 🏗️ **Start Phase 1** of Implementation Roadmap (Week 1: Database Schema)
3. 🧪 **Run emulators** during development (`firebase emulators:start`)
4. 🚀 **Deploy functions** after each phase (`./deploy.sh`)
5. 📊 **Monitor usage** in Firebase Console → Usage and Billing

---

**Estimated Total Setup Time**: ~3 hours (first time), ~1 hour (subsequent projects)

**Support**:
- Firebase Docs: [https://firebase.google.com/docs](https://firebase.google.com/docs)
- Genkit Docs: [https://firebase.google.com/docs/genkit](https://firebase.google.com/docs/genkit)
- Stack Overflow: [firebase] tag

---

*Generated by Jarvis AI - 2025-01-19*
*For: Calmify Psychological Insights Enhancement*
*Status: Planning Phase - Awaiting Platform Setup*

---

*Generated by Jarvis AI - 2025-01-19*
*For: Calmify Psychological Insights Enhancement*
*Status: Planning Phase - Awaiting Approval*
