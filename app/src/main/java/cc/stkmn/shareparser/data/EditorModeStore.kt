package cc.stkmn.shareparser.data

import android.content.Context

class EditorModeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun activate(profileId: String) {
        preferences.edit()
            .putString(KEY_PROFILE_ID, profileId)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun activeProfileId(): String? = preferences
        .getString(KEY_PROFILE_ID, null)
        ?.takeIf { it.isNotBlank() }

    fun clear(profileId: String? = null) {
        if (profileId != null && activeProfileId() != profileId) return
        preferences.edit().remove(KEY_PROFILE_ID).remove(KEY_UPDATED_AT).apply()
    }

    companion object {
        private const val FILE_NAME = "editor_mode"
        private const val KEY_PROFILE_ID = "profile_id"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}
