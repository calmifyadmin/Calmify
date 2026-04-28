package com.lifo.ui.components.coaching

import androidx.compose.runtime.Composable
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource

/**
 * Centralised tutorial step definitions for each screen.
 * Steps are built at composition time so each title/description/button
 * is resolved through Compose Multiplatform's locale-aware StringResource.
 */
object ScreenTutorials {

    /** Unique key used to check/persist "already seen" state. */
    const val KEY_HOME = "tutorial_home"

    @Composable
    fun home(): List<CoachMarkStep> = listOf(
        CoachMarkStep(
            title       = stringResource(Strings.Coach.Home.step1Title),
            description = stringResource(Strings.Coach.Home.step1Desc),
            buttonText  = stringResource(Strings.Coach.buttonNext),
            targetKey   = CoachMarkKeys.HOME_GREETING,
        ),
        CoachMarkStep(
            title       = stringResource(Strings.Coach.Home.step2Title),
            description = stringResource(Strings.Coach.Home.step2Desc),
            buttonText  = stringResource(Strings.Coach.buttonNext),
            targetKey   = CoachMarkKeys.HOME_QUICK_ACTIONS,
        ),
        CoachMarkStep(
            title       = stringResource(Strings.Coach.Home.step3Title),
            description = stringResource(Strings.Coach.Home.step3Desc),
            buttonText  = stringResource(Strings.Coach.buttonNext),
            targetKey   = CoachMarkKeys.HOME_MOOD,
        ),
        CoachMarkStep(
            title       = stringResource(Strings.Coach.Home.step4Title),
            description = stringResource(Strings.Coach.Home.step4Desc),
            buttonText  = stringResource(Strings.Coach.buttonGotIt),
            targetKey   = CoachMarkKeys.HOME_AVATAR,
        ),
    )
}
