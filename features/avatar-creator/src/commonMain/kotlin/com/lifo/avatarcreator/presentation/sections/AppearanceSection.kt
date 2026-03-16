package com.lifo.avatarcreator.presentation.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.Intent
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.State
import com.lifo.avatarcreator.presentation.components.ColorChipPicker
import com.lifo.avatarcreator.presentation.components.ColorOption
import com.lifo.avatarcreator.presentation.components.SectionHeader
import com.lifo.avatarcreator.presentation.components.SliderWithLabel
import com.lifo.avatarcreator.presentation.components.TagPicker
import com.lifo.util.model.AvatarExtras
import com.lifo.util.model.BodyTypes
import com.lifo.util.model.HairColors
import com.lifo.util.model.HairStyles
import com.lifo.util.model.OutfitStyles
import com.lifo.util.model.SkinTones

private val HAIR_COLOR_OPTIONS = listOf(
    ColorOption("Nero", Color(0xFF1A1A1A)),
    ColorOption("Castano", Color(0xFF8B4513)),
    ColorOption("Biondo", Color(0xFFDAA520)),
    ColorOption("Rosso", Color(0xFFCC3333)),
    ColorOption("Bianco", Color.White),
    ColorOption("Grigio", Color(0xFF999999)),
    ColorOption("Argento", Color(0xFFC0C0C0)),
    ColorOption("Blu", Color(0xFF4169E1)),
    ColorOption("Rosa", Color(0xFFFF69B4)),
    ColorOption("Arancione", Color(0xFFFF8C00)),
    ColorOption("Viola", Color(0xFF8A2BE2)),
    ColorOption("Platino", Color(0xFFE5E4E2)),
)

private val SKIN_TONE_OPTIONS = listOf(
    ColorOption("Chiaro", Color(0xFFFDEBD0)),
    ColorOption("Medio", Color(0xFFD4A574)),
    ColorOption("Olivastro", Color(0xFFC4A882)),
    ColorOption("Scuro", Color(0xFF8B6914)),
    ColorOption("Ambrato", Color(0xFFC68C5B)),
)

@Composable
fun AppearanceSection(
    state: State,
    onIntent: (Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SectionHeader(
            title = "Aspetto Fisico",
            narrativeQuestion = "Come appare al mondo? Ogni dettaglio fisico racconta qualcosa di psicologico. L'aspetto e' il primo messaggio che manda.",
        )

        // Body Type
        TagPicker(
            label = "Corporatura",
            options = BodyTypes.all,
            selectedOptions = listOf(state.form.appearance.bodyType).filter { it.isNotBlank() },
            onToggle = { onIntent(Intent.UpdateBodyType(it)) },
            maxSelection = 1,
        )

        // Height
        SliderWithLabel(
            label = "Altezza",
            value = state.form.appearance.height,
            onValueChange = { onIntent(Intent.UpdateHeight(it)) },
            valueRange = 140f..210f,
            valueFormatter = { "${it.toInt()} cm" },
            startLabel = "140 cm",
            endLabel = "210 cm",
        )

        // Skin Tone — color swatches with names
        ColorChipPicker(
            label = "Tono della pelle",
            options = SKIN_TONE_OPTIONS,
            selectedOption = state.form.appearance.skinTone,
            onSelect = { onIntent(Intent.UpdateSkinTone(it)) },
        )

        // Hair Style
        TagPicker(
            label = "Stile capelli",
            options = HairStyles.all,
            selectedOptions = listOf(state.form.appearance.hairStyle).filter { it.isNotBlank() },
            onToggle = { onIntent(Intent.UpdateHairStyle(it)) },
            maxSelection = 1,
        )

        // Hair Color — color swatches with names
        ColorChipPicker(
            label = "Colore capelli",
            options = HAIR_COLOR_OPTIONS,
            selectedOption = state.form.appearance.hairColor,
            onSelect = { onIntent(Intent.UpdateHairColor(it)) },
        )

        // Outfit
        TagPicker(
            label = "Stile abbigliamento",
            options = OutfitStyles.all,
            selectedOptions = listOf(state.form.appearance.outfitType).filter { it.isNotBlank() },
            onToggle = { onIntent(Intent.UpdateOutfit(it)) },
            maxSelection = 1,
        )

        // Extras
        TagPicker(
            label = "Tratti speciali",
            options = AvatarExtras.all,
            selectedOptions = state.form.appearance.extras,
            onToggle = { onIntent(Intent.ToggleExtra(it)) },
            subtitle = "Opzionale — aggiungi dettagli unici",
        )
    }
}
