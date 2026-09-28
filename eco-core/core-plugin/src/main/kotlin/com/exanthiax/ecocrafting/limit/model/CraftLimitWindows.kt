package com.exanthiax.ecocrafting.limit.model

const val UNLIMITED: Int = -1

const val ALL_RECIPES: String = "*"

data class PlayerCraftCount(val recipe: String, val startedAt: Long, val count: Int) {
    fun serialise(): String = "$recipe;$startedAt;$count"
}

fun parsePlayerCraftCounts(entries: List<String>): List<PlayerCraftCount> = entries.mapNotNull { entry ->
    val parts = entry.split(';')
    if (parts.size != 3 || parts[0].isBlank()) return@mapNotNull null
    PlayerCraftCount(
        parts[0],
        parts[1].toLongOrNull() ?: return@mapNotNull null,
        parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
    )
}

fun parseRecipeValues(entries: List<String>): Map<String, Long> = entries.mapNotNull { entry ->
    val parts = entry.split(';')
    if (parts.size != 2 || parts[0].isBlank()) return@mapNotNull null
    parts[0] to (parts[1].toLongOrNull() ?: return@mapNotNull null)
}.toMap()

fun Map<String, Long>.serialiseRecipeValues(): List<String> = map { (recipe, value) -> "$recipe;$value" }

fun resetStampFor(resets: Map<String, Long>, recipe: String): Long =
    maxOf(resets[recipe] ?: Long.MIN_VALUE, resets[ALL_RECIPES] ?: Long.MIN_VALUE)

fun PlayerCraftCount.isCurrent(resets: Map<String, Long>): Boolean = startedAt > resetStampFor(resets, recipe)

fun currentPlayerCrafts(counts: List<PlayerCraftCount>, recipe: String, resets: Map<String, Long>): Int =
    counts.firstOrNull { it.recipe == recipe }?.takeIf { it.isCurrent(resets) }?.count ?: 0

fun windowStart(now: Long, resets: Map<String, Long>, recipe: String): Long {
    val resetStamp = resetStampFor(resets, recipe)
    return if (resetStamp == Long.MIN_VALUE) now else maxOf(now, resetStamp + 1)
}

fun nextResetStamp(now: Long, resets: Map<String, Long>, target: String): Long {
    val latest = if (target == ALL_RECIPES) resets.values.maxOrNull() ?: Long.MIN_VALUE else resetStampFor(resets, target)
    return if (latest == Long.MIN_VALUE) now else maxOf(now, latest + 1)
}

fun recordPlayerCrafts(
    counts: List<PlayerCraftCount>,
    recipe: String,
    crafts: Int,
    now: Long,
    resets: Map<String, Long>
): List<PlayerCraftCount> {
    val existing = counts.firstOrNull { it.recipe == recipe }?.takeIf { it.isCurrent(resets) }
    return counts.filter { it.recipe != recipe } + (
        existing?.copy(count = existing.count + crafts)
            ?: PlayerCraftCount(recipe, windowStart(now, resets, recipe), crafts)
        )
}

fun normaliseLimit(value: Int?): Int = if (value == null || value < 0) UNLIMITED else value

fun remainingUnder(limit: Int, used: Int): Int =
    if (limit < 0) Int.MAX_VALUE else (limit - used).coerceAtLeast(0)
