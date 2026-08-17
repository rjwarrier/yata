package com.mj.yata

import android.net.Uri
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.PersonGroup
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.RecurrenceEnds
import com.mj.yata.domain.model.Subtask
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.TagGroup
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.TaskComment
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.util.export.TaskTransferImporter
import com.mj.yata.util.export.buildTaskTransferLink
import com.mj.yata.util.export.isTaskTransferUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

/**
 * Guards docs/app-links-v2-plan.md's v2 link format (compact positional payload, single link,
 * raw-deflate compression, yata://i host) and pins that links already shared in v1 form
 * (yata://import/tasks, gzip, verbose JSON) still import correctly. android.net.Uri is a
 * method-stub-only class on the plain JVM (like org.json used to be — see
 * JsonExporterFieldsTest), so this runs under Robolectric rather than the bare JUnit runner the
 * rest of the util tests use.
 */
@RunWith(RobolectricTestRunner::class)
class TaskTransferLinkTest {

    private fun task(
        id: String = "t1",
        title: String = "Pay electricity bill",
        listId: String? = null,
        projectId: String? = null,
        priority: String = "high",
        flag: Boolean = false,
        notes: String? = null,
        tagIds: List<String> = emptyList(),
        assigneeIds: List<String> = emptyList(),
        subtasks: List<Subtask> = emptyList()
    ) = Task(
        id = id,
        title = title,
        listId = listId,
        projectId = projectId,
        section = "Morning",
        due = "2026-08-20",
        startDate = "2026-08-18",
        time = "2:00 PM",
        reminder = "15 min before",
        priority = priority,
        flag = flag,
        done = true,
        completedAt = 1_700_000_000_000L,
        createdAt = 1_600_000_000_000L,
        deletedAt = null,
        assigneeIds = assigneeIds,
        tagIds = tagIds,
        recurrence = null,
        subtasks = subtasks,
        notes = notes,
        sortOrder = 5,
        seriesId = "series1",
        archived = false,
        followUpAt = null,
        estimateMinutes = 30
    )

    /** A task carrying none of the schedule fields, for the tests about *encoding size* rather
     * than fidelity. [task] deliberately sets due/start/time/estimate so fidelity tests have
     * something to carry, which makes it a poor subject for "is the link short" assertions. */
    private fun bareTask(id: String = "t1", title: String = "Pay electricity bill", priority: String = "none") =
        task(id = id, title = title, priority = priority)
            .copy(due = null, startDate = null, time = null, reminder = null, estimateMinutes = null)

    private fun importUri(link: String): Uri = Uri.parse(link)

    /** Shared links carry their payload in the URL fragment (so link-preview crawlers never
     * receive it), so the parameters aren't reachable via getQueryParameter on the link itself.
     * This reproduces the same fragment-to-parameters step the importer does. */
    private fun linkParams(link: String): Uri =
        Uri.parse("?" + Uri.parse(link).encodedFragment.orEmpty())

    // --- isTaskTransferUri -------------------------------------------------------------

    @Test
    fun httpsUri_isNotRecognizedAsTransferLink() {
        assertFalse(isTaskTransferUri(Uri.parse("https://example.com/i?s=0&d=x")))
    }

    @Test
    fun wrongHost_isNotRecognizedAsTransferLink() {
        assertFalse(isTaskTransferUri(Uri.parse("yata://other?s=0&d=x")))
    }

    @Test
    fun nullUri_isNotRecognizedAsTransferLink() {
        assertFalse(isTaskTransferUri(null))
    }

    @Test
    fun legacyV1Host_isStillRecognizedAsTransferLink() {
        assertTrue(isTaskTransferUri(Uri.parse("yata://import/tasks?s=0&d=x")))
    }

    @Test
    fun shareableHttpsLink_isRecognized() {
        assertTrue(isTaskTransferUri(Uri.parse("https://ranjithj.in/yata/i#e=x")))
    }

    @Test
    fun otherPathsOnTheSameDomain_areNotTransferLinks() {
        // The site has other pages; only the import path may be claimed by the app, or tapping
        // any link to the website would open YATA.
        assertFalse(isTaskTransferUri(Uri.parse("https://ranjithj.in/yata/")))
        assertFalse(isTaskTransferUri(Uri.parse("https://ranjithj.in/")))
    }

    @Test
    fun yataSchemeLink_stillImports_soAlreadySharedLinksAndTaskerKeepWorking() = runTest {
        val repo = FakeYataRepository()
        // The yata:// form carries its parameters in the query string rather than the fragment.
        val uri = Uri.Builder().scheme("yata").authority("i")
            .appendQueryParameter("t", "Pay electricity bill")
            .appendQueryParameter("p0", "3")
            .build()

        val result = TaskTransferImporter(repo).importFrom(uri)

        assertEquals(1, result.taskCount)
        assertEquals("Pay electricity bill", repo.tasksFlow.value.single().title)
        assertEquals("high", repo.tasksFlow.value.single().priority)
    }

    @Test
    fun httpsLink_roundTripsThroughTheFragment() = runTest {
        val repo = FakeYataRepository()
        val original = task(title = "Pay electricity bill", priority = "high", flag = true)
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertTrue(link.uri.startsWith("https://"))

        val result = TaskTransferImporter(repo).importFrom(importUri(link.uri))

        assertEquals(1, result.taskCount)
        val imported = repo.tasksFlow.value.single()
        assertEquals("Pay electricity bill", imported.title)
        assertEquals("high", imported.priority)
        assertTrue(imported.flag)
    }

    @Test
    fun httpsCompressedLink_roundTripsWithStructure() = runTest {
        val repo = FakeYataRepository()
        val list = YataList(id = "list1", name = "Work", color = "accentA", icon = "folder")
        val original = task(title = "மின்சார கட்டணம் செலுத்து", listId = list.id)
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = mapOf(list.id to list),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = true, includeNotes = false
        )

        TaskTransferImporter(repo).importFrom(importUri(link.uri))

        val importedList = repo.listsFlow.value.single()
        assertEquals("Work", importedList.name)
        assertEquals(importedList.id, repo.tasksFlow.value.single().listId)
    }

    // --- build/share text ---------------------------------------------------------------

    @Test
    fun shareText_singularCount_usesSingularWord() {
        val link = buildTaskTransferLink(
            title = "Pay bill", tasks = listOf(task()), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        val text = link.asShareText("Pay bill", 1)
        assertTrue(text.contains("shared task: Pay bill"))
        assertTrue(text.contains(link.uri))
        // Shared links are https so chat apps linkify them, with the payload in the fragment.
        assertTrue(link.uri, link.uri.startsWith("https://ranjithj.in/yata/i#"))
        // The payload must never sit in the query string: link-preview crawlers fetch the URL
        // server-side, and anything before the "#" would be handed to them.
        assertNull(Uri.parse(link.uri).query)
    }

    @Test
    fun shareText_pluralCount_usesTaskCount() {
        val link = buildTaskTransferLink(
            title = "Work", tasks = listOf(task("t1"), task("t2")), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertTrue(link.asShareText("Work", 2).contains("shared 2 tasks: Work"))
    }

    @Test
    fun onlyOneLinkIsProduced_flagReflectsWhatWasActuallyEmbedded() {
        // The v1 format's bug this closes: a privacy-mode export used to still hand out a
        // second "copies structure" link whose payload had no structure in it. Now there is
        // exactly one link, and its flag can't disagree with its payload.
        val withStructure = buildTaskTransferLink(
            title = "Work", tasks = listOf(task(listId = "list1")),
            listsById = mapOf("list1" to YataList(id = "list1", name = "Work", color = "accentA", icon = "folder")),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = true, includeNotes = false
        )
        assertTrue(withStructure.includesStructure)
        assertEquals("1", linkParams(withStructure.uri).getQueryParameter("s"))

        // Non-Latin text is what reliably forces the compressed form now: plaintext can express
        // every field except structure, so the only thing that rules it out on a
        // structure-free share is losing on length. (An earlier revision of this test used a
        // subtask, which stopped working the moment plaintext learned to carry subtasks.)
        val withoutStructure = buildTaskTransferLink(
            title = "பணிகள்",
            tasks = listOf(task("t1", title = "மின்சார கட்டணம் செலுத்து", listId = "list1")),
            listsById = emptyMap(), projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertNotNull(linkParams(withoutStructure.uri).getQueryParameter("e"))
        assertFalse(withoutStructure.includesStructure)
        // "s" is omitted rather than spelled out as 0 — absence already means "don't copy
        // structure" on the import side, so writing the default cost four characters to say
        // nothing (docs/app-links-v3-plan.md §D).
        assertNull(linkParams(withoutStructure.uri).getQueryParameter("s"))
    }

    @Test
    fun absentStructureFlag_importsIdenticallyToAnExplicitZero() = runTest {
        val payload = JSONArray().put(3).put(JSONArray()).put(JSONArray()).put(JSONArray()).put(JSONArray())
            .put(JSONArray().put(JSONArray().put("Task")))

        val absent = FakeYataRepository()
        TaskTransferImporter(absent).importFrom(manualCompressedUri(payload, "e"))

        val explicitZero = FakeYataRepository()
        val withZero = Uri.parse(manualCompressedUri(payload, "e").toString() + "&s=0")
        TaskTransferImporter(explicitZero).importFrom(withZero)

        assertEquals(1, absent.tasksFlow.value.size)
        assertEquals(
            absent.tasksFlow.value.single().title,
            explicitZero.tasksFlow.value.single().title
        )
    }

    @Test
    fun simpleTask_producesAShorterLinkThanTheOldFormat() {
        // Not a specific byte target — just confirms the compaction is actually happening, since
        // this is the whole point of docs/app-links-v2-plan.md.
        val link = buildTaskTransferLink(
            title = "Pay bill", tasks = listOf(bareTask()), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        // Budget covers the https prefix ("https://ranjithj.in/yata/i#" — 27 chars) that replaced
        // "yata://i?"; the point is still that the payload itself stayed compact. Uses a task with
        // no schedule fields, since carrying a due date legitimately makes a link longer and would
        // turn this into a test of the fidelity policy rather than of the encoding.
        assertTrue("link unexpectedly long: ${link.uri.length}", link.uri.length < 70)
    }

    // --- round trip: no structure ---------------------------------------------------------

    @Test
    fun singleTask_noStructure_importLandsInInboxNormalized() = runTest {
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        val original = task(
            listId = "list1", projectId = "proj1", priority = "high", flag = true,
            notes = "secret notes", tagIds = listOf("tag1"), assigneeIds = listOf("person1")
        )
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )

        val result = importer.importFrom(importUri(link.uri))

        assertEquals(1, result.taskCount)
        assertFalse(result.copiedStructure)
        val imported = repo.tasksFlow.value.single()
        assertEquals(original.title, imported.title)
        assertEquals("high", imported.priority)
        assertTrue(imported.flag)
        assertNull(imported.listId)
        assertNull(imported.projectId)
        assertTrue(imported.tagIds.isEmpty())
        assertTrue(imported.assigneeIds.isEmpty())
        // Schedule fields now travel — they describe the work, not the sender's own planning, so
        // stripping them was losing exactly what a teammate needed
        // (docs/app-links-team-sharing-design.md §2).
        assertEquals("2026-08-20", imported.due)
        assertEquals("2026-08-18", imported.startDate)
        assertEquals("2:00 PM", imported.time)
        assertEquals(30, imported.estimateMinutes)
        // These stay behind: personal scheduling, sender-local layout, and sender-side lifecycle.
        assertNull(imported.reminder)
        assertNull(imported.recurrence)
        assertFalse(imported.done)
        assertNull(imported.completedAt)
        assertNull(imported.followUpAt)
        assertEquals("", imported.section)
        assertFalse(imported.archived)
        assertNull(imported.deletedAt)
        assertTrue(imported.id != original.id)
        assertNull(imported.notes)
    }

    @Test
    fun notes_excludedUnlessIncludeNotesRequested() = runTest {
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        val original = task(notes = "call before arriving")

        val withoutNotes = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        importer.importFrom(importUri(withoutNotes.uri))
        assertNull(repo.tasksFlow.value.single().notes)

        repo.tasksFlow.value = emptyList()
        val withNotes = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = true
        )
        importer.importFrom(importUri(withNotes.uri))
        assertEquals("call before arriving", repo.tasksFlow.value.single().notes)
    }

    // --- round trip: with structure -------------------------------------------------------

    @Test
    fun withStructure_createsMissingListsProjectsTagsPeople() = runTest {
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        val list = YataList(id = "list1", name = "Work", color = "accentA", icon = "folder")
        val project = Project(id = "proj1", name = "Launch", color = "accentB", icon = "layers")
        val tag = Tag(id = "tag1", name = "Urgent", color = "error", description = "time sensitive")
        val person = Person(id = "person1", name = "Ranjith", initials = "R", color = "accentC")
        val original = task(
            listId = list.id, projectId = project.id, tagIds = listOf(tag.id), assigneeIds = listOf(person.id)
        )

        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original),
            listsById = mapOf(list.id to list), projectsById = mapOf(project.id to project),
            tagsById = mapOf(tag.id to tag), peopleById = mapOf(person.id to person),
            includeStructure = true, includeNotes = false
        )

        val result = importer.importFrom(importUri(link.uri))

        assertTrue(result.copiedStructure)
        val importedList = repo.listsFlow.value.single { it.name == "Work" }
        val importedProject = repo.projectsFlow.value.single { it.name == "Launch" }
        val importedTag = repo.tagsFlow.value.single { it.name == "Urgent" }
        val importedPerson = repo.peopleFlow.value.single { it.name == "Ranjith" }
        assertTrue(importedList.id != list.id)
        assertTrue(importedTag.id != tag.id)
        assertEquals("time sensitive", importedTag.description)

        val imported = repo.tasksFlow.value.single()
        assertEquals(importedList.id, imported.listId)
        assertEquals(importedProject.id, imported.projectId)
        assertEquals(listOf(importedTag.id), imported.tagIds)
        assertEquals(listOf(importedPerson.id), imported.assigneeIds)
    }

    @Test
    fun withStructure_reusesExistingEntityByCaseInsensitiveName() = runTest {
        val repo = FakeYataRepository()
        val existing = YataList(id = "existing-list", name = "work", color = "accentD", icon = "star")
        repo.listsFlow.value = listOf(existing)
        val importer = TaskTransferImporter(repo)
        val list = YataList(id = "list1", name = "Work", color = "accentA", icon = "folder")
        val original = task(listId = list.id)

        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = mapOf(list.id to list),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = true, includeNotes = false
        )
        importer.importFrom(importUri(link.uri))

        assertEquals(1, repo.listsFlow.value.size)
        assertEquals(existing.id, repo.tasksFlow.value.single().listId)
    }

    @Test
    fun projectCommonTagIds_areCarriedOntoTheImportedProject() = runTest {
        // Regression test for the orphan-tag bug flagged in docs/app-links-v2-plan.md's phase 3:
        // a project's common tags used to be exported into the tag dictionary (so the tag got
        // created) but never attached to anything on import — not the task, not the project.
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        val tag = Tag(id = "tag1", name = "Urgent", color = "error")
        val project = Project(id = "proj1", name = "Launch", color = "accentB", icon = "layers", commonTagIds = listOf(tag.id))
        val original = task(projectId = project.id) // task itself doesn't carry the tag directly

        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original),
            listsById = emptyMap(), projectsById = mapOf(project.id to project),
            tagsById = mapOf(tag.id to tag), peopleById = emptyMap(),
            includeStructure = true, includeNotes = false
        )
        importer.importFrom(importUri(link.uri))

        val importedTag = repo.tagsFlow.value.single { it.name == "Urgent" }
        val importedProject = repo.projectsFlow.value.single { it.name == "Launch" }
        assertEquals(listOf(importedTag.id), importedProject.commonTagIds)
    }

    @Test
    fun subtasks_titlesAndOrderSurvive_butDoneAndParentAreReset() = runTest {
        // Pinned on the compressed path specifically (forced via a non-Latin title) — the
        // plaintext path carries subtasks too now and is covered separately, and this assertion
        // should keep holding for both rather than silently following whichever one wins.
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        val original = task(
            title = "மின்சார கட்டணம் செலுத்து",
            subtasks = listOf(
                Subtask(id = "s1", title = "Check amount", done = true, sortOrder = 0),
                Subtask(id = "s2", title = "Confirm payee", done = false, parentSubtaskId = "s1", sortOrder = 1)
            )
        )
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertNotNull(linkParams(link.uri).getQueryParameter("e"))

        importer.importFrom(importUri(link.uri))

        val imported = repo.tasksFlow.value.single().subtasks
        assertEquals(listOf("Check amount", "Confirm payee"), imported.map { it.title })
        assertTrue(imported.none { it.done })
        assertTrue(imported.all { it.parentSubtaskId == null })
    }

    @Test
    fun multipleTasks_allImportInOneLink() = runTest {
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        val link = buildTaskTransferLink(
            title = "Work", tasks = listOf(task("t1", title = "First"), task("t2", title = "Second")),
            listsById = emptyMap(), projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )

        val result = importer.importFrom(importUri(link.uri))

        assertEquals(2, result.taskCount)
        assertEquals(setOf("First", "Second"), repo.tasksFlow.value.map { it.title }.toSet())
    }

    @Test
    fun blankTitleTasks_areSkipped() = runTest {
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        val link = buildTaskTransferLink(
            title = "Work", tasks = listOf(task("t1", title = "Real task"), task("t2", title = "   ")),
            listsById = emptyMap(), projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )

        val result = importer.importFrom(importUri(link.uri))

        assertEquals(1, result.taskCount)
        assertEquals("Real task", repo.tasksFlow.value.single().title)
    }

    @Test
    fun priorityAtEachLevel_roundTripsThroughTheOrdinalEncoding() = runTest {
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        listOf("none", "low", "med", "high").forEach { priority ->
            repo.tasksFlow.value = emptyList()
            val link = buildTaskTransferLink(
                title = "T", tasks = listOf(task(priority = priority)), listsById = emptyMap(),
                projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
                includeStructure = false, includeNotes = false
            )
            importer.importFrom(importUri(link.uri))
            assertEquals(priority, repo.tasksFlow.value.single().priority)
        }
    }

    // --- plaintext fast path -----------------------------------------------------------------

    @Test
    fun simpleAsciiSingleTask_usesThePlaintextForm_notTheCompressedBlob() {
        val link = buildTaskTransferLink(
            title = "Pay bill", tasks = listOf(task(priority = "none", flag = false)),
            listsById = emptyMap(), projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        val uri = linkParams(link.uri)
        assertNull(uri.getQueryParameter("e"))
        assertEquals("Pay electricity bill", uri.getQueryParameter("t"))
        // Default priority/flag are omitted entirely, not just zeroed, to keep the readable link short.
        assertNull(uri.getQueryParameter("p0"))
        assertNull(uri.getQueryParameter("f0"))
    }

    @Test
    fun nonLatinTitle_usesTheCompressedForm_becausePercentEncodingIsFarWorseThere() {
        // The regression this closes (docs/app-links-v3-plan.md §A): selection used to be by task
        // *shape*, so a single Indic-script task always took the plaintext path — where each
        // 3-byte character costs 9 percent-encoded chars, making the link up to 2x LONGER than
        // the compressed form. YATA ships nine Indic locales, so this was not an edge case.
        val tamil = task(title = "மின்சார கட்டணம் செலுத்து", priority = "none")
        val link = buildTaskTransferLink(
            title = tamil.title, tasks = listOf(tamil), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertNotNull(linkParams(link.uri).getQueryParameter("e"))
        assertNull(linkParams(link.uri).getQueryParameter("t"))
    }

    @Test
    fun nonLatinTitle_stillRoundTripsThroughWhicheverFormWasChosen() = runTest {
        val repo = FakeYataRepository()
        val hindi = task(title = "बिजली का बिल भरना", priority = "high", flag = true)
        val link = buildTaskTransferLink(
            title = hindi.title, tasks = listOf(hindi), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )

        TaskTransferImporter(repo).importFrom(importUri(link.uri))

        val imported = repo.tasksFlow.value.single()
        assertEquals("बिजली का बिल भरना", imported.title)
        assertEquals("high", imported.priority)
        assertTrue(imported.flag)
    }

    @Test
    fun emittedLinkIsNeverLongerThanThePlaintextAlternative() {
        // The invariant that replaces the old shape heuristic. Stated once, over a spread of
        // scripts and task counts, so no future "just take the readable one when X" shortcut can
        // quietly reintroduce a case where YATA hands the user the longer of two links.
        // The plaintext candidate is rebuilt here with the same Uri.Builder encoding the
        // production path uses, so the comparison is like-for-like rather than approximate.
        val samples = listOf(
            "Pay electricity bill",             // Latin, plaintext should win
            "மின்சார கட்டணம் செலுத்து",          // Tamil, compressed should win
            "बिजली का बिल भरना",                 // Devanagari
            "Zahlung der Stromrechnung heute"   // longer Latin
        )
        samples.forEach { title ->
            listOf(1, 3, 8).forEach { count ->
                // Bare tasks so the titles-only baseline below is a like-for-like alternative:
                // a task with a due date makes the real plaintext form carry extra parameters,
                // and comparing against a baseline that silently omits them would be comparing
                // against a link that loses data.
                val tasks = (0 until count).map { bareTask("t$it", title = "$title $it") }
                val chosen = buildTaskTransferLink(
                    title = title, tasks = tasks, listsById = emptyMap(), projectsById = emptyMap(),
                    tagsById = emptyMap(), peopleById = emptyMap(),
                    includeStructure = false, includeNotes = false
                ).uri
                // Same https wrapper the production path applies, so the comparison is
                // like-for-like rather than penalising the chosen link by its URL prefix.
                val plaintextParams = Uri.Builder().scheme("yata").authority("i").also { b ->
                    tasks.forEach { b.appendQueryParameter("t", it.title) }
                }.build()
                val plaintextEquivalent = Uri.Builder().scheme("https").authority("ranjithj.in")
                    .path("/yata/i").encodedFragment(plaintextParams.encodedQuery).build().toString()

                assertTrue(
                    "\"$title\" x$count produced a ${chosen.length}-char link when a " +
                        "${plaintextEquivalent.length}-char plaintext one was available",
                    chosen.length <= plaintextEquivalent.length
                )
            }
        }
    }

    @Test
    fun simpleSingleTask_plaintextLink_roundTrips() = runTest {
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        val original = task(priority = "med", flag = true)
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )

        val result = importer.importFrom(importUri(link.uri))

        assertEquals(1, result.taskCount)
        assertFalse(result.copiedStructure)
        val imported = repo.tasksFlow.value.single()
        assertEquals(original.title, imported.title)
        assertEquals("med", imported.priority)
        assertTrue(imported.flag)
        assertFalse(imported.done)
        assertNull(imported.listId)
    }

    @Test
    fun plaintextForm_canNowCarryNotesAndSubtasks_andRoundTripsThem() = runTest {
        // Phase C: previously these forced the compressed form regardless of whether it was
        // shorter. Now they're expressible, so size selection gets to consider both.
        val repo = FakeYataRepository()
        val original = task(
            notes = "call first",
            subtasks = listOf(
                Subtask(id = "s1", title = "Check amount", done = true, sortOrder = 0),
                Subtask(id = "s2", title = "Confirm payee", done = false, sortOrder = 1)
            )
        )
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = true
        )
        assertNotNull(linkParams(link.uri).getQueryParameter("t"))

        TaskTransferImporter(repo).importFrom(importUri(link.uri))

        val imported = repo.tasksFlow.value.single()
        assertEquals("call first", imported.notes)
        assertEquals(listOf("Check amount", "Confirm payee"), imported.subtasks.map { it.title })
        // Same normalization as the compressed path: subtasks always arrive not-done and flat.
        assertTrue(imported.subtasks.none { it.done })
        assertTrue(imported.subtasks.all { it.parentSubtaskId == null })
    }

    @Test
    fun plaintextForm_keepsNotesAndSubtasksOnTheRightTask() = runTest {
        // Per-task alignment across repeated/indexed parameters, with the middle task the only
        // one carrying anything — the case a naive positional encoding gets wrong.
        val repo = FakeYataRepository()
        val tasks = listOf(
            task("t1", title = "First", priority = "none"),
            task("t2", title = "Second", priority = "none", notes = "only note",
                subtasks = listOf(Subtask(id = "s1", title = "Only subtask", done = false))),
            task("t3", title = "Third", priority = "none")
        )
        val link = buildTaskTransferLink(
            title = "Work", tasks = tasks, listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = true
        )

        TaskTransferImporter(repo).importFrom(importUri(link.uri))

        val imported = repo.tasksFlow.value.associateBy { it.title }
        assertEquals(3, imported.size)
        assertNull(imported.getValue("First").notes)
        assertEquals("only note", imported.getValue("Second").notes)
        assertNull(imported.getValue("Third").notes)
        assertTrue(imported.getValue("First").subtasks.isEmpty())
        assertEquals(listOf("Only subtask"), imported.getValue("Second").subtasks.map { it.title })
        assertTrue(imported.getValue("Third").subtasks.isEmpty())
    }

    @Test
    fun notesAreStillDroppedFromThePlaintextForm_whenNotesAreExcluded() = runTest {
        val repo = FakeYataRepository()
        val link = buildTaskTransferLink(
            title = "Pay bill", tasks = listOf(task(notes = "secret")), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertNull(linkParams(link.uri).getQueryParameter("n0"))

        TaskTransferImporter(repo).importFrom(importUri(link.uri))
        assertNull(repo.tasksFlow.value.single().notes)
    }

    @Test
    fun taskWithStructure_doesNotUseThePlaintextPath() {
        val list = YataList(id = "list1", name = "Work", color = "accentA", icon = "folder")
        val link = buildTaskTransferLink(
            title = "Pay bill", tasks = listOf(task(listId = "list1")),
            listsById = mapOf("list1" to list), projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = true, includeNotes = false
        )
        assertNull(linkParams(link.uri).getQueryParameter("t"))
    }

    @Test
    fun multipleSimpleAsciiTasks_nowUseThePlaintextForm_andRoundTripWithTheirPriorities() = runTest {
        // Previously excluded by the shape gate even though plaintext stays shorter well past one
        // task for Latin scripts (measured: it wins up to n=8 — docs/app-links-v3-plan.md §A).
        val repo = FakeYataRepository()
        // Bare tasks: with a full schedule attached the plaintext form legitimately grows past
        // the compressed one and loses selection, which would make this a test of the fidelity
        // policy rather than of multi-task plaintext encoding.
        val tasks = listOf(
            bareTask("t1", title = "First", priority = "none"),
            bareTask("t2", title = "Second", priority = "high").copy(flag = true),
            bareTask("t3", title = "Third", priority = "low")
        )
        val link = buildTaskTransferLink(
            title = "Work", tasks = tasks, listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertEquals(listOf("First", "Second", "Third"), linkParams(link.uri).getQueryParameters("t"))

        TaskTransferImporter(repo).importFrom(importUri(link.uri))

        val imported = repo.tasksFlow.value.associateBy { it.title }
        assertEquals(3, imported.size)
        // Indexed p/f must land on the right rows — the whole reason they aren't positional.
        assertEquals("none", imported.getValue("First").priority)
        assertEquals("high", imported.getValue("Second").priority)
        assertEquals("low", imported.getValue("Third").priority)
        assertFalse(imported.getValue("First").flag)
        assertTrue(imported.getValue("Second").flag)
        assertFalse(imported.getValue("Third").flag)
    }

    @Test
    fun plaintextForm_keepsPriorityAlignedWhenAnEarlierTitleIsBlank() = runTest {
        // A blank title is dropped on import; the indices of the survivors must still resolve
        // against their own original positions rather than sliding up by one.
        val repo = FakeYataRepository()
        val tasks = listOf(
            task("t1", title = "   ", priority = "none"),
            task("t2", title = "Real", priority = "high")
        )
        val link = buildTaskTransferLink(
            title = "Work", tasks = tasks, listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )

        TaskTransferImporter(repo).importFrom(importUri(link.uri))

        val imported = repo.tasksFlow.value.single()
        assertEquals("Real", imported.title)
        assertEquals("high", imported.priority)
    }

    // --- link length warning -----------------------------------------------------------------

    @Test
    fun shortLink_doesNotWarnAboutLength() {
        val link = buildTaskTransferLink(
            title = "Pay bill", tasks = listOf(task()), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertFalse(link.mayNotAutoLinkEverywhere)
        assertFalse(link.asShareText("Pay bill", 1).contains("some apps may not"))
    }

    @Test
    fun veryLongPayload_flagsThatItMayNotAutoLink() = runTest {
        // Deflate crushes repeated text almost to nothing, so a length-warning test needs content
        // that doesn't compress — a fixed-seed random string stands in for that reliably.
        val random = kotlin.random.Random(42)
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 "
        val hugeNotes = (1..2000).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        val original = task(notes = hugeNotes)
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = true
        )
        assertTrue(link.mayNotAutoLinkEverywhere)
        assertTrue(link.asShareText(original.title, 1).contains("some apps may not"))
        // Sanity: it's still a well-formed, importable link, just a long one.
        val repo = FakeYataRepository()
        importer(repo).importFrom(importUri(link.uri))
        assertEquals(1, repo.tasksFlow.value.size)
    }

    private fun importer(repo: FakeYataRepository) = TaskTransferImporter(repo)

    // --- v4 fidelity: fields that describe the work travel -----------------------------------

    @Test
    fun scheduleFieldsDescribingTheWork_survive_butPersonalOnesDoNot() = runTest {
        // docs/app-links-team-sharing-design.md §2: a deadline belongs to the task, a reminder
        // belongs to whoever set it. Forced onto the compressed path with a non-Latin title so
        // this pins the v4 payload rather than whichever encoding happened to win.
        val repo = FakeYataRepository()
        val original = task(title = "மின்சார கட்டணம் செலுத்து")
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertNotNull(linkParams(link.uri).getQueryParameter("e"))

        TaskTransferImporter(repo).importFrom(importUri(link.uri))

        val imported = repo.tasksFlow.value.single()
        assertEquals("2026-08-20", imported.due)
        assertEquals("2026-08-18", imported.startDate)
        assertEquals("2:00 PM", imported.time)
        assertEquals(30, imported.estimateMinutes)
        // Deliberately not carried.
        assertNull(imported.reminder)
        assertEquals("", imported.section)
        assertNull(imported.followUpAt)
        assertFalse(imported.done)
        assertNull(imported.completedAt)
    }

    @Test
    fun scheduleFieldsAlsoSurviveThePlaintextForm() = runTest {
        val repo = FakeYataRepository()
        val original = task(title = "Pay bill")
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertNotNull(linkParams(link.uri).getQueryParameter("t"))

        TaskTransferImporter(repo).importFrom(importUri(link.uri))

        val imported = repo.tasksFlow.value.single()
        assertEquals("2026-08-20", imported.due)
        assertEquals("2026-08-18", imported.startDate)
        assertEquals("2:00 PM", imported.time)
        assertEquals(30, imported.estimateMinutes)
        assertNull(imported.reminder)
    }

    @Test
    fun datesTravelAsAbsoluteValues_notOffsets() = runTest {
        // A link opened days after it was sent must still mean the same date.
        val repo = FakeYataRepository()
        val original = task(title = "Pay bill")
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        assertTrue(link.uri, link.uri.contains("2026-08-20") || linkParams(link.uri).getQueryParameter("e") != null)

        TaskTransferImporter(repo).importFrom(importUri(link.uri))
        assertEquals(original.due, repo.tasksFlow.value.single().due)
    }

    @Test
    fun recurrence_roundTripsWithAllItsParts() = runTest {
        val repo = FakeYataRepository()
        val recurring = task(title = "Weekly report").copy(
            recurrence = Recurrence(
                freq = "weekly",
                interval = 2,
                byday = listOf("MO", "TH"),
                bymonthday = null,
                ends = RecurrenceEnds.After(5),
                basedOnCompletion = true
            )
        )
        val link = buildTaskTransferLink(
            title = recurring.title, tasks = listOf(recurring), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )
        // Recurrence has no readable spelling, so this must take the compressed path rather than
        // silently dropping it to keep the prettier encoding.
        assertNotNull(linkParams(link.uri).getQueryParameter("e"))

        TaskTransferImporter(repo).importFrom(importUri(link.uri))

        val imported = repo.tasksFlow.value.single().recurrence
        assertNotNull(imported)
        assertEquals("weekly", imported!!.freq)
        assertEquals(2, imported.interval)
        assertEquals(listOf("MO", "TH"), imported.byday)
        assertEquals(RecurrenceEnds.After(5), imported.ends)
        assertTrue(imported.basedOnCompletion)
    }

    @Test
    fun recurrenceEndingOnADate_roundTrips() = runTest {
        val repo = FakeYataRepository()
        val recurring = task(title = "Monthly review").copy(
            recurrence = Recurrence(
                freq = "monthly", interval = 1, byday = null, bymonthday = 15,
                ends = RecurrenceEnds.On("2027-01-31"), basedOnCompletion = false
            )
        )
        val link = buildTaskTransferLink(
            title = recurring.title, tasks = listOf(recurring), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = false
        )

        TaskTransferImporter(repo).importFrom(importUri(link.uri))

        val imported = repo.tasksFlow.value.single().recurrence
        assertNotNull(imported)
        assertEquals(15, imported!!.bymonthday)
        assertEquals(RecurrenceEnds.On("2027-01-31"), imported.ends)
        assertNull(imported.byday)
    }

    @Test
    fun malformedRecurrenceFrequency_isDroppedRatherThanImported() = runTest {
        // An unknown frequency would make every downstream date calculation nonsense, so it must
        // not survive into the database.
        val repo = FakeYataRepository()
        val payload = JSONArray().put(4).put(JSONArray()).put(JSONArray()).put(JSONArray()).put(JSONArray())
            .put(
                JSONArray().put(
                    JSONArray().put("Task").put(0).put(false).put("").put(-1).put(-1)
                        .put(JSONArray()).put(JSONArray()).put(JSONArray())
                        .put("").put("").put("").put(0)
                        .put(JSONArray().put("fortnightly").put(1))
                )
            )

        TaskTransferImporter(repo).importFrom(manualCompressedUri(payload, "e"))

        assertNull(repo.tasksFlow.value.single().recurrence)
    }

    @Test
    fun v3PayloadWithoutScheduleFields_stillImports() = runTest {
        // Links shared before v4. The row simply stops before the schedule slots.
        val repo = FakeYataRepository()
        val payload = JSONArray().put(3).put(JSONArray()).put(JSONArray()).put(JSONArray()).put(JSONArray())
            .put(JSONArray().put(JSONArray().put("Pay electricity bill").put(3).put(true)))

        val result = TaskTransferImporter(repo).importFrom(manualCompressedUri(payload, "e"))

        assertEquals(1, result.taskCount)
        val imported = repo.tasksFlow.value.single()
        assertEquals("Pay electricity bill", imported.title)
        assertEquals("high", imported.priority)
        assertNull(imported.due)
        assertNull(imported.recurrence)
        assertNull(imported.estimateMinutes)
    }

    // --- v1 legacy links still import ------------------------------------------------------

    @Test
    fun legacyV1Link_stillImportsCorrectly() = runTest {
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        val uri = legacyV1Uri(
            JSONObject().put("v", 1).put("title", "Work").put(
                "tasks",
                JSONArray().put(
                    JSONObject().put("t", "Pay electricity bill").put("p", "high").put("f", true)
                        .put("tags", JSONArray()).put("people", JSONArray()).put("subs", JSONArray())
                )
            ),
            copyStructure = false
        )

        val result = importer.importFrom(uri)

        assertEquals(1, result.taskCount)
        val imported = repo.tasksFlow.value.single()
        assertEquals("Pay electricity bill", imported.title)
        assertEquals("high", imported.priority)
        assertTrue(imported.flag)
        assertFalse(imported.done)
    }

    @Test
    fun legacyV1Link_withStructure_stillCreatesEntities() = runTest {
        val repo = FakeYataRepository()
        val importer = TaskTransferImporter(repo)
        val uri = legacyV1Uri(
            JSONObject().put("v", 1).put("title", "Work")
                .put("tasks", JSONArray().put(JSONObject().put("t", "Task").put("p", "none").put("f", false).put("l", "list1").put("tags", JSONArray()).put("people", JSONArray()).put("subs", JSONArray())))
                .put("lists", JSONArray().put(JSONObject().put("id", "list1").put("n", "Work").put("c", "accentA").put("i", "folder"))),
            copyStructure = true
        )

        importer.importFrom(uri)

        assertEquals(1, repo.listsFlow.value.size)
        assertEquals("Work", repo.tasksFlow.value.single().let { task -> repo.listsFlow.value.first { it.id == task.listId }.name })
    }

    // --- error paths -----------------------------------------------------------------------

    @Test
    fun emptyTasksArray_isRejected() = runTest {
        // v3 layout: [version, lists, projects, tags, people, tasks] — no share title.
        val payload = JSONArray().put(3).put(JSONArray()).put(JSONArray()).put(JSONArray()).put(JSONArray()).put(JSONArray())
        try {
            TaskTransferImporter(FakeYataRepository()).importFrom(manualCompressedUri(payload, "e"))
            fail("expected import of an empty task list to be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("No tasks found"))
        }
    }

    @Test
    fun unsupportedVersion_isRejected() = runTest {
        val payload = JSONArray().put(99).put(JSONArray()).put(JSONArray()).put(JSONArray()).put(JSONArray())
            .put(JSONArray().put(JSONArray().put("Task")))
        try {
            TaskTransferImporter(FakeYataRepository()).importFrom(manualCompressedUri(payload, "e"))
            fail("expected an unrecognized version to be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("Unsupported"))
        }
    }

    @Test
    fun v2CompressedPayload_stillImports() = runTest {
        // Links generated before the title field was dropped. v2 kept the (unread) share title at
        // index 1, so every section sits one slot later than in v3.
        val repo = FakeYataRepository()
        val payload = JSONArray().put(2).put("Shared task set")
            .put(JSONArray()).put(JSONArray()).put(JSONArray()).put(JSONArray())
            .put(JSONArray().put(JSONArray().put("Pay electricity bill").put(3).put(true)))

        val result = TaskTransferImporter(repo).importFrom(manualCompressedUri(payload, "d"))

        assertEquals(1, result.taskCount)
        val imported = repo.tasksFlow.value.single()
        assertEquals("Pay electricity bill", imported.title)
        assertEquals("high", imported.priority)
        assertTrue(imported.flag)
    }

    @Test
    fun v2CompressedPayload_withStructure_stillResolvesDictionaryIndexes() = runTest {
        val repo = FakeYataRepository()
        val payload = JSONArray().put(2).put("Work")
            .put(JSONArray().put(JSONArray().put("Work").put("accentA").put("folder")))
            .put(JSONArray()).put(JSONArray()).put(JSONArray())
            .put(JSONArray().put(JSONArray().put("Task").put(0).put(false).put("").put(0)))

        TaskTransferImporter(repo).importFrom(manualCompressedUri(payload, "d", copyStructure = true))

        val list = repo.listsFlow.value.single()
        assertEquals("Work", list.name)
        assertEquals(list.id, repo.tasksFlow.value.single().listId)
    }

    @Test
    fun malformedBase64Payload_throwsAndWritesNothing() = runTest {
        val uri = Uri.Builder().scheme("yata").authority("i")
            .appendQueryParameter("s", "0")
            .appendQueryParameter("d", "not-valid-base64-!!!")
            .build()
        val repo = FakeYataRepository()
        try {
            TaskTransferImporter(repo).importFrom(uri)
            fail("expected malformed payload to throw")
        } catch (e: IllegalArgumentException) {
            // expected — Base64 decoding rejects it before any repository write happens.
        }
        assertTrue(repo.tasksFlow.value.isEmpty())
    }

    @Test
    fun oversizedPayload_isRejected() = runTest {
        val hugeNotes = "x".repeat(300_000)
        val original = task(notes = hugeNotes)
        val link = buildTaskTransferLink(
            title = original.title, tasks = listOf(original), listsById = emptyMap(),
            projectsById = emptyMap(), tagsById = emptyMap(), peopleById = emptyMap(),
            includeStructure = false, includeNotes = true
        )
        try {
            TaskTransferImporter(FakeYataRepository()).importFrom(importUri(link.uri))
            fail("expected an oversized decompressed payload to be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("too large"))
        }
    }

    @Test
    fun notATransferUri_isRejectedByImporter() = runTest {
        try {
            TaskTransferImporter(FakeYataRepository()).importFrom(Uri.parse("https://example.com"))
            fail("expected a non-transfer uri to be rejected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    /** Hand-builds a yata://i link from a raw payload array under the given payload parameter
     * ("e" = v3, "d" = v2), bypassing the production encoder. Needed both for importer-side
     * validation that [buildTaskTransferLink] can't produce (bad version, empty task list) and
     * for v2 payloads, which the builder no longer emits at all. */
    private fun manualCompressedUri(payload: JSONArray, param: String, copyStructure: Boolean = false): Uri {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val deflater = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION, true)
        val zipped = ByteArrayOutputStream().use { out ->
            java.util.zip.DeflaterOutputStream(out, deflater).use { it.write(bytes) }
            out.toByteArray()
        }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(zipped)
        return Uri.Builder().scheme("yata").authority("i")
            .also { if (copyStructure) it.appendQueryParameter("s", "1") }
            .appendQueryParameter(param, encoded)
            .build()
    }

    /** Hand-builds a legacy yata://import/tasks link (gzip + verbose JSON object), matching
     * exactly what commit 7db50e9's encoder used to produce, to prove already-shared links keep
     * working after the v2 switch. */
    private fun legacyV1Uri(payload: JSONObject, copyStructure: Boolean): Uri {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val zipped = ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(bytes) }
            out.toByteArray()
        }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(zipped)
        return Uri.Builder().scheme("yata").authority("import").path("/tasks")
            .appendQueryParameter("s", if (copyStructure) "1" else "0")
            .appendQueryParameter("d", encoded)
            .build()
    }
}

/** Minimal in-memory [YataRepository] covering only what [TaskTransferImporter] touches.
 * Everything else throws if called, so a test that accidentally depends on unimplemented
 * behaviour fails loudly instead of silently no-opping. */
private class FakeYataRepository : YataRepository {
    val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    val listsFlow = MutableStateFlow<List<YataList>>(emptyList())
    val projectsFlow = MutableStateFlow<List<Project>>(emptyList())
    val tagsFlow = MutableStateFlow<List<Tag>>(emptyList())
    val peopleFlow = MutableStateFlow<List<Person>>(emptyList())

    override fun getTasks(): Flow<List<Task>> = tasksFlow
    override fun getTaskById(id: String): Flow<Task?> = TODO("not used by TaskTransferImporter")
    override fun getInboxCandidateTasks(): Flow<List<Task>> = TODO()
    override fun getRecurringTasks(): Flow<List<Task>> = TODO()
    override fun getTasksForList(listId: String): Flow<List<Task>> = TODO()
    override fun getTasksForProject(projectId: String): Flow<List<Task>> = TODO()
    override fun getTasksForPerson(personId: String): Flow<List<Task>> = TODO()
    override suspend fun getTaskStreak(taskId: String): Int = TODO()

    override suspend fun upsertTask(task: Task, notify: Boolean, resyncReminder: Boolean) {
        tasksFlow.value = tasksFlow.value + task
    }

    override suspend fun upsertTasks(
        tasks: List<Task>,
        notify: Boolean,
        resyncReminder: Boolean,
        preserveExistingCreatedAt: Boolean
    ) {
        tasksFlow.value = tasksFlow.value + tasks
    }

    override suspend fun toggleTaskDone(id: String, notify: Boolean): Unit = TODO()
    override suspend fun skipTaskOccurrence(id: String): Unit = TODO()
    override suspend fun setTaskFlag(id: String, flag: Boolean, notify: Boolean): Unit = TODO()
    override suspend fun setTaskPriority(id: String, priority: String, notify: Boolean): Unit = TODO()
    override suspend fun setTaskContainer(id: String, listId: String?, projectId: String?, sortOrder: Int, notify: Boolean): Unit = TODO()
    override suspend fun setTaskSortOrder(id: String, sortOrder: Int, notify: Boolean): Unit = TODO()

    override fun notifyTasksChanged() {}

    override suspend fun deleteTask(task: Task, notify: Boolean): Unit = TODO()
    override fun getDeletedTasks(): Flow<List<Task>> = TODO()
    override suspend fun restoreTask(id: String): Unit = TODO()
    override fun getArchivedTasks(): Flow<List<Task>> = TODO()
    override suspend fun setTaskArchived(id: String, archived: Boolean): Unit = TODO()
    override suspend fun permanentlyDeleteTask(task: Task): Unit = TODO()
    override suspend fun emptyTrash(): Unit = TODO()
    override suspend fun purgeOldTrash(): Unit = TODO()
    override suspend fun autoArchiveOldCompleted(): Unit = TODO()

    override fun getCommentsForTask(taskId: String): Flow<List<TaskComment>> = TODO()
    override fun getAllComments(): Flow<List<TaskComment>> = TODO()
    override suspend fun addComment(taskId: String, body: String, authorId: String?): Unit = TODO()
    override suspend fun upsertComment(comment: TaskComment): Unit = TODO()
    override suspend fun deleteComment(comment: TaskComment): Unit = TODO()

    override fun getProjects(): Flow<List<Project>> = projectsFlow
    override fun getActiveProjects(): Flow<List<Project>> = TODO()
    override fun getArchivedProjects(): Flow<List<Project>> = TODO()
    override fun getProjectById(id: String): Flow<Project?> = TODO()
    override suspend fun upsertProject(project: Project) {
        projectsFlow.value = projectsFlow.value.filterNot { it.id == project.id } + project
    }
    override suspend fun deleteProject(project: Project): Unit = TODO()
    override suspend fun deleteProjectOnly(project: Project): Unit = TODO()
    override suspend fun setProjectsArchived(ids: List<String>, archived: Boolean): Unit = TODO()

    override fun getLists(): Flow<List<YataList>> = listsFlow
    override fun getActiveLists(): Flow<List<YataList>> = TODO()
    override fun getArchivedLists(): Flow<List<YataList>> = TODO()
    override fun getListById(id: String): Flow<YataList?> = TODO()
    override suspend fun upsertList(list: YataList) {
        listsFlow.value = listsFlow.value.filterNot { it.id == list.id } + list
    }
    override suspend fun deleteList(list: YataList): Unit = TODO()
    override suspend fun deleteListOnly(list: YataList): Unit = TODO()
    override suspend fun setListsArchived(ids: List<String>, archived: Boolean): Unit = TODO()

    override fun getPeople(): Flow<List<Person>> = peopleFlow
    override fun getActivePeople(): Flow<List<Person>> = TODO()
    override fun getArchivedPeople(): Flow<List<Person>> = TODO()
    override fun getPersonById(id: String): Flow<Person?> = TODO()
    override suspend fun upsertPerson(person: Person) {
        peopleFlow.value = peopleFlow.value.filterNot { it.id == person.id } + person
    }
    override suspend fun deletePerson(person: Person): Unit = TODO()

    override fun getPersonGroups(): Flow<List<PersonGroup>> = TODO()
    override suspend fun upsertPersonGroup(group: PersonGroup): Unit = TODO()
    override suspend fun deletePersonGroup(group: PersonGroup): Unit = TODO()

    override fun getTags(): Flow<List<Tag>> = tagsFlow
    override fun getTagById(id: String): Flow<Tag?> = TODO()
    override suspend fun upsertTag(tag: Tag) {
        tagsFlow.value = tagsFlow.value.filterNot { it.id == tag.id } + tag
    }
    override suspend fun upsertTags(tags: List<Tag>, pendingGroup: TagGroup?): Unit = TODO()
    override suspend fun setTagsGroup(tagIds: List<String>, groupId: String?, pendingGroup: TagGroup?): Unit = TODO()
    override suspend fun deleteTag(tag: Tag): Unit = TODO()

    override fun getTagGroups(): Flow<List<TagGroup>> = TODO()
    override suspend fun upsertTagGroup(group: TagGroup): Unit = TODO()
    override suspend fun deleteTagGroup(group: TagGroup): Unit = TODO()

    override suspend fun seedInitialDataIfNeeded(): Unit = TODO()
    override suspend fun deleteAllData(): Unit = TODO()
}
