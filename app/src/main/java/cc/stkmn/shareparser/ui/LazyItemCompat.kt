package cc.stkmn.shareparser.ui

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable

/** Renders grouped content inside one already-created lazy-list item. */
@Composable
internal fun LazyItemScope.item(content: @Composable () -> Unit) {
    content()
}
