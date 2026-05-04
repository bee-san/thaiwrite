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
)

data class LessonSeed(
    val id: String,
    val order: Int,
    val stage: String,
    val title: String,
    val description: String,
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
                title = json.getString("title"),
                description = json.getString("description"),
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
