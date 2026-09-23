package com.example.imiq

import org.junit.Assert.assertEquals
import org.junit.Test

class ValenceSwipeTest {
    private fun direction(
        offset: Float,
        velocity: Float = 0f,
        next: Boolean = true,
        previous: Boolean = true,
    ) = valenceSwipeDirection(offset, 1000f, velocity, 900f, next, previous)

    @Test fun rightAdvancesAndLeftReturnsLikeNeeds() {
        assertEquals(1, direction(200f))
        assertEquals(-1, direction(-200f))
    }

    @Test fun incompleteOrUnavailableCardsCannotAdvance() {
        assertEquals(0, direction(800f, 2000f, next = false))
        assertEquals(0, direction(-800f, -2000f, previous = false))
        assertEquals(-1, direction(-250f, next = false))
    }

    @Test fun smallDragsReturnUnlessTheyAreDeliberateFlings() {
        assertEquals(0, direction(100f))
        assertEquals(0, direction(-100f))
        assertEquals(0, direction(10f, 3000f))
        assertEquals(1, direction(70f, 1000f))
        assertEquals(-1, direction(-70f, -1000f))
        assertEquals(0, direction(70f, -1000f))
        assertEquals(0, direction(-70f, 1000f))
    }
}
