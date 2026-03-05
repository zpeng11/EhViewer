package com.hippo.ehviewer.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import com.ehviewer.core.model.BaseGalleryInfo
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.client.data.FavListUrlBuilder
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class FavoritesViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    val urlBuilder by savedStateHandle.saved(MutableStateSerializer()) {
        mutableStateOf(FavListUrlBuilder(favCat = FavListUrlBuilder.FAV_CAT_LOCAL))
    }

    init {
        if (!urlBuilder.value.isLocal) {
            urlBuilder.value = FavListUrlBuilder(favCat = FavListUrlBuilder.FAV_CAT_LOCAL, keyword = urlBuilder.value.keyword)
        }
    }

    val localFavCount = EhDB.localFavCount
    val localFavoriteFolders = EhDB.localFavoriteFolders

    fun extraFavCount(slot: Int) = EhDB.localFavCount(slot)

    val data = snapshotFlow { urlBuilder.value }.flatMapLatest { builder ->
        val keywordNow = builder.keyword.orEmpty()
        val extraSlot = builder.localExtraSlot
        Pager(PagingConfig(20, jumpThreshold = 40)) {
            when {
                extraSlot != null && keywordNow.isBlank() -> EhDB.localFavLazyList(extraSlot)
                extraSlot != null -> EhDB.searchLocalFav(extraSlot, keywordNow)
                keywordNow.isBlank() -> EhDB.localFavLazyList
                else -> EhDB.searchLocalFav(keywordNow)
            }
        }.flow.map { data -> data.map<_, BaseGalleryInfo> { it } }
    }.cachedIn(viewModelScope)
}
