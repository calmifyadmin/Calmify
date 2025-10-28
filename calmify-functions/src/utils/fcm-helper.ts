/**
 * FCM Helper - Firebase Cloud Messaging Utility
 * Week 8: Push Notification System
 *
 * Handles sending push notifications to users via FCM tokens.
 */

import * as admin from "firebase-admin";
import {logger} from "firebase-functions/v2";

/**
 * Notification payload structure
 */
export interface NotificationPayload {
  title: string;
  body: string;
  imageUrl?: string;
  data?: Record<string, string>;
}

/**
 * Send FCM notification to a user
 *
 * @param userId - User ID to send notification to
 * @param notification - Notification payload
 * @returns Success status and message ID
 */
export async function sendFCM(
  userId: string,
  notification: NotificationPayload
): Promise<{success: boolean; messageId?: string; error?: string}> {
  try {
    logger.info("Attempting to send FCM notification", {
      userId,
      title: notification.title,
    });

    // Get user's FCM token from Firestore
    // Use calmify-native database (must specify on first call)
    const db = admin.firestore();

    const userDoc = await db.collection("users").doc(userId).get();

    logger.info("User document fetch attempt", {
      userId,
      exists: userDoc.exists,
    });

    if (!userDoc.exists) {
      logger.warn("User document not found in Firestore", {
        userId,
        collection: "users",
        database: "calmify-native",
      });
      return {
        success: false,
        error: "USER_NOT_FOUND",
      };
    }

    const userData = userDoc.data();
    const fcmToken = userData?.fcmToken;

    logger.info("FCM token check", {
      userId,
      hasToken: !!fcmToken,
      tokenPreview: fcmToken ? fcmToken.substring(0, 20) + "..." : "null",
    });

    if (!fcmToken) {
      logger.warn("FCM token not found for user", {
        userId,
        userDataKeys: Object.keys(userData || {}),
      });
      return {
        success: false,
        error: "FCM_TOKEN_NOT_FOUND",
      };
    }

    // Construct FCM message
    const message: admin.messaging.Message = {
      token: fcmToken,
      notification: {
        title: notification.title,
        body: notification.body,
        imageUrl: notification.imageUrl,
      },
      data: notification.data || {},
      android: {
        priority: "high",
        notification: {
          channelId: "calmify_reminders",
          priority: "high",
          defaultSound: true,
          defaultVibrateTimings: true,
        },
      },
      apns: {
        payload: {
          aps: {
            sound: "default",
            badge: 1,
          },
        },
      },
    };

    // Send message via Firebase Admin SDK
    const messageId = await admin.messaging().send(message);

    logger.info("FCM notification sent successfully", {
      userId,
      messageId,
      title: notification.title,
    });

    return {
      success: true,
      messageId: messageId,
    };
  } catch (error) {
    logger.error("Failed to send FCM notification", {
      userId,
      error: error instanceof Error ? error.message : String(error),
      errorCode: (error as any)?.code,
      errorStack: error instanceof Error ? error.stack : undefined,
    });

    // Handle specific FCM errors
    if (error instanceof Error) {
      const errorCode = (error as any).code;

      // Token is invalid or expired - clean it up
      if (
        errorCode === "messaging/invalid-registration-token" ||
        errorCode === "messaging/registration-token-not-registered"
      ) {
        logger.warn("Invalid FCM token, removing from user", {userId});

        // Remove invalid token from Firestore
        const db = admin.firestore();
        await db.collection("users").doc(userId).update({
          fcmToken: admin.firestore.FieldValue.delete(),
        });

        return {
          success: false,
          error: "INVALID_TOKEN_REMOVED",
        };
      }
    }

    return {
      success: false,
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

/**
 * Send FCM notification to multiple users (batch)
 *
 * @param userIds - Array of user IDs
 * @param notification - Notification payload
 * @returns Summary of sent/failed notifications
 */
export async function sendFCMBatch(
  userIds: string[],
  notification: NotificationPayload
): Promise<{
  sent: number;
  failed: number;
  errors: Array<{userId: string; error: string}>;
}> {
  logger.info("Sending batch FCM notifications", {
    userCount: userIds.length,
    title: notification.title,
  });

  const results = await Promise.allSettled(
    userIds.map((userId) => sendFCM(userId, notification))
  );

  let sent = 0;
  let failed = 0;
  const errors: Array<{userId: string; error: string}> = [];

  results.forEach((result, index) => {
    if (result.status === "fulfilled" && result.value.success) {
      sent++;
    } else {
      failed++;
      const userId = userIds[index];
      const error = result.status === "fulfilled" ?
        result.value.error || "UNKNOWN" :
        result.reason;
      errors.push({userId, error: String(error)});
    }
  });

  logger.info("Batch FCM notifications completed", {
    sent,
    failed,
    total: userIds.length,
  });

  return {sent, failed, errors};
}
