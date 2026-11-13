package com.cardrop.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehicleValuesTest {
    @Test fun rangeIsConvertedFromMetresAndTimeStaysInMinutes() {
        assertEquals(85f, VehicleValues.rangeKm(85_000f)!!, 0f)
        assertEquals(120, VehicleValues.drivingMinutes(120))
        assertEquals(0f, VehicleValues.rangeKm(0f)!!, 0f)
    }
    @Test fun invalidAndUnavailableValuesStayUnknown() {
        listOf(null, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { assertNull(VehicleValues.rangeKm(it)) }
        listOf(null, -1, 101).forEach { assertNull(VehicleValues.fuel(it)) }
        assertNull(VehicleValues.drivingMinutes(-1))
        assertNull(VehicleValues.drivingMinutes(null))
    }
}
