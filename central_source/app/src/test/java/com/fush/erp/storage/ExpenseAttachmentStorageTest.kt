package com.fush.erp.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseAttachmentStorageTest {
    @Test
    fun managed_uri_round_trip_keeps_only_safe_file_name() {
        val fileName = "123e4567-e89b-12d3-a456-426614174000.pdf"
        val uri = ExpenseAttachmentStorage.managedUriFor(fileName)

        assertEquals("fush-attachment://local/$fileName", uri)
        assertEquals(fileName, ExpenseAttachmentStorage.managedFileName(uri))
    }

    @Test
    fun managed_uri_rejects_traversal_and_external_uris() {
        assertNull(ExpenseAttachmentStorage.managedFileName("content://provider/document/1"))
        assertNull(ExpenseAttachmentStorage.managedFileName("fush-attachment://local/../secret.pdf"))
        assertNull(ExpenseAttachmentStorage.managedFileName("fush-attachment://local/a/b.pdf"))
        assertNull(ExpenseAttachmentStorage.managedFileName("fush-attachment://local/a\\b.pdf"))
    }

    @Test
    fun extension_is_small_and_sanitized() {
        assertEquals(".pdf", ExpenseAttachmentStorage.safeExtension("Invoice.PDF"))
        assertEquals(".jpeg", ExpenseAttachmentStorage.safeExtension("photo.jpeg"))
        assertEquals("", ExpenseAttachmentStorage.safeExtension("no-extension"))
        assertEquals("", ExpenseAttachmentStorage.safeExtension("unsafe.long-extension-name"))
        assertEquals("", ExpenseAttachmentStorage.safeExtension("unsafe.pd/f"))
    }
}
