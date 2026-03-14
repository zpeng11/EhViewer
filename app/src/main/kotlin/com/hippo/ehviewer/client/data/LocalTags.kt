package com.hippo.ehviewer.client.data

import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.model.GalleryTag
import com.ehviewer.core.model.GalleryTagGroup
import com.ehviewer.core.model.PowerStatus
import com.ehviewer.core.model.TagNamespace
import com.ehviewer.core.model.VoteStatus

private class LocalTagBuilder {
    private val groups = linkedMapOf<TagNamespace, LinkedHashMap<String, PowerStatus>>()

    fun add(namespace: TagNamespace, text: String, power: PowerStatus = PowerStatus.Active) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        val group = groups.getOrPut(namespace) { linkedMapOf() }
        val current = group[normalized]
        if (current == null || current == PowerStatus.Weak && power != PowerStatus.Weak) {
            group[normalized] = power
        }
    }

    fun addLanguage(code: String?) {
        languageTagForCode(code)?.let { add(TagNamespace.Language, it) }
    }

    fun addSimpleTag(raw: String) {
        val weak = raw.startsWith('_')
        val normalized = raw.removePrefix("_")
        val parts = normalized.split(':', limit = 2)
        if (parts.size != 2) return
        val namespace = TagNamespace.from(parts[0]) ?: return
        add(namespace, parts[1], if (weak) PowerStatus.Weak else PowerStatus.Active)
    }

    fun build() = groups.mapNotNull { (namespace, tags) ->
        tags.takeIf { it.isNotEmpty() }?.map { (text, power) ->
            GalleryTag(text = text, power = power, vote = VoteStatus.None)
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
    simpleTags?.forEach(::addSimpleTag)
    addLanguage(simpleLanguage)
}.build()

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
