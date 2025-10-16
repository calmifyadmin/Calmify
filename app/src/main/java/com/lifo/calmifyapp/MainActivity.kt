package com.lifo.calmifyapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    // App state management
    private val _appState = MutableStateFlow<AppState>(AppState.Initializing)
    private val appState: StateFlow<AppState> = _appState.asStateFlow()

    // Navigation controller state
    private var navController: NavHostController? = null

    // Keep splash screen visible until app is ready
    private var keepSplashScreen = true

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Install splash screen before super.onCreate
        installSplashScreen().apply {
            setKeepOnScreenCondition { keepSplashScreen }
        }

        // Configure edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
        // Initialize in background
        lifecycleScope.launch {
            initializeApp()
        }

        setContent {
            val currentAppState by appState.collectAsStateWithLifecycle()

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
                Screen.Home.route
            } else {
                Screen.Authentication.route
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking user status", e)
            Screen.Authentication.route
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