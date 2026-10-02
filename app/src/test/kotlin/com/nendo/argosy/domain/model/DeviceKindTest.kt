package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceKindTest {

    @Test
    fun `registered handhelds classify by form factor`() {
        assertEquals(DeviceKind.DUAL_SCREEN, DeviceKind.classify("AYN AYN Thor", "android", "argosy"))
        assertEquals(DeviceKind.HANDHELD_HORIZONTAL, DeviceKind.classify("ayn Odin3", "android", "argosy"))
        assertEquals(DeviceKind.HANDHELD_HORIZONTAL, DeviceKind.classify("Moorechip Retroid Pocket Nova", "android", "argosy"))
        assertEquals(DeviceKind.HANDHELD_HORIZONTAL, DeviceKind.classify("AYANEO Pocket FIT", "android", "argosy"))
        assertEquals(DeviceKind.HANDHELD_VERTICAL, DeviceKind.classify("Moorechip Retroid Pocket Classic", "android", "argosy"))
    }

    @Test
    fun `an unrecognised android device is a phone`() {
        assertEquals(DeviceKind.PHONE, DeviceKind.classify("samsung SC-51F", "android", "argosy"))
    }

    @Test
    fun `platform decides web, tv and desktop`() {
        assertEquals(DeviceKind.WEB, DeviceKind.classify(null, null, null, isWeb = true))
        assertEquals(DeviceKind.TV, DeviceKind.classify("NVIDIA SHIELD Android TV", "android", "argosy"))
        assertEquals(DeviceKind.DESKTOP, DeviceKind.classify("workstation", "linux", "grout"))
        assertEquals(DeviceKind.UNKNOWN, DeviceKind.classify(null, null, null))
    }

    @Test
    fun `a manufacturer repeated by the model is dropped once`() {
        assertEquals("AYN Thor", DeviceKind.withoutRepeatedManufacturer("AYN AYN Thor"))
        assertEquals("ayn Odin3", DeviceKind.withoutRepeatedManufacturer("ayn Odin3"))
        assertEquals("Pixel", DeviceKind.withoutRepeatedManufacturer("Pixel"))
    }
}
