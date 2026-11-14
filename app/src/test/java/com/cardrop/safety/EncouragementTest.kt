package com.cardrop.safety

import com.cardrop.ai.EncouragementContext
import com.cardrop.ai.validatedEncouragement
import org.junit.Assert.*
import org.junit.Test

class EncouragementTest {
    private val partial = EncouragementContext(100, 0, 0, 0, false)
    @Test fun unsafeCompetitiveNumericAndInventedClaimsFallBack() {
        listOf(null, "", "삼촌 차를 따라잡아요!", "급정거 없이 달리고 있어요", "지금 1위예요!", "점수를 회복해봐요!", "완벽한 운전이에요!", "응원".repeat(30)).forEach {
            assertEquals(partial.fallback(), validatedEncouragement(it, partial))
        }
    }
    @Test fun validShortEncouragementIsRetained() {
        val safe = "서두르지 않는 여유로운 운전을 응원해요!"
        assertEquals(safe, validatedEncouragement(safe, partial))
    }
    @Test fun noEventClaimCannotContradictMeasuredEvents() {
        val context = partial.copy(sufficientData = true, brakingCount = 2)
        assertEquals(context.fallback(), validatedEncouragement("급정거 없이 잘 달리고 있어요", context))
    }
}
