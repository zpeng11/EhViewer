package com.hippo.ehviewer.client.data

import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.model.GalleryTag
import com.ehviewer.core.model.GalleryTagGroup
import com.ehviewer.core.model.PowerStatus
import com.ehviewer.core.model.TagNamespace
import com.ehviewer.core.model.VoteStatus

private const val TAG_ORIGINAL = "original"

private class LocalTagBuilder {
    private val groups = linkedMapOf<TagNamespace, LinkedHashSet<String>>()

    fun add(namespace: TagNamespace, text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        groups.getOrPut(namespace) { linkedSetOf() }.add(normalized)
    }

    fun addLanguage(code: String?) {
        languageTagForCode(code)?.let { add(TagNamespace.Language, it) }
    }

    fun addSimpleTag(raw: String) {
        val normalized = raw.removePrefix("_")
        val parts = normalized.split(':', limit = 2)
        if (parts.size != 2) return
        val namespace = TagNamespace.from(parts[0]) ?: return
        val tag = parts[1]
        when (namespace) {
            TagNamespace.Artist,
            TagNamespace.Character,
            TagNamespace.Female,
            TagNamespace.Group,
            TagNamespace.Language,
            TagNamespace.Male,
            TagNamespace.Mixed,
            -> add(namespace, tag)
            TagNamespace.Cosplayer -> add(TagNamespace.Artist, tag)
            TagNamespace.Parody -> if (tag != TAG_ORIGINAL) add(TagNamespace.Parody, tag)
            else -> Unit
        }
    }

    fun build() = groups.mapNotNull { (namespace, tags) ->
        tags.takeIf { it.isNotEmpty() }?.map { text ->
            GalleryTag(text = text, power = PowerStatus.Active, vote = VoteStatus.None)
        }?.let { GalleryTagGroup(namespace = namespace, tags = it) }
    }
}

internal fun languageTagForCode(code: String?): String? {
    val normalized = code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val index = GalleryInfo.S_LANGS.indexOf(normalized)
    return GalleryInfo.S_LANG_TAGS.getOrNull(index)?.substringAfter(':')
}

internal fun simpleTagsToTagGroups(
    simpleTags: List<String>?,
    simpleLanguage: String?,
): List<GalleryTagGroup> = LocalTagBuilder().apply {
    // Keep fallback tags inside the subset ComicInfo can round-trip.
    simpleTags?.forEach(::addSimpleTag)
    addLanguage(simpleLanguage)
}.build()

internal fun needsComicInfoTagNormalization(simpleTags: List<String>?): Boolean = simpleTags?.any { raw ->
    val weak = raw.startsWith('_')
    val normalized = raw.removePrefix("_")
    val parts = normalized.split(':', limit = 2)
    if (parts.size != 2) return@any true
    val namespace = TagNamespace.from(parts[0]) ?: return@any true
    val tag = parts[1]
    when (namespace) {
        TagNamespace.Artist,
        TagNamespace.Character,
        TagNamespace.Female,
        TagNamespace.Group,
        TagNamespace.Language,
        TagNamespace.Male,
        TagNamespace.Mixed,
        -> weak
        TagNamespace.Cosplayer -> true
        TagNamespace.Parody -> weak || tag == TAG_ORIGINAL
        else -> true
    }
} == true

internal fun List<GalleryTagGroup>.toSimpleTagStrings(): List<String>? = asSequence()
    .flatMap { group ->
        group.tags.asSequence().map { tag ->
            buildString {
                append(group.namespace.value)
                append(':')
                append(tag.text)
            }
        }
    }
    .toList()
    .ifEmpty { null }
