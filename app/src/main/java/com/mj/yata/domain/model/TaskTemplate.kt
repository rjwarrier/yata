package com.mj.yata.domain.model

/**
 * A saved task to start new ones from. [link] is a task transfer link (util/export/TaskTransferLink)
 * — the format share links use — so the task's list, project and tags travel by name and resolve
 * against whatever exists when the template is used, rather than by ids that may be gone.
 *
 * Stored in a DataStore string set as "name<US>link". The ASCII unit separator can't be typed
 * into a name field, and [create] strips it anyway.
 */
data class TaskTemplate(val name: String, val link: String) {
    fun encode(): String = "$name$SEPARATOR$link"

    companion object {
        private const val SEPARATOR = '\u001F'

        fun create(name: String, link: String) = TaskTemplate(name.replace(SEPARATOR, ' ').trim(), link)

        fun decode(raw: String): TaskTemplate? {
            val name = raw.substringBefore(SEPARATOR, "").takeIf { it.isNotBlank() } ?: return null
            val link = raw.substringAfter(SEPARATOR, "").takeIf { it.isNotBlank() } ?: return null
            return TaskTemplate(name, link)
        }
    }
}
