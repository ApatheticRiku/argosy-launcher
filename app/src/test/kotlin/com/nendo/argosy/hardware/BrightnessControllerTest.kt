package com.nendo.argosy.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrightnessControllerTest {

    @Test
    fun `a display service reply decodes to its brightness`() {
        assertEquals(0.48f, BrightnessController.parcelFloat("Result: Parcel(00000000 3ef5c28f   '.......>')")!!, 0.001f)
        assertEquals(0.8f, BrightnessController.parcelFloat("Result: Parcel(00000000 3f4ccccd   '......L?')")!!, 0.001f)
    }

    @Test
    fun `a reply carrying an exception code is not read as a brightness`() {
        assertNull(BrightnessController.parcelFloat("Result: Parcel(ffffffec 00000000 '....')"))
    }

    @Test
    fun `a missing or empty reply has no brightness`() {
        assertNull(BrightnessController.parcelFloat(null))
        assertNull(BrightnessController.parcelFloat("Result: Parcel(00000000    '....')"))
    }
}
