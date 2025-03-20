package com.lifo.calmifyapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storageMetadata
import com.lifo.calmifyapp.navigation.SetupNavGraph
import com.lifo.mongo.database.ImageToDeleteDao
import com.lifo.mongo.database.ImageToUploadDao
import com.lifo.mongo.database.entity.ImageToDelete
import com.lifo.mongo.database.entity.ImageToUpload
import com.lifo.ui.theme.CalmifyAppTheme
import com.lifo.util.Constants.APP_ID
import com.lifo.util.Screen
import dagger.hilt.android.AndroidEntryPoint
import io.realm.kotlin.mongodb.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var imageToUploadDao: ImageToUploadDao
    @Inject
    lateinit var imageToDeleteDao: ImageToDeleteDao
    private var keepSplashOpened = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().setKeepOnScreenCondition {
            keepSplashOpened
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        FirebaseApp.initializeApp(this)
        setContent {
            CalmifyAppTheme(dynamicColor = false) {
                val navController = rememberNavController()
                SetupNavGraph(
                    startDestination = getStartDestination(),
                    navController = navController,
                    onDataLoaded = {
                        Log.d("MainActivity", "Dati caricati, chiudo splash screen")
                        keepSplashOpened = false
                    }
                )
            }
        }


        cleanupCheck(
            scope = lifecycleScope,
            imageToUploadDao = imageToUploadDao,
            imageToDeleteDao = imageToDeleteDao,
            activity = this,  // Passa l'activity
        )
    }
}

private fun getStartDestination(): String {
    val user = App.create(APP_ID).currentUser
    return if (user != null && user.loggedIn) Screen.Home.route
    else Screen.Authentication.route
}

private fun cleanupCheck(
    activity: MainActivity, // Aggiungi questo parametro
    scope: CoroutineScope,
    imageToUploadDao: ImageToUploadDao,
    imageToDeleteDao: ImageToDeleteDao
) {
    scope.launch(Dispatchers.IO) {
        val result = imageToUploadDao.getAllImages()
        result.forEach { imageToUpload ->
            retryUploadingImageToFirebase(
                imageToUpload = imageToUpload,
                onSuccess = {
                    scope.launch(Dispatchers.IO) {
                        imageToUploadDao.cleanupImage(imageId = imageToUpload.id)
                    }
                }
            )
        }
        val result2 = imageToDeleteDao.getAllImages()
        result2.forEach { imageToDelete ->
            retryDeletingImageFromFirebase(
                imageToDelete = imageToDelete,
                onSuccess = {
                    scope.launch(Dispatchers.IO) {
                        imageToDeleteDao.cleanupImage(imageId = imageToDelete.id)
                    }
                }
            )
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

        try {
            // Try to upload with original URI
            storage.child(imageToUpload.remoteImagePath)
                .putFile(imageUri, storageMetadata {}, sessionUri)
                .addOnSuccessListener {
                    Log.d("MainActivity", "Successfully uploaded: ${imageToUpload.remoteImagePath}")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Failed to upload: ${imageToUpload.remoteImagePath}", e)

                    // If we get a security exception, we can't do much without context
                    if (e is SecurityException) {
                        Log.e("MainActivity", "Security exception during upload, image permissions may have expired", e)
                    }
                }
                .addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                    if (progress % 25 == 0) { // Log at 0%, 25%, 50%, 75%, 100%
                        Log.d("MainActivity", "Upload progress for ${imageToUpload.remoteImagePath}: $progress%")
                    }
                }
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Security exception trying to access: ${imageToUpload.imageUri}", e)

            // Since we don't have context here, we can't create a backup file
            // We'll just have to log the error and continue
        } catch (e: Exception) {
            Log.e("MainActivity", "Unexpected error during upload", e)
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Critical error during retry upload", e)
    }
}

fun retryDeletingImageFromFirebase(
    imageToDelete: ImageToDelete,
    onSuccess: () -> Unit
) {
    val storage = FirebaseStorage.getInstance().reference
    storage.child(imageToDelete.remoteImagePath).delete()
        .addOnSuccessListener { onSuccess() }
}
fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}