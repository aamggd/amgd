package com.fush.erp.attachments

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AttachmentStorageAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun managedAttachmentCanBeOpenedAndSharedWithPortableReference() {
        val relative = "test/runtime-attachment.txt"
        val file = File(AttachmentStorage.root(context), relative).apply {
            parentFile!!.mkdirs()
            writeText("FUSH attachment test")
        }
        val reference = "fush-attachment://$relative"
        assertTrue(AttachmentStorage.isManaged(reference))
        assertEquals(file.canonicalFile, AttachmentStorage.resolveFile(context, reference).canonicalFile)

        val open = AttachmentStorage.openIntent(context, reference, "text/plain")
        assertEquals(Intent.ACTION_VIEW, open.action)
        assertTrue(open.data!!.scheme == "content")
        assertTrue(open.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(open.clipData)

        val share = AttachmentStorage.shareIntent(context, reference, "runtime-attachment.txt", "text/plain")
        assertEquals(Intent.ACTION_SEND, share.action)
        assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(share.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM))
        assertNotNull(share.clipData)
    }
}
