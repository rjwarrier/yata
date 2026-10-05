package com.mj.yata.util.nl

internal data class EntityParseResult(
    val projectName: String?,
    val listName: String?,
    val tagNames: List<String>,
    val assigneeNames: List<String>
)

internal object EntityRules {
    private const val ENTITY_NAME_CHARS = "\\p{L}\\p{N}_\\-\\s"
    private const val ENTITY_BOUNDARY_KEYWORDS =
            "project\\b|proyecto\\b|projeto\\b|projet\\b|list\\b|lista\\b|liste\\b|tag\\b|etiqueta\\b|Ã©tiquette\\b|\\u00e9tiquette\\b|etiquette\\b|" +
            "tagged?\\b|label\\b|labeled?\\b|assign(?:ed)?\\s+to\\b|asignad[ao]\\s+a\\b|asignar\\s+a\\b|" +
            "atribu[iÃ­]d[ao]\\s+a\\b|atribuir\\s+a\\b|assignÃ©\\s+Ã \\b|assign\\u00e9\\s+\\u00e0\\b|assigne\\s+a\\b|assigner\\s+Ã \\b|assigner\\s+\\u00e0\\b|" +
            "give(?:n)?\\s+to\\b|delegate\\b|delegar\\b|send\\s+to\\b|assign\\b|due\\b|vence\\b|Ã©chÃ©ance\\b|echeance\\b|" +
            // Bare "to"/"from" as connectors, not just as part of "assign(ed) to"/"send to" above -
            // without these, a multi-word project/list/assignee name's lazy capture had nothing
            // to stop it swallowing a trailing filler word ("project Work to tag Errand" captured
            // the project name as "Work to" instead of stopping at "to").
            "to\\b|from\\b|" +
            "at\\b|a\\s+las?\\b|Ã s?\\b|Ã \\b|every\\b|cada\\b|todo\\b|toda\\b|chaque\\b|on\\b|el\\b|le\\b|" +
            "today\\b|tdy\\b|tomorrow\\b|tmr\\b|tmrw\\b|tomrw\\b|tonight\\b|tonite\\b|" +
            "morning\\b|morn\\b|afternoon\\b|evening\\b|night\\b|noon\\b|midnight\\b|" +
            "monday\\b|mon\\b|tuesday\\b|tue\\b|wednesday\\b|wed\\b|thursday\\b|thu\\b|friday\\b|fri\\b|saturday\\b|sat\\b|sunday\\b|sun\\b|" +
            "lunes\\b|martes\\b|miÃ©rcoles\\b|miercoles\\b|jueves\\b|viernes\\b|sÃ¡bado\\b|sabado\\b|domingo\\b|" +
            "segunda(?:-feira)?\\b|terÃ§a(?:-feira)?\\b|terca(?:-feira)?\\b|quarta(?:-feira)?\\b|quinta(?:-feira)?\\b|sexta(?:-feira)?\\b|" +
            "lundi\\b|mardi\\b|mercredi\\b|jeudi\\b|vendredi\\b|samedi\\b|dimanche\\b|" +
            "next\\b|nxt\\b|this\\b|in\\b|by\\b|before\\b|after\\b|starts?\\b|start(?:ing)?\\b|not\\s+before\\b|p[1-3]\\b|" +
            "hash\\s*tag\\b|hashtag\\b|pound\\s*tag\\b|at\\s+sign\\b|plus\\s+project\\b|equals\\s+list\\b"
    private const val ENTITY_WORD_BOUNDARY = "!|#|@|\\+|=|$ENTITY_BOUNDARY_KEYWORDS"

    private val quotedEntityValueRegex =
        Regex("^\\s*(?:\"([^\"]+)\"|'([^']+)'|([$ENTITY_NAME_CHARS]+?))\\s*$")

    private val projectEntityRegex = Regex(
        "(?<![\\p{L}\\p{N}_])(?:\\+|plus\\s+project\\b|in\\s+project\\b|for\\s+project\\b|under\\s+project\\b|project\\b|en\\s+proyecto\\b|para\\s+proyecto\\b|bajo\\s+proyecto\\b|proyecto\\b|em\\s+projeto\\b|para\\s+projeto\\b|projeto\\b|dans\\s+projet\\b|pour\\s+projet\\b|projet\\b)\\s*(\"[^\"]+\"|'[^']+'|[$ENTITY_NAME_CHARS]+?)(?=$|\\s+(?:$ENTITY_WORD_BOUNDARY))",
        RegexOption.IGNORE_CASE
    )

    private val listEntityRegex = Regex(
        "(?<![\\p{L}\\p{N}_])(?:=|equals\\s+list\\b|in\\s+list\\b|for\\s+list\\b|under\\s+list\\b|list\\b|en\\s+lista\\b|para\\s+lista\\b|bajo\\s+lista\\b|lista\\b|em\\s+lista\\b|dans\\s+liste\\b|pour\\s+liste\\b|liste\\b)\\s*(\"[^\"]+\"|'[^']+'|[$ENTITY_NAME_CHARS]+?)(?=$|\\s+(?:$ENTITY_WORD_BOUNDARY))",
        RegexOption.IGNORE_CASE
    )

    private val tagEntityRegex = Regex(
        "(?<![\\p{L}\\p{N}_])(?:#|hash\\s*tag\\s+|hashtag\\s+|pound\\s*tag\\s+|tagged?\\s+as\\s+|tagged?\\s+|tag\\s+as\\s+|tag\\s+|labeled?\\s+as\\s+|labeled?\\s+|label\\s+as\\s+|label\\s+|with\\s+tag\\s+|etiquetad[ao]\\s+como\\s+|etiquetad[ao]\\s+|etiqueta\\s+como\\s+|etiqueta\\s+|con\\s+etiqueta\\s+|marcad[ao]\\s+como\\s+|rÃ³tulo\\s+|rotulo\\s+|Ã©tiquette\\s+|\\u00e9tiquette\\s+|etiquette\\s+|avec\\s+Ã©tiquette\\s+|avec\\s+\\u00e9tiquette\\s+|avec\\s+etiquette\\s+)([\\p{L}\\p{N}_\\-]+)(?![\\p{L}\\p{N}_])",
        RegexOption.IGNORE_CASE
    )

    private val assigneeEntityRegex = Regex(
        "(?<![\\p{L}\\p{N}_])(?:assign(?:ed)?\\s+to\\s+|give(?:n)?\\s+to\\s+|delegate(?:d)?\\s+to\\s+|send\\s+to\\s+|assign\\s+|asignad[ao]\\s+a\\s+|asignar\\s+a\\s+|delegad[ao]\\s+a\\s+|delegar\\s+a\\s+|enviar\\s+a\\s+|atribu[iÃ­]d[ao]\\s+a\\s+|atribuir\\s+a\\s+|delegar\\s+para\\s+|enviar\\s+para\\s+|assignÃ©\\s+Ã \\s+|assign\\u00e9\\s+\\u00e0\\s+|assigne\\s+a\\s+|assigner\\s+Ã \\s+|assigner\\s+\\u00e0\\s+|assigner\\s+a\\s+|dÃ©lÃ©guÃ©\\s+Ã \\s+|d\\u00e9l\\u00e9gu\\u00e9\\s+\\u00e0\\s+|delegue\\s+a\\s+|dÃ©lÃ©guer\\s+Ã \\s+|d\\u00e9l\\u00e9guer\\s+\\u00e0\\s+|deleguer\\s+a\\s+|envoyer\\s+Ã \\s+|envoyer\\s+\\u00e0\\s+|envoyer\\s+a\\s+|at\\s+sign\\s+|@)(\"[^\"]+\"|'[^']+'|[$ENTITY_NAME_CHARS]+?)(?=$|\\s+(?:$ENTITY_WORD_BOUNDARY))",
        RegexOption.IGNORE_CASE
    )

    fun apply(context: ParserContext): EntityParseResult {
        var projectName: String? = null
        firstResolvableMatch(context, projectEntityRegex)?.let { (range, rawValue) ->
            projectName = entityValue(rawValue)
            context.claimProject(range)
        }

        var listName: String? = null
        firstResolvableMatch(context, listEntityRegex)?.let { (range, rawValue) ->
            listName = entityValue(rawValue)
            context.claimList(range)
        }

        val tagNames = mutableListOf<String>()
        for (match in tagEntityRegex.findAll(context.raw)) {
            resolveMatch(context, match)?.let { (range, rawValue) ->
                tagNames.add(entityValue(rawValue))
                context.claimTag(range)
            }
        }

        val assigneeNames = mutableListOf<String>()
        for (match in assigneeEntityRegex.findAll(context.raw)) {
            resolveMatch(context, match)?.let { (range, rawValue) ->
                assigneeNames.add(entityValue(rawValue))
                context.claimAssignee(range)
            }
        }

        return EntityParseResult(
            projectName = projectName,
            listName = listName,
            tagNames = tagNames,
            assigneeNames = assigneeNames
        )
    }

    /** First regex match [resolveMatch] can actually use — same "first that works" shape as
     * [ParserContext.firstFreeMatch], but per-candidate rather than requiring the whole match to
     * already be free. Used for project/list, which take at most one match each. */
    private fun firstResolvableMatch(context: ParserContext, regex: Regex, groupIndex: Int = 1): Pair<IntRange, String>? {
        for (match in regex.findAll(context.raw)) {
            resolveMatch(context, match, groupIndex)?.let { return it }
        }
        return null
    }

    /**
     * Accepts [match] as-is when its full range is already free — identical to the plain
     * `isFree` check every entity match used before this existed, so nothing that already passed
     * changes. Otherwise, salvages a captured value that ran past `ENTITY_BOUNDARY_KEYWORDS`
     * straight into an already-claimed range: this happens for a date/time word (or similar)
     * that some *other* rule recognizes and claims but that isn't in this file's own hand-listed
     * boundary keywords — most often because that word belongs to a language
     * `NaturalLanguageLexicon` supports but this regex's EN/ES/PT/FR keyword list doesn't (e.g.
     * German "morgen"/"tomorrow" in "project Work morgen"). Rather than duplicate every
     * language's date vocabulary here too, the captured value is truncated to end right before
     * the claim it ran into, and re-checked for freeness. Returns null if there's truly nothing
     * usable (e.g. the value's own text is itself entirely claimed).
     */
    private fun resolveMatch(context: ParserContext, match: MatchResult, groupIndex: Int = 1): Pair<IntRange, String>? {
        if (context.isFree(match.range)) return match.range to match.groupValues[groupIndex]
        val group = match.groups[groupIndex] ?: return null
        val obstacle = context.firstObstacleFrom(group.range.first) ?: return null
        if (obstacle > group.range.last || obstacle <= group.range.first) return null
        val truncatedValue = context.raw.substring(group.range.first, obstacle).trimEnd()
        if (truncatedValue.isBlank()) return null
        val truncatedRange = match.range.first..(group.range.first + truncatedValue.length - 1)
        return if (context.isFree(truncatedRange)) truncatedRange to truncatedValue else null
    }

    private fun entityValue(rawValue: String): String =
        quotedEntityValueRegex.matchEntire(rawValue)?.let { match ->
            match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim()
        } ?: rawValue.trim().trim('"', '\'')
}
