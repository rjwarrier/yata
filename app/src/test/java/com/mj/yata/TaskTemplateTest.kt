package com.mj.yata

import com.mj.yata.domain.model.TaskTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskTemplateTest {

    private val link = "https://ranjithj.in/yata/i#v3.abc"

    @Test
    fun roundTripsThroughStorage() {
        val template = TaskTemplate.create("Weekly review", link)
        assertEquals(template, TaskTemplate.decode(template.encode()))
    }

    @Test
    fun createTrimsAndStripsTheSeparator() {
        val template = TaskTemplate.create("  Pack\u001Flist  ", link)
        assertEquals("Pack list", template.name)
        assertEquals(template, TaskTemplate.decode(template.encode()))
    }

    @Test
    fun rejectsMalformedRows() {
        assertNull(TaskTemplate.decode("no separator"))
        assertNull(TaskTemplate.decode("\u001F$link"))
        assertNull(TaskTemplate.decode("Name\u001F"))
    }
}
