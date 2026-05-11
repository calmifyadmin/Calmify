package com.lifo.biocontext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.biocontext.BioContextContract.TypeInventory
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.repository.ProviderStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bio-Signal Transparency Dashboard ("Privacy by Visibility").
 *
 * Shows the user exactly what Calmify has stored locally about their bio-signals,
 * exactly what's been pushed to the server, and gives one-tap GDPR Art.17/20
 * actions. Matches the design at `design/biosignal/Calmify Bio Context.html`.
 *
 * The visual grammar is borrowed from Google Health / Fitbit Premium (semantic
 * colors, narrative-first cards, in-range bands) but the framing is inverted:
 * no scores, no targets, no "out of range" anxiety. See
 * `memory/feedback_calmify_values.md` for the 7 dogmas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BioContextScreen(
    state: BioContextContract.State,
    onIntent: (BioContextContract.Intent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Strings.BioContext.topbar),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Strings.Action.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onIntent(BioContextContract.Intent.Refresh) }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(Strings.BioContext.actionRefresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CalmifySpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg),
        ) {
            Spacer(Modifier.height(CalmifySpacing.sm))

            // Subtitle
            Text(
                text = stringResource(Strings.BioContext.subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
            )

            // 1. Provider status card
            ProviderStatusCard(
                status = state.providerStatus,
                isIngesting = state.isIngesting,
                onIngest = { onIntent(BioContextContract.Intent.IngestNow) },
            )

            // 2. Inventory
            InventoryCard(
                inventory = state.typeInventory,
                totalSamples = state.totalSamplesLocal,
                isEmpty = state.isEmpty,
            )

            // 3. Connected sources
            SourcesCard(sources = state.connectedSources)

            // 4. Server section
            ServerCard(
                aggregatesPending = state.totalAggregatesPending,
            )

            // 5. Privacy actions
            PrivacyActionsCard(
                onExport = { onIntent(BioContextContract.Intent.ExportRequested) },
                onDelete = { showDeleteDialog = true },
                isDeleting = state.isDeleting,
            )

            // 6. Privacy statement
            PrivacyStatementCard()

            Spacer(Modifier.height(CalmifySpacing.xxl))
        }
    }

    if (showDeleteDialog) {
        DeleteAllDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onIntent(BioContextContract.Intent.DeleteAllConfirmed)
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun ProviderStatusCard(
    status: ProviderStatus,
    isIngesting: Boolean,
    onIngest: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val (labelRes, statusColor) = when (status) {
        ProviderStatus.NotInstalled -> Strings.BioContext.providerNotInstalled to colorScheme.error
        ProviderStatus.NotSupported -> Strings.BioContext.providerNotSupported to colorScheme.error
        ProviderStatus.NeedsUpdate -> Strings.BioContext.providerNotInstalled to colorScheme.error
        is ProviderStatus.NeedsPermission -> Strings.BioContext.providerNeedsPermission to colorScheme.tertiary
        ProviderStatus.Ready -> Strings.BioContext.providerReady to colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),                       // status indicator dot
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.HealthAndSafety,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface,
                )
            }

            if (status is ProviderStatus.Ready) {
                Spacer(Modifier.height(CalmifySpacing.md))
                Button(
                    onClick = onIngest,
                    enabled = !isIngesting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CalmifyRadius.xl),
                    contentPadding = PaddingValues(vertical = CalmifySpacing.md, horizontal = CalmifySpacing.xl),
                ) {
                    if (isIngesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(CalmifySpacing.sm))
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(CalmifySpacing.sm))
                    }
                    Text(stringResource(Strings.BioContext.actionIngest))
                }
            }
        }
    }
}

@Composable
private fun InventoryCard(
    inventory: List<TypeInventory>,
    totalSamples: Int,
    isEmpty: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            Text(
                text = stringResource(Strings.BioContext.inventoryTitle),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(CalmifySpacing.xs))
            Text(
                text = stringResource(Strings.BioContext.totalSamples, totalSamples),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(CalmifySpacing.md))

            if (isEmpty) {
                Text(
                    text = stringResource(Strings.BioContext.inventoryEmpty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            } else {
                inventory.forEachIndexed { idx, row ->
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = CalmifySpacing.sm),
                            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                    InventoryRow(row)
                }
            }
        }
    }
}

@Composable
private fun InventoryRow(row: TypeInventory) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(typeLabel(row.type)),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colorScheme.onSurface,
            )
            if (row.uniqueSourceDevices.isNotEmpty()) {
                Text(
                    text = row.uniqueSourceDevices.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        Text(
            text = stringResource(Strings.BioContext.totalSamples, row.sampleCount),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (row.sampleCount > 0) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SourcesCard(sources: List<BioContextContract.ConnectedSource>) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            Text(
                text = stringResource(Strings.BioContext.sourcesTitle),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(CalmifySpacing.md))
            if (sources.isEmpty()) {
                Text(
                    text = stringResource(Strings.BioContext.sourcesEmpty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            } else {
                sources.forEachIndexed { idx, src ->
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = CalmifySpacing.sm),
                            color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = src.deviceName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(Strings.BioContext.sourceViaApp, src.appName),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(aggregatesPending: Int) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            Text(
                text = stringResource(Strings.BioContext.serverTitle),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(CalmifySpacing.md))
            Text(
                text = stringResource(Strings.BioContext.serverNeverRaw),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
            if (aggregatesPending > 0) {
                Spacer(Modifier.height(CalmifySpacing.sm))
                Text(
                    text = stringResource(Strings.BioContext.serverPending, aggregatesPending),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun PrivacyActionsCard(
    onExport: () -> Unit,
    onDelete: () -> Unit,
    isDeleting: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(CalmifySpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
        ) {
            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CalmifyRadius.xl),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                ),
                contentPadding = PaddingValues(vertical = CalmifySpacing.md, horizontal = CalmifySpacing.xl),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(CalmifySpacing.sm))
                Text(stringResource(Strings.BioContext.actionExport))
            }
            Button(
                onClick = onDelete,
                enabled = !isDeleting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CalmifyRadius.xl),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.errorContainer,
                    contentColor = colorScheme.onErrorContainer,
                ),
                contentPadding = PaddingValues(vertical = CalmifySpacing.md, horizontal = CalmifySpacing.xl),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onErrorContainer,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.size(CalmifySpacing.sm))
                Text(stringResource(Strings.BioContext.actionDelete))
            }
        }
    }
}

@Composable
private fun PrivacyStatementCard() {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = colorScheme.primaryContainer.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(CalmifySpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(28.dp),                            // icon container
                shape = CircleShape,
                color = colorScheme.primary.copy(alpha = 0.2f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.primary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Strings.BioContext.statementTitle),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSurface,
                )
                Spacer(Modifier.height(CalmifySpacing.xs))
                Text(
                    text = stringResource(Strings.BioContext.statementNoSellAds),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun DeleteAllDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Strings.BioContext.deleteDialogTitle)) },
        text = { Text(stringResource(Strings.BioContext.deleteDialogBody)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Strings.BioContext.deleteDialogConfirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Strings.Action.cancel))
            }
        },
        shape = RoundedCornerShape(CalmifyRadius.xxl),
    )
}

/** Map a [BioSignalDataType] to its localized label. */
private fun typeLabel(type: BioSignalDataType): StringResource = when (type) {
    BioSignalDataType.HEART_RATE -> Strings.BioContext.typeHeartRate
    BioSignalDataType.HRV -> Strings.BioContext.typeHrv
    BioSignalDataType.SLEEP -> Strings.BioContext.typeSleep
    BioSignalDataType.STEPS -> Strings.BioContext.typeSteps
    BioSignalDataType.RESTING_HEART_RATE -> Strings.BioContext.typeRestingHeartRate
    BioSignalDataType.OXYGEN_SATURATION -> Strings.BioContext.typeOxygenSaturation
    BioSignalDataType.ACTIVITY -> Strings.BioContext.typeActivity
}
