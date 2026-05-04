package com.bee.thaiwrite.data.repo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumSeedIntegrityTest {
    private val lessons = loadLessons()
    private val items = loadItems()
    private val guides = loadGuides()
    private val itemsById = items.associateBy { it.id }
    private val lessonOrderById = lessons.associate { it.id to it.order }

    @Test
    fun `second lesson is already a word bridge`() {
        assertEquals("word_bridge", lessons[1].kind)
    }

    @Test
    fun `word bridges stay between three and five useful words`() {
        val allowedCategories = setOf("Movement", "Essentials", "Home", "Family", "Politeness", "Questions")

        lessons.filter { it.kind == "word_bridge" }.forEach { lesson ->
            assertTrue(lesson.itemIds.size in 3..5)
            lesson.itemIds.forEach { itemId ->
                val item = itemsById.getValue(itemId)
                assertEquals("WORD", item.type)
                assertTrue(item.category in allowedCategories)
            }
        }
    }

    @Test
    fun `build words use only taught components and preview words add one later feature`() {
        items.filter { it.type == "WORD" }.forEach { word ->
            val wordLessonOrder = lessonOrderById.getValue(word.lessonId)
            val laterComponents = word.components.count { component ->
                val componentItem = itemsById.getValue(component)
                lessonOrderById.getValue(componentItem.lessonId) >= wordLessonOrder
            }

            when (word.teachingMode) {
                "build" -> assertEquals(0, laterComponents)
                "preview" -> assertEquals(1, laterComponents)
                else -> error("Unexpected teaching mode for ${word.id}: ${word.teachingMode}")
            }
        }
    }

    @Test
    fun `opening utility track omits obsolete letters until alphabet completion`() {
        val rareIds = setOf(
            "kho_khuat",
            "kho_khon",
            "do_chada",
            "to_patak",
            "tho_than",
            "tho_montho",
            "tho_phuthao",
            "no_nen",
            "so_sala",
            "so_ruesi",
            "lo_chula",
        )
        val utilityTrackIds = lessons
            .takeWhile { it.kind != "alphabet_completion" }
            .flatMap { it.itemIds }
            .toSet()

        assertFalse(utilityTrackIds.any { it in rareIds })
    }

    @Test
    fun `all word components reference defined items`() {
        items.filter { it.type == "WORD" }.forEach { word ->
            assertTrue(word.components.isNotEmpty())
            word.components.forEach { componentId ->
                assertTrue("Missing component $componentId for ${word.id}", itemsById.containsKey(componentId))
            }
        }
    }

    @Test
    fun `trace guides reference current items and cover every study item`() {
        val guideIds = guides.map { it.itemId }
        val guideIdSet = guideIds.toSet()
        val itemIdSet = items.map { it.id }.toSet()

        assertEquals("Guide IDs must be unique", guideIds.size, guideIdSet.size)
        assertEquals("Guides must cover exactly the current study items", itemIdSet, guideIdSet)
        guides.forEach { guide ->
            assertTrue("Missing guide type for ${guide.itemId}", guide.guideType.isNotBlank())
            assertTrue("Missing guide tip for ${guide.itemId}", guide.tip.isNotBlank())
        }
    }

    private fun loadLessons(): List<TestLesson> {
        val text = assetFile("curriculum.json").readText()
        val pattern = Regex(
            "\\{\\s*\"id\":\\s*\"([^\"]+)\"[\\s\\S]*?\"order\":\\s*(\\d+)[\\s\\S]*?\"kind\":\\s*\"([^\"]+)\"[\\s\\S]*?\"itemIds\":\\s*\\[([^\\]]*)\\]\\s*\\}",
        )
        return pattern.findAll(text).map { match ->
            TestLesson(
                id = match.groupValues[1],
                order = match.groupValues[2].toInt(),
                kind = match.groupValues[3],
                itemIds = Regex("\"([^\"]+)\"").findAll(match.groupValues[4]).map { it.groupValues[1] }.toList(),
            )
        }.toList().sortedBy { it.order }
    }

    private fun loadItems(): List<TestItem> =
        assetFile("items.json")
            .readLines()
            .map { it.trim() }
            .filter { it.startsWith("{ \"id\":") }
            .map { line ->
                TestItem(
                    id = capture(line, "\"id\":\\s*\"([^\"]+)\""),
                    lessonId = capture(line, "\"lessonId\":\\s*\"([^\"]+)\""),
                    type = capture(line, "\"type\":\\s*\"([^\"]+)\""),
                    category = captureOrNull(line, "\"category\":\\s*\"([^\"]+)\""),
                    teachingMode = captureOrNull(line, "\"teachingMode\":\\s*\"([^\"]+)\""),
                    components = Regex("\"itemId\":\\s*\"([^\"]+)\"").findAll(line).map { it.groupValues[1] }.toList(),
                )
            }

    private fun loadGuides(): List<TestGuide> =
        assetFile("trace_guides.json")
            .readLines()
            .map { it.trim() }
            .filter { it.startsWith("{ \"itemId\":") }
            .map { line ->
                TestGuide(
                    itemId = capture(line, "\"itemId\":\\s*\"([^\"]+)\""),
                    guideType = capture(line, "\"guideType\":\\s*\"([^\"]+)\""),
                    tip = capture(line, "\"tip\":\\s*\"([^\"]+)\""),
                )
            }

    private fun capture(line: String, pattern: String): String =
        requireNotNull(Regex(pattern).find(line)) { "Missing pattern $pattern in $line" }.groupValues[1]

    private fun captureOrNull(line: String, pattern: String): String? =
        Regex(pattern).find(line)?.groupValues?.get(1)

    private fun assetFile(name: String): File {
        val moduleRelative = File("src/main/assets/$name")
        if (moduleRelative.exists()) {
            return moduleRelative
        }
        return File("app/src/main/assets/$name")
    }

    private data class TestLesson(
        val id: String,
        val order: Int,
        val kind: String,
        val itemIds: List<String>,
    )

    private data class TestItem(
        val id: String,
        val lessonId: String,
        val type: String,
        val category: String?,
        val teachingMode: String?,
        val components: List<String>,
    )

    private data class TestGuide(
        val itemId: String,
        val guideType: String,
        val tip: String,
    )
}
