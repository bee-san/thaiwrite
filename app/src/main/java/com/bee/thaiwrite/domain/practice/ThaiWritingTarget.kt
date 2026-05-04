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
    "า" -> carrierTarget("อา", "Practice the vowel with the carrier อ: อา")
    "ิ" -> carrierTarget("อิ", "Practice the vowel with the carrier อ: อิ")
    "ี" -> carrierTarget("อี", "Practice the vowel with the carrier อ: อี")
    "ุ" -> carrierTarget("อุ", "Practice the vowel with the carrier อ: อุ")
    "ู" -> carrierTarget("อู", "Practice the vowel with the carrier อ: อู")
    "เ" -> carrierTarget("เอ", "Practice the vowel with the carrier อ: เอ")
    "แ" -> carrierTarget("แอ", "Practice the vowel with the carrier อ: แอ")
    "โ" -> carrierTarget("โอ", "Practice the vowel with the carrier อ: โอ")
    else -> ThaiWritingTarget(
        displayText = thai,
        acceptedTexts = listOf(thai),
    )
}

private fun StudyItemSeed.toneWritingTarget(): ThaiWritingTarget = when (thai) {
    "่" -> carrierTarget("อ่", "Practice the tone mark on the carrier อ: อ่")
    "้" -> carrierTarget("อ้", "Practice the tone mark on the carrier อ: อ้")
    "๊" -> carrierTarget("อ๊", "Practice the tone mark on the carrier อ: อ๊")
    "๋" -> carrierTarget("อ๋", "Practice the tone mark on the carrier อ: อ๋")
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
