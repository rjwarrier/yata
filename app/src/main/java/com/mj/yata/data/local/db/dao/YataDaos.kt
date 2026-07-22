package com.mj.yata.data.local.db.dao

import androidx.room.*
import com.mj.yata.data.local.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM people")
    fun getAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE archived = 0")
    fun getActive(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE archived = 1")
    fun getArchived(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :id")
    fun getById(id: String): Flow<PersonEntity?>

    @Query("SELECT * FROM people WHERE id = :id")
    fun getByIdDirect(id: String): PersonEntity?

    @Upsert
    suspend fun insert(person: PersonEntity)

    @Upsert
    suspend fun insertAll(people: List<PersonEntity>)

    @Delete
    suspend fun delete(person: PersonEntity)

    @Query("UPDATE people SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: String)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects")
    fun getAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE archived = 0")
    fun getActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE archived = 1")
    fun getArchived(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getByIdDirect(id: String): ProjectEntity?

    @Upsert
    suspend fun insert(project: ProjectEntity)

    @Upsert
    suspend fun insertAll(projects: List<ProjectEntity>)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("UPDATE projects SET archived = :archived WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<String>, archived: Boolean)
}

@Dao
interface ListDao {
    @Query("SELECT * FROM lists")
    fun getAll(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists WHERE archived = 0")
    fun getActive(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists WHERE archived = 1")
    fun getArchived(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists WHERE id = :id")
    fun getById(id: String): Flow<ListEntity?>

    @Query("SELECT * FROM lists WHERE id = :id")
    fun getByIdDirect(id: String): ListEntity?

    @Upsert
    suspend fun insert(list: ListEntity)

    @Upsert
    suspend fun insertAll(lists: List<ListEntity>)

    @Delete
    suspend fun delete(list: ListEntity)

    @Query("UPDATE lists SET archived = :archived WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<String>, archived: Boolean)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags")
    fun getAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    fun getById(id: String): Flow<TagEntity?>

    @Query("SELECT * FROM tags WHERE id = :id")
    fun getByIdDirect(id: String): TagEntity?

    @Upsert
    suspend fun insert(tag: TagEntity)

    @Upsert
    suspend fun insertAll(tags: List<TagEntity>)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("UPDATE tags SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: String)
}

@Dao
interface TagGroupDao {
    @Query("SELECT * FROM tag_groups")
    fun getAll(): Flow<List<TagGroupEntity>>

    @Upsert
    suspend fun insert(group: TagGroupEntity)

    @Delete
    suspend fun delete(group: TagGroupEntity)
}

@Dao
interface PersonGroupDao {
    @Query("SELECT * FROM person_groups")
    fun getAll(): Flow<List<PersonGroupEntity>>

    @Upsert
    suspend fun insert(group: PersonGroupEntity)

    @Delete
    suspend fun delete(group: PersonGroupEntity)
}

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    fun getTasksWithRelations(): Flow<List<TaskWithRelations>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedTasksWithRelations(): Flow<List<TaskWithRelations>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getTaskWithRelationsById(id: String): Flow<TaskDetailWithRelations?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE listId = :listId AND deletedAt IS NULL ORDER BY sortOrder ASC")
    fun getTasksWithRelationsForList(listId: String): Flow<List<TaskWithRelations>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY sortOrder ASC")
    fun getTasksWithRelationsForProject(projectId: String): Flow<List<TaskWithRelations>>

    @Transaction
    @Query("""
        SELECT t.* FROM tasks t
        INNER JOIN task_person_cross_ref r ON t.id = r.taskId
        WHERE r.personId = :personId AND t.deletedAt IS NULL
        ORDER BY t.sortOrder ASC
    """)
    fun getTasksWithRelationsForPerson(personId: String): Flow<List<TaskWithRelations>>

    @Transaction
    @Query("""
        SELECT DISTINCT t.* FROM tasks t
        LEFT JOIN task_person_cross_ref pRef ON t.id = pRef.taskId
        LEFT JOIN people p ON pRef.personId = p.id
        LEFT JOIN task_tag_cross_ref tRef ON t.id = tRef.taskId
        LEFT JOIN tags tag ON tRef.tagId = tag.id
        LEFT JOIN subtasks s ON t.id = s.taskId
        WHERE (
            t.rowid IN (SELECT rowid FROM tasks_fts WHERE tasks_fts MATCH :searchQuery)
            OR p.name LIKE '%' || :rawQuery || '%'
            OR tag.name LIKE '%' || :rawQuery || '%'
            OR s.title LIKE '%' || :rawQuery || '%'
        ) AND t.deletedAt IS NULL
    """)
    fun searchTasksWithRelations(searchQuery: String, rawQuery: String): Flow<List<TaskWithRelations>>

    @Query("SELECT * FROM tasks")
    fun getAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getByIdDirect(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE done = 0 AND dueDate IS NOT NULL AND deletedAt IS NULL")
    fun getActiveReminderTasksDirect(): List<TaskEntity>

    @Upsert
    suspend fun insert(task: TaskEntity)

    @Query("UPDATE tasks SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: String, timestamp: Long)

    @Query("UPDATE tasks SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("UPDATE tasks SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearProject(projectId: String)

    @Query("UPDATE tasks SET done = :done, completedAt = :completedAt WHERE id = :id")
    suspend fun updateDone(id: String, done: Boolean, completedAt: Long?)

    @Query("UPDATE tasks SET flag = :flag WHERE id = :id")
    suspend fun updateFlag(id: String, flag: Boolean)

    @Query("UPDATE tasks SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: String, priority: String)

    @Query("UPDATE tasks SET listId = :listId, projectId = :projectId, sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateContainer(id: String, listId: String?, projectId: String?, sortOrder: Int)

    @Query("UPDATE tasks SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeTrashOlderThan(threshold: Long)

    @Delete
    suspend fun delete(task: TaskEntity)

    // Many-to-Many Assignees (Person)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskPersonCrossRefs(crossRefs: List<TaskPersonCrossRef>)

    @Query("DELETE FROM task_person_cross_ref WHERE taskId = :taskId")
    suspend fun deleteTaskPersonCrossRefs(taskId: String)

    @Query("""
        SELECT p.* FROM people p 
        INNER JOIN task_person_cross_ref r ON p.id = r.personId 
        WHERE r.taskId = :taskId
    """)
    fun getPeopleForTask(taskId: String): Flow<List<PersonEntity>>

    @Query("""
        SELECT p.* FROM people p
        INNER JOIN task_person_cross_ref r ON p.id = r.personId
        WHERE r.taskId = :taskId
    """)
    fun getPeopleForTaskDirect(taskId: String): List<PersonEntity>

    // Batch variants of the cross-ref reads above, used by upsertTasks to diff-before-write for
    // a whole bulk write in a handful of queries instead of 3 per task.
    @Query("SELECT * FROM task_person_cross_ref WHERE taskId IN (:taskIds)")
    suspend fun getPersonCrossRefsForTasks(taskIds: List<String>): List<TaskPersonCrossRef>

    @Query("SELECT * FROM task_tag_cross_ref WHERE taskId IN (:taskIds)")
    suspend fun getTagCrossRefsForTasks(taskIds: List<String>): List<TaskTagCrossRef>

    // Many-to-Many Tags
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskTagCrossRefs(crossRefs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId")
    suspend fun deleteTaskTagCrossRefs(taskId: String)

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN task_tag_cross_ref r ON t.id = r.tagId
        WHERE r.taskId = :taskId
    """)
    fun getTagsForTask(taskId: String): Flow<List<TagEntity>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN task_tag_cross_ref r ON t.id = r.tagId
        WHERE r.taskId = :taskId
    """)
    fun getTagsForTaskDirect(taskId: String): List<TagEntity>
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder ASC")
    fun getSubtasksForTask(taskId: String): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder ASC")
    suspend fun getSubtasksForTaskDirect(taskId: String): List<SubtaskEntity>

    // Batch variant used by upsertTasks to diff-before-write for a whole bulk write in one
    // query instead of one per task.
    @Query("SELECT * FROM subtasks WHERE taskId IN (:taskIds) ORDER BY sortOrder ASC")
    suspend fun getSubtasksForTasksDirect(taskIds: List<String>): List<SubtaskEntity>

    @Upsert
    suspend fun upsertAll(subtasks: List<SubtaskEntity>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)
}

@Dao
interface TaskCommentDao {
    @Query("SELECT * FROM task_comments WHERE taskId = :taskId ORDER BY createdAt DESC")
    fun getCommentsForTask(taskId: String): Flow<List<TaskCommentEntity>>

    @Query("SELECT * FROM task_comments")
    fun getAll(): Flow<List<TaskCommentEntity>>

    @Upsert
    suspend fun insert(comment: TaskCommentEntity)

    @Delete
    suspend fun delete(comment: TaskCommentEntity)
}
