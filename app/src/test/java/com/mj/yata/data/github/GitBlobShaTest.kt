package com.mj.yata.data.github

import org.junit.Assert.assertEquals
import org.junit.Test

class GitBlobShaTest {

    @Test
    fun emptyBlob_matchesGitHashObject() {
        assertEquals(
            "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391",
            GitBlobSha.of(ByteArray(0))
        )
    }

    @Test
    fun textBlob_matchesGitHashObject() {
        assertEquals(
            "ce013625030ba8dba906f756967f9e9ca394464a",
            GitBlobSha.of("hello\n".toByteArray(Charsets.UTF_8))
        )
    }
}
