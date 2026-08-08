package com.nomi.app.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiRuntimeCredentialTest {
    @Test
    fun `pasted surrounding whitespace is removed before authorization`() {
        val credential = AiRuntimeCredential.from("  test-provider-key\r\n")

        assertEquals("test-provider-key", credential.revealForRequest())
        assertFalse(credential.toString().contains("test-provider-key"))
    }
}
