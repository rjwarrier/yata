package com.mj.yata.tasker.createtask

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot

/**
 * Every field is a plain String so Tasker can substitute %variables into any of them —
 * variable resolution happens on Tasker's side before this object is deserialized, no
 * special handling needed here. See CreateTaskRunner for how each field is interpreted.
 */
@TaskerInputRoot
class CreateTaskInput @JvmOverloads constructor(
    @field:TaskerInputField("title", labelResIdName = "tasker_field_title") var title: String? = null,
    @field:TaskerInputField("project", labelResIdName = "tasker_field_project") var project: String? = null,
    @field:TaskerInputField("list", labelResIdName = "tasker_field_list") var list: String? = null,
    @field:TaskerInputField("tags", labelResIdName = "tasker_field_tags") var tags: String? = null,
    @field:TaskerInputField("assignees", labelResIdName = "tasker_field_assignees") var assignees: String? = null,
    @field:TaskerInputField("due", labelResIdName = "tasker_field_due") var due: String? = null,
    @field:TaskerInputField("time", labelResIdName = "tasker_field_time") var time: String? = null,
    @field:TaskerInputField("reminder", labelResIdName = "tasker_field_reminder") var reminder: String? = null,
    @field:TaskerInputField("priority", labelResIdName = "tasker_field_priority") var priority: String? = null,
    @field:TaskerInputField("section", labelResIdName = "tasker_field_section") var section: String? = null,
    @field:TaskerInputField("repeat", labelResIdName = "tasker_field_repeat") var repeat: String? = null,
    @field:TaskerInputField("notes", labelResIdName = "tasker_field_notes") var notes: String? = null
)
