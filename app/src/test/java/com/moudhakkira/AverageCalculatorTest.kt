package com.moudhakkira

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AverageCalculatorTest {
    @Test
    fun weightsExamTwiceAndRoundsToQuarter() {
        val average = AverageCalculator.calculateSubjectAverage(
            listOf(
                grade(value = 15.0, type = "فرض"),
                grade(value = 18.0, type = "اختبار")
            )
        )

        assertEquals(17.0, average!!, 0.0001)
    }

    @Test
    fun normalizesMarksToTwentyAndRoundsToQuarter() {
        val average = AverageCalculator.calculateSubjectAverage(
            listOf(grade(value = 7.13, maxScore = 10.0, type = "فرض"))
        )

        assertEquals(14.25, average!!, 0.0001)
    }

    @Test
    fun ignoresDeletedAndOutOfRangeMarks() {
        val average = AverageCalculator.calculateSubjectAverage(
            listOf(
                grade(value = 19.0, type = "فرض", isDeleted = true),
                grade(value = 21.0, type = "اختبار")
            )
        )

        assertNull(average)
    }

    @Test
    fun acceptsZeroAsAValidMark() {
        val average = AverageCalculator.calculateSubjectAverage(
            listOf(grade(value = 0.0, type = "اختبار"))
        )

        assertEquals(0.0, average!!, 0.0001)
    }

    private fun grade(
        value: Double,
        maxScore: Double = 20.0,
        type: String,
        isDeleted: Boolean = false
    ) = Grade(
        studentId = "student-1",
        subjectId = "subject-1",
        groupId = "group-1",
        value = value,
        maxScore = maxScore,
        gradeType = type,
        isDeleted = isDeleted
    )
}
