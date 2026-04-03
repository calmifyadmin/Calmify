package com.lifo.ui.components


import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.alert_no
import com.lifo.ui.resources.alert_yes
import org.jetbrains.compose.resources.stringResource

@Composable
fun DisplayAlertDialog(
    title: String,
    message: String,
    dialogOpened: Boolean,
    onDialogClosed: () -> Unit,
    onYesClicked: () -> Unit,
) {
    if (dialogOpened) {
        AlertDialog(
            title = {
                Text(
                    text = title,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = message,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    fontWeight = FontWeight.Normal
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onYesClicked()
                        onDialogClosed()
                    })
                {
                    Text(text = stringResource(Res.string.alert_yes))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDialogClosed)
                {
                    Text(text = stringResource(Res.string.alert_no))
                }
            },
            onDismissRequest = onDialogClosed
        )
    }
}