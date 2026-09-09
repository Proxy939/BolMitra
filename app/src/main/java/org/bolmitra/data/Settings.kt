package org.bolmitra.data

import android.content.Context
import android.content.SharedPreferences
import org.bolmitra.speech.TargetLanguage

/**
 * Persisted app preferences.
 *
 * ### Why `SharedPreferences` and not DataStore
 *
 * DataStore is the current recommendation and it would mean a new dependency, a coroutine/Flow surface
 * and a proto or preferences schema, for perhaps a dozen scalar values read once per screen.
 * `SharedPreferences` needs no dependency at all, is already on every supported API level, and its one
 * real drawback — a synchronous first read — is a single small file here, not a hazard.
 *
 * ### Why these settings and not the ones the screen used to show
 *
 * Every control on the Settings screen was `remember { mutableStateOf(...) }`: it moved, then reset on
 * the next recomposition scope loss, and nothing downstream read it. Rebuilding them as real
 * preferences meant deciding which ones can *mean* anything in an offline app, and several cannot:
 *
 *  - **"Download content on Wi-Fi only"** is gone. The app holds no `INTERNET` permission and makes no
 *    network calls by design (R6). A toggle governing downloads that cannot happen is a claim that
 *    they can.
 *  - **"Content Pack Updates" / "New Worksheets Available" notifications** are gone for the same
 *    reason: there is no server to notify from, and no notification channel is registered.
 *  - **"Translation Mode: Phrasebook-first"** is gone. `TurnOrchestrator` is phrasebook-first by
 *    construction, and §4.5 makes that a safety property rather than a preference — offering to change
 *    it would offer to put machine output ahead of reviewed content.
 *
 * What remains either changes behaviour or is honest about being cosmetic.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---- behaviour-changing -------------------------------------------------------------------

    /**
     * Target language restored on launch, so a Santali teacher does not re-pick it every morning.
     * Falls back to the default when the stored name no longer exists.
     */
    var defaultLanguage: TargetLanguage
        get() = prefs.getString(KEY_LANGUAGE, null)
            ?.let { name -> runCatching { TargetLanguage.valueOf(name) }.getOrNull() }
            ?: TargetLanguage.DEFAULT
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.name).apply()

    /**
     * Whether completed turns are written to the database at all.
     *
     * Real, and load-bearing: with this off nothing is persisted, so History and Recordings stay empty
     * and no audio is written to disk. That is the setting a teacher who does not want children's
     * voices retained actually needs, and §6.9.4 makes it worth having rather than assuming.
     */
    var storeHistory: Boolean
        get() = prefs.getBoolean(KEY_STORE_HISTORY, true)
        set(value) = prefs.edit().putBoolean(KEY_STORE_HISTORY, value).apply()

    /**
     * Whether the teacher's microphone audio is kept alongside the translation.
     *
     * Separate from [storeHistory] on purpose. The text record of a lesson and a recording of children
     * in a classroom are different privacy questions, and collapsing them into one switch would force
     * a teacher to give up the lesson history to avoid keeping audio.
     */
    var keepRecordings: Boolean
        get() = prefs.getBoolean(KEY_KEEP_RECORDINGS, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_RECORDINGS, value).apply()

    /** Haptic confirmation on the mic button. Off by default; some tablets have no motor. */
    var hapticFeedback: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, false)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    /** Screen shown on launch. */
    var startupScreen: String
        get() = prefs.getString(KEY_STARTUP, DEFAULT_STARTUP) ?: DEFAULT_STARTUP
        set(value) = prefs.edit().putString(KEY_STARTUP, value).apply()

    /**
     * Text scale applied to the app's typography.
     *
     * Clamped on read as well as write. A stored value outside this range — from a bad write or a
     * downgrade — would otherwise render the app unusable, and Settings is the one screen a teacher
     * would need to use to fix it.
     */
    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        set(value) = prefs.edit()
            .putFloat(KEY_FONT_SCALE, value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE))
            .apply()

    /** Cosmetic, and labelled as such in the UI. */
    var showTips: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TIPS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TIPS, value).apply()

    /** Restores every value above to its default. Backs "Reset Settings". */
    fun resetAll() {
        prefs.edit().clear().apply()
    }

    /** Everything currently stored, for a diagnostics dump. */
    fun snapshot(): Map<String, Any?> = mapOf(
        "defaultLanguage" to defaultLanguage.name,
        "storeHistory" to storeHistory,
        "keepRecordings" to keepRecordings,
        "hapticFeedback" to hapticFeedback,
        "startupScreen" to startupScreen,
        "fontScale" to fontScale,
        "showTips" to showTips,
    )

    companion object {
        private const val NAME = "bolmitra-settings"

        private const val KEY_LANGUAGE = "default_language"
        private const val KEY_STORE_HISTORY = "store_history"
        private const val KEY_KEEP_RECORDINGS = "keep_recordings"
        private const val KEY_HAPTIC = "haptic_feedback"
        private const val KEY_STARTUP = "startup_screen"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_SHOW_TIPS = "show_tips"

        const val DEFAULT_STARTUP = "HOME"

        /**
         * Font-scale bounds.
         *
         * 0.85 rather than smaller because §6.13 sets a 16 sp floor on the one box a teacher has to
         * read across a classroom, and scaling below this would breach it. 1.30 rather than more
         * because the Live Class layout is a fixed three-column grid and text wraps out of its card
         * beyond that — a limit worth stating rather than discovering.
         */
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.30f

        /** The steps the UI offers, with their labels. */
        val FONT_SCALE_STEPS: List<Pair<String, Float>> = listOf(
            "Small" to 0.85f,
            "Medium" to 1.00f,
            "Large" to 1.15f,
            "Largest" to 1.30f,
        )

        /**
         * Self-check for the clamp.
         *
         * A stored font scale outside the range would render the app unreadable, and the screen needed
         * to repair it is the one that would be unreadable. Cheap to assert, expensive to discover.
         */
        fun validate(settings: Settings): List<String> = buildList {
            val original = settings.fontScale
            try {
                settings.fontScale = 9.0f
                if (settings.fontScale > MAX_FONT_SCALE) add("font scale was not clamped upward")
                settings.fontScale = 0.1f
                if (settings.fontScale < MIN_FONT_SCALE) add("font scale was not clamped downward")
                FONT_SCALE_STEPS.forEach { (label, v) ->
                    if (v < MIN_FONT_SCALE || v > MAX_FONT_SCALE) {
                        add("offered step '$label' ($v) is outside the clamp")
                    }
                }
            } finally {
                settings.fontScale = original
            }
        }
    }
}
