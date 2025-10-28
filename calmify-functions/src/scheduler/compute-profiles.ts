/**
 * Compute Weekly Psychological Profiles
 * Week 6: PSYCHOLOGICAL_INSIGHTS_PLAN.md
 *
 * HTTP Cloud Function triggered by Cloud Scheduler
 * Computes psychological profiles for all active users
 */

import {onRequest} from "firebase-functions/v2/https";
import {logger} from "firebase-functions/v2";
import * as admin from "firebase-admin";
import {
  calculateWeightedAverage,
  calculateStdDev,
  detectPeaks,
  calculateResilience,
  detectTrend,
  calculateConfidence,
  calculateMoodBaseline,
  Trend,
  DataPoint,
} from "../utils/profile-calculator";

/**
 * Get ISO week number for a date
 */
function getISOWeek(date: Date): {week: number; year: number} {
  const target = new Date(date.valueOf());
  const dayNr = (date.getDay() + 6) % 7;
  target.setDate(target.getDate() - dayNr + 3);
  const jan4 = new Date(target.getFullYear(), 0, 4);
  const dayDiff = (target.getTime() - jan4.getTime()) / 86400000;
  const week = 1 + Math.ceil(dayDiff / 7);
  return {week, year: target.getFullYear()};
}

/**
 * Compute psychological profile for a single user
 */
async function computeUserProfile(
  userId: string,
  db: admin.firestore.Firestore
): Promise<{success: boolean; profileId?: string; error?: string}> {
  try {
    logger.info("Computing profile for user", {userId});

    // Get current week
    const now = new Date();
    const {week: weekNumber, year} = getISOWeek(now);

    // Date range: past 7-14 days
    const fourteenDaysAgo = new Date(now);
    fourteenDaysAgo.setDate(now.getDate() - 14);
    const sevenDaysAgo = new Date(now);
    sevenDaysAgo.setDate(now.getDate() - 7);

    // Fetch user's diaries from past 14 days
    const diariesSnapshot = await db
      .collection("diaries")
      .where("ownerId", "==", userId)
      .where("date", ">=", fourteenDaysAgo)
      .where("date", "<=", now)
      .orderBy("date", "asc")
      .get();

    if (diariesSnapshot.empty) {
      logger.info("No diaries found for user", {userId});
      return {success: false, error: "No diaries in period"};
    }

    // Fetch wellbeing snapshots from past 7 days
    const snapshotsSnapshot = await db
      .collection("wellbeing_snapshots")
      .where("ownerId", "==", userId)
      .where("timestamp", ">=", sevenDaysAgo)
      .where("timestamp", "<=", now)
      .orderBy("timestamp", "asc")
      .get();

    const diaryCount = diariesSnapshot.size;
    const snapshotCount = snapshotsSnapshot.size;

    logger.info("Fetched user data", {
      userId,
      diaryCount,
      snapshotCount,
    });

    // Extract stress and mood data from diaries
    const stressData: DataPoint[] = [];
    const moodData: string[] = [];

    diariesSnapshot.forEach((doc) => {
      const diary = doc.data();
      const timestamp = diary.date?.toDate()?.getTime() || Date.now();

      // Stress data
      if (diary.stressLevel !== undefined) {
        stressData.push({
          value: diary.stressLevel,
          timestamp,
        });
      }

      // Mood data
      if (diary.mood) {
        moodData.push(diary.mood);
      }
    });

    // Calculate stress metrics
    const stressBaseline = calculateWeightedAverage(stressData);
    const stressValues = stressData.map((d) => d.value);
    const stressVolatility = calculateStdDev(stressValues);
    const stressPeaks = detectPeaks(stressData, 7, stressBaseline);
    const resilienceIndex = calculateResilience(stressData, stressPeaks);

    // Calculate recovery speed (average days to recover from peaks)
    let recoverySpeed = 0;
    const resolvedPeaks = stressPeaks.filter((p) => p.resolved);
    if (resolvedPeaks.length > 0) {
      let totalRecoveryTime = 0;
      for (const peak of resolvedPeaks) {
        const peakIndex = stressData.findIndex((p) => p.timestamp === peak.timestamp);
        if (peakIndex === -1) continue;

        for (let i = peakIndex + 1; i < stressData.length; i++) {
          if (stressData[i].value <= stressBaseline + 1) {
            const recoveryTimeMs = stressData[i].timestamp - peak.timestamp;
            totalRecoveryTime += recoveryTimeMs / (1000 * 60 * 60 * 24);
            break;
          }
        }
      }
      recoverySpeed = totalRecoveryTime / resolvedPeaks.length;
    }

    // Calculate mood metrics
    const moodBaseline = calculateMoodBaseline(moodData);
    const moodDataPoints: DataPoint[] = moodData.map((mood, index) => ({
      value: calculateMoodBaseline([mood]),
      timestamp: stressData[index]?.timestamp || Date.now(),
    }));
    const moodValues = moodDataPoints.map((d) => d.value);
    const moodVolatility = calculateStdDev(moodValues);
    const moodTrend = detectTrend(moodDataPoints);

    // Calculate clarity and coherence (simplified for MVP)
    const clarityTrend = Trend.STABLE;
    const narrativeCoherence = 0.5;

    // Social and purpose trends (from snapshots if available)
    const socialSupportTrend = Trend.STABLE;
    const purposeTrend = Trend.STABLE;

    // Calculate confidence score
    const confidence = calculateConfidence(diaryCount, snapshotCount, 7);

    // Create profile ID
    const profileId = `${userId}_week_${weekNumber}_${year}`;

    // Save profile to Firestore
    const profileRef = db.collection("psychological_profiles").doc(profileId);

    await profileRef.set({
      id: profileId,
      ownerId: userId,
      weekNumber,
      year,
      computedAt: admin.firestore.FieldValue.serverTimestamp(),

      // Stress Dynamics
      stressBaseline: Math.round(stressBaseline * 100) / 100,
      stressVolatility: Math.round(stressVolatility * 100) / 100,
      stressPeaks: stressPeaks.map((p) => ({
        timestamp: p.timestamp,
        level: p.level,
        trigger: p.trigger,
        resolved: p.resolved,
      })),

      // Mood Dynamics
      moodBaseline: Math.round(moodBaseline * 100) / 100,
      moodVolatility: Math.round(moodVolatility * 100) / 100,
      moodTrend,

      // Resilience Metrics
      resilienceIndex: Math.round(resilienceIndex * 100) / 100,
      recoverySpeed: Math.round(recoverySpeed * 100) / 100,

      // Clarity & Coherence
      clarityTrend,
      narrativeCoherence: Math.round(narrativeCoherence * 100) / 100,

      // Social & Purpose
      socialSupportTrend,
      purposeTrend,

      // Data Quality
      diaryCount,
      snapshotCount,
      confidence: Math.round(confidence * 100) / 100,
    });

    logger.info("Profile saved successfully", {
      userId,
      profileId,
      stressBaseline,
      moodTrend,
      confidence,
    });

    return {success: true, profileId};
  } catch (error) {
    logger.error("Failed to compute profile for user", {
      userId,
      error: error instanceof Error ? error.message : String(error),
    });

    return {
      success: false,
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

/**
 * HTTP Cloud Function: Compute Weekly Profiles
 * Triggered by Cloud Scheduler (Sunday 2 AM)
 */
export const computeWeeklyProfiles = onRequest(
  {
    timeoutSeconds: 540, // 9 minutes (Cloud Scheduler timeout)
    memory: "1GiB",
    region: "europe-west1",
  },
  async (req, res) => {
    logger.info("Starting weekly profile computation");

    try {
      const db = admin.firestore();

      // Get all users who have created a diary in the past 14 days
      const fourteenDaysAgo = new Date();
      fourteenDaysAgo.setDate(fourteenDaysAgo.getDate() - 14);

      const recentDiariesSnapshot = await db
        .collection("diaries")
        .where("date", ">=", fourteenDaysAgo)
        .get();

      // Extract unique user IDs
      const activeUserIds = new Set<string>();
      recentDiariesSnapshot.forEach((doc) => {
        const diary = doc.data();
        if (diary.ownerId) {
          activeUserIds.add(diary.ownerId);
        }
      });

      logger.info("Found active users", {count: activeUserIds.size});

      if (activeUserIds.size === 0) {
        res.status(200).json({
          success: true,
          message: "No active users found",
          profilesComputed: 0,
        });
        return;
      }

      // Compute profiles for each user (with batch processing)
      const results: {
        userId: string;
        success: boolean;
        profileId?: string;
        error?: string;
      }[] = [];

      for (const userId of activeUserIds) {
        const result = await computeUserProfile(userId, db);
        results.push({userId, ...result});

        // Add small delay to avoid rate limits
        await new Promise((resolve) => setTimeout(resolve, 100));
      }

      const successCount = results.filter((r) => r.success).length;
      const errorCount = results.filter((r) => !r.success).length;

      logger.info("Profile computation completed", {
        totalUsers: activeUserIds.size,
        successCount,
        errorCount,
      });

      res.status(200).json({
        success: true,
        message: "Weekly profile computation completed",
        totalUsers: activeUserIds.size,
        profilesComputed: successCount,
        errors: errorCount,
        results: results.map((r) => ({
          userId: r.userId,
          success: r.success,
          profileId: r.profileId,
          error: r.error,
        })),
      });
    } catch (error) {
      logger.error("Fatal error in weekly profile computation", {
        error: error instanceof Error ? error.message : String(error),
      });

      res.status(500).json({
        success: false,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }
);
