package com.mj.yata

import com.mj.yata.data.local.backup.publishLocalBackup
import com.mj.yata.data.local.backup.readLocalBackup
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class LocalBackupPublicationTest {
    @get:Rule val folder = TemporaryFolder()
    private val key = SecretKeySpec(ByteArray(16) { it.toByte() }, "AES")
    private fun cipher(mode: Int, file: File): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, key, GCMParameterSpec(128, ByteArray(12)))
        updateAAD(file.name.toByteArray())
    }
    private fun read(file: File) = cipher(Cipher.DECRYPT_MODE, file).doFinal(file.readBytes())

    @Test fun `published ciphertext retains authenticated name and is read by restore reader`() {
        val file = File(folder.root, "backup.json.enc")
        val payload = "{backup: valid}".toByteArray()
        publishLocalBackup(file, { it.writeBytes(cipher(Cipher.ENCRYPT_MODE, it).doFinal(payload)) }, ::read, payload)
        assertArrayEquals(payload, readLocalBackup(file, ::read))
        assertEquals(listOf(file.name), folder.root.list()!!.toList())
    }

    @Test fun `old renamed backup recovers without altering original ciphertext`() {
        val temp = File(folder.root, "backup.json.enc.tmp")
        val payload = "legacy backup".toByteArray()
        temp.writeBytes(cipher(Cipher.ENCRYPT_MODE, temp).doFinal(payload))
        val file = File(folder.root, "backup.json.enc")
        assertTrue(temp.renameTo(file))
        val original = file.readBytes()
        assertArrayEquals(payload, readLocalBackup(file, ::read))
        assertArrayEquals(original, file.readBytes())
        assertEquals(listOf(file.name), folder.root.list()!!.toList())
    }

    @Test fun `failed write preserves previous published backup and cleans staging`() {
        val file = File(folder.root, "backup.json.enc").apply { writeText("old") }
        try {
            publishLocalBackup(file, { it.writeText("partial"); error("write failed") }, ::read, byteArrayOf())
            fail("Expected write failure")
        } catch (_: IllegalStateException) { }
        assertEquals("old", file.readText())
        assertEquals(listOf(file.name), folder.root.list()!!.toList())
    }

    @Test fun `corrupted backup fails both names and preserves file`() {
        val file = File(folder.root, "backup.json.enc").apply { writeText("corrupt") }
        try {
            readLocalBackup(file, ::read)
            fail("Expected authentication failure")
        } catch (e: Exception) { assertEquals(1, e.suppressed.size) }
        assertEquals("corrupt", file.readText())
        assertEquals(listOf(file.name), folder.root.list()!!.toList())
    }
}
