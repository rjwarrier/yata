package com.mj.yata.util.nl

import com.mj.yata.util.literalWordRegex

internal data class PriorityFlagResult(
    val priority: String?,
    val flag: Boolean
)

internal object PriorityFlagRules {
    private val priorityShorthandRegex = Regex("!{1,2}([1-3])\\b")
    private val priorityBareRegex = Regex("\\bp([1-3])\\b", RegexOption.IGNORE_CASE)

    private val priorityWordPhrases = listOf(
        "not urgent" to "low",
        "non urgent" to "low",
        "not important" to "low",
        "not critical" to "low",
        "highest priority" to "high",
        "top priority" to "high",
        "high priority" to "high",
        "hi priority" to "high",
        "high pri" to "high",
        "hi pri" to "high",
        "super urgent" to "high",
        "must do" to "high",
        "vital" to "high",
        "essential" to "high",
        "urgent" to "high",
        "urgnt" to "high",
        "urgentt" to "high",
        "critical" to "high",
        "critcal" to "high",
        "crit" to "high",
        "asap" to "high",
        "as soon as possible" to "high",
        "drop everything" to "high",
        "high prio" to "high",
        "hi prio" to "high",
        "h prio" to "high",
        "top prio" to "high",
        "medium priority" to "med",
        "meduim priority" to "med",
        "med priority" to "med",
        "normal priority" to "med",
        "medium prio" to "med",
        "medium pri" to "med",
        "med prio" to "med",
        "med pri" to "med",
        "m prio" to "med",
        "normal prio" to "med",
        "lowest priority" to "low",
        "low priority" to "low",
        "lo priority" to "low",
        "low pri" to "low",
        "lo pri" to "low",
        "minor priority" to "low",
        "low prio" to "low",
        "lo prio" to "low",
        "l prio" to "low",
        "back burner" to "low",
        "backburner" to "low",
        "nice to have" to "low",
        "if i have time" to "low",
        "when i can" to "low",
        "when i get a chance" to "low",
        "not urgent" to "low",
        "eventually" to "low",
        "someday" to "low",
        "whenever" to "low",
        "no rush" to "low",
        "mÃ¡xima prioridad" to "high",
        "maxima prioridad" to "high",
        "alta prioridad" to "high",
        "prioridad alta" to "high",
        "muy urgente" to "high",
        "urgente" to "high",
        "crÃ­tico" to "high",
        "critico" to "high",
        "importante" to "high",
        "cuanto antes" to "high",
        "lo antes posible" to "high",
        "prioridad media" to "med",
        "media prioridad" to "med",
        "prioridad normal" to "med",
        "baja prioridad" to "low",
        "prioridad baja" to "low",
        "sin prisa" to "low",
        "cuando pueda" to "low",
        "algÃºn dÃ­a" to "low",
        "algun dia" to "low",
        "prioridade mÃ¡xima" to "high",
        "prioridade maxima" to "high",
        "alta prioridade" to "high",
        "prioridade alta" to "high",
        "muito urgente" to "high",
        "urgente" to "high",
        "crÃ­tico" to "high",
        "critico" to "high",
        "importante" to "high",
        "o quanto antes" to "high",
        "quanto antes" to "high",
        "prioridade mÃ©dia" to "med",
        "prioridade media" to "med",
        "prioridade normal" to "med",
        "baixa prioridade" to "low",
        "prioridade baixa" to "low",
        "sem pressa" to "low",
        "quando puder" to "low",
        "priorit\u00e9 maximale" to "high",
        "prioritÃ© maximale" to "high",
        "priorite maximale" to "high",
        "haute priorit\u00e9" to "high",
        "haute prioritÃ©" to "high",
        "haute priorite" to "high",
        "priorit\u00e9 haute" to "high",
        "prioritÃ© haute" to "high",
        "priorite haute" to "high",
        "tr\u00e8s urgent" to "high",
        "trÃ¨s urgent" to "high",
        "tres urgent" to "high",
        "urgent" to "high",
        "critique" to "high",
        "d\u00e8s que possible" to "high",
        "dÃ¨s que possible" to "high",
        "des que possible" to "high",
        "priorit\u00e9 moyenne" to "med",
        "prioritÃ© moyenne" to "med",
        "priorite moyenne" to "med",
        "priorit\u00e9 normale" to "med",
        "prioritÃ© normale" to "med",
        "priorite normale" to "med",
        "basse priorit\u00e9" to "low",
        "basse prioritÃ©" to "low",
        "basse priorite" to "low",
        "priorit\u00e9 basse" to "low",
        "prioritÃ© basse" to "low",
        "priorite basse" to "low",
        "pas urgent" to "low",
        "quand je peux" to "low"
    )

    private val flagPhrases = listOf(
        "flag this", "flag it", "flagged", "star this", "star it", "starred",
        "important", "importnt", "importnat", "impt", "mark as important", "bookmark", "bookmarked",
        "marcar", "marcar esto", "marcada", "destacar", "destacado", "importante", "marcar como importante",
        "sinalizar", "sinalizado", "destacar isto", "marcar como importante",
        "marquer", "marquÃ©", "marquee", "signaler", "favori", "mettre en favori", "marquer comme important"
    )

    fun apply(context: ParserContext): PriorityFlagResult {
        var priority: String? = null

        priorityShorthandRegex.findAll(context.raw)
            .firstOrNull { match ->
                context.isFree(match.range) &&
                    (match.range.first == 0 || !context.raw[match.range.first - 1].isLetterOrDigit())
            }
            ?.let { match ->
                priority = priorityLevel(match.groupValues[1])
                context.claimPriority(match.range)
            }

        if (priority == null) {
            context.firstFreeMatch(priorityBareRegex)?.let { match ->
                priority = priorityLevel(match.groupValues[1])
                if (priority != null) context.claimPriority(match.range)
            }
        }

        if (priority == null) {
            for ((phrase, level) in priorityWordPhrases) {
                literalWordRegex(phrase).findAll(context.raw)
                    .firstOrNull { match -> context.isFree(match.range) && !followsTagCommand(context.raw, match.range) }
                    ?.let { match ->
                        priority = level
                        context.claimPriority(match.range)
                    }
                if (priority != null) break
            }
        }

        var flag = false
        for (phrase in flagPhrases) {
            context.firstFreeMatch(literalWordRegex(phrase))?.let { match ->
                flag = true
                context.claimFlag(match.range)
            }
            if (flag) break
        }

        return PriorityFlagResult(priority = priority, flag = flag)
    }

    private fun priorityLevel(raw: String): String? =
        when (raw) {
            "1" -> "high"
            "2" -> "med"
            "3" -> "low"
            else -> null
        }

    private fun followsTagCommand(raw: String, range: IntRange): Boolean =
        Regex("(?:#|hash\\s*tag|hashtag|pound\\s*tag|tag(?:ged)?(?:\\s+as)?|label(?:ed)?(?:\\s+as)?|with\\s+tag|etiqueta(?:\\s+como)?|\\u00e9tiquette|etiquette)\\s*$", RegexOption.IGNORE_CASE)
            .containsMatchIn(raw.substring(0, range.first))
}
