package com.lifo.calmifyapp

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.defaultComponentContext
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.lifo.app.DecomposeApp
import com.lifo.calmifyapp.navigation.decompose.RootComponent
import com.lifo.calmifyapp.navigation.decompose.RootDestination
import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.chat.config.ApiConfigManager
import com.lifo.mongo.database.ImageToUploadQueries
import com.lifo.mongo.database.ImageToDeleteQueries
import com.lifo.mongo.database.Image_to_upload_table
import com.lifo.mongo.database.Image_to_delete_table
import com.lifo.ui.theme.CalmifyAppTheme
import org.koin.android.ext.android.inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// Screen state management
sealed class AppState {
    object Initializing : AppState()
    object Ready : AppState()
    data class Error(val message: String, val retry: () -> Unit) : AppState()
}

class MainActivity : AppCompatActivity() {

    private val imageToUploadQueries: ImageToUploadQueries by inject()
    private val imageToDeleteQueries: ImageToDeleteQueries by inject()
    private val apiConfigManager: ApiConfigManager by inject()
    private val geminiNativeVoiceSystem: GeminiNativeVoiceSystem by inject()
    private val mongoRepository: com.lifo.util.repository.MongoRepository by inject()
    private val profileSettingsRepository: com.lifo.util.repository.ProfileSettingsRepository by inject()
    private val featureFlagRepository: com.lifo.util.repository.FeatureFlagRepository by inject()
    private val auth: FirebaseAuth by inject()

    // App state management
    private val _appState = MutableStateFlow<AppState>(AppState.Initializing)
    private val appState: StateFlow<AppState> = _appState.asStateFlow()

    // Decompose root component — created once in onCreate, survives config changes
    private lateinit var rootComponent: RootComponent

    // Keep splash screen visible until app is ready
    private var keepSplashScreen = true

    // Deep link navigation target (from FCM notification)
    private val _deepLinkTarget = MutableStateFlow<String?>(null)
    private val deepLinkTarget: StateFlow<String?> = _deepLinkTarget.asStateFlow()

    // Onboarding completion state
    private val _hasCompletedOnboarding = MutableStateFlow<Boolean?>(null)
    private val hasCompletedOnboarding: StateFlow<Boolean?> = _hasCompletedOnboarding.asStateFlow()

    // Notification permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            println("[MainActivity] Notification permission granted")
        } else {
            println("[MainActivity] WARNING: Notification permission denied")
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Install splash screen before super.onCreate
        installSplashScreen().apply {
            setKeepOnScreenCondition { keepSplashScreen }
        }

        // Configure edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Set status bar to transparent to match TopAppBar color
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // Create notification channels (Week 8: FCM)
        createNotificationChannels()

        // Request notification permission (Android 13+)
        requestNotificationPermission()

        // Configure the API keys from BuildConfig
        // These are loaded from local.properties file and injected at build time
        try {
            // Get API keys from the chat module's BuildConfig
            val chatBuildConfig = Class.forName("com.lifo.chat.BuildConfig")

            // Get Gemini API key
            val geminiApiKeyField = chatBuildConfig.getDeclaredField("GEMINI_API_KEY")
            val geminiApiKey = geminiApiKeyField.get(null) as String

            // Get OpenAI API key
            val openAIApiKeyField = chatBuildConfig.getDeclaredField("KEY_OPENAI_API")
            val openAIApiKey = openAIApiKeyField.get(null) as String

            // Set the API keys if they are not empty
            if (geminiApiKey.isNotEmpty()) {
                apiConfigManager.setGeminiApiKey(geminiApiKey)
                println("[MainActivity] Gemini API key configured from BuildConfig")
            } else {
                println("[MainActivity] WARNING: Gemini API key is empty in BuildConfig")
            }

            if (openAIApiKey.isNotEmpty()) {
                apiConfigManager.setOpenAIApiKey(openAIApiKey)
                println("[MainActivity] OpenAI API key configured from BuildConfig")
            } else {
                println("[MainActivity] WARNING: OpenAI API key is empty in BuildConfig")
            }
        } catch (e: Exception) {
            println("[MainActivity] ERROR: Failed to load API keys from BuildConfig: ${e.message}")
            // In production, you might want to handle this more gracefully
        }
        lifecycleScope.launch {
            geminiNativeVoiceSystem.initialize()
        }
        // Check for deep link from notification
        handleDeepLink(intent)

        // Create Decompose root component with Auth as initial destination.
        // Once initializeApp() resolves the actual start destination,
        // it will call replaceAll() to navigate to the correct screen.
        rootComponent = RootComponent(
            componentContext = defaultComponentContext(),
            featureFlagRepository = featureFlagRepository,
            initialDestination = RootDestination.Auth
        )

        // Initialize in background — will resolve start destination and navigate
        lifecycleScope.launch {
            initializeApp()
        }

        setContent {
            val currentAppState by appState.collectAsStateWithLifecycle()
            val deepLinkRoute by deepLinkTarget.collectAsStateWithLifecycle()

            CalmifyAppTheme(dynamicColor = false) {
                // Always provide a background to prevent white screens
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AnimatedContent(
                        targetState = currentAppState,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) with
                                    fadeOut(animationSpec = tween(200))
                        },
                        contentAlignment = Alignment.Center,
                        label = "AppStateTransition"
                    ) { state ->
                        when (state) {
                            is AppState.Initializing -> {
                                InitializingScreen()
                            }
                            is AppState.Ready -> {
                                // Use DecomposeApp with Decompose navigation
                                DecomposeApp(
                                    rootComponent = rootComponent,
                                    repository = mongoRepository,
                                    auth = auth,
                                    deepLinkRoute = deepLinkRoute,
                                    onDeepLinkHandled = {
                                        _deepLinkTarget.value = null
                                    },
                                    onDataLoaded = {
                                        println("[MainActivity] Navigation data loaded")
                                    }
                                )
                            }
                            is AppState.Error -> {
                                ErrorScreen(
                                    message = state.message,
                                    onRetry = state.retry
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun initializeApp() {
        try {
            // Simulate minimum loading time for smooth UX
            val startTime = System.currentTimeMillis()

            // Initialize Firebase
            withContext(Dispatchers.IO) {
                FirebaseApp.initializeApp(this@MainActivity)
            }

            // Fetch feature flags from Remote Config
            withContext(Dispatchers.IO) {
                try {
                    featureFlagRepository.fetchAndActivate()
                    println("[MainActivity] Feature flags fetched: ${featureFlagRepository.flags.value}")
                } catch (e: Exception) {
                    println("[MainActivity] WARNING: Feature flags fetch failed, using defaults: ${e.message}")
                }
            }

            // Check onboarding completion status
            val user = auth.currentUser
            if (user != null) {
                withContext(Dispatchers.IO) {
                    val result = profileSettingsRepository.hasCompletedOnboarding()
                    _hasCompletedOnboarding.value = if (result is com.lifo.util.model.RequestState.Success) {
                        result.data
                    } else {
                        false // Default to false if check fails, user will go through onboarding
                    }
                }
            } else {
                _hasCompletedOnboarding.value = null // Not logged in, will go to auth
            }

            // Ensure minimum loading time of 1 second
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < 1000) {
                delay(1000 - elapsedTime)
            }

            // Navigate to the correct start destination before showing UI
            val startDestination = getStartDestinationDecompose()
            if (startDestination != RootDestination.Auth) {
                rootComponent.replaceAll(startDestination)
            }

            // If the user changed language in Settings, navigate back there after recreation
            val prefs = getSharedPreferences("calmify_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("return_to_settings", false)) {
                prefs.edit().remove("return_to_settings").apply()
                rootComponent.navigateToSettings()
            }

            // Update state to ready
            _appState.value = AppState.Ready

            // Hide splash screen
            keepSplashScreen = false

            // Start cleanup in background
            cleanupCheck(
                scope = lifecycleScope,
                imageToUploadQueries = imageToUploadQueries,
                imageToDeleteQueries = imageToDeleteQueries
            )

            // Register FCM token (Week 8)
            registerFCMToken()

        } catch (e: Exception) {
            println("[MainActivity] ERROR: Error initializing app: ${e.message}")
            _appState.value = AppState.Error(
                message = "Failed to initialize app",
                retry = {
                    lifecycleScope.launch {
                        _appState.value = AppState.Initializing
                        initializeApp()
                    }
                }
            )
            keepSplashScreen = false
        }
    }

    /**
     * Determine the initial Decompose destination based on auth/onboarding state.
     */
    private fun getStartDestinationDecompose(): RootDestination {
        return try {
            val user = auth.currentUser
            if (user != null) {
                val onboardingComplete = _hasCompletedOnboarding.value ?: false
                if (onboardingComplete) {
                    RootDestination.Home
                } else {
                    RootDestination.Onboarding
                }
            } else {
                RootDestination.Auth
            }
        } catch (e: Exception) {
            println("[MainActivity] ERROR: Error checking user status: ${e.message}")
            RootDestination.Auth
        }
    }

    /**
     * Create notification channels for FCM notifications (Week 8)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Insights Channel (Normal priority)
            val insightsChannel = NotificationChannel(
                CHANNEL_INSIGHTS,
                "Insights psicologici",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifiche quando sono pronti nuovi insight sul tuo diario"
                enableLights(true)
                enableVibration(true)
            }

            // 2. Reminders Channel (High priority)
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Promemoria",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Promemoria per il check-in settimanale e attività di benessere"
                enableLights(true)
                enableVibration(true)
            }

            // 3. Wellness Channel (Low priority)
            val wellnessChannel = NotificationChannel(
                CHANNEL_WELLNESS,
                "Suggerimenti benessere",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Suggerimenti e consigli per il benessere mentale"
                enableLights(false)
                enableVibration(false)
            }

            // 4. Crisis Channel (High priority with special sound)
            val crisisChannel = NotificationChannel(
                CHANNEL_CRISIS,
                "Supporto urgente",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche di supporto urgente e risorse di crisi"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            // Register all channels
            notificationManager.createNotificationChannels(
                listOf(insightsChannel, remindersChannel, wellnessChannel, crisisChannel)
            )

            println("[MainActivity] Notification channels created successfully")
        }
    }

    /**
     * Request notification permission for Android 13+ (Week 8)
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    println("[MainActivity] Notification permission already granted")
                }
                else -> {
                    // Request permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    /**
     * Register FCM token with Firestore (Week 8)
     */
    private fun registerFCMToken() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            println("[MainActivity] User not authenticated, skipping FCM token registration")
            return
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                println("[MainActivity] WARNING: Fetching FCM token failed: ${task.exception?.message}")
                return@addOnCompleteListener
            }

            val token = task.result
            println("[MainActivity] FCM token obtained: ${token.take(20)}...")

            // Save to Firestore using repository
            lifecycleScope.launch {
                try {
                    val result = mongoRepository.saveFCMToken(token)
                    when (result) {
                        is com.lifo.util.model.RequestState.Success -> {
                            println("[MainActivity] FCM token saved to Firestore")
                        }
                        is com.lifo.util.model.RequestState.Error -> {
                            println("[MainActivity] ERROR: Failed to save FCM token: ${result.error.message}")
                        }
                        else -> {
                            println("[MainActivity] WARNING: FCM token save in unexpected state")
                        }
                    }
                } catch (e: Exception) {
                    println("[MainActivity] ERROR: Error saving FCM token: ${e.message}")
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running
        handleDeepLink(intent)
    }

    /**
     * Handle deep linking from FCM notifications
     */
    private fun handleDeepLink(intent: Intent?) {
        intent?.getStringExtra("navigate_to")?.let { route ->
            println("[MainActivity] Deep link navigation to: $route")
            _deepLinkTarget.value = route
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("recreated", true)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch { // Ensure cleanup is called on a coroutine scope
            geminiNativeVoiceSystem.cleanup()
        }
    }

    companion object {
        // Notification channel IDs (Week 8: FCM)
        const val CHANNEL_INSIGHTS = "calmify_insights"
        const val CHANNEL_REMINDERS = "calmify_reminders"
        const val CHANNEL_WELLNESS = "calmify_wellness"
        const val CHANNEL_CRISIS = "calmify_crisis"
    }
}

@Composable
private fun InitializingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1109)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = com.lifo.ui.R.drawable.logo_calmify),
                contentDescription = "Calmify",
                modifier = Modifier
                    .size(88.dp)
                    .scale(scale),
                tint = Color(0xFF31C48D)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "CALMIFY",
                color = Color(0xFF31C48D).copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Try Again")
            }
        }
    }
}

// Cleanup functions remain the same
private fun cleanupCheck(
    scope: CoroutineScope,
    imageToUploadQueries: ImageToUploadQueries,
    imageToDeleteQueries: ImageToDeleteQueries
) {
    scope.launch(Dispatchers.IO) {
        try {
            // Cleanup images to upload
            val imagesToUpload = imageToUploadQueries.getAllImages().executeAsList()
            imagesToUpload.forEach { imageToUpload ->
                retryUploadingImageToFirebase(
                    imageToUpload = imageToUpload,
                    onSuccess = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                imageToUploadQueries.cleanupImage(imageToUpload.id)
                            } catch (e: Exception) {
                                println("[MainActivity] ERROR: Error cleaning up uploaded image: ${e.message}")
                            }
                        }
                    }
                )
            }

            // Cleanup images to delete
            val imagesToDelete = imageToDeleteQueries.getAllImages().executeAsList()
            imagesToDelete.forEach { imageToDelete ->
                retryDeletingImageFromFirebase(
                    imageToDelete = imageToDelete,
                    onSuccess = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                imageToDeleteQueries.cleanupImage(imageToDelete.id)
                            } catch (e: Exception) {
                                println("[MainActivity] ERROR: Error cleaning up deleted image: ${e.message}")
                            }
                        }
                    }
                )
            }
        } catch (e: Exception) {
            println("[MainActivity] ERROR: Error during cleanup check: ${e.message}")
        }
    }
}

fun retryUploadingImageToFirebase(
    imageToUpload: Image_to_upload_table,
    onSuccess: () -> Unit
) {
    try {
        val storage = FirebaseStorage.getInstance().reference
        val imageUri = Uri.parse(imageToUpload.imageUri)
        val sessionUri = Uri.parse(imageToUpload.sessionUri)

        println("[MainActivity] Attempting to retry upload for: ${imageToUpload.remoteImagePath}")

        storage.child(imageToUpload.remoteImagePath)
            .putFile(imageUri, StorageMetadata.Builder().build(), sessionUri)
            .addOnSuccessListener {
                println("[MainActivity] Successfully uploaded: ${imageToUpload.remoteImagePath}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                println("[MainActivity] ERROR: Failed to upload: ${imageToUpload.remoteImagePath}: ${e.message}")
            }
    } catch (e: Exception) {
        println("[MainActivity] ERROR: Critical error during retry upload: ${e.message}")
    }
}

fun retryDeletingImageFromFirebase(
    imageToDelete: Image_to_delete_table,
    onSuccess: () -> Unit
) {
    try {
        val storage = FirebaseStorage.getInstance().reference
        storage.child(imageToDelete.remoteImagePath).delete()
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                println("[MainActivity] ERROR: Failed to delete: ${imageToDelete.remoteImagePath}: ${e.message}")
            }
    } catch (e: Exception) {
        println("[MainActivity] ERROR: Error during delete retry: ${e.message}")
    }
}

fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}
