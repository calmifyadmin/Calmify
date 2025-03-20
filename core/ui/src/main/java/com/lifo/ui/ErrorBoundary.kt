import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ErrorBoundary(
    content: @Composable () -> Unit
) {
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val errorHandler = remember {
        CoroutineExceptionHandler { _, throwable ->
            Log.e("ErrorBoundary", "Error caught in composable", throwable)
            hasError = true
            errorMessage = throwable.message ?: "Unknown error occurred"
        }
    }

    val scope = rememberCoroutineScope()

    if (hasError) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("An error occurred in the UI")
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    hasError = false
                    errorMessage = null
                }) {
                    Text("Retry")
                }
            }
        }
    } else {
        // Usiamo un key differente per forzare il riavvio del LaunchedEffect quando cambia hasError
        val key = if (hasError) "error" else "content"

        LaunchedEffect(key) {
            try {
                // Wrapper per il contenuto che può generare errori
                withContext(errorHandler) {
                    // Non possiamo chiamare direttamente content() qui, ma possiamo
                    // impostare uno stato che controlla cosa viene visualizzato
                }
            } catch (e: Exception) {
                Log.e("ErrorBoundary", "Exception in LaunchedEffect", e)
                hasError = true
                errorMessage = e.message
            }
        }

        // Mostriamo il contenuto normalmente
        content()
    }
}