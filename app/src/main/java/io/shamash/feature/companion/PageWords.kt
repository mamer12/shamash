package io.shamash.feature.companion

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import io.shamash.R
import java.util.Locale

/**
 * The words of the phone page, in the phone's language (CMP-16): English or Arabic resources whatever the TV's own
 * language is. Each key is used as `{{key}}` in the page and as `T.key` in its script.
 */
internal object PhoneWords {
    /** The app's resources in [language] ("ar" or "en"). */
    fun resources(context: Context, language: String): Resources {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(config).resources
    }

    fun page(res: Resources): Map<String, String> = KEYS.mapValues { (_, id) -> res.getString(id) }

    private val KEYS: Map<String, Int> = mapOf(
        "lang" to R.string.companion_page_lang,
        "dir" to R.string.companion_page_dir,
        "title" to R.string.companion_page_title,
        "intro" to R.string.companion_page_intro,
        "pin" to R.string.companion_page_pin,
        "text" to R.string.companion_page_text,
        "send" to R.string.companion_page_send,
        "sending" to R.string.companion_page_sending,
        "offline" to R.string.companion_page_offline,
        "wrong" to R.string.companion_wrong_pin,
        "change" to R.string.companion_page_change_pin,
        "locked" to R.string.companion_locked,
        "profile" to R.string.cmp_page_profile,
        "tab_send" to R.string.cmp_page_tab_send,
        "tab_type" to R.string.cmp_page_tab_type,
        "tab_rows" to R.string.cmp_page_tab_rows,
        "tab_teams" to R.string.cmp_page_tab_teams,
        "tab_feeds" to R.string.cmp_page_tab_feeds,
        "tab_backup" to R.string.cmp_page_tab_backup,
        "open_now" to R.string.cmp_page_open_now,
        "add_queue" to R.string.cmp_page_add_queue,
        "queued" to R.string.cmp_page_queued,
        "profile_pin" to R.string.cmp_page_profile_pin,
        "owner_pin" to R.string.cmp_page_owner_pin,
        "go" to R.string.cmp_page_go,
        "profile_pin_wrong" to R.string.cmp_page_profile_pin_wrong,
        "profile_locked" to R.string.cmp_page_profile_locked,
        "refused" to R.string.cmp_page_refused,
        "type_intro" to R.string.cmp_page_type_intro,
        "type_none" to R.string.cmp_page_type_none,
        "type_send" to R.string.cmp_page_type_send,
        "filled" to R.string.cmp_page_filled,
        "no_field" to R.string.cmp_page_no_field,
        "refresh" to R.string.cmp_page_refresh,
        "rows_intro" to R.string.cmp_page_rows_intro,
        "up" to R.string.cmp_page_up,
        "down" to R.string.cmp_page_down,
        "hide" to R.string.cmp_page_hide,
        "show" to R.string.cmp_page_show,
        "pin_row" to R.string.cmp_page_pin_row,
        "unpin" to R.string.cmp_page_unpin,
        "rename" to R.string.cmp_page_rename,
        "save" to R.string.cmp_page_save,
        "saved" to R.string.cmp_page_saved,
        "rows_invalid" to R.string.cmp_page_rows_invalid,
        "teams_intro" to R.string.cmp_page_teams_intro,
        "competitions" to R.string.cmp_page_competitions,
        "teams_invalid" to R.string.cmp_page_teams_invalid,
        "feeds_intro" to R.string.cmp_page_feeds_intro,
        "feed_name" to R.string.cmp_page_feed_name,
        "feed_url" to R.string.cmp_page_feed_url,
        "feed_kind" to R.string.cmp_page_feed_kind,
        "feed_style" to R.string.cmp_page_feed_style,
        "feed_level" to R.string.cmp_page_feed_level,
        "feed_http" to R.string.cmp_page_feed_http,
        "style_wide" to R.string.cmp_page_style_wide,
        "style_video" to R.string.cmp_page_style_video,
        "style_poster" to R.string.cmp_page_style_poster,
        "level_all" to R.string.cmp_page_level_all,
        "level_family" to R.string.cmp_page_level_family,
        "level_kids" to R.string.cmp_page_level_kids,
        "feed_try" to R.string.cmp_page_feed_try,
        "feed_add" to R.string.cmp_page_feed_add,
        "feed_remove" to R.string.cmp_page_feed_remove,
        "added" to R.string.cmp_page_added,
        "removed" to R.string.cmp_page_removed,
        "feed_url_bad" to R.string.cmp_page_feed_url_bad,
        "feed_private" to R.string.cmp_page_feed_private,
        "feed_full" to R.string.cmp_page_feed_full,
        "feed_title_bad" to R.string.cmp_page_feed_title_bad,
        "feed_unreadable" to R.string.cmp_page_feed_unreadable,
        "feed_missing" to R.string.cmp_page_feed_missing,
        "no_feeds" to R.string.cmp_page_no_feeds,
        "backup_intro" to R.string.cmp_page_backup_intro,
        "export" to R.string.cmp_page_export,
        "import" to R.string.cmp_page_import,
        "passphrase" to R.string.cmp_page_passphrase,
        "passphrase_again" to R.string.cmp_page_passphrase_again,
        "mismatch" to R.string.cmp_page_mismatch,
        "include_keys" to R.string.cmp_page_include_keys,
        "include_pins" to R.string.cmp_page_include_pins,
        "confirm_tv" to R.string.cmp_page_confirm_tv,
        "exported" to R.string.cmp_page_exported,
        "imported" to R.string.cmp_page_imported,
        "declined" to R.string.cmp_page_declined,
        "busy" to R.string.cmp_page_busy,
        "wait" to R.string.cmp_page_wait,
        "file" to R.string.cmp_page_file,
        "backup_weak_passphrase" to R.string.cmp_page_backup_weak_passphrase,
        "backup_too_large" to R.string.cmp_page_backup_too_large,
        "backup_not_a_backup" to R.string.cmp_page_backup_not_a_backup,
        "backup_newer_version" to R.string.cmp_page_backup_newer_version,
        "backup_wrong_passphrase_or_damaged" to R.string.cmp_page_backup_wrong_passphrase_or_damaged,
        "backup_invalid" to R.string.cmp_page_backup_invalid,
        "import_failed" to R.string.cmp_page_import_failed,
        "invalid" to R.string.cmp_page_invalid,
        "failed" to R.string.cmp_page_failed,
        "too_large" to R.string.cmp_page_too_large,
        "queue_invalid" to R.string.cmp_page_queue_invalid,
    )
}
