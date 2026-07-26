package com.mj.yata.data.demo

import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.PersonGroup
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Recurrence
import com.mj.yata.domain.model.Subtask
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.TagGroup
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.TaskComment
import com.mj.yata.domain.model.YataList
import java.time.LocalDate

data class DemoDataset(
    val tasks: List<Task>,
    val projects: List<Project>,
    val lists: List<YataList>,
    val people: List<Person>,
    val personGroups: List<PersonGroup>,
    val tags: List<Tag>,
    val tagGroups: List<TagGroup>,
    val comments: List<TaskComment>
)

/** Builds a fresh, realistic dataset (dates relative to today) for demo-mode screenshots — pure
 * in-memory, never touches the real database. Regenerated each time [DemoRepository] is
 * constructed so dates stay current for the life of the process. */
object DemoData {

    const val ME_ID = "demo-person-me"
    private const val ANANYA_ID = "demo-person-ananya"
    private const val MARCUS_ID = "demo-person-marcus"
    private const val PRIYA_ID = "demo-person-priya"
    private const val LEO_ID = "demo-person-leo"

    private const val PROJ_WEBSITE = "demo-proj-website"
    private const val PROJ_LAUNCH = "demo-proj-launch"
    private const val PROJ_MARKETING = "demo-proj-marketing"
    private const val PROJ_RENOVATION = "demo-proj-renovation"

    private const val LIST_PERSONAL = "demo-list-personal"
    private const val LIST_WORK = "demo-list-work"
    private const val LIST_SHOPPING = "demo-list-shopping"

    private const val TAG_URGENT = "demo-tag-urgent"
    private const val TAG_WAITING = "demo-tag-waiting"
    private const val TAG_QUICKWIN = "demo-tag-quickwin"
    private const val TAG_MEETING = "demo-tag-meeting"
    private const val TAG_REVIEW = "demo-tag-review"

    fun build(): DemoDataset {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L

        val people = listOf(
            Person(id = ME_ID, name = "You", initials = "Y", color = "accentA", isMe = true, starred = true),
            Person(id = ANANYA_ID, name = "Ananya Rao", initials = "AR", color = "accentB", groupId = "demo-pgroup-team", starred = true),
            Person(id = MARCUS_ID, name = "Marcus Chen", initials = "MC", color = "accentC", groupId = "demo-pgroup-team"),
            Person(id = PRIYA_ID, name = "Priya Nair", initials = "PN", color = "accentD", groupId = "demo-pgroup-team"),
            Person(id = LEO_ID, name = "Leo Fischer", initials = "LF", color = "accentE", groupId = "demo-pgroup-team")
        )

        val personGroups = listOf(
            PersonGroup(id = "demo-pgroup-team", name = "Product Team", color = "accentB")
        )

        val projects = listOf(
            Project(
                id = PROJ_WEBSITE, name = "Website Redesign", color = "accentA", icon = "computer",
                starred = true, description = "Revamp the marketing site with the new brand system"
            ),
            Project(
                id = PROJ_LAUNCH, name = "Product Launch", color = "accentF", icon = "event",
                starred = true, description = "Coordinate the v2.0 launch across teams"
            ),
            Project(
                id = PROJ_MARKETING, name = "Q3 Marketing", color = "accentC", icon = "chart",
                description = "Campaign planning and content calendar"
            ),
            Project(
                id = PROJ_RENOVATION, name = "Home Renovation", color = "accentG", icon = "build",
                description = "Kitchen and living room remodel"
            )
        )

        val lists = listOf(
            YataList(id = LIST_PERSONAL, name = "Personal", color = "accentD", icon = "home", starred = true),
            YataList(id = LIST_WORK, name = "Work", color = "accentB", icon = "work"),
            YataList(id = LIST_SHOPPING, name = "Shopping", color = "accentE", icon = "shopping")
        )

        val tagGroups = listOf(
            TagGroup(id = "demo-taggroup-status", name = "Status", color = "accentJ")
        )

        val tags = listOf(
            Tag(id = TAG_URGENT, name = "Urgent", color = "error"),
            Tag(id = TAG_WAITING, name = "Waiting on", color = "accentH", groupId = "demo-taggroup-status"),
            Tag(id = TAG_QUICKWIN, name = "Quick win", color = "accentI"),
            Tag(id = TAG_MEETING, name = "Meeting", color = "accentJ"),
            Tag(id = TAG_REVIEW, name = "Needs review", color = "accentK", groupId = "demo-taggroup-status")
        )

        val logoTaskId = "demo-task-logo-concepts"

        val tasks = listOf(
            Task(
                id = "demo-task-wireframes", title = "Finalize homepage wireframes",
                listId = null, projectId = PROJ_WEBSITE, section = "Morning",
                due = today.minusDays(2).toString(), time = null, reminder = null,
                priority = "high", flag = true, done = false,
                assigneeIds = listOf(ANANYA_ID, MARCUS_ID), tagIds = listOf(TAG_URGENT),
                recurrence = null, subtasks = emptyList(),
                notes = "Waiting on brand color approval from Priya before the final pass.",
                sortOrder = 0
            ),
            Task(
                id = "demo-task-tiles", title = "Order kitchen tiles",
                listId = null, projectId = PROJ_RENOVATION, section = "Morning",
                due = today.minusDays(1).toString(), time = null, reminder = null,
                priority = "med", flag = false, done = false,
                assigneeIds = listOf(ME_ID), tagIds = listOf(TAG_WAITING),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 1
            ),
            Task(
                id = "demo-task-login-bug", title = "Fix critical login bug",
                listId = LIST_WORK, projectId = null, section = "Morning",
                due = today.minusDays(1).toString(), time = null, reminder = null,
                priority = "high", flag = true, done = false,
                assigneeIds = listOf(ME_ID), tagIds = listOf(TAG_URGENT),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 2
            ),
            Task(
                id = "demo-task-review-pr", title = "Review pull request from Marcus",
                listId = null, projectId = PROJ_WEBSITE, section = "Morning",
                due = today.toString(), time = "10:00 AM", reminder = null,
                priority = "high", flag = true, done = false,
                assigneeIds = listOf(ME_ID), tagIds = listOf(TAG_REVIEW),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 3
            ),
            Task(
                id = "demo-task-launch-checklist", title = "Prepare launch day checklist",
                listId = null, projectId = PROJ_LAUNCH, section = "Morning",
                due = today.toString(), time = "11:30 AM", reminder = "1 hour before",
                priority = "med", flag = false, done = false,
                assigneeIds = listOf(ME_ID, PRIYA_ID), tagIds = emptyList(),
                recurrence = null,
                subtasks = listOf(
                    Subtask(id = "demo-sub-1", title = "Confirm press release copy", done = true, sortOrder = 0),
                    Subtask(id = "demo-sub-2", title = "Schedule social posts", done = false, sortOrder = 1),
                    Subtask(id = "demo-sub-3", title = "Brief support team", done = false, sortOrder = 2)
                ),
                notes = null, sortOrder = 4
            ),
            Task(
                id = "demo-task-design-sync", title = "Sync with design team",
                listId = null, projectId = PROJ_WEBSITE, section = "Afternoon",
                due = today.toString(), time = "2:00 PM", reminder = "15 min before",
                priority = "none", flag = false, done = false,
                assigneeIds = listOf(ME_ID, ANANYA_ID), tagIds = listOf(TAG_MEETING),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 5
            ),
            Task(
                id = "demo-task-vendor-email", title = "Reply to vendor email",
                listId = LIST_WORK, projectId = null, section = "Afternoon",
                due = today.toString(), time = null, reminder = null,
                priority = "low", flag = false, done = false,
                assigneeIds = listOf(ME_ID), tagIds = listOf(TAG_QUICKWIN),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 6
            ),
            Task(
                id = "demo-task-walk", title = "30 min walk",
                listId = LIST_PERSONAL, projectId = null, section = "Afternoon",
                due = today.toString(), time = null, reminder = null,
                priority = "none", flag = false, done = false,
                assigneeIds = listOf(ME_ID), tagIds = emptyList(),
                recurrence = Recurrence(freq = "daily", interval = 1), subtasks = emptyList(),
                notes = null, sortOrder = 7
            ),
            Task(
                id = "demo-task-groceries", title = "Buy groceries for the week",
                listId = LIST_SHOPPING, projectId = null, section = "Afternoon",
                due = today.toString(), time = null, reminder = null,
                priority = "low", flag = false, done = false,
                assigneeIds = listOf(ME_ID), tagIds = emptyList(), recurrence = null,
                subtasks = listOf(
                    Subtask(id = "demo-sub-g1", title = "Milk", done = false, sortOrder = 0),
                    Subtask(id = "demo-sub-g2", title = "Eggs", done = false, sortOrder = 1),
                    Subtask(id = "demo-sub-g3", title = "Coffee", done = true, sortOrder = 2)
                ),
                notes = null, sortOrder = 8
            ),
            Task(
                id = "demo-task-campaign-brief", title = "Draft Q3 campaign brief",
                listId = null, projectId = PROJ_MARKETING, section = "Morning",
                due = today.plusDays(2).toString(), time = null, reminder = null,
                priority = "med", flag = false, done = false,
                assigneeIds = listOf(MARCUS_ID), tagIds = listOf(TAG_REVIEW),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 9
            ),
            Task(
                id = "demo-task-contractor-followup", title = "Follow up with contractor",
                listId = null, projectId = PROJ_RENOVATION, section = "Morning",
                due = today.plusDays(4).toString(), time = null, reminder = null,
                priority = "low", flag = false, done = false,
                assigneeIds = listOf(ME_ID), tagIds = listOf(TAG_WAITING),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 10
            ),
            Task(
                id = "demo-task-quarterly-planning", title = "Quarterly planning meeting",
                listId = LIST_WORK, projectId = null, section = "Morning",
                due = today.plusDays(6).toString(), time = "9:00 AM", reminder = "1 day before",
                priority = "med", flag = false, done = false,
                assigneeIds = listOf(ME_ID, ANANYA_ID, MARCUS_ID, PRIYA_ID), tagIds = listOf(TAG_MEETING),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 11
            ),
            Task(
                id = "demo-task-user-testing", title = "User testing session",
                listId = null, projectId = PROJ_WEBSITE, section = "Morning",
                due = today.plusDays(9).toString(), time = null, reminder = null,
                priority = "high", flag = false, done = false,
                assigneeIds = listOf(LEO_ID), tagIds = emptyList(),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 12
            ),
            Task(
                id = "demo-task-analytics-dashboard", title = "Set up analytics dashboard",
                listId = null, projectId = PROJ_MARKETING, section = "Morning",
                due = today.plusDays(1).toString(), time = null, reminder = null,
                priority = "med", flag = false, done = false,
                assigneeIds = listOf(MARCUS_ID), tagIds = emptyList(),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 13
            ),
            Task(
                id = "demo-task-atomic-habits", title = "Read \"Atomic Habits\"",
                listId = LIST_PERSONAL, projectId = null, section = "Afternoon",
                due = null, time = null, reminder = null,
                priority = "none", flag = false, done = false,
                assigneeIds = listOf(ME_ID), tagIds = emptyList(),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 14
            ),
            Task(
                id = "demo-task-resume", title = "Update resume",
                listId = LIST_WORK, projectId = null, section = "Afternoon",
                due = null, time = null, reminder = null,
                priority = "low", flag = false, done = false,
                assigneeIds = listOf(ME_ID), tagIds = emptyList(),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 15
            ),
            Task(
                id = "demo-task-status-update", title = "Send weekly status update",
                listId = LIST_WORK, projectId = null, section = "Morning",
                due = today.toString(), time = null, reminder = null,
                priority = "med", flag = false, done = true, completedAt = now - 3 * 60 * 60 * 1000,
                assigneeIds = listOf(ME_ID), tagIds = emptyList(),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 16
            ),
            Task(
                id = "demo-task-book-flight", title = "Book flight for offsite",
                listId = null, projectId = PROJ_LAUNCH, section = "Morning",
                due = today.minusDays(1).toString(), time = null, reminder = null,
                priority = "high", flag = false, done = true, completedAt = now - 1 * day,
                assigneeIds = listOf(ME_ID), tagIds = emptyList(),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 17
            ),
            Task(
                id = "demo-task-domain-renewal", title = "Renew domain registration",
                listId = LIST_WORK, projectId = null, section = "Morning",
                due = today.minusDays(3).toString(), time = null, reminder = null,
                priority = "low", flag = false, done = true, completedAt = now - 3 * day,
                assigneeIds = listOf(ME_ID), tagIds = emptyList(),
                recurrence = null, subtasks = emptyList(), notes = null, sortOrder = 18
            ),
            Task(
                id = logoTaskId, title = "Design new logo concepts",
                listId = null, projectId = PROJ_WEBSITE, section = "Morning",
                due = today.minusDays(5).toString(), time = null, reminder = null,
                priority = "high", flag = false, done = true, completedAt = now - 5 * day,
                assigneeIds = listOf(ANANYA_ID, ME_ID), tagIds = listOf(TAG_REVIEW),
                recurrence = null,
                subtasks = listOf(
                    Subtask(id = "demo-sub-l1", title = "Concept sketches", done = true, sortOrder = 0),
                    Subtask(id = "demo-sub-l2", title = "Client feedback round", done = true, sortOrder = 1)
                ),
                notes = "Approved v3 — warm coral direction. Assets exported to the shared drive.",
                sortOrder = 19
            )
        )

        val comments = listOf(
            TaskComment(
                id = "demo-comment-1", taskId = logoTaskId,
                body = "Loving the new coral direction!", createdAt = now - 5 * day + 3600_000,
                authorId = ANANYA_ID
            ),
            TaskComment(
                id = "demo-comment-2", taskId = logoTaskId,
                body = "Agreed, let's move forward with v3.", createdAt = now - 5 * day + 7200_000,
                authorId = ME_ID
            )
        )

        return DemoDataset(
            tasks = tasks, projects = projects, lists = lists, people = people,
            personGroups = personGroups, tags = tags, tagGroups = tagGroups, comments = comments
        )
    }
}
