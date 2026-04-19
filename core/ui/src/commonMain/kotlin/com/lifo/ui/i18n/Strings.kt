package com.lifo.ui.i18n

import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Typed facade over Compose Multiplatform string resources.
 *
 * Single source of truth for every UI string in Calmify. Always prefer
 * `Strings.<Group>.<key>` over raw `Res.string.*` access — the facade gives
 * compile-safe autocompletion, rename-in-one-point, and semantic grouping.
 *
 * Call sites:
 * ```
 * AppText(Strings.Action.save)
 * stringResource(Strings.Action.save)
 * ```
 *
 * Adding a new string:
 * 1. Add key to `core/ui/src/commonMain/composeResources/values/strings.xml` (EN default).
 * 2. Add translation to the 11 other `values-<lang>/strings.xml` files.
 * 3. Add entry here in the matching group.
 *
 * See `memory/i18n_strategy.md` for naming conventions and patterns.
 */
object Strings {

    /** App-wide branding and constants. */
    object App {
        val name: StringResource get() = Res.string.app_name
    }

    /**
     * Reusable action verbs (button labels, menu items).
     * These are generic — specific actions belong in their screen group.
     */
    object Action {
        val save: StringResource get() = Res.string.save
        val cancel: StringResource get() = Res.string.cancel
        val close: StringResource get() = Res.string.close
        val confirm: StringResource get() = Res.string.confirm
        val delete: StringResource get() = Res.string.delete
        val retry: StringResource get() = Res.string.retry
        val back: StringResource get() = Res.string.back
        val continue_: StringResource get() = Res.string.action_continue  // `continue` is a reserved word
        val edit: StringResource get() = Res.string.action_edit
        val add: StringResource get() = Res.string.add          // pre-existing key
        val remove: StringResource get() = Res.string.remove    // pre-existing key
        val share: StringResource get() = Res.string.share      // pre-existing key
    }

    /**
     * Error messages shown in UI (snackbars, dialogs, inline).
     * Do NOT use for exceptions logged — those stay as raw strings.
     */
    object Error {
        val generic: StringResource get() = Res.string.error_generic
        val unauthorized: StringResource get() = Res.string.error_unauthorized
        val validation: StringResource get() = Res.string.error_validation
        val rateLimit: StringResource get() = Res.string.error_rate_limit
        val loading: StringResource get() = Res.string.error_loading       // pre-existing
        val connection: StringResource get() = Res.string.error_connection // pre-existing
    }

    /**
     * UI states (loading, empty, offline placeholders).
     */
    object State {
        val loading: StringResource get() = Res.string.state_loading
        val empty: StringResource get() = Res.string.state_empty
        val offline: StringResource get() = Res.string.state_offline
        val noResults: StringResource get() = Res.string.no_results        // pre-existing
    }

    /**
     * Accessibility contentDescription strings.
     * All icon/image contentDescription MUST come from here — never hardcoded,
     * never English-only. Screen readers read these.
     */
    object A11y {
        val close: StringResource get() = Res.string.a11y_close
        val menu: StringResource get() = Res.string.a11y_menu
        val userProfile: StringResource get() = Res.string.a11y_user_profile
        val back: StringResource get() = Res.string.a11y_back
        val search: StringResource get() = Res.string.a11y_search
    }

    /**
     * Screen-specific strings, grouped by feature/screen.
     * Use when a string has no reusable semantics (it belongs to one place).
     * Sub-objects added per-feature during Fase C migration.
     */
    object Screen {
        /** Home feature — screen-specific strings for top bar, search, dialogs, community cards. */
        object Home {
            val title: StringResource get() = Res.string.screen_home_title
            val fabWrite: StringResource get() = Res.string.screen_home_fab_write
            val searchPlaceholder: StringResource get() = Res.string.screen_home_search_placeholder
            val searchRecent: StringResource get() = Res.string.screen_home_search_recent
            val searchTypeHint: StringResource get() = Res.string.screen_home_search_type_hint
            val qaTalkTitle: StringResource get() = Res.string.screen_home_qa_talk_title
            val qaTalkSubtitle: StringResource get() = Res.string.screen_home_qa_talk_subtitle
            val qaVoiceAssistantSubtitle: StringResource get() = Res.string.screen_home_qa_voice_assistant_subtitle
            val qaWriteTitle: StringResource get() = Res.string.screen_home_qa_write_title
            val qaWriteSubtitle: StringResource get() = Res.string.screen_home_qa_write_subtitle
            val qaWellbeingTitle: StringResource get() = Res.string.screen_home_qa_wellbeing_title
            val qaSnapshotLabel: StringResource get() = Res.string.screen_home_qa_snapshot_label
            val communityLikes: StringResource get() = Res.string.screen_home_community_likes
            val communityReplies: StringResource get() = Res.string.screen_home_community_replies
            val communityPhotoBy: StringResource get() = Res.string.screen_home_community_photo_by
            val signoutTitle: StringResource get() = Res.string.screen_home_signout_title
            val signoutMessage: StringResource get() = Res.string.screen_home_signout_message
            val signoutProgress: StringResource get() = Res.string.screen_home_signout_progress
            val signoutFailure: StringResource get() = Res.string.screen_home_signout_failure
            val deleteAllTitle: StringResource get() = Res.string.screen_home_delete_all_title
            val deleteAllMessage: StringResource get() = Res.string.screen_home_delete_all_message
            val deleteAllSuccess: StringResource get() = Res.string.screen_home_delete_all_success
            val topicsTooltipTitle: StringResource get() = Res.string.screen_home_topics_tooltip_title
            val streakTooltipTitle: StringResource get() = Res.string.screen_home_streak_tooltip_title
            val diaryFallbackTitle: StringResource get() = Res.string.screen_home_diary_fallback_title
            // a11y grouping for Home screen
            val a11yNewDiary: StringResource get() = Res.string.a11y_home_new_diary
            val a11yDatePicker: StringResource get() = Res.string.a11y_home_date_picker
            val a11yClearSearch: StringResource get() = Res.string.a11y_home_clear_search
            val a11yRemoveHistory: StringResource get() = Res.string.a11y_home_remove_history
        }

        /** Search feature — tabs, sections, placeholder, error/empty states. */
        object Search {
            val tabAll: StringResource get() = Res.string.screen_search_tab_all
            val tabThreads: StringResource get() = Res.string.screen_search_tab_threads
            val tabUsers: StringResource get() = Res.string.screen_search_tab_users
            val sectionUsers: StringResource get() = Res.string.screen_search_section_users
            val sectionThreads: StringResource get() = Res.string.screen_search_section_threads
            val userLabel: StringResource get() = Res.string.screen_search_user_label
            val followersCount: StringResource get() = Res.string.screen_search_followers_count
            val moodFilterAll: StringResource get() = Res.string.screen_search_mood_filter_all
            val placeholder: StringResource get() = Res.string.screen_search_placeholder
            val emptyHint: StringResource get() = Res.string.screen_search_empty_hint
            val noResults: StringResource get() = Res.string.screen_search_no_results
            val noResultsFor: StringResource get() = Res.string.screen_search_no_results_for
            val loading: StringResource get() = Res.string.screen_search_loading
            val error: StringResource get() = Res.string.screen_search_error
        }

        /** Composer feature — new post / reply form. */
        object Composer {
            val titleNew: StringResource get() = Res.string.screen_composer_title_new
            val titleReply: StringResource get() = Res.string.screen_composer_title_reply
            val placeholder: StringResource get() = Res.string.screen_composer_placeholder
            val a11yGif: StringResource get() = Res.string.a11y_composer_gif
            val a11yAttach: StringResource get() = Res.string.a11y_composer_attach
            val a11yQuote: StringResource get() = Res.string.a11y_composer_quote
            val a11yMore: StringResource get() = Res.string.a11y_composer_more
            val replyPermAll: StringResource get() = Res.string.screen_composer_reply_perm_all
            val replyPermFollowers: StringResource get() = Res.string.screen_composer_reply_perm_followers
            val replyPermMentioned: StringResource get() = Res.string.screen_composer_reply_perm_mentioned
            val visibilityPublic: StringResource get() = Res.string.screen_composer_visibility_public
            val visibilityFollowers: StringResource get() = Res.string.screen_composer_visibility_followers
            val visibilityPrivate: StringResource get() = Res.string.screen_composer_visibility_private
        }

        /** Messaging feature — chat room + conversation list. */
        object Messaging {
            val placeholder: StringResource get() = Res.string.screen_messaging_placeholder
            val keepAuthentic: StringResource get() = Res.string.screen_messaging_keep_authentic
            val disappearInfo: StringResource get() = Res.string.screen_messaging_disappear_info
            val a11ySend: StringResource get() = Res.string.a11y_messaging_send
            val a11yAttachImage: StringResource get() = Res.string.a11y_messaging_attach_image
            val a11yCamera: StringResource get() = Res.string.a11y_messaging_camera
            val a11yImage: StringResource get() = Res.string.a11y_messaging_image
            val a11yAttachmentN: StringResource get() = Res.string.a11y_messaging_attachment_n
            val a11yRemoveAttachment: StringResource get() = Res.string.a11y_messaging_remove_attachment
        }

        /** Avatar Creator feature — wizard + creation pipeline + list. */
        object Avatar {
            val progressSending: StringResource get() = Res.string.screen_avatar_progress_sending
            val progressPreparing: StringResource get() = Res.string.screen_avatar_progress_preparing
            val progressGenerating: StringResource get() = Res.string.screen_avatar_progress_generating
            val stagePersonalityDone: StringResource get() = Res.string.screen_avatar_stage_personality_done
            val stageBodyNow: StringResource get() = Res.string.screen_avatar_stage_body_now
            val ready: StringResource get() = Res.string.screen_avatar_ready
            val readyDetail: StringResource get() = Res.string.screen_avatar_ready_detail
            val errorTitle: StringResource get() = Res.string.screen_avatar_error_title
            val voiceLabel: StringResource get() = Res.string.screen_avatar_voice_label
            val attachmentStyle: StringResource get() = Res.string.screen_avatar_attachment_style
            val personalityPressure: StringResource get() = Res.string.screen_avatar_personality_pressure
            val personalityDecisions: StringResource get() = Res.string.screen_avatar_personality_decisions
            val voiceChoose: StringResource get() = Res.string.screen_avatar_voice_choose
            val voiceTone: StringResource get() = Res.string.screen_avatar_voice_tone
        }

        /** Insight feature — diary insight view + feedback dialog. */
        object Insight {
            val loading: StringResource get() = Res.string.screen_insight_loading
            val feedbackDialogTitle: StringResource get() = Res.string.screen_insight_feedback_dialog_title
            val feedbackPlaceholder: StringResource get() = Res.string.screen_insight_feedback_placeholder
            val feedbackSend: StringResource get() = Res.string.screen_insight_feedback_send
            val feedbackUseful: StringResource get() = Res.string.screen_insight_feedback_useful
            val feedbackNotUseful: StringResource get() = Res.string.screen_insight_feedback_not_useful
        }

        /** Meditation feature — practice screen + post-session dialog. */
        object Meditation {
            val start: StringResource get() = Res.string.screen_meditation_start
            val notesPlaceholder: StringResource get() = Res.string.screen_meditation_notes_placeholder
            val saveSession: StringResource get() = Res.string.screen_meditation_save_session
            val discardBack: StringResource get() = Res.string.screen_meditation_discard_back
        }

        /** Thread Detail feature — thread view with replies. */
        object ThreadDetail {
            val replyingTo: StringResource get() = Res.string.screen_thread_detail_replying_to
            val repliesHeader: StringResource get() = Res.string.screen_thread_detail_replies_header
            val loadingError: StringResource get() = Res.string.screen_thread_detail_loading_error
        }

        /** Social Profile feature — edit + view profile screens. */
        object SocialProfile {
            val changePhoto: StringResource get() = Res.string.screen_social_profile_change_photo
            val tapChangePhoto: StringResource get() = Res.string.screen_social_profile_tap_change_photo
            val sectionIdentity: StringResource get() = Res.string.screen_social_profile_section_identity
            val sectionInterests: StringResource get() = Res.string.screen_social_profile_section_interests
            val visits30d: StringResource get() = Res.string.screen_social_profile_visits_30d
            val a11yBlockUser: StringResource get() = Res.string.a11y_social_profile_block_user
            val a11yCoverPhoto: StringResource get() = Res.string.a11y_social_profile_cover_photo
            val a11yRemoveInterest: StringResource get() = Res.string.a11y_social_profile_remove_interest
            val a11yAddInterest: StringResource get() = Res.string.a11y_social_profile_add_interest
        }
    }

    // Shared a11y across multiple features
    object SharedA11y {
        val verifiedBadge: StringResource get() = Res.string.a11y_verified_badge
    }
}
