package com.hippo.ehviewer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ehviewer.core.database.model.LocalFavoriteFolder
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.GalleryInfo.Companion.LOCAL_FAVORITED
import com.ehviewer.core.model.GalleryInfo.Companion.NOT_FAVORITED
import com.hippo.ehviewer.EhDB

fun resolveFavoriteName(
    favoriteSlot: Int,
    extraFavoriteNameMap: Map<Int, String>,
    localFavoriteName: String,
): String? = when (favoriteSlot) {
    NOT_FAVORITED -> null
    LOCAL_FAVORITED -> localFavoriteName
    in LocalFavoriteFolder.VALID_SLOT_RANGE -> extraFavoriteNameMap[favoriteSlot] ?: localFavoriteName
    else -> null
}

@Composable
fun rememberFavoriteNameResolver(): (Int) -> String? {
    val localFavoriteName = stringResource(R.string.local_favorites)
    val localFavoriteFolders by remember { EhDB.localFavoriteFolders }.collectAsState(emptyList())
    val extraFavoriteNameMap = remember(localFavoriteFolders) {
        localFavoriteFolders.associate { it.slot to it.name }
    }
    return remember(extraFavoriteNameMap, localFavoriteName) {
        { favoriteSlot -> resolveFavoriteName(favoriteSlot, extraFavoriteNameMap, localFavoriteName) }
    }
}
