/**
 * Send Weekly Reminders - Cloud Scheduler Function
 * Week 8: FCM Push Notifications
 *
 * Triggered by Cloud Scheduler (e.g., every Sunday at 9 PM)
 * Sends reminders to users who haven't completed their weekly wellbeing snapshot.
 *
 * Logic:
 * - Finds all active users (created at least 1 diary in last 30 days)
 * - Checks if user has created a wellbeing_snapshot this week (Monday-Sunday)
 * - If snapshotCount = 0 → send reminder
 * - NO stress-based skipping (user should always complete weekly snapshot)
 */

import {onRequest} from "firebase-functions/v2/https";
import {logger} from "firebase-functions/v2";
import * as admin from "firebase-admin";
import {sendFCMBatch} from "../utils/fcm-helper";

/**
 * HTTP Cloud Function: sendWeeklyReminders
 *
 * Triggered by Cloud Scheduler (Sundays at 9 PM)
 * Security: Requires proper authentication (OIDC token)
 */
export const sendWeeklyReminders = onRequest(
  {
    timeoutSeconds: 300, // 5 minutes max
    memory: "512MiB",
    region: "europe-west1",
  },
  async (request, response) => {
    logger.info("sendWeeklyReminders triggered", {
      method: request.method,
      timestamp: new Date().toISOString(),
    });

    try {
      const db = admin.firestore();

      // Calculate current week boundaries (Monday 00:00:00 to Sunday 23:59:59)
      const now = new Date();
      const weekStart = getWeekStart(now);
      const weekEnd = getWeekEnd(now);

      logger.info("Checking snapshots for current week", {
        weekStart: weekStart.toISOString(),
        weekEnd: weekEnd.toISOString(),
      });

      // Step 1: Get all active users (created at least 1 diary in last 30 days)
      const thirtyDaysAgo = new Date();
      thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

      const recentDiariesSnapshot = await db
        .collection("diaries")
        .where("date", ">=", thirtyDaysAgo)
        .get();

      // Extract unique user IDs
      const activeUserIds = new Set<string>();
      recentDiariesSnapshot.forEach((doc) => {
        const diary = doc.data();
        if (diary.ownerId) {
          activeUserIds.add(diary.ownerId);
        }
      });

      logger.info("Found active users", {
        count: activeUserIds.size,
      });

      if (activeUserIds.size === 0) {
        response.status(200).json({
          success: true,
          message: "No active users found",
          sent: 0,
        });
        return;
      }

      // Step 2: For each user, check if they have a wellbeing_snapshot this week
      const usersToRemind: string[] = [];
      const skippedUsers: Array<{userId: string; reason: string}> = [];

      for (const userId of activeUserIds) {
        // Query wellbeing_snapshots for this user this week
        const snapshotsSnapshot = await db
          .collection("wellbeing_snapshots")
          .where("ownerId", "==", userId)
          .where("timestamp", ">=", weekStart)
          .where("timestamp", "<=", weekEnd)
          .get();

        const snapshotCount = snapshotsSnapshot.size;

        if (snapshotCount === 0) {
          logger.info("User needs reminder - no snapshots this week", {
            userId,
            weekStart: weekStart.toISOString(),
          });
          usersToRemind.push(userId);
        } else {
          logger.info("User already completed snapshot", {
            userId,
            snapshotCount,
          });
          skippedUsers.push({
            userId,
            reason: "HAS_SNAPSHOT_THIS_WEEK",
          });
        }

        // Add small delay to avoid rate limits
        await new Promise((resolve) => setTimeout(resolve, 50));
      }

      logger.info("Filtered users for reminders", {
        toRemind: usersToRemind.length,
        skipped: skippedUsers.length,
      });

      if (usersToRemind.length === 0) {
        response.status(200).json({
          success: true,
          message: "No users need reminders (all completed snapshots)",
          sent: 0,
          skipped: skippedUsers.length,
          skippedReasons: skippedUsers,
        });
        return;
      }

      // Step 3: Send FCM notifications
      const notificationResult = await sendFCMBatch(usersToRemind, {
        title: "📝 Il tuo check-in settimanale ti aspetta!",
        body: "Prenditi un momento per riflettere sulla tua settimana. " +
          "Come ti senti?",
        data: {
          type: "WEEKLY_SNAPSHOT_REMINDER",
          action: "OPEN_SNAPSHOT_SCREEN",
        },
      });

      logger.info("Weekly reminders sent", {
        sent: notificationResult.sent,
        failed: notificationResult.failed,
        total: usersToRemind.length,
      });

      // Log failures
      if (notificationResult.failed > 0) {
        logger.warn("Some notifications failed", {
          failed: notificationResult.failed,
          errors: notificationResult.errors,
        });
      }

      response.status(200).json({
        success: true,
        message: "Weekly reminders processed",
        sent: notificationResult.sent,
        failed: notificationResult.failed,
        skipped: skippedUsers.length,
        skippedReasons: skippedUsers,
        errors: notificationResult.errors.length > 0 ?
          notificationResult.errors :
          undefined,
      });
    } catch (error) {
      logger.error("Failed to send weekly reminders", {
        error: error instanceof Error ? error.message : String(error),
        stack: error instanceof Error ? error.stack : undefined,
      });

      response.status(500).json({
        success: false,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }
);

/**
 * Get the start of the current week (Monday 00:00:00)
 */
function getWeekStart(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1); // Adjust for Monday start
  d.setDate(diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

/**
 * Get the end of the current week (Sunday 23:59:59)
 */
function getWeekEnd(date: Date): Date {
  const d = getWeekStart(date);
  d.setDate(d.getDate() + 6);
  d.setHours(23, 59, 59, 999);
  return d;
}
