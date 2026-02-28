package com.lifo.calmifyapp

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lifo.calmifyapp.MainActivity.Companion.CHANNEL_CRISIS
import com.lifo.calmifyapp.MainActivity.Companion.CHANNEL_INSIGHTS
import com.lifo.calmifyapp.MainActivity.Companion.CHANNEL_REMINDERS
import com.lifo.calmifyapp.MainActivity.Companion.CHANNEL_WELLNESS

/**
 * Firebase Cloud Messaging Service - Week 8
 *
 * Handles incoming FCM notifications and manages FCM token lifecycle.
 */
class CalmifyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        // Notification types
        private const val TYPE_INSIGHT_READY = "INSIGHT_READY"
        private const val TYPE_WEEKLY_SNAPSHOT = "WEEKLY_SNAPSHOT_REMINDER"
        private const val TYPE_CRISIS = "CRISIS"
        private const val TYPE_WELLNESS = "WELLNESS_TIP"

        // Deep link actions
        private const val ACTION_OPEN_SNAPSHOT = "OPEN_SNAPSHOT_SCREEN"
        private const val ACTION_OPEN_INSIGHTS = "OPEN_INSIGHTS_SCREEN"
        private const val ACTION_OPEN_HOME = "OPEN_HOME_SCREEN"
    }

    /**
     * Called when a new FCM token is generated or refreshed
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("[CalmifyFCM] New FCM token: $token")

        // Save token to Firestore
        saveFCMTokenToFirestore(token)
    }

    /**
     * Called when a message is received from FCM
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        println("[CalmifyFCM] Message received from: ${message.from}")

        // Extract notification type from data payload
        val notificationType = message.data["type"] ?: TYPE_WELLNESS
        val title = message.notification?.title ?: message.data["title"] ?: "Calmify"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val imageUrl = message.notification?.imageUrl?.toString()
        val action = message.data["action"]
        val diaryId = message.data["diaryId"] // For INSIGHT_READY notifications
        val insightId = message.data["insightId"]

        println("[CalmifyFCM] Notification type: $notificationType, title: $title, diaryId: $diaryId")

        // Show notification based on type
        showNotification(
            type = notificationType,
            title = title,
            body = body,
            imageUrl = imageUrl,
            action = action,
            diaryId = diaryId,
            insightId = insightId
        )
    }

    /**
     * Display notification with appropriate channel and intent
     */
    private fun showNotification(
        type: String,
        title: String,
        body: String,
        imageUrl: String?,
        action: String?,
        diaryId: String? = null,
        insightId: String? = null
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Determine channel ID based on notification type
        val channelId = when (type) {
            TYPE_INSIGHT_READY -> CHANNEL_INSIGHTS
            TYPE_WEEKLY_SNAPSHOT -> CHANNEL_REMINDERS
            TYPE_CRISIS -> CHANNEL_CRISIS
            TYPE_WELLNESS -> CHANNEL_WELLNESS
            else -> CHANNEL_WELLNESS // Default fallback
        }

        // Create pending intent for deep linking
        val intent = createDeepLinkIntent(action, diaryId, insightId)
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // Unique request code
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // Make sure this icon exists
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(getNotificationPriority(type))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // Add large icon if image URL is provided
        imageUrl?.let {
            // TODO: Load image from URL using Coil or Glide
            // For now, just use default icon
        }

        // Show notification with unique ID based on timestamp
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())

        println("[CalmifyFCM] Notification displayed: ID=$notificationId, channel=$channelId")
    }

    /**
     * Create deep link intent based on action
     */
    private fun createDeepLinkIntent(
        action: String?,
        diaryId: String? = null,
        insightId: String? = null
    ): Intent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Add action extras for deep linking (route names from Screen.kt)
        when (action) {
            ACTION_OPEN_SNAPSHOT -> {
                intent.putExtra("navigate_to", "wellbeing_snapshot_screen") // WellbeingSnapshot route
            }
            ACTION_OPEN_INSIGHTS -> {
                // Insight screen requires diaryId parameter
                if (diaryId != null) {
                    intent.putExtra("navigate_to", "insight_screen?diaryId=$diaryId")
                    intent.putExtra("diaryId", diaryId)
                    insightId?.let { intent.putExtra("insightId", it) }
                    println("[CalmifyFCM] Creating intent for insight screen with diaryId: $diaryId")
                } else {
                    println("[CalmifyFCM] WARNING: OPEN_INSIGHTS action received but diaryId is null, opening home instead")
                    intent.putExtra("navigate_to", "home_screen")
                }
            }
            ACTION_OPEN_HOME -> {
                intent.putExtra("navigate_to", "home_screen") // Home route
            }
            else -> {
                // Default: open home
                intent.putExtra("navigate_to", "home_screen")
            }
        }

        return intent
    }

    /**
     * Get notification priority based on type
     */
    private fun getNotificationPriority(type: String): Int {
        return when (type) {
            TYPE_CRISIS, TYPE_WEEKLY_SNAPSHOT -> NotificationCompat.PRIORITY_HIGH
            TYPE_INSIGHT_READY -> NotificationCompat.PRIORITY_DEFAULT
            TYPE_WELLNESS -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
    }

    /**
     * Save FCM token to Firestore (calmify-native database)
     */
    private fun saveFCMTokenToFirestore(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            println("[CalmifyFCM] WARNING: User not authenticated, cannot save FCM token")
            return
        }

        // Save to Firestore users collection
        val db = FirebaseFirestore.getInstance()

        // Use calmify-native database (default)
        db.collection("users")
            .document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                println("[CalmifyFCM] FCM token saved to Firestore successfully")
            }
            .addOnFailureListener { e ->
                println("[CalmifyFCM] ERROR: Failed to save FCM token to Firestore: ${e.message}")

                // If update fails (document doesn't exist), try set with merge
                db.collection("users")
                    .document(userId)
                    .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        println("[CalmifyFCM] FCM token created in Firestore successfully")
                    }
                    .addOnFailureListener { e2 ->
                        println("[CalmifyFCM] ERROR: Failed to create FCM token in Firestore: ${e2.message}")
                    }
            }
    }
}
