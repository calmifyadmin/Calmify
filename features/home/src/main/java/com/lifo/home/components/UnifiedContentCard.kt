package com.lifo.home.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifo.ui.theme.Elevation
import com.lifo.ui.components.Gallery
import com.lifo.ui.util.fetchImagesFromFirebase
import com.lifo.ui.providers.MoodUiProvider
import com.lifo.util.model.HomeContentItem
import com.lifo.util.model.Mood
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Unified card component that renders both Diary and Chat content items
 * with Material3 design and proper accessibility
 */
@Composable
fun UnifiedContentCard(
    item: HomeContentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localDensity = LocalDensity.current
    var componentHeight by remember { mutableStateOf(0.dp) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Spacer(modifier = Modifier.width(14.dp))
        Surface(
            modifier = Modifier
                .width(2.dp)
                .height(componentHeight + 14.dp),
            tonalElevation = Elevation.Level1
        ) {}
        Spacer(modifier = Modifier.width(20.dp))
        Surface(
            modifier = Modifier
                .clip(shape = Shapes().medium)
                .onGloballyPositioned {
                    componentHeight = with(localDensity) { it.size.height.toDp() }
                },
            tonalElevation = Elevation.Level1
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                UnifiedClassicHeader(item = item)
                
                when (item) {
                    is HomeContentItem.DiaryItem -> {
                        DiaryContentPreview(item = item)
                    }
                    is HomeContentItem.ChatItem -> {
                        ChatContentPreview(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedClassicHeader(
    item: HomeContentItem,
    modifier: Modifier = Modifier
) {
    val headerColor = when (item) {
        is HomeContentItem.DiaryItem -> MoodUiProvider.getContainerColor(item.mood)
        is HomeContentItem.ChatItem -> {
            if (item.isLiveMode) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        }
    }
    
    val contentColor = when (item) {
        is HomeContentItem.DiaryItem -> MoodUiProvider.getContentColor(item.mood)
        is HomeContentItem.ChatItem -> {
            if (item.isLiveMode) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
        }
    }
    
    val formatter = remember {
        DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(headerColor)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (item) {
                is HomeContentItem.DiaryItem -> {
                    Image(
                        modifier = Modifier.size(18.dp),
                        painter = painterResource(id = MoodUiProvider.getIcon(item.mood)),
                        contentDescription = "Mood Icon"
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = item.mood.name,
                        color = contentColor,
                        style = TextStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                    )
                }
                is HomeContentItem.ChatItem -> {
                    Icon(
                        imageVector = if (item.isLiveMode) Icons.Default.LiveTv else Icons.Default.Chat,
                        contentDescription = if (item.isLiveMode) "Live Chat Icon" else "Chat Icon",
                        modifier = Modifier.size(18.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = if (item.isLiveMode) "Live Chat" else "Chat",
                        color = contentColor,
                        style = TextStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                    )
                }
            }
        }
        Text(
            text = formatter.format(Instant.ofEpochMilli(item.createdAt)),
            color = contentColor,
            style = TextStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
        )
    }
}


@Composable
private fun ShowGalleryButton(
    galleryOpened: Boolean,
    galleryLoading: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(
            text = if (galleryOpened)
                if (galleryLoading) "Loading" else "Hide Gallery"
            else "Show Gallery",
            style = TextStyle(fontSize = MaterialTheme.typography.bodySmall.fontSize)
        )
    }
}

@Composable
private fun DiaryContentPreview(
    item: HomeContentItem.DiaryItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var galleryOpened by remember { mutableStateOf(false) }
    var galleryLoading by remember { mutableStateOf(false) }
    val downloadedImages = remember { mutableStateListOf<String>() }
    
    LaunchedEffect(key1 = galleryOpened) {
        if (galleryOpened && downloadedImages.isEmpty()) {
            galleryLoading = true
            fetchImagesFromFirebase(
                remoteImagePaths = item.images,
                onImageDownload = { image ->
                    downloadedImages.add(image)
                },
                onImageDownloadFailed = {
                    Toast.makeText(
                        context,
                        "Images not uploaded yet. Wait a little bit, or try uploading again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    galleryLoading = false
                    galleryOpened = false
                }
            ) {
                galleryLoading = false
                galleryOpened = true
            }
        }
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Content preview
        Text(
            modifier = Modifier.padding(all = 14.dp),
            text = item.content,
            style = TextStyle(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        
        // Show Gallery Button
        if (item.images.isNotEmpty()) {
            ShowGalleryButton(
                galleryOpened = galleryOpened,
                galleryLoading = galleryLoading
            ) {
                galleryOpened = !galleryOpened
            }
        }
        
        // Animated Gallery
        AnimatedVisibility(
            visible = galleryOpened && !galleryLoading,
            enter = fadeIn() + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        ) {
            Column(modifier = Modifier.padding(all = 14.dp)) {
                Gallery(images = downloadedImages)
            }
        }
    }
}

@Composable
private fun ChatContentPreview(
    item: HomeContentItem.ChatItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Title (for chat)
        if (item.title.isNotBlank()) {
            Text(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
                text = item.title,
                style = TextStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Content preview
        val previewText = item.summary?.takeIf { it.isNotBlank() } 
            ?: item.lastMessage?.takeIf { it.isNotBlank() }
            ?: "No preview available"
            
        Text(
            modifier = Modifier.padding(all = 14.dp),
            text = previewText,
            style = TextStyle(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        
        // Chat stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${item.messageCount} messages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = item.aiModel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}



private fun formatDate(epochMillis: Long): String {
    val instant = Instant.ofEpochMilli(epochMillis)
    val localDateTime = instant.atZone(ZoneId.systemDefault())
    
    return when {
        localDateTime.toLocalDate() == java.time.LocalDate.now() -> {
            DateTimeFormatter.ofPattern("HH:mm").format(localDateTime)
        }
        localDateTime.toLocalDate().year == java.time.LocalDate.now().year -> {
            DateTimeFormatter.ofPattern("MMM dd").format(localDateTime)
        }
        else -> {
            DateTimeFormatter.ofPattern("MMM dd, yyyy").format(localDateTime)
        }
    }
}