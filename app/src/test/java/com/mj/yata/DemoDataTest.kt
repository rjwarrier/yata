package com.mj.yata

import com.mj.yata.data.demo.DemoData
import com.mj.yata.data.demo.DemoRepository
import com.mj.yata.domain.model.isDeferredOn
import com.mj.yata.domain.model.isWaitingOn
import com.mj.yata.util.RecurrenceEvaluator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DemoDataTest {
    private val today = LocalDate.of(2026, 10, 27)

    @Test fun datasetReferencesRemainValidAcrossGroupsProjectsTasksAndSubtasks() {
        val data = DemoData.build(today)
        val projects = data.projects.associateBy { it.id }
        val groups = data.projectGroups.map { it.id }.toSet()
        val people = data.people.map { it.id }.toSet()
        val tags = data.tags.map { it.id }.toSet()
        val lists = data.lists.map { it.id }.toSet()
        val taskIds = data.tasks.map { it.id }.toSet()
        assertEquals(data.tasks.size, taskIds.size)
        assertEquals(data.projects.size, projects.size)
        assertEquals(data.projectGroups.size, groups.size)
        data.projects.forEach { project ->
            project.groupId?.let { assertTrue(it in groups) }
            assertTrue(tags.containsAll(project.commonTagIds))
        }
        data.tasks.forEach { task ->
            task.projectId?.let { assertTrue(it in projects) }
            task.listId?.let { assertTrue(it in lists) }
            assertFalse(task.projectId != null && task.listId != null)
            assertTrue(people.containsAll(task.assigneeIds))
            assertTrue(tags.containsAll(task.tagIds))
            task.projectId?.let { id ->
                val sections = projects.getValue(id).sectionNames
                assertTrue(task.section.isBlank() || sections.isEmpty() || task.section in sections)
            }
            val subtaskIds = task.subtasks.map { it.id }.toSet()
            assertEquals(task.subtasks.size, subtaskIds.size)
            task.subtasks.forEach { subtask ->
                subtask.parentSubtaskId?.let { assertTrue(it in subtaskIds && it != subtask.id) }
            }
            assertNotNull(task.createdAt)
            task.completedAt?.let { assertTrue(task.createdAt!! <= it) }
        }
        data.comments.forEach {
            assertTrue(it.taskId in taskIds)
            it.authorId?.let { id -> assertTrue(id in people) }
        }
    }

    @Test fun samplesPopulateNewerScreensAndKeepDatesRelative() {
        val data = DemoData.build(today)
        val now = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue(data.projectGroups.size >= 2)
        assertTrue(data.projects.any { it.groupId == null && !it.archived })
        assertTrue(data.projectGroups.any { group -> data.projects.none { it.groupId == group.id } })
        assertTrue(data.projects.any { it.archived })
        assertTrue(data.projects.any { it.sectionNames.isNotEmpty() && it.commonTagIds.isNotEmpty() })
        assertTrue(data.tags.any { !it.description.isNullOrBlank() })
        assertTrue(data.tasks.any { it.estimateMinutes != null && it.trackedMinutes > 0 && !it.done })
        assertTrue(data.tasks.any { it.done && it.estimateMinutes != null && it.trackedMinutes > 0 })
        assertTrue(data.tasks.any { it.postponementCount > 0 })
        assertTrue(data.tasks.any { it.isDeferredOn(today.toString()) })
        assertTrue(data.tasks.any { it.isWaitingOn(now, DemoData.ME_ID) })
        assertTrue(data.tasks.any { task -> task.subtasks.any { it.parentSubtaskId != null } })
        assertTrue(data.projects.any { it.name == "Q4 Marketing" })
        val shifted = DemoData.build(today.plusDays(1))
        data.tasks.filter { it.due != null }.forEach { original ->
            val next = shifted.tasks.single { it.id == original.id }
            assertEquals(LocalDate.parse(original.due).plusDays(1).toString(), next.due)
        }
    }

    @Test fun repositorySeparatesLiveArchivedAndTrashedTasksForEveryRead() = runBlocking {
        val repository = DemoRepository()
        val live = repository.getTasks().first()
        val archived = repository.getArchivedTasks().first()
        val trashed = repository.getDeletedTasks().first()
        assertTrue(archived.isNotEmpty())
        assertTrue(trashed.isNotEmpty())
        val hiddenIds = (archived + trashed).map { it.id }.toSet()
        assertTrue(live.none { it.id in hiddenIds || it.archived || it.deletedAt != null })
        assertTrue(repository.getInboxCandidateTasks().first().none { it.id in hiddenIds || it.done })
        assertTrue(repository.getRecurringTasks().first().none { it.id in hiddenIds || it.done })
        assertTrue(repository.getOpenTasks().all { !it.done && it.id !in hiddenIds })
        assertTrue(repository.getTasksByIds(hiddenIds).isEmpty())
        (archived + trashed).forEach { hidden ->
            assertNotNull(repository.getTaskById(hidden.id).first())
            hidden.listId?.let { id -> assertTrue(repository.getTasksForList(id).first().none { it.id in hiddenIds }) }
            hidden.projectId?.let { id -> assertTrue(repository.getTasksForProject(id).first().none { it.id in hiddenIds }) }
            hidden.assigneeIds.forEach { id -> assertTrue(repository.getTasksForPerson(id).first().none { it.id in hiddenIds }) }
        }
    }

    @Test fun recurringTaskHasHistoryForHeatmapAndStreak() = runBlocking {
        val repository = DemoRepository()
        val recurring = repository.getRecurringTasks().first().first { it.seriesId != null }
        val history = repository.getSeriesCompletions(recurring.id)
        assertTrue(history.isNotEmpty())
        assertTrue(history.all { it.done && it.seriesId == recurring.seriesId && it.trackedMinutes > 0 })
        assertEquals(history.sortedByDescending { it.completedAt }, history)
        assertTrue(RecurrenceEvaluator.computeStreak(history) > 0)
        assertTrue(repository.getSeriesCompletions("missing-task").isEmpty())
    }

    @Test fun demoWritesRemainReadOnly() = runBlocking {
        val repository = DemoRepository()
        val tasks = repository.getTasks().first()
        val projects = repository.getProjects().first()
        val groups = repository.getProjectGroups().first()
        assertTrue(groups.isNotEmpty())
        repository.upsertTask(tasks.first().copy(title = "Changed"))
        repository.deleteProjectGroup(groups.first())
        repository.setProjectsGroup(projects.map { it.id }, null)
        repository.deleteAllData()
        assertEquals(tasks, repository.getTasks().first())
        assertEquals(projects, repository.getProjects().first())
        assertEquals(groups, repository.getProjectGroups().first())
    }
}
