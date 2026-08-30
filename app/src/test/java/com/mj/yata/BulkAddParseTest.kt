package com.mj.yata

import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.util.NaturalLanguageParser
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
