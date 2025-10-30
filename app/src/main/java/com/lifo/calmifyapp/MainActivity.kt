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
import android.util.Log
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.lifo.app.CalmifyApp
import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.chat.config.ApiConfigManager
import com.lifo.mongo.database.dao.ImageToDeleteDao
import com.lifo.mongo.database.dao.ImageToUploadDao
import com.lifo.mongo.database.entity.ImageToDelete
import com.lifo.mongo.database.entity.ImageToUpload
import com.lifo.ui.theme.CalmifyAppTheme
import com.lifo.util.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

// Screen state management
sealed class AppState {
    object Initializing : AppState()
    object Ready : AppState()
    data class Error(val message: String, val retry: () -> Unit) : AppState()
}

// Assuming you have an ApiConfigManager class and it's set up for Hilt injection
// For example:
// class ApiConfigManager @Inject constructor() {
//    fun setGeminiApiKey(apiKey: String) {
//        // Implement your logic to set the API key, e.g., store it in preferences or a singleton
//        Log.d("ApiConfigManager", "Setting Gemini API Key: $apiKey")
//    }
// }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var imageToUploadDao: ImageToUploadDao

    @Inject
    lateinit var imageToDeleteDao: ImageToDeleteDao

    @Inject
    lateinit var apiConfigManager: ApiConfigManager // Inject your ApiConfigManager here

    @Inject
    lateinit var geminiNativeVoiceSystem: GeminiNativeVoiceSystem // Assuming you inject this too

    @Inject
    lateinit var mongoRepository: com.lifo.mongo.repository.MongoRepository // For FCM token

    @Inject
    lateinit var profileSettingsRepository: com.lifo.mongo.repository.ProfileSettingsRepository // For onboarding check

    // App state management
    private val _appState = MutableStateFlow<AppState>(AppState.Initializing)
    private val appState: StateFlow<AppState> = _appState.asStateFlow()

    // Navigation controller state
    private var navController: NavHostController? = null

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
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.w("MainActivity", "Notification permission denied")
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
                Log.d("MainActivity", "Gemini API key configured from BuildConfig")
            } else {
                Log.w("MainActivity", "Gemini API key is empty in BuildConfig")
            }

            if (openAIApiKey.isNotEmpty()) {
                apiConfigManager.setOpenAIApiKey(openAIApiKey)
                Log.d("MainActivity", "OpenAI API key configured from BuildConfig")
            } else {
                Log.w("MainActivity", "OpenAI API key is empty in BuildConfig")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load API keys from BuildConfig", e)
            // In production, you might want to handle this more gracefully
        }
        lifecycleScope.launch {
            geminiNativeVoiceSystem.initialize()
        }
        // Check for deep link from notification
        handleDeepLink(intent)

        // Initialize in background
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
                                // Use the new CalmifyApp with global navigation bar
                                CalmifyApp(
                                    startDestination = getStartDestination(),
                                    repository = mongoRepository,
                                    deepLinkRoute = deepLinkRoute,
                                    onDeepLinkHandled = {
                                        // Clear deep link after navigation
                                        _deepLinkTarget.value = null
                                    },
                                    onDataLoaded = {
                                        // Data loaded callback - can be used for analytics
                                        Log.d("MainActivity", "Navigation data loaded")
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

            // Check onboarding completion status
            val user = FirebaseAuth.getInstance().currentUser
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

            // Update state to ready
            _appState.value = AppState.Ready

            // Hide splash screen
            keepSplashScreen = false

            // Start cleanup in background
            cleanupCheck(
                scope = lifecycleScope,
                imageToUploadDao = imageToUploadDao,
                imageToDeleteDao = imageToDeleteDao
            )

            // Register FCM token (Week 8)
            registerFCMToken()

        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing app", e)
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

    private fun getStartDestination(): String {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                // User is authenticated, check onboarding status
                val onboardingComplete = _hasCompletedOnboarding.value ?: false
                if (onboardingComplete) {
                    Screen.Home.route
                } else {
                    Screen.Onboarding.route
                }
            } else {
                Screen.Authentication.route
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking user status", e)
            Screen.Authentication.route
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

            Log.d("MainActivity", "Notification channels created successfully")
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
                    Log.d("MainActivity", "Notification permission already granted")
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
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.d("MainActivity", "User not authenticated, skipping FCM token registration")
            return
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("MainActivity", "FCM token obtained: ${token.take(20)}...")

            // Save to Firestore using repository
            lifecycleScope.launch {
                try {
                    val result = mongoRepository.saveFCMToken(token)
                    when (result) {
                        is com.lifo.util.model.RequestState.Success -> {
                            Log.d("MainActivity", "FCM token saved to Firestore")
                        }
                        is com.lifo.util.model.RequestState.Error -> {
                            Log.e("MainActivity", "Failed to save FCM token", result.error)
                        }
                        else -> {
                            Log.w("MainActivity", "FCM token save in unexpected state")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error saving FCM token", e)
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
            Log.d("MainActivity", "Deep link navigation to: $route")
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Custom loading animation
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = com.lifo.ui.R.drawable.logo_calmify),
                        contentDescription = "Calmify Logo",
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 3.dp
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
    imageToUploadDao: ImageToUploadDao,
    imageToDeleteDao: ImageToDeleteDao
) {
    scope.launch(Dispatchers.IO) {
        try {
            // Cleanup images to upload
            val imagesToUpload = imageToUploadDao.getAllImages()
            imagesToUpload.forEach { imageToUpload ->
                retryUploadingImageToFirebase(
                    imageToUpload = imageToUpload,
                    onSuccess = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                imageToUploadDao.cleanupImage(imageId = imageToUpload.id)
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error cleaning up uploaded image", e)
                            }
                        }
                    }
                )
            }

            // Cleanup images to delete
            val imagesToDelete = imageToDeleteDao.getAllImages()
            imagesToDelete.forEach { imageToDelete ->
                retryDeletingImageFromFirebase(
                    imageToDelete = imageToDelete,
                    onSuccess = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                imageToDeleteDao.cleanupImage(imageId = imageToDelete.id)
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error cleaning up deleted image", e)
                            }
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during cleanup check", e)
        }
    }
}

fun retryUploadingImageToFirebase(
    imageToUpload: ImageToUpload,
    onSuccess: () -> Unit
) {
    try {
        val storage = FirebaseStorage.getInstance().reference
        val imageUri = Uri.parse(imageToUpload.imageUri)
        val sessionUri = Uri.parse(imageToUpload.sessionUri)

        Log.d("MainActivity", "Attempting to retry upload for: ${imageToUpload.remoteImagePath}")

        storage.child(imageToUpload.remoteImagePath)
            .putFile(imageUri, StorageMetadata.Builder().build(), sessionUri)
            .addOnSuccessListener {
                Log.d("MainActivity", "Successfully uploaded: ${imageToUpload.remoteImagePath}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Failed to upload: ${imageToUpload.remoteImagePath}", e)
            }
    } catch (e: Exception) {
        Log.e("MainActivity", "Critical error during retry upload", e)
    }
}

fun retryDeletingImageFromFirebase(
    imageToDelete: ImageToDelete,
    onSuccess: () -> Unit
) {
    try {
        val storage = FirebaseStorage.getInstance().reference
        storage.child(imageToDelete.remoteImagePath).delete()
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Failed to delete: ${imageToDelete.remoteImagePath}", e)
            }
    } catch (e: Exception) {
        Log.e("MainActivity", "Error during delete retry", e)
    }
}

fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}
