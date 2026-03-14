/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.client.data

import com.ehviewer.core.model.GalleryDetail
import com.ehviewer.core.model.GalleryInfo
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhFilter

private val LANGUAGES = arrayOf(
    "English",
    "Chinese",
    "Spanish",
    "Korean",
    "Russian",
    "French",
    "Portuguese",
    "Thai",
    "German",
    "Italian",
    "Vietnamese",
    "Polish",
    "Hungarian",
    "Dutch",
)

suspend fun GalleryDetail.fillInfo(updateSimpleTags: Boolean = true) {
    val index = LANGUAGES.indexOf(language)
    if (index != -1) simpleLanguage = GalleryInfo.S_LANGS[index]
    if (updateSimpleTags) {
        simpleTags = tagGroups.toSimpleTagStrings()
    }
    favoriteSlot = EhDB.getLocalFavoriteSlot(gid)
}

suspend fun GalleryDetail.filterComments() {
    comments = with(comments) {
        val scoreThreshold = Settings.commentThreshold.value
        copy(
            comments = comments.filter {
                it.uploader ||
                    it.score > scoreThreshold &&
                    !EhFilter.filterCommenter(it.user.orEmpty()) &&
                    !EhFilter.filterComment(it.comment)
            },
        )
    }
}
