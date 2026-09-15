package cc.stkmn.shareparser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.stkmn.shareparser.data.AppLanguage
import java.util.Locale

object AppLocale {
    var language: AppLanguage by mutableStateOf(AppLanguage.SYSTEM)
        private set

    fun apply(context: Context, language: AppLanguage) {
        this.language = language
    }

    fun isGerman(): Boolean = when (language) {
        AppLanguage.DE -> true
        AppLanguage.EN -> false
        AppLanguage.SYSTEM -> Locale.getDefault().language == "de"
    }

    fun text(german: String, english: String): String = if (isGerman()) german else english
}
