package com.lifo.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifo.ui.components.GoogleButton
import com.lifo.ui.theme.CalmifySpacing

@Composable
fun AuthenticationContent(
    modifier: Modifier = Modifier,
    loadingState: Boolean,
    onButtonClicked: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero section — logo + tagline (centered, takes most space)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 40.dp),                  // custom — out-of-scale auth side margin, intentional
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                modifier = Modifier.size(96.dp),               // logo container — content-driven
                painter = painterResource(id = com.lifo.ui.R.drawable.logo_calmify),
                contentDescription = "Calmify"
            )
            Spacer(modifier = Modifier.height(CalmifySpacing.xxl))  // was 32.dp ✓
            Text(
                text = stringResource(id = R.string.auth_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(CalmifySpacing.md))   // was 12.dp ✓
            Text(
                text = stringResource(id = R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Sign-in button — small, at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = CalmifySpacing.xxxl), // h=40 custom, v=48 → xxxl ✓
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GoogleButton(
                iconPainter = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic),
                loadingState = loadingState,
                onClick = onButtonClicked
            )
        }
    }
}
