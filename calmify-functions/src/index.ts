/**
 * Calmify Cloud Functions - AI Insight Generation
 * Week 4: PSYCHOLOGICAL_INSIGHTS_PLAN.md
 *
 * Trigger: onDiaryCreated (Firestore document.onCreate)
 * AI: Gemini 2.0 Flash via Genkit
 * Output: diary_insights collection
 */

import {onDocumentCreated} from "firebase-functions/v2/firestore";
import {setGlobalOptions, logger} from "firebase-functions/v2";
import * as admin from "firebase-admin";
import {genkit} from "genkit";
import {googleAI, gemini20FlashExp} from "@genkit-ai/googleai";
import {defineSecret} from "firebase-functions/params";
import {z} from "zod";
import {sendFCM} from "./utils/fcm-helper";

// Initialize Firebase Admin
admin.initializeApp();

// Configure Firestore to use calmify-native database (MUST BE CALLED ONCE)
admin.firestore().settings({databaseId: "calmify-native"});

// Secret for Gemini API Key
const geminiApiKey = defineSecret("GEMINI_API_KEY");

// Initialize Genkit with Google AI plugin
const ai = genkit({
  plugins: [googleAI()],
});

// Global options for cost control
setGlobalOptions({
  maxInstances: 10,
  region: "europe-west1", // Cambia se preferisci us-central1
});

/**
 * Diary Document Schema (from Firestore)
 */
interface DiaryDocument {
  _id: string;
  ownerId: string;
  mood: string;
  title: string;
  description: string;
  images: string[];
  date: admin.firestore.Timestamp;
  dayKey?: string; // Business date YYYY-MM-DD (set by client)
  timezone?: string; // User's timezone when diary was created (e.g., "Europe/Rome")
  // Psychological metrics (Week 1)
  emotionIntensity: number;
  stressLevel: number;
  energyLevel: number;
  calmAnxietyLevel: number;
  primaryTrigger: string;
  dominantBodySensation: string;
}

/**
 * Cognitive Pattern Schema (CBT-informed)
 */
const CognitivePatternSchema = z.object({
  patternType: z.enum([
    "CATASTROPHIZING",
    "ALL_OR_NOTHING",
    "OVERGENERALIZATION",
    "MIND_READING",
    "FORTUNE_TELLING",
    "EMOTIONAL_REASONING",
    "SHOULD_STATEMENTS",
    "LABELING",
    "PERSONALIZATION",
    "NONE",
  ]).describe("Type of cognitive distortion detected"),
  patternName: z.string().describe(
    "Human-readable name in Italian"
  ),
  description: z.string().describe(
    "Brief explanation of the pattern in Italian"
  ),
  evidence: z.string().describe(
    "Direct quote from diary showing this pattern"
  ),
  confidence: z.number().min(0).max(1).describe(
    "Confidence in this pattern detection (0-1)"
  ),
});

/**
 * AI-Generated Insight Schema (Aligned with PSYCHOLOGICAL_INSIGHTS_PLAN.md)
 */
const InsightSchema = z.object({
  // Sentiment Analysis
  sentimentPolarity: z.number().min(-1).max(1).describe(
    "Sentiment from -1 (very negative) to +1 (very positive)"
  ),
  sentimentMagnitude: z.number().min(0).describe(
    "Emotional intensity, 0 (neutral) to 10+ (very intense)"
  ),

  // Content Analysis
  topics: z.array(z.string()).max(5).describe(
    "Main topics/themes (max 5, Italian)"
  ),
  keyPhrases: z.array(z.string()).max(5).describe(
    "Key phrases extracted from diary (max 5, Italian)"
  ),

  // Cognitive Patterns (CBT-informed)
  cognitivePatterns: z.array(CognitivePatternSchema).max(3).describe(
    "Detected cognitive distortions (max 3)"
  ),

  // Summary & Suggestions
  summary: z.string().max(500).describe(
    "1-2 sentence AI summary in Italian"
  ),
  suggestedPrompts: z.array(z.string()).min(2).max(3).describe(
    "Reflective questions for further exploration (Italian)"
  ),

  // Confidence & Metadata
  confidence: z.number().min(0).max(1).describe(
    "Overall confidence in the analysis (0-1)"
  ),
});

/**
 * Genkit AI Flow: Generate Psychological Insight
 */
const generateInsightFlow = ai.defineFlow(
  {
    name: "generateDiaryInsight",
    inputSchema: z.object({
      diary: z.custom<DiaryDocument>(),
    }),
    outputSchema: InsightSchema,
  },
  async ({diary}) => {
    logger.info("Starting AI insight generation", {diaryId: diary._id});

    // Construct the analysis prompt
    const prompt = `
Sei uno psicologo clinico esperto che analizza diari personali per
generare insight psicologici completi.

**DIARIO DA ANALIZZARE:**

Data: ${diary.date.toDate().toLocaleDateString("it-IT")}
Umore: ${diary.mood}
Titolo: ${diary.title}
Descrizione: ${diary.description}

**METRICHE PSICOLOGICHE:**
- Intensità Emotiva: ${diary.emotionIntensity}/10
- Livello di Stress: ${diary.stressLevel}/10
- Livello di Energia: ${diary.energyLevel}/10
- Calma/Ansia: ${diary.calmAnxietyLevel}/10 (0=ansioso, 10=calmo)
- Trigger Principale: ${diary.primaryTrigger}
- Sensazione Corporea Dominante: ${diary.dominantBodySensation}

---

**COMPITO:** Genera un'analisi completa seguendo questa struttura JSON:

**1. SENTIMENT ANALYSIS:**
- \`sentimentPolarity\`: numero da -1.0 (molto negativo) a +1.0 (molto positivo)
  - Basato su linguaggio usato, umore dichiarato, e metriche
  - Esempio: descrizione tipo "giornata orribile" → -0.7
  - Esempio: "mi sento bene" → +0.6
- \`sentimentMagnitude\`: intensità emotiva da 0 (neutro) a 10+ (molto intenso)
  - Basato su emotionIntensity e forza delle parole
  - Esempio: "leggermente triste" → 2.0
  - Esempio: "sono distrutto emotivamente" → 8.5

**2. CONTENT ANALYSIS:**
- \`topics\`: array di 3-5 temi principali (parole chiave in italiano)
  - Estrai i temi più rilevanti dal testo
  - Esempi: ["lavoro", "famiglia", "salute mentale", "relazioni"]
- \`keyPhrases\`: array di 3-5 frasi chiave estratte letteralmente dal testo
  - Quotes dirette che catturano l'essenza emotiva
  - Esempi: ["mi sento sopraffatto", "ho bisogno di una pausa"]

**3. COGNITIVE PATTERNS (CBT-INFORMED):**
- \`cognitivePatterns\`: array di 0-3 distorsioni cognitive identificate
- Per ogni pattern fornisci:
  - \`patternType\`: uno di questi enum:
    - CATASTROPHIZING: "Andrà tutto male", "È un disastro"
    - ALL_OR_NOTHING: "Sono un fallimento totale o un successo"
    - OVERGENERALIZATION: "Succede sempre", "Non funziona mai"
    - MIND_READING: "Pensano che io sia incompetente"
    - FORTUNE_TELLING: "So già che fallirò"
    - EMOTIONAL_REASONING: "Mi sento male quindi la situazione è male"
    - SHOULD_STATEMENTS: "Dovrei essere migliore", "Non dovrei sentirmi così"
    - LABELING: "Sono un perdente", "Sono stupido"
    - PERSONALIZATION: "È tutta colpa mia"
    - NONE: nessuna distorsione rilevata
  - \`patternName\`: nome in italiano del pattern
  - \`description\`: breve spiegazione (1 frase)
  - \`evidence\`: citazione diretta dal diario che mostra questo pattern
  - \`confidence\`: 0.0-1.0 (quanto sei sicuro)
- Se non trovi distorsioni cognitive, usa array vuoto []

**4. SUMMARY:**
- \`summary\`: 1-2 frasi che sintetizzano l'analisi in italiano
  - Tono empatico e professionale
  - Esempio: "La tua giornata mostra segni di stress lavorativo bilanciato
  da un senso di realizzazione. Emerge una tendenza alla catastrofizzazione
  che potrebbe amplificare l'ansia."

**5. SUGGESTED PROMPTS:**
- \`suggestedPrompts\`: 2-3 domande riflessive per approfondire (italiano)
  - Basate sui temi e pattern identificati
  - Esempi:
    - "Quali strategie usi per gestire lo stress lavorativo?"
    - "Come puoi riformulare i pensieri catastrofici in modo equilibrato?"
    - "Cosa ti ha dato più soddisfazione oggi?"

**6. CONFIDENCE:**
- \`confidence\`: 0.0-1.0 (quanto sei sicuro dell'intera analisi)
  - 0.9-1.0: diario molto dettagliato, analisi affidabile
  - 0.7-0.89: buone informazioni ma alcune lacune
  - 0.5-0.69: informazioni limitate
  - <0.5: troppo vago per analisi affidabile

---

**LINEE GUIDA ETICHE:**
- Rispondi SEMPRE in italiano
- Tono professionale ma caldo ed empatico
- NON dare diagnosi cliniche (es. "depressione", "disturbo d'ansia")
- NON sostituirti a un terapeuta
- Se rilevi segnali di crisi (parole come "suicidio", "farla finita",
  stress 9-10 + ansia 0-2), includi nei suggestedPrompts:
  "Considera di parlare con un professionista della salute mentale"

Genera l'analisi in formato JSON strutturato seguendo lo schema fornito.
`;

    try {
      const result = await ai.generate({
        model: gemini20FlashExp,
        prompt: prompt,
        output: {schema: InsightSchema},
        config: {
          temperature: 0.7, // Bilanciamento creatività/coerenza
          maxOutputTokens: 1000,
          apiKey: geminiApiKey.value(),
        },
      });

      const insight = result.output;

      if (!insight) {
        throw new Error("AI generated null insight");
      }

      logger.info("AI insight generated successfully", {
        diaryId: diary._id,
        sentimentPolarity: insight.sentimentPolarity,
        confidence: insight.confidence,
        topicsCount: insight.topics.length,
        patternsCount: insight.cognitivePatterns.length,
      });

      return insight;
    } catch (error) {
      logger.error("AI insight generation failed", {
        diaryId: diary._id,
        error: error instanceof Error ? error.message : String(error),
      });

      // Fallback insight in caso di errore
      return {
        sentimentPolarity: 0.0,
        sentimentMagnitude: 0.0,
        topics: [],
        keyPhrases: [],
        cognitivePatterns: [],
        summary: "Impossibile generare insight automatici al momento. " +
          "Riprova più tardi o contatta il supporto.",
        suggestedPrompts: [
          "Come ti senti riguardo a questa giornata?",
          "Cosa vorresti approfondire di questa esperienza?",
        ],
        confidence: 0.0,
      };
    }
  }
);

/**
 * Cloud Function: onDiaryCreated
 * Trigger: Firestore onCreate at diaries/{diaryId}
 * Database: calmify-native (Firestore Native Mode)
 */
export const onDiaryCreated = onDocumentCreated(
  {
    document: "diaries/{diaryId}",
    database: "calmify-native",
    secrets: [geminiApiKey],
    timeoutSeconds: 60,
    memory: "512MiB",
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      logger.warn("No data in event", {eventId: event.id});
      return;
    }

    const diary = snapshot.data() as DiaryDocument;
    const diaryId = event.params.diaryId;

    logger.info("Diary created, generating insight", {
      diaryId,
      ownerId: diary.ownerId,
      mood: diary.mood,
    });

    try {
      // Generate AI insight
      const insight = await generateInsightFlow({diary});

      // Save insight to Firestore (calmify-native database)
      const db = admin.firestore();
      const insightRef = db
        .collection("diary_insights")
        .doc(); // Auto-generate ID

      // Use dayKey from diary (business date for grouping in charts)
      // generatedAt tracks when the insight was actually created
      // dayKey ensures insight appears on the correct day in charts
      const dayKey = diary.dayKey ||
        diary.date.toDate().toISOString().split("T")[0]; // Fallback
      const sourceTimezone = diary.timezone ||
        "Europe/Rome"; // Fallback for old diaries

      logger.info("Insight metadata", {
        diaryId,
        dayKey,
        sourceTimezone,
        diaryTimestamp: diary.date.toDate().toISOString(),
      });

      await insightRef.set({
        diaryId: diaryId,
        ownerId: diary.ownerId,
        // When insight was generated (technical)
        generatedAt: admin.firestore.FieldValue.serverTimestamp(),
        dayKey: dayKey, // Business date from diary (for charts)
        sourceTimezone: sourceTimezone, // Timezone reference

        // Sentiment Analysis
        sentimentPolarity: insight.sentimentPolarity,
        sentimentMagnitude: insight.sentimentMagnitude,

        // Content Analysis
        topics: insight.topics,
        keyPhrases: insight.keyPhrases,

        // Cognitive Patterns
        cognitivePatterns: insight.cognitivePatterns,

        // Summary & Suggestions
        summary: insight.summary,
        suggestedPrompts: insight.suggestedPrompts,

        // Confidence & Metadata
        confidence: insight.confidence,
        modelUsed: "gemini-2.0-flash-exp",
        processingTimeMs: null,

        // User Feedback (inizialmente null)
        userCorrection: null,
        userRating: null,
      });

      logger.info("Insight saved to Firestore", {
        diaryId,
        insightId: insightRef.id,
        sentimentPolarity: insight.sentimentPolarity,
        confidence: insight.confidence,
        topicsCount: insight.topics.length,
        patternsCount: insight.cognitivePatterns.length,
      });

      // 🚨 CRISIS DETECTION: Check for negative sentiment + high magnitude
      if (insight.sentimentPolarity < -0.7 && insight.sentimentMagnitude > 7) {
        logger.warn("HIGHLY NEGATIVE SENTIMENT DETECTED", {
          diaryId,
          ownerId: diary.ownerId,
          sentimentPolarity: insight.sentimentPolarity,
          sentimentMagnitude: insight.sentimentMagnitude,
        });

        // Send CRISIS notification via FCM
        await sendFCM(diary.ownerId, {
          title: "💙 Siamo qui per te",
          body: "Abbiamo notato che stai attraversando un momento " +
            "difficile. Considera di parlare con un professionista " +
            "della salute mentale.",
          data: {
            type: "CRISIS",
            action: "OPEN_HOME_SCREEN",
            insightId: insightRef.id,
            diaryId: diaryId,
          },
        });

        logger.info("CRISIS notification sent", {
          diaryId,
          ownerId: diary.ownerId,
        });
      } else {
        // Send regular INSIGHT_READY notification
        // Only if confidence is high enough
        if (insight.confidence > 0.5) {
          const summaryPreview = insight.summary.length > 100 ?
            insight.summary.substring(0, 97) + "..." :
            insight.summary;

          await sendFCM(diary.ownerId, {
            title: "✨ Nuovi insight sul tuo diario",
            body: summaryPreview,
            data: {
              type: "INSIGHT_READY",
              action: "OPEN_INSIGHTS_SCREEN",
              insightId: insightRef.id,
              diaryId: diaryId,
            },
          });

          logger.info("INSIGHT_READY notification sent", {
            diaryId,
            insightId: insightRef.id,
            confidence: insight.confidence,
          });
        } else {
          logger.info("Skipping notification - low confidence", {
            diaryId,
            confidence: insight.confidence,
          });
        }
      }

      return {success: true, insightId: insightRef.id};
    } catch (error) {
      logger.error("Failed to generate or save insight", {
        diaryId,
        error: error instanceof Error ? error.message : String(error),
      });

      // Non fallire la function, solo logga l'errore
      return {success: false, error: String(error)};
    }
  }
);

/**
 * Export Weekly Profile Computation (HTTP Function)
 */
export {computeWeeklyProfiles} from "./scheduler/compute-profiles";

/**
 * Export Weekly Reminders (HTTP Function - Cloud Scheduler triggered)
 */
export {sendWeeklyReminders} from "./scheduler/send-reminders";
