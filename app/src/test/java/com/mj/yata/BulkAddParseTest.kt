package com.mj.yata

import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.ui.sheets.bulkLinesFrom
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.ParsedQuickAdd
import com.mj.yata.util.findBestEntityMatch
import com.mj.yata.util.resolveParsedQuickAddEntities
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * The pasted-roster shape bulk add exists for: one person per line, each carrying the same date,
 * tag and project. Every line has to parse independently and keep its own title.
 */
class BulkAddParseTest {

    private val ref = LocalDate.of(2026, 8, 30)

    private val lines = listOf(
        "Jyothi Gopalakrishnan [SJ Associates] Aug 31 #Achu_STP +ITR",
        "Dhiphin [MRD Associates] Aug 31 #Achu_STP +ITR",
        "TK Rajendran [MRD Associates] Aug 31 #Achu_STP +ITR",
        "P A Francis [United Marketing] Aug 31 #Achu_STP +ITR",
        "M Jessintha [United Marketing] Aug 31 #Achu_STP +ITR",
        "Jithosh Sreedharan [Printer Care] Aug 31 #Achu_STP +ITR",
        "Jestin [Printer Care] Aug 31 #Achu_STP +ITR",
        "EP Davi [Eluvathingal Traders] Aug 31 #Achu_STP +ITR",
        "EP Raphy [Eluvathingal Traders] Aug 31 #Achu_STP +ITR"
    )

    @Test
    fun eachLineKeepsItsOwnTitleAndSharedMetadata() {
        val parsed = lines.map { NaturalLanguageParser.parse(it, ref) }

        // The bracketed company stays part of the title — it isn't an entity mention.
        assertEquals("Jyothi Gopalakrishnan [SJ Associates]", parsed[0].title)
        assertEquals("EP Raphy [Eluvathingal Traders]", parsed[8].title)
        assertEquals(lines.size, parsed.map { it.title }.distinct().size)

        parsed.forEach { p ->
            assertEquals("2026-08-31", p.due)
            assertEquals(listOf("Achu_STP"), p.tagNames)
            assertEquals("ITR", p.projectName)
            // A trailing "+ITR" must not be mistaken for an assignee.
            assertTrue(p.assigneeNames.isEmpty())
        }
    }

    @Test
    fun caseVariantsOfOneNameCollapseToASingleMissingEntity() {
        // findBestEntityMatch is case-insensitive, so "#ITR"/"#itr"/"#Itr" all resolve to one tag
        // once it exists. A plain distinct() sees three missing names and "Create missing items"
        // would make three tags, two of which nothing would ever resolve to again.
        val parsed = listOf("A #ITR +Work", "B #itr +work", "C #Itr +WORK")
            .map { NaturalLanguageParser.parse(it, ref) }

        fun List<String>.distinctNames() = distinctBy { it.trim().lowercase() }
        assertEquals(listOf("ITR"), parsed.flatMap { it.tagNames }.distinctNames())
        assertEquals(listOf("Work"), parsed.mapNotNull { it.projectName }.distinctNames())

        // And creating only that one is genuinely enough for every spelling to attach.
        val created = listOf(Tag(id = "t1", name = "ITR", color = "accentA"))
        parsed.flatMap { it.tagNames }.forEach { name ->
            assertEquals("t1", findBestEntityMatch(name, created, { t -> t.name })?.id)
        }
    }

    @Test
    fun perLineMemoizationMakesAnEditReparseOnlyTheEditedLine() {
        // NaturalLanguageParser's own LRU holds 64 entries, so a longer paste evicts everything
        // on each keystroke and re-parses every line. The sheet memoizes per line instead; this
        // asserts the shape of that memo rather than a wall-clock number.
        val lines = (1..200).map { "Person $it Aug 31 #Achu_STP +ITR" }
        val memo = HashMap<String, ParsedQuickAdd>()
        var parseCount = 0
        fun pass(ls: List<String>) {
            val fresh = LinkedHashMap<String, ParsedQuickAdd>()
            ls.forEach { l ->
                val v = memo[l] ?: NaturalLanguageParser.parse(l, ref).also { parseCount++ }
                fresh[l] = v
            }
            memo.clear(); memo.putAll(fresh)
        }

        pass(lines)
        assertEquals(200, parseCount)

        parseCount = 0
        pass(lines.dropLast(1) + (lines.last() + "x"))
        assertEquals(1, parseCount)

        // The line edited away must not be retained, or a long session pins every keystroke.
        assertEquals(200, memo.size)
    }

    @Test
    fun stripsListMarkersOnlyWhenThePasteIsActuallyAList() {
        // A roster pasted out of a numbered or bulleted list: the marker is formatting, not part
        // of anyone's name. Previously "-" happened to be stripped by the title cleaner while
        // "1.", "2)", "*" and bullets survived into the title, so the same paste came out
        // inconsistently depending on which marker the source used.
        val numbered = bulkLinesFrom("1. Jyothi\n2. Dhiphin\n3. TK Rajendran")
        assertEquals(listOf("Jyothi", "Dhiphin", "TK Rajendran"), numbered)

        val bulleted = bulkLinesFrom("- Jyothi\n* Dhiphin\n• TK Rajendran")
        assertEquals(listOf("Jyothi", "Dhiphin", "TK Rajendran"), bulleted)

        val parenthesised = bulkLinesFrom("1) Jyothi\n2) Dhiphin")
        assertEquals(listOf("Jyothi", "Dhiphin"), parenthesised)
    }

    @Test
    fun leavesNumbersAloneWhenTheyAreContentRatherThanFormatting() {
        // Only a majority of marked lines counts as list formatting, so one line that merely
        // starts with a numeral doesn't cost the others their leading text.
        assertEquals(
            listOf("1. Introduction", "Chapter about scope", "Chapter about method"),
            bulkLinesFrom("1. Introduction\nChapter about scope\nChapter about method")
        )
        // Initials and decimals are never markers — the trailing-space rule is what protects them.
        assertEquals(
            listOf("P A Francis [United Marketing]", "1.5x review pass"),
            bulkLinesFrom("P A Francis [United Marketing]\n1.5x review pass")
        )
    }

    @Test
    fun splitsOnEveryLineTerminatorNotJustNewline() {
        // A spreadsheet or rich-text paste can arrive CR-separated or with U+2028/U+2029; a
        // "\n"-only split turned the whole paste into one very long single task.
        assertEquals(listOf("A", "B", "C"), bulkLinesFrom("A\r\nB\r\nC"))
        assertEquals(listOf("A", "B"), bulkLinesFrom("A\rB"))
        assertEquals(listOf("A", "B"), bulkLinesFrom("A\u2028B"))
        assertEquals(listOf("A", "B"), bulkLinesFrom("A\u2029B"))
    }

    @Test
    fun blankAndWhitespaceOnlyLinesAreDropped() {
        assertEquals(listOf("A", "B"), bulkLinesFrom("A\n\n   \n\tB\n"))
    }

    @Test
    fun entitiesAreDroppedWhenTheyDoNotExistYetAndAttachOnceTheyDo() {
        val parsed = NaturalLanguageParser.parse(lines.first(), ref)

        // This is what the bulk preview's unmatched chips exist to make visible: with nothing to
        // resolve against, the tag and project silently resolve to nothing at all.
        val unresolved = resolveParsedQuickAddEntities(
            quickAdd = parsed,
            baseListId = null, baseProjectId = null,
            baseTagIds = emptyList(), baseAssigneeIds = emptyList(),
            lists = emptyList(), projects = emptyList(), people = emptyList(), tags = emptyList(),
            projectsEnabled = true, tagsEnabled = true, peopleEnabled = true
        )
        assertNull(unresolved.projectId)
        assertTrue(unresolved.tagIds.isEmpty())

        // Once "Create missing items" has made them, the same paste attaches both.
        val resolved = resolveParsedQuickAddEntities(
            quickAdd = parsed,
            baseListId = null, baseProjectId = null,
            baseTagIds = emptyList(), baseAssigneeIds = emptyList(),
            lists = emptyList(), people = emptyList(),
            projects = listOf(Project(id = "proj_1", name = "ITR", color = "accentA", icon = "layers")),
            tags = listOf(Tag(id = "tag_1", name = "Achu_STP", color = "accentB")),
            projectsEnabled = true, tagsEnabled = true, peopleEnabled = true
        )
        assertEquals("proj_1", resolved.projectId)
        assertEquals(listOf("tag_1"), resolved.tagIds)
    }
}
