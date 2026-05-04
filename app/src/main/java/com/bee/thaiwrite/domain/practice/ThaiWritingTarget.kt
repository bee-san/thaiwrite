package com.bee.thaiwrite.domain.practice

import com.bee.thaiwrite.data.model.ItemType
import com.bee.thaiwrite.data.model.StudyItemSeed

data class ThaiWritingTarget(
    val displayText: String,
    val acceptedTexts: List<String>,
    val supportText: String? = null,
)

fun StudyItemSeed.writingTarget(): ThaiWritingTarget = when (type) {
    ItemType.CONSONANT,
    ItemType.WORD,
    -> ThaiWritingTarget(
        displayText = thai,
        acceptedTexts = listOf(thai),
    )

    ItemType.VOWEL -> vowelWritingTarget()
    ItemType.TONE -> toneWritingTarget()
}

private fun StudyItemSeed.vowelWritingTarget(): ThaiWritingTarget = when (thai) {
    "า" -> carrierTarget("อา", "Use the carrier อ so you can see that า is written after the consonant: อา.")
    "ะ" -> carrierTarget("อะ", "Use the carrier อ so you can see that ะ is written after the consonant and keeps the vowel short: อะ.")
    "ำ" -> carrierTarget("อำ", "Use the carrier อ so you can see that ำ sits above and after the consonant as one package: อำ.")
    "ิ" -> carrierTarget("อิ", "Use the carrier อ so you can place ิ above the consonant: อิ.")
    "ี" -> carrierTarget("อี", "Use the carrier อ so you can place ี above the consonant with its longer tail: อี.")
    "ุ" -> carrierTarget("อุ", "Use the carrier อ so you can place ุ below the consonant: อุ.")
    "ู" -> carrierTarget("อู", "Use the carrier อ so you can place ู below the consonant with its deeper tail: อู.")
    "ั" -> carrierTarget("อั", "Use the carrier อ so you can place ั above the consonant. This mark makes a short a sound inside the word: อั.")
    "เ" -> carrierTarget("เอ", "Use the carrier อ so you can see that เ is written before the consonant: เอ.")
    "แ" -> carrierTarget("แอ", "Use the carrier อ so you can see that แ is written before the consonant: แอ.")
    "โ" -> carrierTarget("โอ", "Use the carrier อ so you can see the frame vowel โ around the consonant: โอ.")
    "ไ" -> carrierTarget("ไอ", "Use the carrier อ so you can see that ไ is written before the consonant: ไอ.")
    "ใ" -> carrierTarget("ใอ", "Use the carrier อ so you can see that ใ is also written before the consonant: ใอ.")
    else -> ThaiWritingTarget(
        displayText = thai,
        acceptedTexts = listOf(thai),
    )
}

private fun StudyItemSeed.toneWritingTarget(): ThaiWritingTarget = when (thai) {
    "่" -> carrierTarget("อ่", "Use the carrier อ so you can place ่ above the consonant stack: อ่.")
    "้" -> carrierTarget("อ้", "Use the carrier อ so you can place ้ above the consonant stack: อ้.")
    "๊" -> carrierTarget("อ๊", "Use the carrier อ so you can place ๊ above the consonant stack: อ๊.")
    "๋" -> carrierTarget("อ๋", "Use the carrier อ so you can place ๋ above the consonant stack: อ๋.")
    else -> ThaiWritingTarget(
        displayText = thai,
        acceptedTexts = listOf(thai),
    )
}

private fun StudyItemSeed.carrierTarget(displayText: String, supportText: String): ThaiWritingTarget =
    ThaiWritingTarget(
        displayText = displayText,
        acceptedTexts = listOf(displayText, thai),
        supportText = supportText,
    )
