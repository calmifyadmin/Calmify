package com.lifo.calmifyapp.model

import androidx.compose.ui.graphics.Color
import com.lifo.calmifyapp.R
import com.lifo.calmifyapp.ui.theme.NeutralColor

import com.lifo.calmifyapp.ui.theme.AngryColor
import com.lifo.calmifyapp.ui.theme.AwfulColor
import com.lifo.calmifyapp.ui.theme.BoredColor
import com.lifo.calmifyapp.ui.theme.CalmColor
import com.lifo.calmifyapp.ui.theme.DepressedColor
import com.lifo.calmifyapp.ui.theme.DisappointedColor
import com.lifo.calmifyapp.ui.theme.HappyColor
import com.lifo.calmifyapp.ui.theme.HumorousColor
import com.lifo.calmifyapp.ui.theme.LonelyColor
import com.lifo.calmifyapp.ui.theme.MysteriousColor
import com.lifo.calmifyapp.ui.theme.RomanticColor
import com.lifo.calmifyapp.ui.theme.ShamefulColor
import com.lifo.calmifyapp.ui.theme.SurprisedColor
import com.lifo.calmifyapp.ui.theme.SuspiciousColor
import com.lifo.calmifyapp.ui.theme.TenseColor
enum class Mood(
    val icon: Int,
    val contentColor: Color,
    val containerColor: Color
) {
    Neutral(
        icon = R.drawable.neutral,
        contentColor = Color.Black,
        containerColor = NeutralColor
    ),
    Happy(
        icon = R.drawable.happy,
        contentColor = Color.Black,
        containerColor = HappyColor
    ),
    Angry(
        icon = R.drawable.angry,
        contentColor = Color.White,
        containerColor = AngryColor
    ),
    Bored(
        icon = R.drawable.bored,
        contentColor = Color.Black,
        containerColor = BoredColor
    ),
    Calm(
        icon = R.drawable.calm,
        contentColor = Color.Black,
        containerColor = CalmColor
    ),
    Depressed(
        icon = R.drawable.depressed,
        contentColor = Color.Black,
        containerColor = DepressedColor
    ),
    Disappointed(
        icon = R.drawable.disappointed,
        contentColor = Color.White,
        containerColor = DisappointedColor
    ),
    Humorous(
        icon = R.drawable.humorous,
        contentColor = Color.Black,
        containerColor = HumorousColor
    ),
    Lonely(
        icon = R.drawable.lonely,
        contentColor = Color.White,
        containerColor = LonelyColor
    ),
    Mysterious(
        icon = R.drawable.mysterious,
        contentColor = Color.Black,
        containerColor = MysteriousColor
    ),
    Romantic(
        icon = R.drawable.romantic,
        contentColor = Color.White,
        containerColor = RomanticColor
    ),
    Shameful(
        icon = R.drawable.shameful,
        contentColor = Color.White,
        containerColor = ShamefulColor
    ),
    Awful(
        icon = R.drawable.awful,
        contentColor = Color.Black,
        containerColor = AwfulColor
    ),
    Surprised(
        icon = R.drawable.surprised,
        contentColor = Color.Black,
        containerColor = SurprisedColor
    ),
    Suspicious(
        icon = R.drawable.suspicious,
        contentColor = Color.Black,
        containerColor = SuspiciousColor
    ),
    Tense(
        icon = R.drawable.tense,
        contentColor = Color.Black,
        containerColor = TenseColor
    )
}