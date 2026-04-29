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

    /** Time-of-day journal prompts. */
    object JournalPrompt {
        val weekendMorning: StringResource get() = Res.string.journal_prompt_weekend_morning
        val afternoonPause: StringResource get() = Res.string.journal_prompt_afternoon_pause
        val eveningCalm: StringResource get() = Res.string.journal_prompt_evening_calm
    }

    /** Connection feature labels. */
    object Connection {
        val qualityTimeTitle: StringResource get() = Res.string.connection_quality_time_title
        val qualityTimeLabel: StringResource get() = Res.string.connection_quality_time_label
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
}
