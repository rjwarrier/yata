package com.mj.yata.domain.model

enum class DateAliasTarget(val label: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    NEXT_WEEK("Next week"),
    NEXT_MONTH("Next month"),
    WEEKEND("Weekend"),
    MONDAY("Monday"),
    TUESDAY("Tuesday"),
    WEDNESDAY("Wednesday"),
    THURSDAY("Thursday"),
    FRIDAY("Friday"),
    SATURDAY("Saturday"),
    SUNDAY("Sunday")
}

data class DateAliasDefinition(
    val alias: String,
    val target: DateAliasTarget
) {
    fun encode(): String = "${alias.trim().lowercase()}|${target.name}"

    companion object {
        fun decode(raw: String): DateAliasDefinition? {
            val parts = raw.split("|", limit = 2)
            val alias = parts.getOrNull(0)?.trim()?.lowercase().orEmpty()
            val target = parts.getOrNull(1)?.let { name ->
                DateAliasTarget.entries.firstOrNull { it.name == name }
            }
            return if (alias.isNotBlank() && target != null) DateAliasDefinition(alias, target) else null
        }
    }
}
