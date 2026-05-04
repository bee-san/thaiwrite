package com.bee.thaiwrite.domain.fsrs

import com.bee.thaiwrite.data.db.CardState
import com.bee.thaiwrite.data.db.StudyCardEntity
import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class SchedulingResult(
    val updatedCard: StudyCardEntity,
    val reviewedAt: Instant,
)

class FsrsPassFailScheduler(
    private val requestRetention: Double = 0.9,
    private val maximumIntervalDays: Int = 36500,
    private val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    private val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
) {
    private val w = doubleArrayOf(
        0.2120,
        1.2931,
        2.3065,
        8.2956,
        6.4133,
        0.8334,
        3.0194,
        0.0010,
        1.8722,
        0.1666,
        0.7960,
        1.4835,
        0.0614,
        0.2629,
        1.6483,
        0.6014,
        1.8729,
        0.5425,
        0.0912,
        0.0658,
        0.1542,
    )
    private val decay = -w[20]
    private val factor = exp(ln(0.9) / decay) - 1.0
    private val intervalModifier = ((requestRetention.pow(1.0 / decay)) - 1.0) / factor

    fun review(card: StudyCardEntity, now: Instant, passed: Boolean): SchedulingResult {
        val state = CardState.valueOf(card.state)
        val grade = if (passed) Grade.GOOD else Grade.AGAIN
        val memory = if (card.stability <= 0.0 || card.difficulty <= 0.0) null else MemoryState(card.difficulty, card.stability)
        val elapsedDays = computeElapsedDays(card, now, state)
        val nextMemory = nextState(memory, elapsedDays, grade)
        val nextCard = when (state) {
            CardState.NEW -> advanceLearning(
                card = card,
                now = now,
                passed = passed,
                nextMemory = nextMemory,
                currentState = CardState.LEARNING,
                steps = learningSteps,
                currentStep = if (passed) 1 else 0,
            )
            CardState.LEARNING -> advanceLearning(
                card = card,
                now = now,
                passed = passed,
                nextMemory = nextMemory,
                currentState = CardState.LEARNING,
                steps = learningSteps,
                currentStep = if (passed) card.learningStep + 1 else 0,
            )
            CardState.REVIEW -> {
                if (passed) {
                    val dueAt = now.plus(Duration.ofDays(nextIntervalDays(nextMemory.stability).toLong()))
                    card.copy(
                        state = CardState.REVIEW.name,
                        dueAt = dueAt.toEpochMilli(),
                        stability = nextMemory.stability,
                        difficulty = nextMemory.difficulty,
                        lastReviewedAt = now.toEpochMilli(),
                        scheduledDays = Duration.between(now, dueAt).toDays().toInt(),
                        learningStep = 0,
                        reps = card.reps + 1,
                        lastOutcomePass = true,
                    )
                } else {
                    val dueAt = now.plus(relearningSteps.first())
                    card.copy(
                        state = CardState.RELEARNING.name,
                        dueAt = dueAt.toEpochMilli(),
                        stability = nextMemory.stability,
                        difficulty = nextMemory.difficulty,
                        lastReviewedAt = now.toEpochMilli(),
                        scheduledDays = 0,
                        learningStep = 0,
                        reps = card.reps + 1,
                        lapses = card.lapses + 1,
                        lastOutcomePass = false,
                    )
                }
            }
            CardState.RELEARNING -> advanceLearning(
                card = card,
                now = now,
                passed = passed,
                nextMemory = nextMemory,
                currentState = CardState.RELEARNING,
                steps = relearningSteps,
                currentStep = if (passed) card.learningStep + 1 else 0,
            )
        }
        return SchedulingResult(updatedCard = nextCard, reviewedAt = now)
    }

    private fun advanceLearning(
        card: StudyCardEntity,
        now: Instant,
        passed: Boolean,
        nextMemory: MemoryState,
        currentState: CardState,
        steps: List<Duration>,
        currentStep: Int,
    ): StudyCardEntity {
        if (passed && currentStep >= steps.size) {
            val dueAt = now.plus(Duration.ofDays(nextIntervalDays(nextMemory.stability).toLong()))
            return card.copy(
                state = CardState.REVIEW.name,
                dueAt = dueAt.toEpochMilli(),
                stability = nextMemory.stability,
                difficulty = nextMemory.difficulty,
                lastReviewedAt = now.toEpochMilli(),
                scheduledDays = Duration.between(now, dueAt).toDays().toInt(),
                learningStep = 0,
                reps = card.reps + 1,
                lastOutcomePass = true,
            )
        }

        val stepIndex = currentStep.coerceIn(0, max(steps.lastIndex, 0))
        val dueAt = now.plus(steps[stepIndex])
        return card.copy(
            state = currentState.name,
            dueAt = dueAt.toEpochMilli(),
            stability = nextMemory.stability,
            difficulty = nextMemory.difficulty,
            lastReviewedAt = now.toEpochMilli(),
            scheduledDays = 0,
            learningStep = stepIndex,
            reps = card.reps + 1,
            lapses = card.lapses + if (passed || currentState == CardState.LEARNING) 0 else 1,
            lastOutcomePass = passed,
        )
    }

    private fun computeElapsedDays(card: StudyCardEntity, now: Instant, state: CardState): Double {
        val last = card.lastReviewedAt ?: return 0.0
        if (state == CardState.LEARNING || state == CardState.RELEARNING || state == CardState.NEW) {
            return 0.0
        }
        val deltaMillis = max(0L, now.toEpochMilli() - last)
        return deltaMillis / 86_400_000.0
    }

    private fun nextIntervalDays(stability: Double): Int =
        min(maximumIntervalDays, max(1, (stability * intervalModifier).roundToInt()))

    private fun nextState(memoryState: MemoryState?, elapsedDays: Double, grade: Grade): MemoryState {
        val difficulty = memoryState?.difficulty ?: 0.0
        val stability = memoryState?.stability ?: 0.0
        if (difficulty == 0.0 && stability == 0.0) {
            return MemoryState(
                difficulty = clamp(initDifficulty(grade), 1.0, 10.0),
                stability = max(initStability(grade), 0.1),
            )
        }

        val retrievability = if (elapsedDays == 0.0) 1.0 else forgettingCurve(elapsedDays, stability)
        val nextStability = when {
            elapsedDays == 0.0 -> nextShortTermStability(stability, grade)
            grade == Grade.AGAIN -> {
                val afterFail = nextForgetStability(difficulty, stability, retrievability)
                clamp((stability / exp(w[17] * w[18])), 0.001, afterFail)
            }
            else -> nextRecallStability(difficulty, stability, retrievability, grade)
        }
        val nextDifficulty = nextDifficulty(difficulty, grade)
        return MemoryState(
            difficulty = nextDifficulty,
            stability = nextStability,
        )
    }

    private fun forgettingCurve(elapsedDays: Double, stability: Double): Double =
        (1 + (factor * elapsedDays) / stability).pow(decay)

    private fun initStability(grade: Grade): Double =
        when (grade) {
            Grade.AGAIN -> w[0]
            Grade.GOOD -> w[2]
        }

    private fun initDifficulty(grade: Grade): Double {
        val mapped = when (grade) {
            Grade.AGAIN -> 1
            Grade.GOOD -> 3
        }
        return w[4] - exp((mapped - 1) * w[5]) + 1
    }

    private fun meanReversion(init: Double, current: Double): Double = w[7] * init + (1 - w[7]) * current

    private fun nextDifficulty(difficulty: Double, grade: Grade): Double {
        val mapped = when (grade) {
            Grade.AGAIN -> 1
            Grade.GOOD -> 3
        }
        val delta = -w[6] * (mapped - 3)
        val damped = difficulty + ((delta * (10 - difficulty)) / 9)
        return clamp(meanReversion(initDifficulty(Grade.GOOD), damped), 1.0, 10.0)
    }

    private fun nextRecallStability(difficulty: Double, stability: Double, retrievability: Double, grade: Grade): Double {
        val hardPenalty = 1.0
        val easyBonus = 1.0
        return clamp(
            stability * (
                1 + exp(w[8]) *
                    (11 - difficulty) *
                    stability.pow(-w[9]) *
                    (exp((1 - retrievability) * w[10]) - 1) *
                    hardPenalty *
                    easyBonus
                ),
            0.001,
            36500.0,
        )
    }

    private fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double =
        clamp(
            w[11] *
                difficulty.pow(-w[12]) *
                ((stability + 1).pow(w[13]) - 1) *
                exp((1 - retrievability) * w[14]),
            0.001,
            36500.0,
        )

    private fun nextShortTermStability(stability: Double, grade: Grade): Double {
        val mapped = when (grade) {
            Grade.AGAIN -> 1
            Grade.GOOD -> 3
        }
        val sinc = stability.pow(-w[19]) * exp(w[17] * (mapped - 3 + w[18]))
        return clamp(stability * sinc.coerceAtLeast(1.0), 0.001, 36500.0)
    }

    private fun clamp(value: Double, minimum: Double, maximum: Double): Double = min(maximum, max(minimum, value))

    private data class MemoryState(
        val difficulty: Double,
        val stability: Double,
    )

    private enum class Grade {
        AGAIN,
        GOOD,
    }
}
