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

    /** Navigation labels (drawer + bottom bar). Reused as screen titles when applicable. */
    object Nav {
        val home: StringResource get() = Res.string.nav_home
        val activity: StringResource get() = Res.string.nav_activity
        val settings: StringResource get() = Res.string.nav_settings
        val write: StringResource get() = Res.string.nav_write
        val profile: StringResource get() = Res.string.nav_profile
        val avatar: StringResource get() = Res.string.nav_avatar
        val feed: StringResource get() = Res.string.nav_feed
        val journal: StringResource get() = Res.string.nav_journal
        val aiChat: StringResource get() = Res.string.nav_ai_chat
        val community: StringResource get() = Res.string.nav_community
        val journey: StringResource get() = Res.string.nav_journey
        val garden: StringResource get() = Res.string.nav_garden
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
        val notifications: StringResource get() = Res.string.a11y_notifications
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
            // Activity feed period grouping (Phase J Tier 3)
            val periodToday: StringResource get() = Res.string.home_period_today
            val periodYesterday: StringResource get() = Res.string.home_period_yesterday
            val periodThisWeek: StringResource get() = Res.string.home_period_this_week
            val periodLastWeek: StringResource get() = Res.string.home_period_last_week
            val periodThisMonth: StringResource get() = Res.string.home_period_this_month
            val periodOlder: StringResource get() = Res.string.home_period_older
            val entryCountOne: StringResource get() = Res.string.home_entry_count_one
            val entryCountMany: StringResource get() = Res.string.home_entry_count_many
            // Tier 3.G — Home dashboard pass
            val quickActionWrite: StringResource get() = Res.string.home_quick_action_write
            val weekThis: StringResource get() = Res.string.home_week_this
            val weekProgress: StringResource get() = Res.string.home_week_progress
            val weekGrowthLabel: StringResource get() = Res.string.home_week_growth_label
            val weekDaySingular: StringResource get() = Res.string.home_week_day_singular
            val weekDayPlural: StringResource get() = Res.string.home_week_day_plural
            val weekGoalProgress: StringResource get() = Res.string.home_week_goal_progress
            val weekStreakTooltipBody: StringResource get() = Res.string.home_week_streak_tooltip_body
            val dayInitialMonday: StringResource get() = Res.string.home_day_initial_monday
            val dayInitialTuesday: StringResource get() = Res.string.home_day_initial_tuesday
            val dayInitialWednesday: StringResource get() = Res.string.home_day_initial_wednesday
            val dayInitialThursday: StringResource get() = Res.string.home_day_initial_thursday
            val dayInitialFriday: StringResource get() = Res.string.home_day_initial_friday
            val dayInitialSaturday: StringResource get() = Res.string.home_day_initial_saturday
            val dayInitialSunday: StringResource get() = Res.string.home_day_initial_sunday
            val dailyActionsTitle: StringResource get() = Res.string.home_daily_actions_title
            val reflectionCardTitle: StringResource get() = Res.string.home_reflection_card_title
            val moodSectionTitle: StringResource get() = Res.string.home_mood_section_title
            val moodCardTitle: StringResource get() = Res.string.home_mood_card_title
            val periodFilter7: StringResource get() = Res.string.home_period_filter_7
            val periodFilter30: StringResource get() = Res.string.home_period_filter_30
            val periodFilter90: StringResource get() = Res.string.home_period_filter_90
            val moodBreakdownPositive: StringResource get() = Res.string.home_mood_breakdown_positive
            val moodBreakdownNeutral: StringResource get() = Res.string.home_mood_breakdown_neutral
            val moodBreakdownNegative: StringResource get() = Res.string.home_mood_breakdown_negative
            val communityCount: StringResource get() = Res.string.home_community_count
            val communitySeeAll: StringResource get() = Res.string.home_community_see_all
            val moodDominantLabel: StringResource get() = Res.string.home_mood_dominant_label
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

        /** Snapshot wellness onboarding screen. */
        object Snapshot {
            val momentTitle: StringResource get() = Res.string.screen_snapshot_moment_title
            val momentSubtitle: StringResource get() = Res.string.screen_snapshot_moment_subtitle
            val whyImportant: StringResource get() = Res.string.screen_snapshot_why_important
            val whySelfAwareness: StringResource get() = Res.string.screen_snapshot_why_self_awareness
            val whyTracking: StringResource get() = Res.string.screen_snapshot_why_tracking
            val whyInsights: StringResource get() = Res.string.screen_snapshot_why_insights
            val howWorks: StringResource get() = Res.string.screen_snapshot_how_works
            val dataPrivate: StringResource get() = Res.string.screen_snapshot_data_private
            val dataPrivateDetail: StringResource get() = Res.string.screen_snapshot_data_private_detail
            val addMore: StringResource get() = Res.string.screen_snapshot_add_more
            val addMorePlaceholder: StringResource get() = Res.string.screen_snapshot_add_more_placeholder
            val start: StringResource get() = Res.string.screen_snapshot_start
            val complete: StringResource get() = Res.string.screen_snapshot_complete
            val next: StringResource get() = Res.string.screen_snapshot_next
            val sectionLife: StringResource get() = Res.string.screen_snapshot_section_life
            val sectionPsych: StringResource get() = Res.string.screen_snapshot_section_psych
            val sectionSelf: StringResource get() = Res.string.screen_snapshot_section_self
            val sectionIndicators: StringResource get() = Res.string.screen_snapshot_section_indicators
            val q1Text: StringResource get() = Res.string.screen_snapshot_q1_text
            val q1Min: StringResource get() = Res.string.screen_snapshot_q1_min
            val q1Max: StringResource get() = Res.string.screen_snapshot_q1_max
            val q2Text: StringResource get() = Res.string.screen_snapshot_q2_text
            val q2Min: StringResource get() = Res.string.screen_snapshot_q2_min
            val q2Max: StringResource get() = Res.string.screen_snapshot_q2_max
            val q3Text: StringResource get() = Res.string.screen_snapshot_q3_text
            val q3Min: StringResource get() = Res.string.screen_snapshot_q3_min
            val q3Max: StringResource get() = Res.string.screen_snapshot_q3_max
            val q4Text: StringResource get() = Res.string.screen_snapshot_q4_text
            val q4Min: StringResource get() = Res.string.screen_snapshot_q4_min
            val q4Max: StringResource get() = Res.string.screen_snapshot_q4_max
            val q5Text: StringResource get() = Res.string.screen_snapshot_q5_text
            val q5Min: StringResource get() = Res.string.screen_snapshot_q5_min
            val q5Max: StringResource get() = Res.string.screen_snapshot_q5_max
            val q6Text: StringResource get() = Res.string.screen_snapshot_q6_text
            val q6Min: StringResource get() = Res.string.screen_snapshot_q6_min
            val q6Max: StringResource get() = Res.string.screen_snapshot_q6_max
            val q7Text: StringResource get() = Res.string.screen_snapshot_q7_text
            val q7Min: StringResource get() = Res.string.screen_snapshot_q7_min
            val q7Max: StringResource get() = Res.string.screen_snapshot_q7_max
            val q8Text: StringResource get() = Res.string.screen_snapshot_q8_text
            val q8Min: StringResource get() = Res.string.screen_snapshot_q8_min
            val q8Max: StringResource get() = Res.string.screen_snapshot_q8_max
            val q9Text: StringResource get() = Res.string.screen_snapshot_q9_text
            val q9Min: StringResource get() = Res.string.screen_snapshot_q9_min
            val q9Max: StringResource get() = Res.string.screen_snapshot_q9_max
            val q10Text: StringResource get() = Res.string.screen_snapshot_q10_text
            val q10Min: StringResource get() = Res.string.screen_snapshot_q10_min
            val q10Max: StringResource get() = Res.string.screen_snapshot_q10_max
        }

        /** Settings feature — small misc keys. */
        object Settings {
            val selectLanguage: StringResource get() = Res.string.screen_settings_select_language
        }

        /** Chat feature — toast + voice button labels. */
        object Chat {
            val copiedClipboard: StringResource get() = Res.string.screen_chat_copied_clipboard
            val listen: StringResource get() = Res.string.screen_chat_listen
            val stopVoice: StringResource get() = Res.string.screen_chat_stop_voice
        }

        /** Humanoid (avatar viewer) feature — buttons + titles. */
        object Humanoid {
            val create: StringResource get() = Res.string.screen_humanoid_create
            val loadDemo: StringResource get() = Res.string.screen_humanoid_load_demo
        }

        /** Thread Detail feature — thread view with replies. */
        object ThreadDetail {
            val replyingTo: StringResource get() = Res.string.screen_thread_detail_replying_to
            val repliesHeader: StringResource get() = Res.string.screen_thread_detail_replies_header
            val loadingError: StringResource get() = Res.string.screen_thread_detail_loading_error
            val replyTo: StringResource get() = Res.string.screen_thread_detail_reply_to
            val replyPlaceholder: StringResource get() = Res.string.screen_thread_detail_reply_placeholder
        }

        /** Percorso (Journey) screen. */
        object Percorso {
            val title: StringResource get() = Res.string.screen_percorso_title
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
            // Phase D additions
            val editButton: StringResource get() = Res.string.screen_social_profile_edit_button
            val shareButton: StringResource get() = Res.string.screen_social_profile_share_button
            val followButton: StringResource get() = Res.string.screen_social_profile_follow_button
            val followingButton: StringResource get() = Res.string.screen_social_profile_following_button
            val userFallback: StringResource get() = Res.string.screen_social_profile_user_fallback
            val followersSuffix: StringResource get() = Res.string.screen_social_profile_followers_suffix
            val followingSuffix: StringResource get() = Res.string.screen_social_profile_following_suffix
        }

        /** Follow List screen tabs. */
        object FollowList {
            val tabFollowers: StringResource get() = Res.string.screen_follow_list_tab_followers
            val tabFollowing: StringResource get() = Res.string.screen_follow_list_tab_following
        }
    }

    /** Side drawer entries (extends Nav with PRO-only / settings actions). */
    object Drawer {
        val history: StringResource get() = Res.string.drawer_history
        val createAvatar: StringResource get() = Res.string.drawer_create_avatar
        val subscription: StringResource get() = Res.string.drawer_subscription
        val manageSubscription: StringResource get() = Res.string.drawer_manage_subscription
        val signOut: StringResource get() = Res.string.drawer_sign_out
        val deleteData: StringResource get() = Res.string.drawer_delete_data
    }

    /** Floating action button labels. */
    object Fab {
        val talkToEve: StringResource get() = Res.string.fab_talk_to_eve
        val newPost: StringResource get() = Res.string.fab_new_post
        val write: StringResource get() = Res.string.fab_write
    }

    /** Home / dashboard hero greeting fragments. */
    object Hero {
        val greetingPrefix: StringResource get() = Res.string.hero_greeting_prefix
        val wellbeingIs: StringResource get() = Res.string.hero_wellbeing_is
        val morningQuestion: StringResource get() = Res.string.hero_morning_question
        val afternoonPause: StringResource get() = Res.string.hero_afternoon_pause
        val afternoonQuestion: StringResource get() = Res.string.hero_afternoon_question
        val eveningQuestion: StringResource get() = Res.string.hero_evening_question
        val trendGrowth: StringResource get() = Res.string.hero_trend_growth
        val trendDecline: StringResource get() = Res.string.hero_trend_decline
        val trendStable: StringResource get() = Res.string.hero_trend_stable
        val scoreExcellent: StringResource get() = Res.string.hero_score_excellent
        val scoreGreat: StringResource get() = Res.string.hero_score_great
        val scoreGood: StringResource get() = Res.string.hero_score_good
        val scoreAverage: StringResource get() = Res.string.hero_score_average
        val scoreBelowAverage: StringResource get() = Res.string.hero_score_below_average
        val scoreToImprove: StringResource get() = Res.string.hero_score_to_improve
    }

    /**
     * Mood labels — all moods used across TextAnalyzer, composer chips, post visualisation.
     * Storage canonical = IT (legacy). Display via composable lookup.
     */
    object Mood {
        val neutral: StringResource get() = Res.string.mood_neutral
        val happy: StringResource get() = Res.string.mood_happy
        val angry: StringResource get() = Res.string.mood_angry
        val depressed: StringResource get() = Res.string.mood_depressed
        val disappointed: StringResource get() = Res.string.mood_disappointed
        val romantic: StringResource get() = Res.string.mood_romantic
        val calm: StringResource get() = Res.string.mood_calm
        val tense: StringResource get() = Res.string.mood_tense
        val lonely: StringResource get() = Res.string.mood_lonely
        val confused: StringResource get() = Res.string.mood_confused
        val guilty: StringResource get() = Res.string.mood_guilty
        val awful: StringResource get() = Res.string.mood_awful
        val surprised: StringResource get() = Res.string.mood_surprised
        val bored: StringResource get() = Res.string.mood_bored
        val amused: StringResource get() = Res.string.mood_amused
        val suspicious: StringResource get() = Res.string.mood_suspicious
        val grateful: StringResource get() = Res.string.mood_grateful
        val motivated: StringResource get() = Res.string.mood_motivated
        val anxious: StringResource get() = Res.string.mood_anxious
        val sad: StringResource get() = Res.string.mood_sad
    }

    /** Composer post-category enum labels. Storage canonical = IT (legacy). */
    object ComposerCategory {
        val discovery: StringResource get() = Res.string.composer_category_discovery
        val challenge: StringResource get() = Res.string.composer_category_challenge
        val question: StringResource get() = Res.string.composer_category_question
    }

    /** Garden taxonomy: category, difficulty, ikigai circles, activity entries. */
    object Garden {
        val categoryAll: StringResource get() = Res.string.garden_category_all
        val categoryWriting: StringResource get() = Res.string.garden_category_writing
        val categoryMind: StringResource get() = Res.string.garden_category_mind
        val categoryBody: StringResource get() = Res.string.garden_category_body
        val categorySpirit: StringResource get() = Res.string.garden_category_spirit
        val categoryHabits: StringResource get() = Res.string.garden_category_habits
        val difficultyEasy: StringResource get() = Res.string.garden_difficulty_easy
        val difficultyMedium: StringResource get() = Res.string.garden_difficulty_medium
        val difficultyAdvanced: StringResource get() = Res.string.garden_difficulty_advanced
        val ikigaiPassion: StringResource get() = Res.string.garden_ikigai_passion
        val ikigaiTalent: StringResource get() = Res.string.garden_ikigai_talent
        val ikigaiMission: StringResource get() = Res.string.garden_ikigai_mission
        val ikigaiProfession: StringResource get() = Res.string.garden_ikigai_profession

        /** Halo-redesign card chrome (added 2026-05-04, matches `Ikigai.html` design source). */
        val ikigaiTitle: StringResource get() = Res.string.ikigai_title
        val ikigaiCardSubtitle: StringResource get() = Res.string.ikigai_card_subtitle
        val ikigaiHaloPrompt: StringResource get() = Res.string.ikigai_halo_prompt
        val ikigaiProgressLabel: StringResource get() = Res.string.ikigai_progress_label
        val ikigaiAddPlaceholder: StringResource get() = Res.string.ikigai_add_placeholder
        val ikigaiAddToTemplate: StringResource get() = Res.string.ikigai_add_to_template

        /** Per-activity name + description pairs (19 entries). */
        object Activity {
            val diaryName: StringResource get() = Res.string.garden_activity_diary_name
            val diaryDesc: StringResource get() = Res.string.garden_activity_diary_desc
            val brainDumpName: StringResource get() = Res.string.garden_activity_brain_dump_name
            val brainDumpDesc: StringResource get() = Res.string.garden_activity_brain_dump_desc
            val gratitudeName: StringResource get() = Res.string.garden_activity_gratitude_name
            val gratitudeDesc: StringResource get() = Res.string.garden_activity_gratitude_desc
            val meditationName: StringResource get() = Res.string.garden_activity_meditation_name
            val meditationDesc: StringResource get() = Res.string.garden_activity_meditation_desc
            val reframingName: StringResource get() = Res.string.garden_activity_reframing_name
            val reframingDesc: StringResource get() = Res.string.garden_activity_reframing_desc
            val blocksName: StringResource get() = Res.string.garden_activity_blocks_name
            val blocksDesc: StringResource get() = Res.string.garden_activity_blocks_desc
            val recurringThoughtsName: StringResource get() = Res.string.garden_activity_recurring_thoughts_name
            val recurringThoughtsDesc: StringResource get() = Res.string.garden_activity_recurring_thoughts_desc
            val energyName: StringResource get() = Res.string.garden_activity_energy_name
            val energyDesc: StringResource get() = Res.string.garden_activity_energy_desc
            val sleepName: StringResource get() = Res.string.garden_activity_sleep_name
            val sleepDesc: StringResource get() = Res.string.garden_activity_sleep_desc
            val movementName: StringResource get() = Res.string.garden_activity_movement_name
            val movementDesc: StringResource get() = Res.string.garden_activity_movement_desc
            val dashboardName: StringResource get() = Res.string.garden_activity_dashboard_name
            val dashboardDesc: StringResource get() = Res.string.garden_activity_dashboard_desc
            val valuesName: StringResource get() = Res.string.garden_activity_values_name
            val valuesDesc: StringResource get() = Res.string.garden_activity_values_desc
            val ikigaiName: StringResource get() = Res.string.garden_activity_ikigai_name
            val ikigaiDesc: StringResource get() = Res.string.garden_activity_ikigai_desc
            val aweName: StringResource get() = Res.string.garden_activity_awe_name
            val aweDesc: StringResource get() = Res.string.garden_activity_awe_desc
            val silenceName: StringResource get() = Res.string.garden_activity_silence_name
            val silenceDesc: StringResource get() = Res.string.garden_activity_silence_desc
            val connectionsName: StringResource get() = Res.string.garden_activity_connections_name
            val connectionsDesc: StringResource get() = Res.string.garden_activity_connections_desc
            val inspirationName: StringResource get() = Res.string.garden_activity_inspiration_name
            val inspirationDesc: StringResource get() = Res.string.garden_activity_inspiration_desc
            val habitsName: StringResource get() = Res.string.garden_activity_habits_name
            val habitsDesc: StringResource get() = Res.string.garden_activity_habits_desc
            val environmentName: StringResource get() = Res.string.garden_activity_environment_name
            val environmentDesc: StringResource get() = Res.string.garden_activity_environment_desc
        }
    }

    /** Generic confirmation / informational dialogs. */
    object Dialog {
        val accountDeleted: StringResource get() = Res.string.dialog_account_deleted
        val featureUnavailableTitle: StringResource get() = Res.string.dialog_feature_unavailable_title
        val featureUnavailableBody: StringResource get() = Res.string.dialog_feature_unavailable_body
        val goBack: StringResource get() = Res.string.dialog_go_back
    }

    /** Date / weekday names + greeting variants. */
    object DateTime {
        val weekdaySaturday: StringResource get() = Res.string.datetime_weekday_saturday
        val weekdaySunday: StringResource get() = Res.string.datetime_weekday_sunday
        val greetingMorning: StringResource get() = Res.string.datetime_greeting_morning
        val greetingAfternoon: StringResource get() = Res.string.datetime_greeting_afternoon
        val greetingEvening: StringResource get() = Res.string.datetime_greeting_evening
        val greetingNight: StringResource get() = Res.string.datetime_greeting_night
        val weekLabel: StringResource get() = Res.string.datetime_week_label
    }

    /**
     * Time-of-day + state-of-mind journal prompts shown in the JournalHome
     * `DailyPromptCard`. Resolved at `@Composable` scope by `getContextualPromptResource`
     * — the helper picks one StringResource based on diary history, mood, and clock.
     */
    object JournalPrompt {
        // Existing (Tier 1) — also reused by getContextualPrompt mapping.
        val weekendMorning: StringResource get() = Res.string.journal_prompt_weekend_morning
        val afternoonPause: StringResource get() = Res.string.journal_prompt_afternoon_pause
        val eveningCalm: StringResource get() = Res.string.journal_prompt_evening_calm

        // Tier 3.E — contextual prompts.
        val safeSpace: StringResource get() = Res.string.journal_prompt_safe_space
        val todayMorning: StringResource get() = Res.string.journal_prompt_today_morning
        val todayAfternoon: StringResource get() = Res.string.journal_prompt_today_afternoon
        val todayEvening: StringResource get() = Res.string.journal_prompt_today_evening
        val afterBreak: StringResource get() = Res.string.journal_prompt_after_break
        val streakLong: StringResource get() = Res.string.journal_prompt_streak_long
        val streakMedium: StringResource get() = Res.string.journal_prompt_streak_medium
        val weekdayMorning: StringResource get() = Res.string.journal_prompt_weekday_morning
        val midMorning: StringResource get() = Res.string.journal_prompt_mid_morning
        val weekendAfternoon: StringResource get() = Res.string.journal_prompt_weekend_afternoon
        val evening: StringResource get() = Res.string.journal_prompt_evening
        val night: StringResource get() = Res.string.journal_prompt_night

        // Mood follow-ups (when last diary's mood informs today's prompt).
        val moodLow: StringResource get() = Res.string.journal_prompt_mood_low
        val moodLonely: StringResource get() = Res.string.journal_prompt_mood_lonely
        val moodTense: StringResource get() = Res.string.journal_prompt_mood_tense
        val moodAngry: StringResource get() = Res.string.journal_prompt_mood_angry
        val moodDisappointed: StringResource get() = Res.string.journal_prompt_mood_disappointed
        val moodShameful: StringResource get() = Res.string.journal_prompt_mood_shameful
        val moodHappyMorning: StringResource get() = Res.string.journal_prompt_mood_happy_morning

        // Card chrome + timestamp formats.
        val dailyPromptSubtitle: StringResource get() = Res.string.journal_dailyprompt_subtitle
        val dailyPromptCardTitle: StringResource get() = Res.string.journal_dailyprompt_card_title
        val dailyPromptCardCta: StringResource get() = Res.string.journal_dailyprompt_card_cta
        val emptyStateTitle: StringResource get() = Res.string.journal_empty_state_title
        val timestampToday: StringResource get() = Res.string.journal_timestamp_today
        val timestampYesterday: StringResource get() = Res.string.journal_timestamp_yesterday
    }

    /**
     * Weekly reflection card content (PercorsoScreen + ProfileDashboard).
     * `buildWeeklyReflection` is `@Composable` and resolves these conditionally
     * based on profile baselines + trend + resilience + diary count.
     */
    object Weekly {
        val cardTitle: StringResource get() = Res.string.weekly_card_title
        val journeyHeader: StringResource get() = Res.string.weekly_journey_header
        val moodPositive: StringResource get() = Res.string.weekly_mood_positive
        val moodBalanced: StringResource get() = Res.string.weekly_mood_balanced
        val moodDifficult: StringResource get() = Res.string.weekly_mood_difficult
        val stressHigh: StringResource get() = Res.string.weekly_stress_high
        val stressManaged: StringResource get() = Res.string.weekly_stress_managed
        val stressLow: StringResource get() = Res.string.weekly_stress_low
        val resilienceStrong: StringResource get() = Res.string.weekly_resilience_strong
        val diaryCount: StringResource get() = Res.string.weekly_diary_count
    }

    /** Connection feature labels. */
    object Connection {
        val qualityTimeTitle: StringResource get() = Res.string.connection_quality_time_title
        val qualityTimeLabel: StringResource get() = Res.string.connection_quality_time_label
    }

    /**
     * SentimentLabel enum (in `core/util/.../model/DiaryInsight.kt`) localized labels.
     * Same circular-dep pattern as Trend/BlockType — UI sites resolve via inline
     * `when (label) -> Strings.Sentiment.X` to avoid `core/util -> core/ui` dep.
     */
    object Sentiment {
        val veryNegative: StringResource get() = Res.string.sentiment_very_negative
        val negative: StringResource get() = Res.string.sentiment_negative
        val neutral: StringResource get() = Res.string.sentiment_neutral
        val positive: StringResource get() = Res.string.sentiment_positive
        val veryPositive: StringResource get() = Res.string.sentiment_very_positive
    }

    /**
     * Maps a canonical IT mood tag (stored in Firestore for backward compat —
     * see ComposerContract.MOOD_TAGS) to its localized [StringResource].
     * Returns null if the tag is not a known canonical (caller can fallback).
     */
    fun moodTagLocalizedRes(canonical: String): StringResource? = when (canonical) {
        "Felice" -> Res.string.mood_happy
        "Sereno" -> Res.string.mood_calm
        "Grato" -> Res.string.mood_grateful
        "Motivato" -> Res.string.mood_motivated
        "Pensieroso", "Confuso" -> Res.string.mood_confused
        "Triste" -> Res.string.mood_sad
        "Ansioso" -> Res.string.mood_anxious
        "Stanco" -> Res.string.mood_neutral
        else -> null
    }

    /** Snapshot wellness-onboarding wizard scroll header + CTAs. */
    object SnapshotWizard {
        val howFeel: StringResource get() = Res.string.snapshot_q_how_feel
        val howFeelToday: StringResource get() = Res.string.snapshot_q_how_feel_today
        val swipeToChoose: StringResource get() = Res.string.snapshot_swipe_to_choose
    }

    /** Journal home filter chips (Tier 3.G). */
    object JournalFilter {
        val all: StringResource get() = Res.string.journal_filter_all
        val diary: StringResource get() = Res.string.journal_filter_diary
        val gratitude: StringResource get() = Res.string.journal_filter_gratitude
        val energy: StringResource get() = Res.string.journal_filter_energy
        val sleep: StringResource get() = Res.string.journal_filter_sleep
        val meditation: StringResource get() = Res.string.journal_filter_meditation
        val habits: StringResource get() = Res.string.journal_filter_habits
        val brainDump: StringResource get() = Res.string.journal_filter_brain_dump
        val movement: StringResource get() = Res.string.journal_filter_movement
    }

    /** Feed top tabs (Tier 3.G). */
    object Feed {
        val tabAll: StringResource get() = Res.string.feed_tab_all
        val tabDiscoveries: StringResource get() = Res.string.feed_tab_discoveries
        val tabChallenges: StringResource get() = Res.string.feed_tab_challenges
        val tabQuestions: StringResource get() = Res.string.feed_tab_questions
    }

    /**
     * Diary creation 10-step wizard (`WriteContent.kt`). Per-step heading +
     * subtitle + CTA buttons. Step 1 (mood) lives in [SnapshotWizard].
     */
    object WriteWizard {
        val titleHeading: StringResource get() = Res.string.write_step_title_heading
        val titleSubtitle: StringResource get() = Res.string.write_step_title_subtitle
        val descriptionHeading: StringResource get() = Res.string.write_step_description_heading
        val descriptionSubtitle: StringResource get() = Res.string.write_step_description_subtitle
        val intensityTitle: StringResource get() = Res.string.write_step_intensity_title
        val intensitySubtitle: StringResource get() = Res.string.write_step_intensity_subtitle
        val stressTitle: StringResource get() = Res.string.write_step_stress_title
        val stressSubtitle: StringResource get() = Res.string.write_step_stress_subtitle
        val energyTitle: StringResource get() = Res.string.write_step_energy_title
        val energySubtitle: StringResource get() = Res.string.write_step_energy_subtitle
        val calmTitle: StringResource get() = Res.string.write_step_calm_title
        val calmSubtitle: StringResource get() = Res.string.write_step_calm_subtitle
        val triggerTitle: StringResource get() = Res.string.write_step_trigger_title
        val triggerSubtitle: StringResource get() = Res.string.write_step_trigger_subtitle
        val sensationTitle: StringResource get() = Res.string.write_step_sensation_title
        val sensationSubtitle: StringResource get() = Res.string.write_step_sensation_subtitle
        val almostDoneTitle: StringResource get() = Res.string.write_step_almost_done_title
        val almostDoneSubtitle: StringResource get() = Res.string.write_step_almost_done_subtitle
        val saveDiary: StringResource get() = Res.string.write_save_diary
        val smartCapture: StringResource get() = Res.string.write_smart_capture
        val smartCaptureReanalyze: StringResource get() = Res.string.write_smart_capture_reanalyze
        val smartCaptureMoodLabel: StringResource get() = Res.string.write_smart_capture_mood_label
        val complete: StringResource get() = Res.string.write_wizard_complete
    }

    /**
     * Wellness wizard chrome (Tier 3.D) — top app bar titles + section headers
     * + save/dialog CTAs + per-screen specific labels for 8 wellness screens
     * (BrainDump / Gratitude / EnergyCheckIn / Movement / SleepLog / Inspiration /
     * Reframe / Connection). Reuses `Strings.Action.{save,cancel,confirm,...}`
     * for shared verbs.
     */
    object Wellness {
        // BrainDump
        val brainDumpTitle: StringResource get() = Res.string.braindump_title
        val brainDumpSubtitle: StringResource get() = Res.string.braindump_subtitle
        val brainDumpSaveCd: StringResource get() = Res.string.braindump_a11y_save
        val brainDumpPlaceholderHint: StringResource get() = Res.string.braindump_placeholder_hint
        // Gratitude
        val gratitudeSubtitleUpdate: StringResource get() = Res.string.gratitude_subtitle_update
        val gratitudeSubtitleToday: StringResource get() = Res.string.gratitude_subtitle_today
        val gratitudeLabelFirst: StringResource get() = Res.string.gratitude_label_first
        val gratitudeLabelSecond: StringResource get() = Res.string.gratitude_label_second
        val gratitudeLabelThird: StringResource get() = Res.string.gratitude_label_third
        val gratitudeSaveUpdate: StringResource get() = Res.string.gratitude_save_update
        val gratitudeSaveCreate: StringResource get() = Res.string.gratitude_save_create
        val gratitudeJournalQuestion: StringResource get() = Res.string.gratitude_journal_question
        // EnergyCheckIn
        val energyTitle: StringResource get() = Res.string.energy_title
        val energySubtitleUpdate: StringResource get() = Res.string.energy_subtitle_update
        val energySubtitleCreate: StringResource get() = Res.string.energy_subtitle_create
        val energySectionPerceived: StringResource get() = Res.string.energy_section_perceived
        val energySectionSleepHours: StringResource get() = Res.string.energy_section_sleep_hours
        val energySaveUpdate: StringResource get() = Res.string.energy_save_update
        val energySaveCreate: StringResource get() = Res.string.energy_save_create
        val energySectionWater: StringResource get() = Res.string.energy_section_water
        val energyMinusCd: StringResource get() = Res.string.energy_a11y_minus
        val energyPlusCd: StringResource get() = Res.string.energy_a11y_plus
        val energySectionMovement: StringResource get() = Res.string.energy_section_movement
        val energySectionMovementType: StringResource get() = Res.string.energy_section_movement_type
        val energySectionMeals: StringResource get() = Res.string.energy_section_meals
        val energyMealsYes: StringResource get() = Res.string.energy_meals_yes
        val energyMealsNo: StringResource get() = Res.string.energy_meals_no
        val energyWaterNone: StringResource get() = Res.string.energy_water_none
        val energyWaterLow: StringResource get() = Res.string.energy_water_low
        val energyWaterGood: StringResource get() = Res.string.energy_water_good
        val energyWaterExcellent: StringResource get() = Res.string.energy_water_excellent
        // Movement
        val movementTitle: StringResource get() = Res.string.movement_title
        val movementSubtitle: StringResource get() = Res.string.movement_subtitle
        val movementSaveCd: StringResource get() = Res.string.movement_a11y_save
        val movementSectionWhat: StringResource get() = Res.string.movement_section_what
        val movementSectionDuration: StringResource get() = Res.string.movement_section_duration
        val movementSectionAfter: StringResource get() = Res.string.movement_section_after
        val movementSectionNotes: StringResource get() = Res.string.movement_section_notes
        val movementStreakGreat: StringResource get() = Res.string.movement_streak_great
        val movementStreakKeep: StringResource get() = Res.string.movement_streak_keep
        // SleepLog
        val sleepTitle: StringResource get() = Res.string.sleep_title
        val sleepSubtitleUpdate: StringResource get() = Res.string.sleep_subtitle_update
        val sleepSubtitleCreate: StringResource get() = Res.string.sleep_subtitle_create
        val sleepSaveCd: StringResource get() = Res.string.sleep_a11y_save
        val sleepQuestionBedtime: StringResource get() = Res.string.sleep_question_bedtime
        val sleepSaveUpdate: StringResource get() = Res.string.sleep_save_update
        val sleepSaveCreate: StringResource get() = Res.string.sleep_save_create
        val sleepTapEdit: StringResource get() = Res.string.sleep_tap_edit
        val sleepDialogConfirm: StringResource get() = Res.string.sleep_dialog_confirm
        val sleepDialogCancel: StringResource get() = Res.string.sleep_dialog_cancel
        val sleepQualityQuestion: StringResource get() = Res.string.sleep_quality_question
        val sleepDisturbanceQuestion: StringResource get() = Res.string.sleep_disturbance_question
        val sleepNoDisturbance: StringResource get() = Res.string.sleep_no_disturbance
        val sleepScreensQuestion: StringResource get() = Res.string.sleep_screens_question
        val sleepScreensSubtitle: StringResource get() = Res.string.sleep_screens_subtitle
        val sleepQuality1: StringResource get() = Res.string.sleep_quality_1
        val sleepQuality2: StringResource get() = Res.string.sleep_quality_2
        val sleepQuality3: StringResource get() = Res.string.sleep_quality_3
        val sleepQuality4: StringResource get() = Res.string.sleep_quality_4
        val sleepQuality5: StringResource get() = Res.string.sleep_quality_5
        // Inspiration
        val inspirationTitle: StringResource get() = Res.string.inspiration_screen_title
        val inspirationQuestion: StringResource get() = Res.string.inspiration_screen_question
        val inspirationSave: StringResource get() = Res.string.inspiration_save_btn
        val inspirationPracticeHint: StringResource get() = Res.string.inspiration_practice_hint
        // Reframe
        val reframeStep1Title: StringResource get() = Res.string.reframe_step_1_title
        val reframeStep2Title: StringResource get() = Res.string.reframe_step_2_title
        val reframeStep3Title: StringResource get() = Res.string.reframe_step_3_title
        val reframeStep1Subtitle: StringResource get() = Res.string.reframe_step_1_subtitle
        val reframeStep2Subtitle: StringResource get() = Res.string.reframe_step_2_subtitle
        val reframeStep3Subtitle: StringResource get() = Res.string.reframe_step_3_subtitle
        val reframeStepIndicator: StringResource get() = Res.string.reframe_step_indicator
        val reframePlaceholderThought: StringResource get() = Res.string.reframe_placeholder_thought
        val reframeEvidenceFor: StringResource get() = Res.string.reframe_evidence_for
        val reframeException: StringResource get() = Res.string.reframe_exception
        val reframeFriendQuestion: StringResource get() = Res.string.reframe_friend_question
        val reframeYearQuestion: StringResource get() = Res.string.reframe_year_question
        val reframeRewritePrompt: StringResource get() = Res.string.reframe_rewrite_prompt
        val reframePlaceholderRewrite: StringResource get() = Res.string.reframe_placeholder_rewrite
        val reframePlaceholderGeneric: StringResource get() = Res.string.reframe_placeholder_generic
        // Connection
        val connectionTitle: StringResource get() = Res.string.connection_title
        val connectionSubtitle: StringResource get() = Res.string.connection_subtitle
        val connectionQuestionType: StringResource get() = Res.string.connection_question_type
        val connectionWhoService: StringResource get() = Res.string.connection_who_service
        val connectionWhoQualityTime: StringResource get() = Res.string.connection_who_quality_time
        val connectionWhoGratitude: StringResource get() = Res.string.connection_who_gratitude
        val connectionToldThem: StringResource get() = Res.string.connection_q_told_them
        val connectionUnsolicited: StringResource get() = Res.string.connection_q_unsolicited
        val connectionQualityTimeTogetherQ: StringResource get() = Res.string.connection_q_quality_time_together
        val connectionGratefulFor: StringResource get() = Res.string.connection_q_grateful_for
        val connectionWhatDidYouDo: StringResource get() = Res.string.connection_q_what_did_you_do
        val connectionHowSpentTime: StringResource get() = Res.string.connection_q_how_spent_time
        val connectionToldThemYes: StringResource get() = Res.string.connection_told_them_yes
        val connectionRecent: StringResource get() = Res.string.connection_recent
        val connectionExpressed: StringResource get() = Res.string.connection_expressed
        val connectionDeleteCd: StringResource get() = Res.string.connection_a11y_delete
        val connectionRemoveCd: StringResource get() = Res.string.connection_a11y_remove
        val connectionAddCd: StringResource get() = Res.string.connection_a11y_add
        val connectionLabelGratitude: StringResource get() = Res.string.connection_label_gratitude
        val connectionLabelService: StringResource get() = Res.string.connection_label_service
        val connectionLabelQualityTime: StringResource get() = Res.string.connection_label_quality_time
        val connectionMonthlyReflection: StringResource get() = Res.string.connection_monthly_reflection
        val connectionRelationsNourish: StringResource get() = Res.string.connection_relations_nourish
        val connectionRelationsDrain: StringResource get() = Res.string.connection_relations_drain
        val connectionSave: StringResource get() = Res.string.connection_save
        val connectionClose: StringResource get() = Res.string.connection_close
    }

    /**
     * Garden activity expanded card (Tier 3.F) — long body description + 3 benefit
     * bullets per activity (19 activities × 4 strings = 76 keys + 2 chrome).
     * Activity name+desc come from `Strings.Garden.Activity.*`.
     */
    object GardenCard {
        val benefitsHeader: StringResource get() = Res.string.garden_card_benefits_header
        val startActivity: StringResource get() = Res.string.garden_card_start_activity
        // Per-activity (19 × 4): long body + 3 benefit bullets
        val diaryLong: StringResource get() = Res.string.garden_card_diary_long
        val diaryB1: StringResource get() = Res.string.garden_card_diary_b1
        val diaryB2: StringResource get() = Res.string.garden_card_diary_b2
        val diaryB3: StringResource get() = Res.string.garden_card_diary_b3
        val brainDumpLong: StringResource get() = Res.string.garden_card_brain_dump_long
        val brainDumpB1: StringResource get() = Res.string.garden_card_brain_dump_b1
        val brainDumpB2: StringResource get() = Res.string.garden_card_brain_dump_b2
        val brainDumpB3: StringResource get() = Res.string.garden_card_brain_dump_b3
        val gratitudeLong: StringResource get() = Res.string.garden_card_gratitude_long
        val gratitudeB1: StringResource get() = Res.string.garden_card_gratitude_b1
        val gratitudeB2: StringResource get() = Res.string.garden_card_gratitude_b2
        val gratitudeB3: StringResource get() = Res.string.garden_card_gratitude_b3
        val meditationLong: StringResource get() = Res.string.garden_card_meditation_long
        val meditationB1: StringResource get() = Res.string.garden_card_meditation_b1
        val meditationB2: StringResource get() = Res.string.garden_card_meditation_b2
        val meditationB3: StringResource get() = Res.string.garden_card_meditation_b3
        val reframingLong: StringResource get() = Res.string.garden_card_reframing_long
        val reframingB1: StringResource get() = Res.string.garden_card_reframing_b1
        val reframingB2: StringResource get() = Res.string.garden_card_reframing_b2
        val reframingB3: StringResource get() = Res.string.garden_card_reframing_b3
        val blocksLong: StringResource get() = Res.string.garden_card_blocks_long
        val blocksB1: StringResource get() = Res.string.garden_card_blocks_b1
        val blocksB2: StringResource get() = Res.string.garden_card_blocks_b2
        val blocksB3: StringResource get() = Res.string.garden_card_blocks_b3
        val recurringThoughtsLong: StringResource get() = Res.string.garden_card_recurring_thoughts_long
        val recurringThoughtsB1: StringResource get() = Res.string.garden_card_recurring_thoughts_b1
        val recurringThoughtsB2: StringResource get() = Res.string.garden_card_recurring_thoughts_b2
        val recurringThoughtsB3: StringResource get() = Res.string.garden_card_recurring_thoughts_b3
        val energyLong: StringResource get() = Res.string.garden_card_energy_long
        val energyB1: StringResource get() = Res.string.garden_card_energy_b1
        val energyB2: StringResource get() = Res.string.garden_card_energy_b2
        val energyB3: StringResource get() = Res.string.garden_card_energy_b3
        val sleepLong: StringResource get() = Res.string.garden_card_sleep_long
        val sleepB1: StringResource get() = Res.string.garden_card_sleep_b1
        val sleepB2: StringResource get() = Res.string.garden_card_sleep_b2
        val sleepB3: StringResource get() = Res.string.garden_card_sleep_b3
        val movementLong: StringResource get() = Res.string.garden_card_movement_long
        val movementB1: StringResource get() = Res.string.garden_card_movement_b1
        val movementB2: StringResource get() = Res.string.garden_card_movement_b2
        val movementB3: StringResource get() = Res.string.garden_card_movement_b3
        val dashboardLong: StringResource get() = Res.string.garden_card_dashboard_long
        val dashboardB1: StringResource get() = Res.string.garden_card_dashboard_b1
        val dashboardB2: StringResource get() = Res.string.garden_card_dashboard_b2
        val dashboardB3: StringResource get() = Res.string.garden_card_dashboard_b3
        val valuesLong: StringResource get() = Res.string.garden_card_values_long
        val valuesB1: StringResource get() = Res.string.garden_card_values_b1
        val valuesB2: StringResource get() = Res.string.garden_card_values_b2
        val valuesB3: StringResource get() = Res.string.garden_card_values_b3
        val ikigaiLong: StringResource get() = Res.string.garden_card_ikigai_long
        val ikigaiB1: StringResource get() = Res.string.garden_card_ikigai_b1
        val ikigaiB2: StringResource get() = Res.string.garden_card_ikigai_b2
        val ikigaiB3: StringResource get() = Res.string.garden_card_ikigai_b3
        val aweLong: StringResource get() = Res.string.garden_card_awe_long
        val aweB1: StringResource get() = Res.string.garden_card_awe_b1
        val aweB2: StringResource get() = Res.string.garden_card_awe_b2
        val aweB3: StringResource get() = Res.string.garden_card_awe_b3
        val silenceLong: StringResource get() = Res.string.garden_card_silence_long
        val silenceB1: StringResource get() = Res.string.garden_card_silence_b1
        val silenceB2: StringResource get() = Res.string.garden_card_silence_b2
        val silenceB3: StringResource get() = Res.string.garden_card_silence_b3
        val connectionsLong: StringResource get() = Res.string.garden_card_connections_long
        val connectionsB1: StringResource get() = Res.string.garden_card_connections_b1
        val connectionsB2: StringResource get() = Res.string.garden_card_connections_b2
        val connectionsB3: StringResource get() = Res.string.garden_card_connections_b3
        val inspirationLong: StringResource get() = Res.string.garden_card_inspiration_long
        val inspirationB1: StringResource get() = Res.string.garden_card_inspiration_b1
        val inspirationB2: StringResource get() = Res.string.garden_card_inspiration_b2
        val inspirationB3: StringResource get() = Res.string.garden_card_inspiration_b3
        val habitsLong: StringResource get() = Res.string.garden_card_habits_long
        val habitsB1: StringResource get() = Res.string.garden_card_habits_b1
        val habitsB2: StringResource get() = Res.string.garden_card_habits_b2
        val habitsB3: StringResource get() = Res.string.garden_card_habits_b3
        val environmentLong: StringResource get() = Res.string.garden_card_environment_long
        val environmentB1: StringResource get() = Res.string.garden_card_environment_b1
        val environmentB2: StringResource get() = Res.string.garden_card_environment_b2
        val environmentB3: StringResource get() = Res.string.garden_card_environment_b3
    }

    /**
     * Meditation feature (Phase 1 redesign). 5-phase wizard:
     * Welcome → Screening → Configure → Session → Overview.
     *
     * Sub-objects:
     * - [Welcome], [Screening], [Configure], [Session], [Stop], [Overview] — per-screen chrome
     * - [Technique] — 6 evidence-based breathing techniques (name + short + summary + mechanism + 3 coach lines)
     * - [Goal], [Experience], [Audio] — Configure choices
     * - [Risk] — 8 medical screening flags (label + sub)
     * - [Cue] — pacer overlay words (Breathe in/Hold/Breathe out/Arrive/Release/Breathe)
     * - [SettleCoach], [IntegrateCoach] — sub-phase coach line rotation (3 each)
     * - [Time] — duration formatting
     *
     * Domain enum mappers (resolve enum → StringResource at UI layer to keep
     * `core/util` free of resource dependencies):
     * - [techniqueName], [techniqueShort], [techniqueSummary], [techniqueMechanism], [techniqueCoach]
     * - [goalLabel], [experienceLabel], [audioLabel]
     * - [riskLabel], [riskSub]
     */
    object Meditation {

        // ── Welcome (13) ────────────────────────────────────────────────
        object Welcome {
            val topbar: StringResource get() = Res.string.meditation_welcome_topbar
            val pill: StringResource get() = Res.string.meditation_welcome_pill
            val title: StringResource get() = Res.string.meditation_welcome_title
            val lede: StringResource get() = Res.string.meditation_welcome_lede
            val cardTitle: StringResource get() = Res.string.meditation_welcome_card_title
            val b1Title: StringResource get() = Res.string.meditation_welcome_b1_title
            val b1Sub: StringResource get() = Res.string.meditation_welcome_b1_sub
            val b2Title: StringResource get() = Res.string.meditation_welcome_b2_title
            val b2Sub: StringResource get() = Res.string.meditation_welcome_b2_sub
            val b3Title: StringResource get() = Res.string.meditation_welcome_b3_title
            val b3Sub: StringResource get() = Res.string.meditation_welcome_b3_sub
            val fineprint: StringResource get() = Res.string.meditation_welcome_fineprint
            val cta: StringResource get() = Res.string.meditation_welcome_cta
        }

        // ── Screening (10) ──────────────────────────────────────────────
        object Screening {
            val topbar: StringResource get() = Res.string.meditation_screening_topbar
            val title: StringResource get() = Res.string.meditation_screening_title
            val lede: StringResource get() = Res.string.meditation_screening_lede
            val bannerLead: StringResource get() = Res.string.meditation_screening_banner_lead
            val bannerBody: StringResource get() = Res.string.meditation_screening_banner_body
            val noneApply: StringResource get() = Res.string.meditation_screening_none_apply
            val noneApplySub: StringResource get() = Res.string.meditation_screening_none_apply_sub
            val warnLead: StringResource get() = Res.string.meditation_screening_warn_lead
            val warnBody: StringResource get() = Res.string.meditation_screening_warn_body
            val fineprint: StringResource get() = Res.string.meditation_screening_fineprint
            val continueBtn: StringResource get() = Res.string.meditation_screening_continue
        }

        // ── Configure (~28) ─────────────────────────────────────────────
        object Configure {
            val topbar: StringResource get() = Res.string.meditation_configure_topbar
            val title: StringResource get() = Res.string.meditation_configure_title
            val lockpill: StringResource get() = Res.string.meditation_configure_lockpill
            val cardDuration: StringResource get() = Res.string.meditation_configure_card_duration
            val cardDurationSub: StringResource get() = Res.string.meditation_configure_card_duration_sub
            val durationMinSuffix: StringResource get() = Res.string.meditation_duration_min_suffix
            val cardGoal: StringResource get() = Res.string.meditation_configure_card_goal
            val cardGoalSub: StringResource get() = Res.string.meditation_configure_card_goal_sub
            val cardExperience: StringResource get() = Res.string.meditation_configure_card_experience
            val firstTimeBanner: StringResource get() = Res.string.meditation_configure_first_time_banner
            val cardAudio: StringResource get() = Res.string.meditation_configure_card_audio
            val cardTechnique: StringResource get() = Res.string.meditation_configure_card_technique
            val techSubOverridable: StringResource get() = Res.string.meditation_configure_tech_sub_overridable
            val techSubRestricted: StringResource get() = Res.string.meditation_configure_tech_sub_restricted
            val techSubFirstTime: StringResource get() = Res.string.meditation_configure_tech_sub_first_time
            val techAutoPill: StringResource get() = Res.string.meditation_configure_tech_auto_pill
            val tech478Cap: StringResource get() = Res.string.meditation_configure_tech_478_cap
            val fineprint: StringResource get() = Res.string.meditation_configure_fineprint
            val redoScreeningLink: StringResource get() = Res.string.meditation_configure_redo_screening_link
            val cta: StringResource get() = Res.string.meditation_configure_cta
        }

        // ── Goal labels (5) ─────────────────────────────────────────────
        object Goal {
            val stress: StringResource get() = Res.string.meditation_goal_stress
            val focus: StringResource get() = Res.string.meditation_goal_focus
            val sleep: StringResource get() = Res.string.meditation_goal_sleep
            val anxiety: StringResource get() = Res.string.meditation_goal_anxiety
            val grounding: StringResource get() = Res.string.meditation_goal_grounding
        }

        // ── Experience labels (3) ───────────────────────────────────────
        object Experience {
            val first: StringResource get() = Res.string.meditation_exp_first
            val occasional: StringResource get() = Res.string.meditation_exp_occasional
            val regular: StringResource get() = Res.string.meditation_exp_regular
        }

        // ── Audio labels (3) ────────────────────────────────────────────
        object Audio {
            val voice: StringResource get() = Res.string.meditation_audio_voice
            val chimes: StringResource get() = Res.string.meditation_audio_chimes
            val silent: StringResource get() = Res.string.meditation_audio_silent
        }

        // ── Risk flags (8 × 2 = 16) ─────────────────────────────────────
        object Risk {
            val pregnancy: StringResource get() = Res.string.meditation_risk_pregnancy
            val pregnancySub: StringResource get() = Res.string.meditation_risk_pregnancy_sub
            val cardio: StringResource get() = Res.string.meditation_risk_cardio
            val cardioSub: StringResource get() = Res.string.meditation_risk_cardio_sub
            val respiratory: StringResource get() = Res.string.meditation_risk_respiratory
            val epilepsy: StringResource get() = Res.string.meditation_risk_epilepsy
            val panic: StringResource get() = Res.string.meditation_risk_panic
            val recentSurgery: StringResource get() = Res.string.meditation_risk_recent_surgery
            val eye: StringResource get() = Res.string.meditation_risk_eye
            val driving: StringResource get() = Res.string.meditation_risk_driving
        }

        // ── Techniques (6 × 6 fields = 36) ──────────────────────────────
        object Technique {
            // Coherent
            val coherentName: StringResource get() = Res.string.meditation_tech_coherent_name
            val coherentShort: StringResource get() = Res.string.meditation_tech_coherent_short
            val coherentSummary: StringResource get() = Res.string.meditation_tech_coherent_summary
            val coherentMechanism: StringResource get() = Res.string.meditation_tech_coherent_mechanism
            val coherentCoach1: StringResource get() = Res.string.meditation_tech_coherent_coach1
            val coherentCoach2: StringResource get() = Res.string.meditation_tech_coherent_coach2
            val coherentCoach3: StringResource get() = Res.string.meditation_tech_coherent_coach3
            // Extended exhale
            val exhaleName: StringResource get() = Res.string.meditation_tech_exhale_name
            val exhaleShort: StringResource get() = Res.string.meditation_tech_exhale_short
            val exhaleSummary: StringResource get() = Res.string.meditation_tech_exhale_summary
            val exhaleMechanism: StringResource get() = Res.string.meditation_tech_exhale_mechanism
            val exhaleCoach1: StringResource get() = Res.string.meditation_tech_exhale_coach1
            val exhaleCoach2: StringResource get() = Res.string.meditation_tech_exhale_coach2
            val exhaleCoach3: StringResource get() = Res.string.meditation_tech_exhale_coach3
            // Box
            val boxName: StringResource get() = Res.string.meditation_tech_box_name
            val boxShort: StringResource get() = Res.string.meditation_tech_box_short
            val boxSummary: StringResource get() = Res.string.meditation_tech_box_summary
            val boxMechanism: StringResource get() = Res.string.meditation_tech_box_mechanism
            val boxCoach1: StringResource get() = Res.string.meditation_tech_box_coach1
            val boxCoach2: StringResource get() = Res.string.meditation_tech_box_coach2
            val boxCoach3: StringResource get() = Res.string.meditation_tech_box_coach3
            // 4-7-8
            val w478Name: StringResource get() = Res.string.meditation_tech_478_name
            val w478Short: StringResource get() = Res.string.meditation_tech_478_short
            val w478Summary: StringResource get() = Res.string.meditation_tech_478_summary
            val w478Mechanism: StringResource get() = Res.string.meditation_tech_478_mechanism
            val w478Coach1: StringResource get() = Res.string.meditation_tech_478_coach1
            val w478Coach2: StringResource get() = Res.string.meditation_tech_478_coach2
            val w478Coach3: StringResource get() = Res.string.meditation_tech_478_coach3
            // Belly
            val bellyName: StringResource get() = Res.string.meditation_tech_belly_name
            val bellyShort: StringResource get() = Res.string.meditation_tech_belly_short
            val bellySummary: StringResource get() = Res.string.meditation_tech_belly_summary
            val bellyMechanism: StringResource get() = Res.string.meditation_tech_belly_mechanism
            val bellyCoach1: StringResource get() = Res.string.meditation_tech_belly_coach1
            val bellyCoach2: StringResource get() = Res.string.meditation_tech_belly_coach2
            val bellyCoach3: StringResource get() = Res.string.meditation_tech_belly_coach3
            // Body scan
            val scanName: StringResource get() = Res.string.meditation_tech_scan_name
            val scanShort: StringResource get() = Res.string.meditation_tech_scan_short
            val scanSummary: StringResource get() = Res.string.meditation_tech_scan_summary
            val scanMechanism: StringResource get() = Res.string.meditation_tech_scan_mechanism
            val scanCoach1: StringResource get() = Res.string.meditation_tech_scan_coach1
            val scanCoach2: StringResource get() = Res.string.meditation_tech_scan_coach2
            val scanCoach3: StringResource get() = Res.string.meditation_tech_scan_coach3
        }

        // ── Session phase labels + cues (15) ────────────────────────────
        object Session {
            val phaseSettling: StringResource get() = Res.string.meditation_session_phase_settling
            val phasePractice: StringResource get() = Res.string.meditation_session_phase_practice
            val phaseIntegration: StringResource get() = Res.string.meditation_session_phase_integration
            val paused: StringResource get() = Res.string.meditation_session_paused
            val stopButton: StringResource get() = Res.string.meditation_session_stop_button
            val a11yPause: StringResource get() = Res.string.meditation_a11y_pause
            val a11yResume: StringResource get() = Res.string.meditation_a11y_resume
            val a11yStop: StringResource get() = Res.string.meditation_a11y_stop_session
            val audioVoice: StringResource get() = Res.string.meditation_session_audio_voice
            val audioChimes: StringResource get() = Res.string.meditation_session_audio_chimes
            val metaTemplate: StringResource get() = Res.string.meditation_session_meta_template
        }

        object Cue {
            val breatheIn: StringResource get() = Res.string.meditation_cue_breathe_in
            val hold: StringResource get() = Res.string.meditation_cue_hold
            val breatheOut: StringResource get() = Res.string.meditation_cue_breathe_out
            val arrive: StringResource get() = Res.string.meditation_cue_arrive
            val release: StringResource get() = Res.string.meditation_cue_release
            val breathe: StringResource get() = Res.string.meditation_cue_breathe
        }

        object SettleCoach {
            val line1: StringResource get() = Res.string.meditation_settle_coach1
            val line2: StringResource get() = Res.string.meditation_settle_coach2
            val line3: StringResource get() = Res.string.meditation_settle_coach3
        }

        object IntegrateCoach {
            val line1: StringResource get() = Res.string.meditation_integrate_coach1
            val line2: StringResource get() = Res.string.meditation_integrate_coach2
            val line3: StringResource get() = Res.string.meditation_integrate_coach3
        }

        // ── Stop modal (4) ──────────────────────────────────────────────
        object Stop {
            val title: StringResource get() = Res.string.meditation_stop_title
            val body: StringResource get() = Res.string.meditation_stop_body
            val keep: StringResource get() = Res.string.meditation_stop_keep
            val end: StringResource get() = Res.string.meditation_stop_end
        }

        // ── Overview (~18) ──────────────────────────────────────────────
        object Overview {
            val topbar: StringResource get() = Res.string.meditation_overview_topbar
            val pill: StringResource get() = Res.string.meditation_overview_pill
            val titleDone: StringResource get() = Res.string.meditation_overview_title_done
            val titleStopped: StringResource get() = Res.string.meditation_overview_title_stopped
            val ledeWithCycles: StringResource get() = Res.string.meditation_overview_lede_with_cycles
            val ledeNoCycles: StringResource get() = Res.string.meditation_overview_lede_no_cycles
            val cardNoticeTitle: StringResource get() = Res.string.meditation_overview_card_notice_title
            val noticeB1: StringResource get() = Res.string.meditation_overview_notice_b1
            val noticeB2: StringResource get() = Res.string.meditation_overview_notice_b2
            val noticeB3: StringResource get() = Res.string.meditation_overview_notice_b3
            val cardPracticeTitle: StringResource get() = Res.string.meditation_overview_card_practice_title
            val cardPracticeBody: StringResource get() = Res.string.meditation_overview_card_practice_body
            val cardDailyTitle: StringResource get() = Res.string.meditation_overview_card_daily_title
            val dailyB1Title: StringResource get() = Res.string.meditation_overview_daily_b1_title
            val dailyB1Sub: StringResource get() = Res.string.meditation_overview_daily_b1_sub
            val dailyB2Title: StringResource get() = Res.string.meditation_overview_daily_b2_title
            val dailyB2SubTemplate: StringResource get() = Res.string.meditation_overview_daily_b2_sub_template
            val banner: StringResource get() = Res.string.meditation_overview_banner
            val btnDifferent: StringResource get() = Res.string.meditation_overview_btn_different
            val btnRedo: StringResource get() = Res.string.meditation_overview_btn_redo
        }

        // ── Time formatting (3) ─────────────────────────────────────────
        object Time {
            val minutesOnly: StringResource get() = Res.string.meditation_time_minutes_only
            val minSec: StringResource get() = Res.string.meditation_time_min_sec
            val secondsOnly: StringResource get() = Res.string.meditation_time_seconds_only
        }
    }

    /**
     * Percorso/Journey interior (`PercorsoScreen.kt`). Top weekly card,
     * map section, 4 pillars, per-pillar stats with plural suffixes.
     * Activity name labels reuse `Strings.Garden.Activity.*` from Tier 2.A.
     */
    object Percorso {
        val weeklyProgress: StringResource get() = Res.string.percorso_weekly_progress
        val journeyMap: StringResource get() = Res.string.percorso_journey_map
        val pillarMind: StringResource get() = Res.string.percorso_pillar_mind
        val pillarBody: StringResource get() = Res.string.percorso_pillar_body
        val pillarSpirit: StringResource get() = Res.string.percorso_pillar_spirit
        val pillarHabits: StringResource get() = Res.string.percorso_pillar_habits
        val statSessionsOne: StringResource get() = Res.string.percorso_stat_sessions_one
        val statSessionsMany: StringResource get() = Res.string.percorso_stat_sessions_many
        val statFactsOne: StringResource get() = Res.string.percorso_stat_facts_one
        val statFactsMany: StringResource get() = Res.string.percorso_stat_facts_many
        val statActsOne: StringResource get() = Res.string.percorso_stat_acts_one
        val statActsMany: StringResource get() = Res.string.percorso_stat_acts_many
        val statHabitsOne: StringResource get() = Res.string.percorso_stat_habits_one
        val statHabitsMany: StringResource get() = Res.string.percorso_stat_habits_many
        val statProgressFraction: StringResource get() = Res.string.percorso_stat_progress_fraction
        val statToDiscover: StringResource get() = Res.string.percorso_stat_to_discover
        val statConfirmed: StringResource get() = Res.string.percorso_stat_confirmed
        val statToday: StringResource get() = Res.string.percorso_stat_today
        val statTotal: StringResource get() = Res.string.percorso_stat_total
    }

    /**
     * Block screen content. `BlockType` enum (in `core/util`) intentionally has
     * NO localized field — UI sites resolve `Strings.Block.typeXxx`/`suggestionXxx`
     * via inline `when (type) -> ...` to avoid `core/util -> core/ui` upward dep.
     */
    object Block {
        // BlockType labels (used as chip + diagnosis title + history card title).
        val typeFearOfFailure: StringResource get() = Res.string.block_type_fear_of_failure
        val typeOverload: StringResource get() = Res.string.block_type_overload
        val typeLimitingBelief: StringResource get() = Res.string.block_type_limiting_belief
        val typeCreativeBlock: StringResource get() = Res.string.block_type_creative_block
        val typeUnknown: StringResource get() = Res.string.block_type_unknown
        // Eve coaching sentence per type (DiagnosisStep).
        val suggestionFearOfFailure: StringResource get() = Res.string.block_suggestion_fear_of_failure
        val suggestionOverload: StringResource get() = Res.string.block_suggestion_overload
        val suggestionLimitingBelief: StringResource get() = Res.string.block_suggestion_limiting_belief
        val suggestionCreativeBlock: StringResource get() = Res.string.block_suggestion_creative_block
        val suggestionUnknown: StringResource get() = Res.string.block_suggestion_unknown
        // DescribeStep chrome.
        val describeTitle: StringResource get() = Res.string.block_describe_title
        val describeBody: StringResource get() = Res.string.block_describe_body
        val activeHint: StringResource get() = Res.string.block_active_hint
        val eveSays: StringResource get() = Res.string.block_eve_says
        // ActionStep chrome.
        val actionStepTitle: StringResource get() = Res.string.block_action_step_title
        val actionStepSubtitle: StringResource get() = Res.string.block_action_step_subtitle
        // Reusable action card titles.
        val actionBrainDumpTitle: StringResource get() = Res.string.block_action_brain_dump_title
        val actionMeditationTitle: StringResource get() = Res.string.block_action_meditation_title
        val actionReframeThoughtTitle: StringResource get() = Res.string.block_action_reframe_thought_title
        val actionReframeShortTitle: StringResource get() = Res.string.block_action_reframe_short_title
        // Per-type action subtitles (the hint under each ActionItem).
        val actionOverloadBrainDumpSubtitle: StringResource get() = Res.string.block_action_overload_brain_dump_subtitle
        val actionOverloadMeditationSubtitle: StringResource get() = Res.string.block_action_overload_meditation_subtitle
        val actionLimitingReframeSubtitle: StringResource get() = Res.string.block_action_limiting_reframe_subtitle
        val actionLimitingBrainDumpSubtitle: StringResource get() = Res.string.block_action_limiting_brain_dump_subtitle
        val actionFearReframeSubtitle: StringResource get() = Res.string.block_action_fear_reframe_subtitle
        val actionFearMeditationSubtitle: StringResource get() = Res.string.block_action_fear_meditation_subtitle
        val actionCreativeBrainDumpSubtitle: StringResource get() = Res.string.block_action_creative_brain_dump_subtitle
        val actionCreativeMeditationSubtitle: StringResource get() = Res.string.block_action_creative_meditation_subtitle
        val actionUnknownBrainDumpSubtitle: StringResource get() = Res.string.block_action_unknown_brain_dump_subtitle
        val actionUnknownReframeSubtitle: StringResource get() = Res.string.block_action_unknown_reframe_subtitle
        val actionUnknownMeditationSubtitle: StringResource get() = Res.string.block_action_unknown_meditation_subtitle
        // HistoryStep empty + resolution note.
        val historyEmptyTitle: StringResource get() = Res.string.block_history_empty_title
        val historyEmptySubtitle: StringResource get() = Res.string.block_history_empty_subtitle
        val resolutionNotePrefix: StringResource get() = Res.string.block_resolution_note_prefix
    }

    /** PsychologicalProfile.Trend enum localized labels + descriptive sentences. */
    object Trend {
        val improving: StringResource get() = Res.string.trend_improving
        val stable: StringResource get() = Res.string.trend_stable
        val declining: StringResource get() = Res.string.trend_declining
        val insufficientData: StringResource get() = Res.string.trend_insufficient_data
        val msgImproving: StringResource get() = Res.string.trend_msg_improving
        val msgDeclining: StringResource get() = Res.string.trend_msg_declining
        val msgStable: StringResource get() = Res.string.trend_msg_stable
        val msgInsufficient: StringResource get() = Res.string.trend_msg_insufficient
    }

    /** Info tooltip bottom-sheet content (10 wellness concept tooltips). */
    object Tooltip {
        val cognitivePatternsTitle: StringResource get() = Res.string.tooltip_cognitive_patterns_title
        val cognitivePatternsBody: StringResource get() = Res.string.tooltip_cognitive_patterns_body
        val sleepMoodTitle: StringResource get() = Res.string.tooltip_sleep_mood_title
        val sleepMoodBody: StringResource get() = Res.string.tooltip_sleep_mood_body
        val sdtTitle: StringResource get() = Res.string.tooltip_sdt_title
        val sdtBody: StringResource get() = Res.string.tooltip_sdt_body
        val wellbeingTrendTitle: StringResource get() = Res.string.tooltip_wellbeing_trend_title
        val wellbeingTrendBody: StringResource get() = Res.string.tooltip_wellbeing_trend_body
        val emotionalIntensityTitle: StringResource get() = Res.string.tooltip_emotional_intensity_title
        val emotionalIntensityBody: StringResource get() = Res.string.tooltip_emotional_intensity_body
        val stressLevelTitle: StringResource get() = Res.string.tooltip_stress_level_title
        val stressLevelBody: StringResource get() = Res.string.tooltip_stress_level_body
        val calmAnxietyTitle: StringResource get() = Res.string.tooltip_calm_anxiety_title
        val calmAnxietyBody: StringResource get() = Res.string.tooltip_calm_anxiety_body
        val guidedBreathingTitle: StringResource get() = Res.string.tooltip_guided_breathing_title
        val guidedBreathingBody: StringResource get() = Res.string.tooltip_guided_breathing_body
        val habitStackingTitle: StringResource get() = Res.string.tooltip_habit_stacking_title
        val habitStackingBody: StringResource get() = Res.string.tooltip_habit_stacking_body
        val minimumActionTitle: StringResource get() = Res.string.tooltip_minimum_action_title
        val minimumActionBody: StringResource get() = Res.string.tooltip_minimum_action_body
    }

    /** Thread options sheet (save/hide/mute/block/report). */
    object ThreadOptions {
        val save: StringResource get() = Res.string.thread_options_save
        val hide: StringResource get() = Res.string.thread_options_hide
        val mute: StringResource get() = Res.string.thread_options_mute
        val block: StringResource get() = Res.string.thread_options_block
        val report: StringResource get() = Res.string.thread_options_report
        val reportSent: StringResource get() = Res.string.thread_options_report_sent
    }

    /**
     * Coach mark / onboarding tutorial strings.
     * Backing data: ScreenTutorials.kt (composable functions returning List<CoachMarkStep>).
     */
    object Coach {
        val buttonNext: StringResource get() = Res.string.coach_button_next
        val buttonGotIt: StringResource get() = Res.string.coach_button_got_it

        object Home {
            val step1Title: StringResource get() = Res.string.coach_home_step1_title
            val step1Desc: StringResource get() = Res.string.coach_home_step1_desc
            val step2Title: StringResource get() = Res.string.coach_home_step2_title
            val step2Desc: StringResource get() = Res.string.coach_home_step2_desc
            val step3Title: StringResource get() = Res.string.coach_home_step3_title
            val step3Desc: StringResource get() = Res.string.coach_home_step3_desc
            val step4Title: StringResource get() = Res.string.coach_home_step4_title
            val step4Desc: StringResource get() = Res.string.coach_home_step4_desc
        }
    }

    // Shared a11y across multiple features
    object SharedA11y {
        val verifiedBadge: StringResource get() = Res.string.a11y_verified_badge
        val reply: StringResource get() = Res.string.a11y_reply
        val repost: StringResource get() = Res.string.a11y_repost
        val share: StringResource get() = Res.string.a11y_share
        val options: StringResource get() = Res.string.a11y_options
        val copyCode: StringResource get() = Res.string.a11y_copy_code
        val appLogo: StringResource get() = Res.string.a11y_app_logo
        val image: StringResource get() = Res.string.a11y_image_generic
    }

    /**
     * Bio-Signal transparency dashboard (Phase 2.UI, 2026-05-11).
     * Surfaces what the app has stored locally + what's been pushed to the
     * server, with one-tap export (GDPR Art.20) + delete-all (Art.17).
     */
    object BioContext {
        val topbar: StringResource get() = Res.string.bio_context_topbar
        val subtitle: StringResource get() = Res.string.bio_context_subtitle

        // Provider status
        val providerNotInstalled: StringResource get() = Res.string.bio_provider_not_installed
        val providerNotSupported: StringResource get() = Res.string.bio_provider_not_supported
        val providerNeedsPermission: StringResource get() = Res.string.bio_provider_needs_permission
        val providerReady: StringResource get() = Res.string.bio_provider_ready
        val providerInstallCta: StringResource get() = Res.string.bio_provider_install_cta

        // Inventory
        val inventoryTitle: StringResource get() = Res.string.bio_inventory_title
        val inventoryEmpty: StringResource get() = Res.string.bio_inventory_empty
        val totalSamples: StringResource get() = Res.string.bio_total_samples

        // Sources
        val sourcesTitle: StringResource get() = Res.string.bio_sources_title
        val sourcesEmpty: StringResource get() = Res.string.bio_sources_empty
        val sourceViaApp: StringResource get() = Res.string.bio_source_via_app

        // Sync state
        val lastSync: StringResource get() = Res.string.bio_last_sync

        // Server section
        val serverTitle: StringResource get() = Res.string.bio_server_title
        val serverPending: StringResource get() = Res.string.bio_server_pending
        val serverNeverRaw: StringResource get() = Res.string.bio_server_never_raw

        // Data type labels — used inline by type chip rendering
        val typeHeartRate: StringResource get() = Res.string.bio_type_heart_rate
        val typeHrv: StringResource get() = Res.string.bio_type_hrv
        val typeSleep: StringResource get() = Res.string.bio_type_sleep
        val typeSteps: StringResource get() = Res.string.bio_type_steps
        val typeRestingHeartRate: StringResource get() = Res.string.bio_type_resting_heart_rate
        val typeOxygenSaturation: StringResource get() = Res.string.bio_type_oxygen_saturation
        val typeActivity: StringResource get() = Res.string.bio_type_activity

        // Privacy actions
        val actionRefresh: StringResource get() = Res.string.bio_action_refresh
        val actionIngest: StringResource get() = Res.string.bio_action_ingest
        val actionExport: StringResource get() = Res.string.bio_action_export
        val actionDelete: StringResource get() = Res.string.bio_action_delete

        // Delete confirmation dialog
        val deleteDialogTitle: StringResource get() = Res.string.bio_delete_dialog_title
        val deleteDialogBody: StringResource get() = Res.string.bio_delete_dialog_body
        val deleteDialogConfirm: StringResource get() = Res.string.bio_delete_dialog_confirm

        // Toasts
        val toastSynced: StringResource get() = Res.string.bio_toast_synced
        val toastPartial: StringResource get() = Res.string.bio_toast_partial
        val toastDeleted: StringResource get() = Res.string.bio_toast_deleted

        // Privacy statement
        val statementTitle: StringResource get() = Res.string.bio_statement_title
        val statementNoSellAds: StringResource get() = Res.string.bio_statement_no_sell_ads
    }

    /**
     * Bio-Signal Onboarding (Phase 3, 2026-05-11) — 5-step dedicated pager.
     * Pager order: Intro → DataTypes → Why → Permission → Confirm.
     */
    object BioOnboarding {
        // Chrome
        val skip: StringResource get() = Res.string.bio_onb_skip
        val back: StringResource get() = Res.string.bio_onb_back
        val cont: StringResource get() = Res.string.bio_onb_continue
        val stepLabel: StringResource get() = Res.string.bio_onb_step_label

        // Step 1 — Intro
        val introEyebrow: StringResource get() = Res.string.bio_onb_intro_eyebrow
        val introTitle: StringResource get() = Res.string.bio_onb_intro_title
        val introBody: StringResource get() = Res.string.bio_onb_intro_body
        val introFineprint: StringResource get() = Res.string.bio_onb_intro_fineprint
        val introCta: StringResource get() = Res.string.bio_onb_intro_cta

        // Step 2 — DataTypes
        val typesTitle: StringResource get() = Res.string.bio_onb_types_title
        val typesSubtitle: StringResource get() = Res.string.bio_onb_types_subtitle
        val typeHrExplain: StringResource get() = Res.string.bio_onb_type_hr_explain
        val typeHrvExplain: StringResource get() = Res.string.bio_onb_type_hrv_explain
        val typeSleepExplain: StringResource get() = Res.string.bio_onb_type_sleep_explain
        val typeStepsExplain: StringResource get() = Res.string.bio_onb_type_steps_explain
        val typeRestingHrExplain: StringResource get() = Res.string.bio_onb_type_resting_hr_explain
        val typeSpo2Explain: StringResource get() = Res.string.bio_onb_type_spo2_explain
        val typeActivityExplain: StringResource get() = Res.string.bio_onb_type_activity_explain

        // Step 3 — Why
        val whyTitle: StringResource get() = Res.string.bio_onb_why_title
        val whyCard1Title: StringResource get() = Res.string.bio_onb_why_card1_title
        val whyCard1Body: StringResource get() = Res.string.bio_onb_why_card1_body
        val whyCard2Title: StringResource get() = Res.string.bio_onb_why_card2_title
        val whyCard2Body: StringResource get() = Res.string.bio_onb_why_card2_body
        val whyCard3Title: StringResource get() = Res.string.bio_onb_why_card3_title
        val whyCard3Body: StringResource get() = Res.string.bio_onb_why_card3_body

        // Step 4 — Permission
        val permTitle: StringResource get() = Res.string.bio_onb_perm_title
        val permBody: StringResource get() = Res.string.bio_onb_perm_body
        val permInstallNeeded: StringResource get() = Res.string.bio_onb_perm_install_needed
        val permInstallCta: StringResource get() = Res.string.bio_onb_perm_install_cta
        val permGrantCta: StringResource get() = Res.string.bio_onb_perm_grant_cta
        val permGrantedSummary: StringResource get() = Res.string.bio_onb_perm_granted_summary

        // Step 5 — Confirm
        val confirmTitle: StringResource get() = Res.string.bio_onb_confirm_title
        val confirmBody: StringResource get() = Res.string.bio_onb_confirm_body
        val confirmSkippedTitle: StringResource get() = Res.string.bio_onb_confirm_skipped_title
        val confirmSkippedBody: StringResource get() = Res.string.bio_onb_confirm_skipped_body
        val confirmCta: StringResource get() = Res.string.bio_onb_confirm_cta
    }

    /**
     * Bio-Signal Confidence atoms (Phase 5, 2026-05-16) — chip + footer used by
     * every contextual bio card to surface source + reliability. Per
     * `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` Decision 2: DataConfidence
     * always visible.
     */
    object BioConfidence {
        val chipA11y: StringResource get() = Res.string.bio_conf_chip_a11y
        val footerTemplate: StringResource get() = Res.string.bio_conf_footer_template
        val unknownDevice: StringResource get() = Res.string.bio_conf_unknown_device
        val levelHigh: StringResource get() = Res.string.bio_conf_level_high
        val levelMedium: StringResource get() = Res.string.bio_conf_level_medium
        val levelLow: StringResource get() = Res.string.bio_conf_level_low

        fun levelLabel(level: com.lifo.util.model.ConfidenceLevel): StringResource = when (level) {
            com.lifo.util.model.ConfidenceLevel.HIGH -> levelHigh
            com.lifo.util.model.ConfidenceLevel.MEDIUM -> levelMedium
            com.lifo.util.model.ConfidenceLevel.LOW -> levelLow
        }
    }

    /**
     * Bio-Signal contextual cards inside host features (Phase 5, 2026-05-16).
     * These keys live in `:core:ui` so all hosts (home/journal/meditation/insight)
     * draw from the same vocabulary. Grammar from
     * `design/biosignal/Calmify BioContextual Cards.html`.
     */
    object BioCard {
        // Card 3 — Home Today narrative
        val homeNarrativeSleepHr: StringResource get() = Res.string.bio_card_home_narrative_sleep_hr
        val homeNarrativeSleepOnly: StringResource get() = Res.string.bio_card_home_narrative_sleep_only
        val homeNarrativeHrOnly: StringResource get() = Res.string.bio_card_home_narrative_hr_only
        val homeNarrativeStepsOnly: StringResource get() = Res.string.bio_card_home_narrative_steps_only
        val homeChipSleepDuration: StringResource get() = Res.string.bio_card_home_chip_sleep_duration
        val homeChipHrBpm: StringResource get() = Res.string.bio_card_home_chip_hr_bpm
        val homeChipSteps: StringResource get() = Res.string.bio_card_home_chip_steps
        val homeOpenA11y: StringResource get() = Res.string.bio_card_home_open_a11y
        val homeOpenTrailing: StringResource get() = Res.string.bio_card_home_open_trailing

        // Phase 6.2 — personalized range-hint suffix appended to the narrative
        val homeHintWithin: StringResource get() = Res.string.bio_card_home_hint_within
        val homeHintBelow: StringResource get() = Res.string.bio_card_home_hint_below
        val homeHintAbove: StringResource get() = Res.string.bio_card_home_hint_above

        fun homeHintFor(hint: com.lifo.util.model.BioRangeHint): StringResource = when (hint) {
            com.lifo.util.model.BioRangeHint.WITHIN -> homeHintWithin
            com.lifo.util.model.BioRangeHint.BELOW -> homeHintBelow
            com.lifo.util.model.BioRangeHint.ABOVE -> homeHintAbove
        }

        // Card 1 — Journal composer banner (Phase 5.2)
        val journalShortNight: StringResource get() = Res.string.bio_card_journal_short_night
        val journalSolidRest: StringResource get() = Res.string.bio_card_journal_solid_rest
        val journalSleepDurationInline: StringResource get() = Res.string.bio_card_journal_sleep_duration_inline
    }

    /** Shared atom for any slim contextual bio banner — dismiss a11y label. */
    object BioBanner {
        val dismissA11y: StringResource get() = Res.string.bio_banner_dismiss_a11y
    }

    /** PRO gate atom — non-hostile lock used inline beneath FREE bio cards. */
    object BioProLock {
        val proChip: StringResource get() = Res.string.bio_pro_chip
        val a11yTemplate: StringResource get() = Res.string.bio_pro_lock_a11y_template
        val actionUpgrade: StringResource get() = Res.string.bio_pro_lock_action_upgrade
    }

    /**
     * Meditation outro bio card (Phase 5.3, Card 2). Lives in `:features:meditation`
     * but the keys live here for facade consistency.
     */
    object BioMeditation {
        val cardTitle: StringResource get() = Res.string.bio_medi_card_title
        val cardScope: StringResource get() = Res.string.bio_medi_card_scope
        val narrativeDrop: StringResource get() = Res.string.bio_medi_narrative_drop
        val narrativeStable: StringResource get() = Res.string.bio_medi_narrative_stable
        val chartA11y: StringResource get() = Res.string.bio_medi_chart_a11y
        val hrvGateCopy: StringResource get() = Res.string.bio_medi_hrv_gate_copy
        val clockMinSec: StringResource get() = Res.string.bio_medi_clock_min_sec
    }

    /**
     * Cross-signal correlation card (Phase 5.4, Card 4). PRO-only, currently
     * surfaces in Home below Community until an Insight pattern feed exists.
     */
    object BioCrossSignal {
        val cardTitle: StringResource get() = Res.string.bio_xs_card_title
        val cardMeta: StringResource get() = Res.string.bio_xs_card_meta
        val patternLabel: StringResource get() = Res.string.bio_xs_pattern_label
        val narrative: StringResource get() = Res.string.bio_xs_narrative
        val rowMedLabel: StringResource get() = Res.string.bio_xs_row_med_label
        val rowMedSublabel: StringResource get() = Res.string.bio_xs_row_med_sublabel
        val rowMedValue: StringResource get() = Res.string.bio_xs_row_med_value
        val rowMedDelta: StringResource get() = Res.string.bio_xs_row_med_delta
        val rowHrvLabel: StringResource get() = Res.string.bio_xs_row_hrv_label
        val rowHrvSublabel: StringResource get() = Res.string.bio_xs_row_hrv_sublabel
        val rowHrvValue: StringResource get() = Res.string.bio_xs_row_hrv_value
        val rowHrvDelta: StringResource get() = Res.string.bio_xs_row_hrv_delta
        val barsA11y: StringResource get() = Res.string.bio_xs_bars_a11y
        val dismissA11y: StringResource get() = Res.string.bio_xs_dismiss_a11y
    }
}
