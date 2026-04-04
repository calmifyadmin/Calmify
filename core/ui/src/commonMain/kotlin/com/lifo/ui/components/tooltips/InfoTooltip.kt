package com.lifo.ui.components.tooltips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.info_tooltip_cd
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import org.jetbrains.compose.resources.stringResource

/**
 * A small ⓘ icon that opens a [ModalBottomSheet] with a title and detailed
 * explanation when tapped.
 *
 * Designed for psychological / wellness concepts that users may not be familiar
 * with.  All text should be in Italian, warm and non-judgmental.
 *
 * Usage:
 * ```kotlin
 * Row(verticalAlignment = Alignment.CenterVertically) {
 *     Text("Pattern cognitivi")
 *     InfoTooltip(
 *         title       = "Cosa sono i pattern cognitivi?",
 *         description = "Sono schemi ricorrenti nel tuo modo di pensare. ..."
 *     )
 * }
 * ```
 *
 * @param title       Header shown inside the bottom sheet.
 * @param description Body text — keep it 2-4 sentences, plain Italian.
 * @param iconSize    Size of the ⓘ icon (default 18.dp).
 * @param iconTint    Tint for the icon (defaults to onSurfaceVariant).
 * @param modifier    Applied to the clickable icon container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTooltip(
    title: String,
    description: String,
    iconSize: Dp = 18.dp,
    iconTint: Color? = null,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Icon(
        imageVector         = Icons.Outlined.Info,
        contentDescription  = stringResource(Res.string.info_tooltip_cd, title),
        tint                = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier            = modifier
            .size(iconSize)
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
                role              = Role.Button,
                onClick           = { showSheet = true },
            ),
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest  = { showSheet = false },
            sheetState        = sheetState,
            containerColor    = MaterialTheme.colorScheme.surfaceContainerLow,
            shape             = RoundedCornerShape(topStart = CalmifyRadius.xxl, topEnd = CalmifyRadius.xxl),
        ) {
            InfoTooltipSheetContent(title = title, description = description)
        }
    }
}

@Composable
private fun InfoTooltipSheetContent(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CalmifySpacing.xl, vertical = CalmifySpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Outlined.Info,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(CalmifySpacing.sm))
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(CalmifySpacing.md))

        Text(
            text  = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CalmifySpacing.xl))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pre-built tooltip definitions for the key psychological concepts in Calmify
// ─────────────────────────────────────────────────────────────────────────────

object TooltipContent {

    // ── Insight / Percorso screen ─────────────────────────────────────────────

    val cognitivePatterns = Pair(
        "Cosa sono i pattern cognitivi?",
        "I pattern cognitivi sono schemi ricorrenti nel tuo modo di pensare e interpretare le situazioni. " +
        "Ad esempio, tendere sempre a catastrofizzare o a vedere le cose solo in bianco e nero. " +
        "Riconoscerli non significa che sei \"rotto\" — è semplicemente il primo passo per scegliere risposte diverse."
    )

    val sleepMoodCorrelation = Pair(
        "Correlazione sonno-umore",
        "Dormire bene e sentirsi bene sono strettamente legati: meno ore di sonno spesso si traducono in " +
        "umore più basso il giorno dopo. Calmify incrocia le tue note di sonno e di umore nel tempo " +
        "per mostrarti questa relazione specifica nella tua vita."
    )

    val selfDeterminationTheory = Pair(
        "Self-Determination Theory (SDT)",
        "È un modello psicologico che identifica tre bisogni fondamentali per il benessere: " +
        "autonomia (sentire di scegliere liberamente), competenza (sentirsi capaci) e connessione (sentirsi vicini agli altri). " +
        "Quando questi tre bisogni sono soddisfatti, la motivazione è autentica e duratura."
    )

    val wellbeingTrend = Pair(
        "Trend di benessere",
        "Il trend mostra come il tuo benessere complessivo è cambiato nel tempo, " +
        "combinando umore, energia, sonno e qualità delle riflessioni. " +
        "Non cercare la perfezione: anche un piccolo miglioramento costante nel tempo ha un impatto enorme."
    )

    // ── Write / Diario screen ─────────────────────────────────────────────────

    val emotionalIntensity = Pair(
        "Intensità emotiva",
        "Misura quanto forte senti un'emozione in questo momento, indipendentemente da quale sia. " +
        "Un'emozione intensa non è né buona né cattiva: indica solo quanto ti sta coinvolgendo. " +
        "Notare l'intensità ti aiuta a capire quali situazioni ti toccano di più."
    )

    val stressLevel = Pair(
        "Livello di stress",
        "Lo stress è una risposta naturale del corpo a sfide percepite. " +
        "Tenere traccia del tuo livello di stress nel tempo aiuta a identificare " +
        "i momenti o i contesti che ti pesano di più, così puoi agire in modo mirato."
    )

    val calmAnxietyLevel = Pair(
        "Calma vs. Ansia",
        "Questa scala cattura il tuo stato di allerta interno in questo momento. " +
        "L'ansia non è un difetto: è il cervello che cerca di proteggerti. " +
        "Annotarla regolarmente ti permette di vedere i trigger ricorrenti e, nel tempo, di rispondere con più consapevolezza."
    )

    // ── Meditazione screen ────────────────────────────────────────────────────

    val guidedBreathing = Pair(
        "Respirazione guidata",
        "La respirazione lenta e consapevole attiva il sistema nervoso parasimpatico — " +
        "quello che \"frena\" la risposta allo stress. " +
        "Bastano pochi minuti per sentire una differenza reale nel tono muscolare e nella chiarezza mentale."
    )

    // ── Abitudini screen ─────────────────────────────────────────────────────

    val habitStacking = Pair(
        "Habit Stacking",
        "È una tecnica che collega una nuova abitudine a una già consolidata. " +
        "Esempio: \"Dopo aver fatto colazione, scrivo tre cose per cui sono grato\". " +
        "Usando un comportamento esistente come \"gancio\", è molto più facile costruire qualcosa di nuovo."
    )

    val minimumAction = Pair(
        "Minimum Action",
        "Il punto di ingresso più piccolo possibile per un'abitudine. " +
        "Invece di \"fare 30 minuti di meditazione\", il minimum action è \"mettiti sul cuscino\". " +
        "Abbassare la soglia iniziale rende quasi impossibile non iniziare — e spesso si finisce per fare molto di più."
    )
}
