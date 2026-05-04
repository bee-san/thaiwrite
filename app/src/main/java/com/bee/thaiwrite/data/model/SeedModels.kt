package com.bee.thaiwrite.data.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ItemType {
    CONSONANT,
    VOWEL,
    TONE,
    WORD,
}

enum class LessonKind {
    SYMBOL_BATCH,
    WORD_BRIDGE,
    ALPHABET_COMPLETION,
    ;

    companion object {
        fun fromJson(value: String): LessonKind = when (value.lowercase()) {
            "symbol_batch" -> SYMBOL_BATCH
            "word_bridge" -> WORD_BRIDGE
            "alphabet_completion" -> ALPHABET_COMPLETION
            else -> error("Unknown lesson kind: $value")
        }
    }
}

enum class TeachingMode {
    BUILD,
    PREVIEW,
    ;

    companion object {
        fun fromJson(value: String): TeachingMode = when (value.lowercase()) {
            "build" -> BUILD
            "preview" -> PREVIEW
            else -> error("Unknown teaching mode: $value")
        }
    }
}

data class LessonIntroSeed(
    val whatThisIs: String,
    val howItBehaves: String,
    val whyItMatters: String,
    val example: String,
)

data class ItemComponentSeed(
    val itemId: String,
    val note: String,
)

data class StudyItemSeed(
    val id: String,
    val lessonId: String,
    val sortOrder: Int,
    val type: ItemType,
    val thai: String,
    val transliteration: String,
    val english: String,
    val audioText: String,
    val prompt: String,
    val category: String?,
    val teachingNote: String?,
    val teachingMode: TeachingMode?,
    val components: List<ItemComponentSeed>,
)

data class LessonSeed(
    val id: String,
    val order: Int,
    val stage: String,
    val kind: LessonKind,
    val title: String,
    val description: String,
    val intro: LessonIntroSeed,
    val itemIds: List<String>,
)

data class GuideSeed(
    val itemId: String,
    val guideType: String,
    val tip: String,
)

data class SeedBundle(
    val lessons: List<LessonSeed>,
    val items: List<StudyItemSeed>,
    val guides: Map<String, GuideSeed>,
)

object SeedLoader {
    fun load(context: Context): SeedBundle {
        val lessonsRoot = context.assets.open("curriculum.json").bufferedReader().use { it.readText() }
        val itemsRoot = context.assets.open("items.json").bufferedReader().use { it.readText() }
        val guidesRoot = context.assets.open("trace_guides.json").bufferedReader().use { it.readText() }

        val lessons = JSONObject(lessonsRoot).getJSONArray("lessons").mapObjects { json ->
            LessonSeed(
                id = json.getString("id"),
                order = json.getInt("order"),
                stage = json.getString("stage"),
                kind = LessonKind.fromJson(json.getString("kind")),
                title = json.getString("title"),
                description = json.getString("description"),
                intro = json.getJSONObject("intro").let { intro ->
                    LessonIntroSeed(
                        whatThisIs = intro.getString("whatThisIs"),
                        howItBehaves = intro.getString("howItBehaves"),
                        whyItMatters = intro.getString("whyItMatters"),
                        example = intro.getString("example"),
                    )
                },
                itemIds = json.getJSONArray("itemIds").mapStrings(),
            )
        }.sortedBy { it.order }

        val items = JSONObject(itemsRoot).getJSONArray("items").mapObjects { json ->
            StudyItemSeed(
                id = json.getString("id"),
                lessonId = json.getString("lessonId"),
                sortOrder = json.getInt("sortOrder"),
                type = ItemType.valueOf(json.getString("type")),
                thai = json.getString("thai"),
                transliteration = json.getString("transliteration"),
                english = json.getString("english"),
                audioText = json.getString("audioText"),
                prompt = json.getString("prompt"),
                category = json.optStringOrNull("category"),
                teachingNote = json.optStringOrNull("teachingNote"),
                teachingMode = json.optStringOrNull("teachingMode")?.let(TeachingMode::fromJson),
                components = json.optJSONArray("components")?.mapObjects { component ->
                    ItemComponentSeed(
                        itemId = component.getString("itemId"),
                        note = component.getString("note"),
                    )
                } ?: emptyList(),
            )
        }.sortedBy { it.sortOrder }

        val guides = JSONObject(guidesRoot).getJSONArray("guides").mapObjects { json ->
            GuideSeed(
                itemId = json.getString("itemId"),
                guideType = json.getString("guideType"),
                tip = json.getString("tip"),
            )
        }.associateBy { it.itemId }

        return SeedBundle(
            lessons = lessons,
            items = items,
            guides = guides,
        )
    }
}

private inline fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
    buildList(length()) {
        for (index in 0 until length()) {
            add(block(getJSONObject(index)))
        }
    }

private fun JSONArray.mapStrings(): List<String> =
    buildList(length()) {
        for (index in 0 until length()) {
            add(getString(index))
        }
    }

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).takeUnless { it.isBlank() }
