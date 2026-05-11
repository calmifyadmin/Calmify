package com.lifo.subscription

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.resources.*
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.repository.SubscriptionRepository
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    state: SubscriptionContract.State,
    onIntent: (SubscriptionContract.Intent) -> Unit,
) {
    val scrollState = rememberScrollState()

    // PRO Switch: Waitlist dialog when premium not yet enabled
    if (state.showWaitlistDialog) {
        WaitlistDialog(
            email = state.waitlistEmail,
            isLoading = state.isLoading,
            isSubmitted = state.waitlistSubmitted,
            onEmailChange = { onIntent(SubscriptionContract.Intent.UpdateWaitlistEmail(it)) },
            onSubmit = { onIntent(SubscriptionContract.Intent.SubmitWaitlistEmail) },
            onDismiss = { onIntent(SubscriptionContract.Intent.DismissWaitlistDialog) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { onIntent(SubscriptionContract.Intent.DismissPaywall) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = CalmifySpacing.xl), // was 20.dp → xl (24)
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(CalmifySpacing.sm)) // was 8.dp ✓

                PaywallHeader()

                Spacer(modifier = Modifier.height(28.dp))

                FeatureComparisonSection()

                Spacer(modifier = Modifier.height(28.dp))

                if (state.isPro) {
                    ManageSubscriptionCard(
                        expiresAt = state.expiresAt,
                        isAutoRenewing = state.isAutoRenewing,
                        status = state.subscriptionStatus,
                        onManage = { onIntent(SubscriptionContract.Intent.OpenManagePortal) },
                    )
                } else {
                    // Single Pro card — prefer monthly, fall back to first product
                    val proProduct = state.availableProducts.firstOrNull { it.lookupKey == "calmify_premium_monthly" }
                        ?: state.availableProducts.firstOrNull()
                    ProProductCard(
                        product = proProduct,
                        isCurrentTier = false,
                        isWaitlistMode = state.isWaitlistMode,
                        onPurchase = {
                            val lookupKey = proProduct?.lookupKey ?: "calmify_premium_monthly"
                            onIntent(SubscriptionContract.Intent.PurchaseSubscription(lookupKey))
                        },
                    )

                    Spacer(modifier = Modifier.height(CalmifySpacing.lg)) // was 16.dp ✓

                    TextButton(
                        onClick = { onIntent(SubscriptionContract.Intent.RefreshSubscriptionState) },
                    ) {
                        Text(
                            text = stringResource(Res.string.paywall_restore),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.error?.let { errorMessage ->
                    Spacer(modifier = Modifier.height(CalmifySpacing.sm)) // was 8.dp ✓
                    ErrorCard(errorMessage)
                }

                Spacer(modifier = Modifier.height(CalmifySpacing.xl)) // was 24.dp ✓
            }

            // Loading overlay
            AnimatedVisibility(
                visible = state.isLoading,
                enter = fadeIn(tween(200)) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                ),
                exit = fadeOut(tween(150)) + scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(150),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        shape = RoundedCornerShape(CalmifyRadius.lg), // was 16.dp ✓
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(CalmifySpacing.xxl), // was 32.dp ✓
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(CalmifySpacing.lg)) // was 16.dp ✓
                            Text(
                                text = stringResource(Res.string.loading),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// Header
// =====================================================================

@Composable
private fun PaywallHeader() {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    val breathingTransition = rememberInfiniteTransition(label = "headerBreathing")
    val breathingScale by breathingTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "headerBreathingScale",
    )

    val shimmerTransition = rememberInfiniteTransition(label = "headerShimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "headerShimmerOffset",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = breathingScale
                    scaleY = breathingScale
                }
                .clip(CircleShape)
                .drawBehind {
                    val brush = Brush.linearGradient(
                        colors = listOf(primaryColor, tertiaryColor),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    drawRect(brush = brush)

                    val shimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0f),
                        ),
                        start = Offset(shimmerOffset, 0f),
                        end = Offset(shimmerOffset + 160f, size.height),
                    )
                    drawRect(brush = shimmerBrush)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = onPrimaryColor,
            )
        }

        Spacer(modifier = Modifier.height(CalmifySpacing.lg)) // was 16.dp ✓

        Text(
            text = stringResource(Res.string.paywall_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(CalmifySpacing.sm)) // was 8.dp ✓

        Text(
            text = stringResource(Res.string.paywall_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// =====================================================================
// Feature comparison — 2 columns: Free vs Pro
// =====================================================================

@Composable
private fun FeatureComparisonSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg), // was 16.dp ✓
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(CalmifySpacing.xl), // was 20.dp → xl (24)
        ) {
            Text(
                text = stringResource(Res.string.paywall_compare_plans),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(CalmifySpacing.lg)) // was 16.dp ✓

            // Column headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.paywall_features),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.5f),
                )
                Text(
                    text = stringResource(Res.string.paywall_free),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(0.8f),
                )
                Text(
                    text = stringResource(Res.string.paywall_pro),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(0.8f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(CalmifySpacing.md)) // was 12.dp ✓
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(CalmifySpacing.sm)) // was 8.dp ✓

            val features = listOf(
                Triple(stringResource(Res.string.feature_diary), stringResource(Res.string.feature_diary_free), true),
                Triple(stringResource(Res.string.feature_chat_ai), stringResource(Res.string.feature_chat_ai_free), true),
                Triple(stringResource(Res.string.feature_mood_tracking), true, true),
                Triple(stringResource(Res.string.feature_unlimited_diaries), false, true),
                Triple(stringResource(Res.string.feature_unlimited_chat), false, true),
                Triple(stringResource(Res.string.feature_custom_avatar), false, true),
                Triple(stringResource(Res.string.feature_advanced_insights), false, true),
                Triple(stringResource(Res.string.feature_social_messages), false, true),
                Triple(stringResource(Res.string.feature_priority_support), false, true),
                Triple(stringResource(Res.string.feature_early_access), false, true),
            )

            features.forEachIndexed { index, (name, free, pro) ->
                FeatureRow(
                    feature = name,
                    free = free,
                    pro = pro,
                    index = index,
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    feature: String,
    free: Any,
    pro: Any,
    index: Int,
) {
    val enterAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enterAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * 50,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enterAnim.value
                translationY = (1f - enterAnim.value) * 16f
            }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isProExclusive = free is Boolean && !free
        Row(
            modifier = Modifier.weight(1.5f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isProExclusive) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 2.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = feature,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        FeatureCell(value = free, modifier = Modifier.weight(0.8f))
        FeatureCell(
            value = pro,
            modifier = Modifier
                .weight(0.8f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
            isPro = true,
        )
    }
}

@Composable
private fun FeatureCell(
    value: Any,
    modifier: Modifier = Modifier,
    isPro: Boolean = false,
) {
    Box(
        modifier = modifier.padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (value) {
            is Boolean -> {
                if (value) {
                    val checkScale = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        checkScale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(Res.string.paywall_included),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = checkScale.value
                                scaleY = checkScale.value
                            },
                        tint = if (isPro) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.paywall_not_included),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            is String -> {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// =====================================================================
// Single Pro product card
// =====================================================================

@Composable
private fun ProProductCard(
    product: SubscriptionRepository.ProductInfo?,
    isCurrentTier: Boolean,
    isWaitlistMode: Boolean,
    onPurchase: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    val glowTransition = rememberInfiniteTransition(label = "cardGlow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cardGlowAlpha",
    )

    val pulseTransition = rememberInfiniteTransition(label = "subscribePulse")
    val buttonScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "subscribeButtonScale",
    )

    val defaultTitle = stringResource(Res.string.paywall_title)
    val defaultDesc = stringResource(Res.string.paywall_default_desc)
    val displayProduct = product ?: SubscriptionRepository.ProductInfo(
        lookupKey = "calmify_premium_monthly",
        title = defaultTitle,
        description = defaultDesc,
        priceAmount = 499L,
        currency = "EUR",
        interval = "month",
    )
    val formattedPrice = formatPrice(displayProduct.priceAmount, displayProduct.currency, displayProduct.interval)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val glowBrush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = glowAlpha * 0.15f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.width * 0.7f,
                )
                drawRect(brush = glowBrush)
            }
            .border(
                width = 2.dp,
                color = primaryColor.copy(alpha = glowAlpha),
                shape = RoundedCornerShape(CalmifyRadius.lg), // was 16.dp ✓
            ),
        shape = RoundedCornerShape(CalmifyRadius.lg), // was 16.dp ✓
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CalmifySpacing.xl), // was 20.dp → xl (24)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Diamond,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = primaryColor,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayProduct.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = displayProduct.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(CalmifySpacing.lg)) // was 16.dp ✓

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = formattedPrice,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = primaryColor,
                    )
                    if (isWaitlistMode) {
                        Text(
                            text = stringResource(Res.string.paywall_indicative_prices),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = onPurchase,
                    enabled = !isCurrentTier,
                    shape = RoundedCornerShape(CalmifyRadius.md), // was 12.dp ✓
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isWaitlistMode) MaterialTheme.colorScheme.secondary else primaryColor,
                    ),
                    modifier = Modifier
                        .height(44.dp)
                        .then(
                            if (!isCurrentTier) {
                                Modifier.graphicsLayer {
                                    scaleX = buttonScale
                                    scaleY = buttonScale
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (isCurrentTier) {
                        Text(
                            text = stringResource(Res.string.paywall_current_plan),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else if (isWaitlistMode) {
                        Text(
                            text = stringResource(Res.string.paywall_join_waitlist),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(Res.string.paywall_subscribe),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// Manage subscription card (shown when user is PRO)
// =====================================================================

@Composable
private fun ManageSubscriptionCard(
    expiresAt: Long,
    isAutoRenewing: Boolean,
    status: String,
    onManage: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = primaryColor.copy(alpha = 0.6f),
                shape = RoundedCornerShape(CalmifyRadius.lg), // was 16.dp ✓
            ),
        shape = RoundedCornerShape(CalmifyRadius.lg), // was 16.dp ✓
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CalmifySpacing.xl), // was 20.dp → xl (24)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = primaryColor,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Calmify PRO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = manageStatusLine(status, isAutoRenewing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(CalmifySpacing.lg)) // was 16.dp ✓
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(CalmifySpacing.md)) // was 12.dp ✓

            InfoRow(
                label = if (isAutoRenewing) "Prossimo rinnovo" else "Scade il",
                value = formatExpiry(expiresAt),
            )
            Spacer(modifier = Modifier.height(6.dp))
            InfoRow(
                label = "Rinnovo automatico",
                value = if (isAutoRenewing) "Attivo" else "Disattivato",
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onManage,
                shape = RoundedCornerShape(CalmifyRadius.md), // was 12.dp ✓
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gestisci abbonamento",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(CalmifySpacing.sm)) // was 8.dp ✓

            Text(
                text = "Cancella, cambia piano, aggiorna il metodo di pagamento o scarica le fatture sul portale Stripe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun manageStatusLine(status: String, isAutoRenewing: Boolean): String = when (status.lowercase()) {
    "active" -> if (isAutoRenewing) "Abbonamento attivo" else "Attivo — cancellato a fine periodo"
    "trialing" -> "Periodo di prova attivo"
    "past_due" -> "Pagamento in sospeso"
    "canceled" -> "Cancellato"
    else -> status.ifBlank { "Stato sconosciuto" }
}

private fun formatExpiry(expiresAt: Long): String {
    if (expiresAt <= 0L) return "—"
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(expiresAt)
    val dt = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val day = dt.dayOfMonth.toString().padStart(2, '0')
    val month = dt.monthNumber.toString().padStart(2, '0')
    return "$day/$month/${dt.year}"
}

// =====================================================================
// Error card
// =====================================================================

private fun formatPrice(amountInCents: Long, currency: String, interval: String): String {
    val whole = amountInCents / 100L
    val cents = (amountInCents % 100L).toInt()
    val symbol = when (currency.uppercase()) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        else -> currency.uppercase() + " "
    }
    val amount = if (cents == 0) whole.toString() else {
        val padded = if (cents < 10) "0$cents" else cents.toString()
        "$whole,$padded"
    }
    val suffix = when (interval.lowercase()) {
        "year" -> "/anno"
        "month" -> "/mese"
        "week" -> "/sett."
        "day" -> "/giorno"
        else -> ""
    }
    return "$symbol$amount$suffix"
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.md), // was 12.dp ✓
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.md), // was 16+12 ✓
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
