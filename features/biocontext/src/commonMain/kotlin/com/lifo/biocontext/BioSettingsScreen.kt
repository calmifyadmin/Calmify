package com.lifo.biocontext

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.repository.ProviderStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bio-Signal master Settings panel — Phase 9.1.3 (2026-05-17).
 *
 * 1:1 with `design/biosignal/Calmify Bio Settings.html`. Six sections:
 *
 *   1. Master toggle           — Bio-signal integration on/off + connected status + Sync now
 *   2. Permissions per type    — 7 toggle rows + per-type Delete-collected-data link
 *   3. Connected sources       — source cards + Add another source CTA
 *   4. Privacy & data          — nav-rows to BioContext / Export / Delete-all + cloud-upload tradeoff
 *   5. Surfaces                — 4 surface toggles (Journal banner, Meditation outro, Home Today, Insight)
 *   6. Subscription reminder   — PRO upgrade card with 3 perks
 *
 * Per dogma #3 (helpful, not optimizing): every toggle has a "why" line +
 * everything can be turned off without losing app functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BioSettingsScreen(
    state: BioSettingsContract.State,
    onIntent: (BioSettingsContract.Intent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var showDeleteSheet by remember { mutableStateOf(false) }
    var showTradeoffDetail by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Strings.BioSettings.topbar),
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
                    IconButton(onClick = { onIntent(BioSettingsContract.Intent.OpenBioContext) }) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = stringResource(Strings.BioSettings.helpA11y),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
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

            // 1. Master toggle
            MasterCard(
                masterEnabled = state.masterEnabled,
                isConnected = state.providerStatus is ProviderStatus.Ready,
                primarySource = state.connectedSources.firstOrNull(),
                lastSyncMillis = state.lastSyncMillis,
                samplesToday = state.samplesToday,
                isIngesting = state.isIngesting,
                onMasterToggle = { onIntent(BioSettingsContract.Intent.SetMasterEnabled(it)) },
                onSyncNow = { onIntent(BioSettingsContract.Intent.SyncNow) },
                onReactivate = { onIntent(BioSettingsContract.Intent.SetMasterEnabled(true)) },
            )

            // Sections 2-6 dim when master is off
            val collapsibleAlpha = if (state.masterEnabled) 1f else 0.4f
            Box(modifier = Modifier.alpha(collapsibleAlpha)) {
                Column {
                    // 2. Permissions per type
                    SectionEyebrow(label = stringResource(Strings.BioSettings.sectionPermissions))
                    SectionHelp(stringResource(Strings.BioSettings.permissionsHelp))
                    Spacer(Modifier.height(CalmifySpacing.md))
                    BioSignalDataType.entries.forEach { type ->
                        if (type != BioSignalDataType.entries.first()) Spacer(Modifier.height(8.dp))
                        PermRow(
                            type = type,
                            enabled = type in state.enabledTypes && state.masterEnabled,
                            onToggle = { onIntent(BioSettingsContract.Intent.SetTypeEnabled(type, it)) },
                            onDelete = { onIntent(BioSettingsContract.Intent.DeleteTypeData(type)) },
                        )
                    }

                    // 3. Connected sources
                    SectionEyebrow(label = stringResource(Strings.BioSettings.sectionSources))
                    SectionHelp(stringResource(Strings.BioSettings.sourcesHelp))
                    Spacer(Modifier.height(CalmifySpacing.md))
                    state.connectedSources.forEachIndexed { idx, source ->
                        if (idx > 0) Spacer(Modifier.height(8.dp))
                        SourceCard(source = source)
                    }
                    if (state.connectedSources.isNotEmpty()) Spacer(Modifier.height(10.dp))
                    AddSourceButton(onClick = { onIntent(BioSettingsContract.Intent.OpenAddSource) })

                    // 4. Privacy & data
                    SectionEyebrow(label = stringResource(Strings.BioSettings.sectionPrivacy))
                    SectionHelp(stringResource(Strings.BioSettings.privacyHelp))
                    Spacer(Modifier.height(CalmifySpacing.md))
                    NavRow(
                        icon = Icons.Outlined.Visibility,
                        title = stringResource(Strings.BioSettings.navInventoryTitle),
                        sub = stringResource(Strings.BioSettings.navInventorySub),
                        onClick = { onIntent(BioSettingsContract.Intent.OpenBioContext) },
                    )
                    Spacer(Modifier.height(8.dp))
                    NavRow(
                        icon = Icons.Outlined.Download,
                        title = stringResource(Strings.BioSettings.navExportTitle),
                        sub = stringResource(Strings.BioSettings.navExportSub),
                        onClick = { onIntent(BioSettingsContract.Intent.ExportAll) },
                    )
                    Spacer(Modifier.height(8.dp))
                    NavRow(
                        icon = Icons.Outlined.DeleteForever,
                        title = stringResource(Strings.BioSettings.navDeleteTitle),
                        sub = stringResource(Strings.BioSettings.navDeleteSub),
                        destructive = true,
                        onClick = { showDeleteSheet = true },
                    )

                    // Tradeoff card: cloud upload (PRO-only feature)
                    Spacer(Modifier.height(12.dp))
                    TradeoffCard(
                        cloudUploadEnabled = state.cloudUploadEnabled,
                        detailExpanded = showTradeoffDetail,
                        onToggleCloud = { onIntent(BioSettingsContract.Intent.SetCloudUpload(it)) },
                        onToggleDetail = { showTradeoffDetail = !showTradeoffDetail },
                    )

                    // 5. Surface toggles
                    SectionEyebrow(label = stringResource(Strings.BioSettings.sectionSurfaces))
                    SectionHelp(stringResource(Strings.BioSettings.surfacesHelp))
                    Spacer(Modifier.height(CalmifySpacing.md))
                    BioSettingsContract.BioSurface.entries.forEachIndexed { idx, surface ->
                        if (idx > 0) Spacer(Modifier.height(8.dp))
                        SurfaceRow(
                            surface = surface,
                            enabled = surface in state.enabledSurfaces,
                            onToggle = { onIntent(BioSettingsContract.Intent.SetSurfaceEnabled(surface, it)) },
                        )
                    }

                    // 6. PRO card
                    Spacer(Modifier.height(CalmifySpacing.xl))
                    ProCard(
                        isPro = state.isPro,
                        onCta = { onIntent(BioSettingsContract.Intent.OpenSubscription) },
                    )
                }
            }

            // Footnote
            Spacer(Modifier.height(CalmifySpacing.xl))
            Text(
                text = stringResource(Strings.BioSettings.footnote),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = cs.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CalmifySpacing.xxl))
        }
    }

    if (showDeleteSheet) {
        // Reuses the same pattern as BioContextScreen's delete sheet
        // (type "delete" / "elimina" to confirm). Composable copy here so the
        // sheet's lifecycle is screen-local.
        com.lifo.biocontext.BioDeleteConfirmSheet(
            onCancel = { showDeleteSheet = false },
            onConfirm = {
                showDeleteSheet = false
                onIntent(BioSettingsContract.Intent.ConfirmDeleteAll)
            },
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════
// 1. Master toggle card
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun MasterCard(
    masterEnabled: Boolean,
    isConnected: Boolean,
    primarySource: BioContextContract.ConnectedSource?,
    lastSyncMillis: Long?,
    samplesToday: Int,
    isIngesting: Boolean,
    onMasterToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onReactivate: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = if (masterEnabled) cs.surfaceContainerLow else cs.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            // Top row: leaf icon + title + master switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(cs.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Favorite,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Strings.BioSettings.masterTitle),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                        ),
                        color = cs.onSurface,
                    )
                    Text(
                        text = stringResource(Strings.BioSettings.masterSub),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = cs.onSurfaceVariant,
                    )
                }
                Switch(checked = masterEnabled, onCheckedChange = onMasterToggle)
            }

            if (masterEnabled) {
                // Status block (connected · device · last sync · samples today)
                Spacer(Modifier.height(CalmifySpacing.lg))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CalmifyRadius.md))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.5f)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        val statusLine = if (isConnected && primarySource != null) {
                            stringResource(
                                Strings.BioSettings.statusConnected,
                                primarySource.deviceName,
                                primarySource.appName.ifBlank { "" },
                            )
                        } else if (isConnected) {
                            stringResource(Strings.BioSettings.statusConnectedNoSource)
                        } else {
                            stringResource(Strings.BioSettings.statusNotConnected)
                        }
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                            ),
                            color = cs.onSurface,
                        )
                        val ageText = formatRelativeMinutes(lastSyncMillis)
                        Text(
                            text = stringResource(
                                Strings.BioSettings.statusMeta,
                                ageText,
                                samplesToday,
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = cs.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onSyncNow,
                        enabled = !isIngesting,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = stringResource(Strings.BioContext.actionIngest),
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                // Reactivate prompt (when master is off)
                Spacer(Modifier.height(CalmifySpacing.lg))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CalmifyRadius.md))
                        .background(cs.surfaceContainerHighest)
                        .padding(14.dp),
                ) {
                    Text(
                        text = stringResource(Strings.BioSettings.reactivateBody),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(CalmifyRadius.pill))
                            .background(cs.primary)
                            .clickable(onClick = onReactivate)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            tint = cs.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(Strings.BioSettings.reactivateCta),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            ),
                            color = cs.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// Section helpers
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionEyebrow(label: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.2.sp,
        ),
        color = cs.onSurfaceVariant,
        modifier = Modifier.padding(top = 32.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
    )
}

@Composable
private fun SectionHelp(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

// ═════════════════════════════════════════════════════════════════════════
// 2. Permission row (per-type)
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun PermRow(
    type: BioSignalDataType,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg),
        color = if (enabled) cs.surfaceContainerLow else cs.surfaceContainerHigh.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(CalmifyRadius.md))
                    .background(
                        if (enabled) cs.primary.copy(alpha = 0.12f)
                        else cs.surfaceContainerHighest,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = typeIconLocal(type),
                    contentDescription = null,
                    tint = if (enabled) cs.primary else cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(typeLabelLocal(type)),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = cs.onSurface,
                )
                Text(
                    text = stringResource(typePermWhy(type)),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CalmifyRadius.pill))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(Strings.BioSettings.deleteCollected),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// 3. Connected source card + Add source CTA
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun SourceCard(source: BioContextContract.ConnectedSource) {
    val cs = MaterialTheme.colorScheme
    val ageText = formatRelativeMinutes(source.lastSeenMillis)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg),
        color = cs.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
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
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(cs.primary)
                        .align(Alignment.BottomEnd),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.deviceName.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    color = cs.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (source.appName.isNotBlank()) {
                            stringResource(Strings.BioContext.sourceViaApp, source.appName) + " · "
                        } else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = cs.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Strings.BioContext.lastSync, ageText),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = cs.primary,
                    )
                }
            }
            IconButton(onClick = { /* per-source options menu in 9.3 */ }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(Strings.BioSettings.sourceOptionsA11y),
                    tint = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddSourceButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg),
        color = cs.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Strings.BioSettings.addSourceTitle),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = cs.onSurface,
                )
                Text(
                    text = stringResource(Strings.BioSettings.addSourceSub),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = cs.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// 4. Nav row + cloud-upload tradeoff
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    sub: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tint = if (destructive) cs.error else cs.onSurfaceVariant
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg),
        color = cs.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (destructive) cs.error.copy(alpha = 0.12f)
                        else cs.surfaceContainerHigh,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = if (destructive) cs.error else cs.onSurface,
                )
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = cs.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TradeoffCard(
    cloudUploadEnabled: Boolean,
    detailExpanded: Boolean,
    onToggleCloud: (Boolean) -> Unit,
    onToggleDetail: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = cs.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(cs.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudUpload,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Strings.BioSettings.tradeoffTitle),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                        ),
                        color = cs.onSurface,
                    )
                    Text(
                        text = stringResource(Strings.BioSettings.tradeoffSub),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = cs.onSurfaceVariant,
                    )
                }
                Switch(checked = cloudUploadEnabled, onCheckedChange = onToggleCloud)
            }
            Spacer(Modifier.height(CalmifySpacing.md))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(CalmifyRadius.pill))
                    .clickable(onClick = onToggleDetail)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Strings.BioSettings.tradeoffExpand),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                    ),
                    color = cs.primary,
                )
                Icon(
                    imageVector = if (detailExpanded) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (detailExpanded) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Strings.BioSettings.tradeoffBody),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    ),
                    color = cs.onSurface,
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// 5. Surface row (per-card on/off)
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun SurfaceRow(
    surface: BioSettingsContract.BioSurface,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (icon, titleRes, subRes) = surfaceDescriptor(surface)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg),
        color = if (enabled) cs.surfaceContainerLow else cs.surfaceContainerHigh.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(CalmifyRadius.md))
                    .background(
                        if (enabled) cs.primary.copy(alpha = 0.12f)
                        else cs.surfaceContainerHighest,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) cs.primary else cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = cs.onSurface,
                )
                Text(
                    text = stringResource(subRes),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = cs.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// 6. PRO upgrade card
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun ProCard(isPro: Boolean, onCta: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.xxl),
        color = cs.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CalmifyRadius.pill))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(if (isPro) Strings.BioSettings.tierPro else Strings.BioSettings.tierFree),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                        ),
                        color = cs.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CalmifyRadius.pill))
                        .background(cs.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(Strings.BioProLock.proChip),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                        ),
                        color = cs.primary,
                    )
                }
            }
            Spacer(Modifier.height(CalmifySpacing.md))
            Text(
                text = stringResource(Strings.BioSettings.proTitle),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                ),
                color = cs.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Strings.BioSettings.proDesc),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(CalmifySpacing.md))
            listOf(
                Strings.BioSettings.proPerk1,
                Strings.BioSettings.proPerk2,
                Strings.BioSettings.proPerk3,
            ).forEachIndexed { idx, res ->
                if (idx > 0) Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                        color = cs.onSurface,
                    )
                }
            }
            if (!isPro) {
                Spacer(Modifier.height(CalmifySpacing.md))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(CalmifyRadius.pill))
                        .background(cs.primary)
                        .clickable(onClick = onCta)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(Strings.BioSettings.proCta),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        ),
                        color = cs.onPrimary,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = cs.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// Helpers
// ═════════════════════════════════════════════════════════════════════════

private fun surfaceDescriptor(surface: BioSettingsContract.BioSurface): Triple<ImageVector, StringResource, StringResource> =
    when (surface) {
        BioSettingsContract.BioSurface.JOURNAL_BANNER -> Triple(
            Icons.Outlined.EditNote,
            Strings.BioSettings.surfaceJournalTitle,
            Strings.BioSettings.surfaceJournalSub,
        )
        BioSettingsContract.BioSurface.MEDITATION_OUTRO -> Triple(
            Icons.Outlined.SelfImprovement,
            Strings.BioSettings.surfaceMeditationTitle,
            Strings.BioSettings.surfaceMeditationSub,
        )
        BioSettingsContract.BioSurface.HOME_TODAY -> Triple(
            Icons.Outlined.Home,
            Strings.BioSettings.surfaceHomeTitle,
            Strings.BioSettings.surfaceHomeSub,
        )
        BioSettingsContract.BioSurface.INSIGHT_PATTERN -> Triple(
            Icons.Outlined.AutoAwesome,
            Strings.BioSettings.surfaceInsightTitle,
            Strings.BioSettings.surfaceInsightSub,
        )
    }

private fun typeIconLocal(type: BioSignalDataType): ImageVector = when (type) {
    BioSignalDataType.HEART_RATE -> Icons.Outlined.Favorite
    BioSignalDataType.HRV -> Icons.Outlined.AutoAwesome
    BioSignalDataType.SLEEP -> Icons.Outlined.Bedtime
    BioSignalDataType.STEPS -> Icons.Outlined.Home  // placeholder — same family
    BioSignalDataType.RESTING_HEART_RATE -> Icons.Outlined.Favorite
    BioSignalDataType.OXYGEN_SATURATION -> Icons.Outlined.CloudUpload
    BioSignalDataType.ACTIVITY -> Icons.Outlined.SelfImprovement
}

private fun typeLabelLocal(type: BioSignalDataType): StringResource = when (type) {
    BioSignalDataType.HEART_RATE -> Strings.BioContext.typeHeartRate
    BioSignalDataType.HRV -> Strings.BioContext.typeHrv
    BioSignalDataType.SLEEP -> Strings.BioContext.typeSleep
    BioSignalDataType.STEPS -> Strings.BioContext.typeSteps
    BioSignalDataType.RESTING_HEART_RATE -> Strings.BioContext.typeRestingHeartRate
    BioSignalDataType.OXYGEN_SATURATION -> Strings.BioContext.typeOxygenSaturation
    BioSignalDataType.ACTIVITY -> Strings.BioContext.typeActivity
}

private fun typePermWhy(type: BioSignalDataType): StringResource = when (type) {
    BioSignalDataType.HEART_RATE -> Strings.BioSettings.permWhyHr
    BioSignalDataType.HRV -> Strings.BioSettings.permWhyHrv
    BioSignalDataType.SLEEP -> Strings.BioSettings.permWhySleep
    BioSignalDataType.STEPS -> Strings.BioSettings.permWhySteps
    BioSignalDataType.RESTING_HEART_RATE -> Strings.BioSettings.permWhyRestingHr
    BioSignalDataType.OXYGEN_SATURATION -> Strings.BioSettings.permWhySpo2
    BioSignalDataType.ACTIVITY -> Strings.BioSettings.permWhyActivity
}

private fun formatRelativeMinutes(millis: Long?): String {
    if (millis == null) return "—"
    val ageMins = ((System.currentTimeMillis() - millis) / 60_000L).toInt().coerceAtLeast(0)
    return when {
        ageMins < 1 -> "just now"
        ageMins < 60 -> "$ageMins min"
        ageMins < 60 * 24 -> "${ageMins / 60}h"
        else -> "${ageMins / (60 * 24)}d"
    }
}

// ═════════════════════════════════════════════════════════════════════════
// Delete confirmation sheet (shared pattern with BioContextScreen)
// ═════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BioDeleteConfirmSheet(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var phrase by remember { mutableStateOf("") }
    val confirmWord = stringResource(Strings.BioContext.deleteConfirmWord)
    val canConfirm = phrase.trim().equals(confirmWord, ignoreCase = true)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = cs.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
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
                androidx.compose.foundation.text.BasicTextField(
                    value = phrase,
                    onValueChange = { phrase = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onSurface,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CalmifyRadius.md))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CalmifyRadius.xl))
                    .background(if (canConfirm) cs.error.copy(alpha = 0.12f) else cs.surfaceContainerHigh)
                    .clickable(enabled = canConfirm, onClick = onConfirm)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Strings.BioContext.deleteSheetConfirmCta),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    ),
                    color = if (canConfirm) cs.error else cs.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}
