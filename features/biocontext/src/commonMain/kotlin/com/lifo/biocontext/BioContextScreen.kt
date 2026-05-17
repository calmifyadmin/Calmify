package com.lifo.biocontext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShareLocation
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.biocontext.BioContextContract.InventoryWindow
import com.lifo.biocontext.BioContextContract.TypeInventory
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.repository.ProviderStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bio-Signal Transparency Dashboard ("Privacy by Visibility") — Phase 9.1.1 rewrite (2026-05-17).
 *
 * 1:1 visual port of `design/biosignal/Calmify Bio Context.html` (1267 LOC mockup).
 * Anatomy matches the mockup section-by-section:
 *
 *   01 Header hero       — display title + lede + Lock-icon promise card
 *   02 Active connections — list of connected sources with sync status (ok/warn dot)
 *   03 On this device     — raw sample inventory with 7d/30d/all window picker, 2-col grid
 *   04 Sent to server     — what aggregates left the device + privacy footer
 *   05 Confidence per source — table of which device contributes which signals at what density
 *   06 Your rights        — GDPR Art.17 (delete) + Art.20 (export) + Disconnect actions
 *   07 What we promise    — 4 non-negotiable privacy commitments
 *   + Sticky action bar   — Export + How this works
 *   + DeleteSheet         — type "delete" to confirm Art.17
 *   + HowSheet            — explains aggregation
 *
 * Real data sources:
 * - Connections + Inventory + Sources: from local SQLDelight via [BioContextViewModel]
 * - Confidence per source: derived from per-sample confidence within each source group
 * - Sent timeline: placeholder until a server-side audit log is added (Phase 9.3)
 *
 * Per `memory/feedback_calmify_values.md` dogma #4 (data sovereignty) every number
 * shown is computed from local state; nothing is fabricated. Where the mockup
 * showed mock data the screen falls back to honest empty-state copy.
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
    var showDeleteSheet by remember { mutableStateOf(false) }
    var showHowSheet by remember { mutableStateOf(false) }

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
        bottomBar = {
            BioStickyActionBar(
                onExport = { onIntent(BioContextContract.Intent.ExportRequested) },
                onHow = { showHowSheet = true },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CalmifySpacing.xl),
        ) {
            Spacer(Modifier.height(CalmifySpacing.sm))

            // 01 — Header hero
            HeaderHero(onHow = { showHowSheet = true })

            // 02 — Active connections
            SectionEyebrow(num = 1, label = stringResource(Strings.BioContext.sectionConnections))
            ConnectionsCard(
                providerStatus = state.providerStatus,
                connectedSources = state.connectedSources,
                isIngesting = state.isIngesting,
                onIngest = { onIntent(BioContextContract.Intent.IngestNow) },
            )

            // 03 — On this device (inventory + window picker)
            SectionEyebrow(num = 2, label = stringResource(Strings.BioContext.sectionInventory))
            InventoryCard(
                inventory = state.typeInventory,
                selectedWindow = state.selectedWindow,
                onWindowChange = { onIntent(BioContextContract.Intent.SetWindow(it)) },
                isEmpty = state.isEmpty,
            )

            // 04 — Sent to server (placeholder for audit log)
            SectionEyebrow(num = 3, label = stringResource(Strings.BioContext.sectionSent))
            SentCard(
                aggregatesPending = state.totalAggregatesPending,
                lastSyncMillis = state.lastSyncMillis,
            )

            // 05 — Confidence per source
            SectionEyebrow(num = 4, label = stringResource(Strings.BioContext.sectionConfidence))
            ConfidencePerSourceCard(
                inventory = state.typeInventory,
                sources = state.connectedSources,
            )

            // 06 — Your rights (GDPR)
            SectionEyebrow(num = 5, label = stringResource(Strings.BioContext.sectionRights))
            RightsCard(
                onExport = { onIntent(BioContextContract.Intent.ExportRequested) },
                onDisconnect = { onIntent(BioContextContract.Intent.DisconnectAll) },
                onDelete = { showDeleteSheet = true },
            )

            // 07 — What we promise
            SectionEyebrow(num = 6, label = stringResource(Strings.BioContext.sectionPromises))
            PromisesCard()

            // Centered fineprint
            Spacer(Modifier.height(CalmifySpacing.xl))
            Text(
                text = stringResource(Strings.BioContext.fineprintWellness),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth(),
            )
            // Clearance for the sticky action bar
            Spacer(Modifier.height(96.dp))
        }
    }

    if (showDeleteSheet) {
        BioDeleteSheet(
            onCancel = { showDeleteSheet = false },
            onConfirm = {
                showDeleteSheet = false
                onIntent(BioContextContract.Intent.DeleteAllConfirmed)
            },
        )
    }
    if (showHowSheet) {
        BioHowSheet(onClose = { showHowSheet = false })
    }
}

// ═════════════════════════════════════════════════════════════════════════
// SECTIONS
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun HeaderHero(onHow: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(top = CalmifySpacing.sm)) {
        Text(
            text = stringResource(Strings.BioContext.heroTitle),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.2).sp,
            ),
            color = cs.onSurface,
        )
        Spacer(Modifier.height(CalmifySpacing.sm))
        Text(
            text = stringResource(Strings.BioContext.heroLede),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.15.sp,
            ),
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        // Lock-icon promise card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CalmifyRadius.xxl),
            color = cs.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(CalmifySpacing.xl),
                horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(cs.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column {
                    Text(
                        text = stringResource(Strings.BioContext.promiseTitle),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        ),
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Strings.BioContext.promiseBody),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        ),
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(Strings.BioContext.promiseLink),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = cs.primary,
                        modifier = Modifier.clickable(onClick = onHow),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionsCard(
    providerStatus: ProviderStatus,
    connectedSources: List<BioContextContract.ConnectedSource>,
    isIngesting: Boolean,
    onIngest: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = cs.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            if (connectedSources.isEmpty()) {
                // Empty state — no devices, friendly nudge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(CalmifyRadius.md))
                            .background(cs.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Watch,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Strings.BioContext.connectionsEmptyTitle),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                            ),
                            color = cs.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(Strings.BioContext.connectionsEmptyBody),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            ),
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            } else {
                connectedSources.forEachIndexed { idx, source ->
                    ConnectionRow(source = source, isIngesting = isIngesting && idx == 0)
                    if (idx < connectedSources.size - 1) {
                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.3f))
                    }
                }
                // Sync now button (only when provider is Ready)
                if (providerStatus is ProviderStatus.Ready) {
                    Spacer(Modifier.height(CalmifySpacing.md))
                    BioSecondaryButton(
                        label = stringResource(Strings.BioContext.actionIngest),
                        icon = Icons.Outlined.Refresh,
                        enabled = !isIngesting,
                        onClick = onIngest,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    source: BioContextContract.ConnectedSource,
    isIngesting: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val (statusKind, statusLabel) = remember(source.lastSeenMillis, isIngesting) {
        if (isIngesting) StatusKind.OK to "Syncing…"
        else if (source.lastSeenMillis == null) StatusKind.MUTED to "Unknown"
        else {
            val ageMillis = System.currentTimeMillis() - source.lastSeenMillis
            val ageHours = ageMillis / (3600L * 1000L)
            when {
                ageHours < 6 -> StatusKind.OK to "Synced"
                ageHours < 48 -> StatusKind.WARN to "Stale"
                else -> StatusKind.WARN to "Very stale"
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(CalmifyRadius.md))
                .background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (source.deviceName.contains("phone", ignoreCase = true)) Icons.Outlined.Smartphone
                              else Icons.Outlined.Watch,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.deviceName.ifBlank { "Unknown device" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                    color = cs.onSurface,
                    maxLines = 1,
                )
                if (source.appName.isNotBlank()) {
                    Text(
                        text = " · ${source.appName}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatLastSync(source.lastSeenMillis),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = cs.onSurfaceVariant,
            )
        }
        BioStatusDot(kind = statusKind, label = statusLabel)
    }
}

@Composable
private fun InventoryCard(
    inventory: List<TypeInventory>,
    selectedWindow: InventoryWindow,
    onWindowChange: (InventoryWindow) -> Unit,
    isEmpty: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = cs.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Strings.BioContext.inventoryTitle),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                        ),
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(Strings.BioContext.inventorySub),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        ),
                        color = cs.onSurfaceVariant,
                    )
                }
                BioWindowTabs(
                    selected = selectedWindow,
                    onSelect = onWindowChange,
                    modifier = Modifier.padding(start = CalmifySpacing.md),
                )
            }
            Spacer(Modifier.height(CalmifySpacing.md))
            if (isEmpty) {
                Text(
                    text = stringResource(Strings.BioContext.inventoryEmpty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                )
            } else {
                // 2-column grid (manual using Column of Rows to keep KMP-clean)
                inventory.chunked(2).forEach { rowPair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CalmifySpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.sm),
                    ) {
                        rowPair.forEach { item ->
                            BioInventoryCell(
                                item = item,
                                window = selectedWindow,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowPair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SentCard(
    aggregatesPending: Int,
    lastSyncMillis: Long?,
) {
    // For 9.1.1 the card is narrative + footer only. Phase 9.3 adds the
    // expandable per-aggregate timeline once we have a local audit log.
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = cs.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            // Narrative row-top eyebrow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ShareLocation,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(Strings.BioContext.sentEyebrow),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 0.8.sp,
                    ),
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(CalmifySpacing.md))
            // Narrative sentence
            val lastSyncText = formatLastSync(lastSyncMillis)
            Text(
                text = stringResource(
                    Strings.BioContext.sentNarrative,
                    aggregatesPending,
                    lastSyncText,
                ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    letterSpacing = 0.15.sp,
                ),
                color = cs.onSurface,
            )
            Spacer(Modifier.height(CalmifySpacing.lg))
            // Footer line: privacy reassurance
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(Strings.BioContext.sentFooter),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfidencePerSourceCard(
    inventory: List<TypeInventory>,
    sources: List<BioContextContract.ConnectedSource>,
) {
    val cs = MaterialTheme.colorScheme
    // Derive: for each source, group inventory types and average confidence
    // (we use the inventory's per-type confidence inference as a proxy).
    val rows = remember(inventory, sources) {
        if (sources.isEmpty() || inventory.all { it.sampleCount == 0 }) emptyList()
        else sources.mapNotNull { source ->
            val typesForThisSource = inventory.filter { inv ->
                inv.uniqueSourceDevices.any { it.equals(source.deviceName, ignoreCase = true) }
            }
            if (typesForThisSource.isEmpty()) return@mapNotNull null
            val confidence = inferAggregateConfidence(typesForThisSource)
            Triple(source.deviceName.ifBlank { "Unknown" }, typesForThisSource.map { it.type }, confidence)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = cs.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            Text(
                text = stringResource(Strings.BioContext.confidenceTitle),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                ),
                color = cs.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Strings.BioContext.confidenceSub),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(CalmifySpacing.md))
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(Strings.BioContext.confidenceEmpty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                )
            } else {
                rows.forEachIndexed { idx, (sourceName, types, confidence) ->
                    if (idx > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.3f))
                    // Resolve type labels in @Composable scope BEFORE the join (stringResource
                    // can't be called inside a non-@Composable lambda like joinToString { ... }).
                    val typeLabels = types.map { stringResource(typeLabel(it)) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sourceName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                ),
                                color = cs.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = typeLabels.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                ),
                                color = cs.onSurfaceVariant,
                            )
                        }
                        BioConfidenceBars(level = confidence)
                    }
                }
            }
        }
    }
}

@Composable
private fun RightsCard(
    onExport: () -> Unit,
    onDisconnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = cs.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            Text(
                text = stringResource(Strings.BioContext.rightsTitle),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                ),
                color = cs.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Strings.BioContext.rightsSub),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(CalmifySpacing.lg))
            BioSecondaryButton(
                label = stringResource(Strings.BioContext.actionExportArt20),
                icon = Icons.Outlined.Download,
                onClick = onExport,
            )
            Spacer(Modifier.height(10.dp))
            BioSecondaryButton(
                label = stringResource(Strings.BioContext.actionDisconnect),
                icon = Icons.Outlined.LinkOff,
                onClick = onDisconnect,
            )
            Spacer(Modifier.height(10.dp))
            BioDangerButton(
                label = stringResource(Strings.BioContext.actionDeleteArt17),
                icon = Icons.Outlined.DeleteForever,
                onClick = onDelete,
            )
            Spacer(Modifier.height(CalmifySpacing.md))
            Text(
                text = stringResource(Strings.BioContext.rightsFineprint),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PromisesCard() {
    val cs = MaterialTheme.colorScheme
    val items = listOf(
        Triple(Icons.Outlined.Block, Strings.BioContext.promise1Strong, Strings.BioContext.promise1Rest),
        Triple(Icons.Outlined.Campaign, Strings.BioContext.promise2Strong, Strings.BioContext.promise2Rest),
        Triple(Icons.Outlined.LinkOff, Strings.BioContext.promise3Strong, Strings.BioContext.promise3Rest),
        Triple(Icons.Outlined.Psychology, Strings.BioContext.promise4Strong, Strings.BioContext.promise4Rest),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = cs.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            Text(
                text = stringResource(Strings.BioContext.promisesTitle),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                ),
                color = cs.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Strings.BioContext.promisesSub),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(CalmifySpacing.md))
            items.forEachIndexed { idx, (icon, strongRes, restRes) ->
                if (idx > 0) Spacer(Modifier.height(10.dp))
                val strongText = stringResource(strongRes)
                val restText = stringResource(restRes)
                // Single AnnotatedString — keeps "bold prefix + rest" as ONE flowing
                // sentence so it wraps on word boundaries (the old two-Text Row
                // wrapped character-by-character on the right edge — screenshot bug).
                val sentence = remember(strongText, restText) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(strongText)
                        }
                        append(' ')
                        append(restText)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CalmifyRadius.lg))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(cs.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = sentence,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        ),
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(Strings.BioContext.promisesFineprint),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// ATOMS
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionEyebrow(num: Int, label: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(top = 32.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = num.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                color = cs.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 1.2.sp,
            ),
            color = cs.onSurfaceVariant,
        )
    }
}

private enum class StatusKind { OK, WARN, MUTED }

@Composable
private fun BioStatusDot(kind: StatusKind, label: String) {
    val cs = MaterialTheme.colorScheme
    val (dotColor, textColor) = when (kind) {
        StatusKind.OK -> cs.primary to cs.primary
        StatusKind.WARN -> Color(0xFFE0A93D) to Color(0xFFE0A93D)
        StatusKind.MUTED -> cs.onSurfaceVariant.copy(alpha = 0.55f) to cs.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (kind == StatusKind.WARN) {
            // Hollow dot for warn — shape signals state without relying on hue
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .border(2.dp, dotColor, CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            color = textColor,
        )
    }
}

@Composable
private fun BioConfidenceBars(level: ConfidenceLevel) {
    val cs = MaterialTheme.colorScheme
    val (filledCount, fillColor, labelColor, labelRes) = when (level) {
        ConfidenceLevel.HIGH -> Quadruple(3, cs.primary, cs.primary, Strings.BioConfidence.levelHigh)
        ConfidenceLevel.MEDIUM -> Quadruple(2, cs.primary, cs.onSurface, Strings.BioConfidence.levelMedium)
        ConfidenceLevel.LOW -> Quadruple(1, Color(0xFFE0A93D), Color(0xFFE0A93D), Strings.BioConfidence.levelLow)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { idx ->
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (idx < filledCount) fillColor
                            else cs.onSurfaceVariant.copy(alpha = 0.25f),
                        ),
                )
            }
        }
        Text(
            text = stringResource(labelRes).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.8.sp,
            ),
            color = labelColor,
        )
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Quadruple<A, B, C, D>.component4() = d

@Composable
private fun BioWindowTabs(
    selected: InventoryWindow,
    onSelect: (InventoryWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(cs.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        InventoryWindow.entries.forEach { window ->
            val isOn = window == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isOn) cs.background else Color.Transparent)
                    .clickable { onSelect(window) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(windowLabel(window)),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    color = if (isOn) cs.onSurface else cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BioInventoryCell(
    item: TypeInventory,
    window: InventoryWindow,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val confidence = remember(item) { inferConfidenceForType(item) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CalmifyRadius.lg))
            .background(cs.surfaceContainerHigh)
            .padding(14.dp)
            .heightIn(min = 116.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(typeLabel(item.type)).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 0.8.sp,
                ),
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
            Icon(
                imageVector = typeIcon(item.type),
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(
                text = item.sampleCount.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    letterSpacing = (-0.2).sp,
                    fontFamily = FontFamily.Monospace,
                ),
                color = cs.onSurface,
            )
            Text(
                text = stringResource(typeUnit(item.type)) +
                    " · " + stringResource(windowSubLabel(window)),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        BioConfidenceBars(level = confidence)
    }
}

@Composable
private fun BioSecondaryButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.xl))
            .background(cs.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = cs.onSurface, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun BioDangerButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dangerColor = cs.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.xl))
            .border(1.dp, dangerColor, RoundedCornerShape(CalmifyRadius.xl))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = dangerColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = dangerColor,
        )
    }
}

@Composable
private fun BioPrimaryButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(CalmifyRadius.xl))
            .background(cs.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = cs.onPrimary,
        )
    }
}

@Composable
private fun BioStickyActionBar(onExport: () -> Unit, onHow: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // weight(1f) on each so they split 50/50. The atoms internally
            // call fillMaxWidth() which made the primary collapse the
            // secondary entirely before — sticky-bar bug from screenshot.
            Box(modifier = Modifier.weight(1f)) {
                BioSecondaryButton(
                    label = stringResource(Strings.BioContext.actionExportShort),
                    icon = Icons.Outlined.Download,
                    onClick = onExport,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                BioPrimaryButton(
                    label = stringResource(Strings.BioContext.actionHowShort),
                    icon = Icons.Outlined.Help,
                    onClick = onHow,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BioDeleteSheet(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var phrase by remember { mutableStateOf("") }
    val confirmWord = stringResource(Strings.BioContext.deleteConfirmWord)
    val canConfirm = phrase.trim().equals(confirmWord, ignoreCase = true)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = cs.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = stringResource(Strings.BioContext.deleteSheetTitle),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                ),
                color = cs.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Strings.BioContext.deleteSheetBody),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.15.sp,
                ),
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CalmifyRadius.md))
                    .background(cs.background)
                    .padding(14.dp),
            ) {
                Text(
                    text = stringResource(Strings.BioContext.deleteSheetPrompt, confirmWord),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onSurface,
                    ),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CalmifyRadius.md))
                        .border(1.dp, cs.outlineVariant, RoundedCornerShape(CalmifyRadius.md))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    BioSecondaryButton(
                        label = stringResource(Strings.Action.cancel),
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onCancel,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    BioDangerButton(
                        label = stringResource(Strings.BioContext.deleteSheetConfirmCta),
                        icon = Icons.Outlined.DeleteForever,
                        onClick = { if (canConfirm) onConfirm() },
                    )
                }
            }
            if (!canConfirm && phrase.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Strings.BioContext.deleteSheetMismatch, confirmWord),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    color = cs.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BioHowSheet(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = cs.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = stringResource(Strings.BioContext.howSheetTitle),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                ),
                color = cs.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Strings.BioContext.howSheetBody1),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                ),
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Strings.BioContext.howSheetBody2),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                ),
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            BioPrimaryButton(
                label = stringResource(Strings.BioContext.howSheetCta),
                icon = Icons.Outlined.Help,
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// HELPERS
// ═════════════════════════════════════════════════════════════════════════

private fun typeLabel(type: BioSignalDataType): StringResource = when (type) {
    BioSignalDataType.HEART_RATE -> Strings.BioContext.typeHeartRate
    BioSignalDataType.HRV -> Strings.BioContext.typeHrv
    BioSignalDataType.SLEEP -> Strings.BioContext.typeSleep
    BioSignalDataType.STEPS -> Strings.BioContext.typeSteps
    BioSignalDataType.RESTING_HEART_RATE -> Strings.BioContext.typeRestingHeartRate
    BioSignalDataType.OXYGEN_SATURATION -> Strings.BioContext.typeOxygenSaturation
    BioSignalDataType.ACTIVITY -> Strings.BioContext.typeActivity
}

private fun typeUnit(type: BioSignalDataType): StringResource = when (type) {
    BioSignalDataType.SLEEP -> Strings.BioContext.unitSessions
    BioSignalDataType.ACTIVITY -> Strings.BioContext.unitSessions
    BioSignalDataType.STEPS -> Strings.BioContext.unitDays
    else -> Strings.BioContext.unitSamples
}

private fun typeIcon(type: BioSignalDataType): ImageVector = when (type) {
    BioSignalDataType.HEART_RATE -> Icons.Outlined.Favorite
    BioSignalDataType.HRV -> Icons.Outlined.GraphicEq
    BioSignalDataType.SLEEP -> Icons.Outlined.Bedtime
    BioSignalDataType.STEPS -> Icons.Outlined.DirectionsWalk
    BioSignalDataType.RESTING_HEART_RATE -> Icons.Outlined.MonitorHeart
    BioSignalDataType.OXYGEN_SATURATION -> Icons.Outlined.Air
    BioSignalDataType.ACTIVITY -> Icons.Outlined.DirectionsRun
}

private fun windowLabel(window: InventoryWindow): StringResource = when (window) {
    InventoryWindow.SEVEN_DAYS -> Strings.BioContext.windowSevenDays
    InventoryWindow.THIRTY_DAYS -> Strings.BioContext.windowThirtyDays
    InventoryWindow.ALL_TIME -> Strings.BioContext.windowAllTime
}

private fun windowSubLabel(window: InventoryWindow): StringResource = when (window) {
    InventoryWindow.SEVEN_DAYS -> Strings.BioContext.windowSevenDaysSub
    InventoryWindow.THIRTY_DAYS -> Strings.BioContext.windowThirtyDaysSub
    InventoryWindow.ALL_TIME -> Strings.BioContext.windowAllTimeSub
}

/** Infer per-type confidence from sample density (a proxy until we surface per-sample confidence). */
private fun inferConfidenceForType(item: TypeInventory): ConfidenceLevel {
    if (item.sampleCount == 0) return ConfidenceLevel.LOW
    return when (item.type) {
        BioSignalDataType.HRV -> when {
            item.sampleCount >= 50 -> ConfidenceLevel.MEDIUM
            item.sampleCount >= 14 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
        BioSignalDataType.HEART_RATE -> when {
            item.sampleCount >= 500 -> ConfidenceLevel.HIGH
            item.sampleCount >= 100 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
        BioSignalDataType.SLEEP, BioSignalDataType.STEPS, BioSignalDataType.RESTING_HEART_RATE -> when {
            item.sampleCount >= 14 -> ConfidenceLevel.HIGH
            item.sampleCount >= 5 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
        else -> ConfidenceLevel.MEDIUM
    }
}

private fun inferAggregateConfidence(items: List<TypeInventory>): ConfidenceLevel {
    val avg = items.map { inferConfidenceForType(it) }
    val score: Int = avg.fold(0) { acc, lvl ->
        acc + when (lvl) { ConfidenceLevel.LOW -> 1; ConfidenceLevel.MEDIUM -> 2; ConfidenceLevel.HIGH -> 3 }
    }
    val mean = score.toDouble() / avg.size
    return when {
        mean >= 2.34 -> ConfidenceLevel.HIGH
        mean >= 1.67 -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.LOW
    }
}

private fun formatLastSync(millis: Long?): String {
    if (millis == null) return "—"
    val ageMillis = System.currentTimeMillis() - millis
    val ageMinutes = ageMillis / 60_000L
    return when {
        ageMinutes < 1 -> "just now"
        ageMinutes < 60 -> "$ageMinutes min ago"
        ageMinutes < 60 * 24 -> "${ageMinutes / 60}h ago"
        else -> "${ageMinutes / (60 * 24)}d ago"
    }
}
