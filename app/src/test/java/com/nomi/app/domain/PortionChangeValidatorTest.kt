package com.nomi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortionChangeValidatorTest {
    private val twoSlices = Nutrition(
        caloriesKcal = 176.0,
        proteinGrams = 7.0,
        carbsGrams = 32.0,
        fatGrams = 2.0,
    )

    @Test
    fun `three slices from two validates multiplier and deterministic nutrition`() {
        val result = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 2.0,
            newQuantity = 3.0,
            originalUnit = "slices",
            newUnit = "slice",
            reportedMultiplier = 1.5,
            proposedNutrition = Nutrition(264.0, 10.5, 48.0, 3.0),
        )

        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
        assertEquals(1.5, result.multiplier!!, 0.0)
        assertEquals(264.0, result.expectedNutrition!!.caloriesKcal, 0.0)
    }

    @Test
    fun `doubling quantity while calories decrease is rejected`() {
        val result = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 2.0,
            newQuantity = 4.0,
            originalUnit = "slice",
            proposedNutrition = Nutrition(100.0, 5.0, 20.0, 1.0),
        )

        assertFalse(result.isValid)
        assertTrue(PortionValidationIssue.INCONSISTENT_NUTRITION in result.issues)
        assertEquals(352.0, result.expectedNutrition!!.caloriesKcal, 0.0)
    }

    @Test
    fun `reported multiplier inconsistent with quantity is rejected`() {
        val result = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 200.0,
            newQuantity = 300.0,
            originalUnit = "g",
            reportedMultiplier = 0.5,
        )

        assertFalse(result.isValid)
        assertEquals(1.5, result.multiplier!!, 0.0)
        assertTrue(PortionValidationIssue.INCONSISTENT_MULTIPLIER in result.issues)
    }

    @Test
    fun `compatible mass unit conversion produces mathematical multiplier`() {
        val result = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 200.0,
            newQuantity = 0.3,
            originalUnit = "g",
            newUnit = "kg",
            reportedMultiplier = 1.5,
        )

        assertTrue(result.isValid)
        assertEquals(1.5, result.multiplier!!, 1e-12)
    }

    @Test
    fun `milligrams and grams are compatible mass units`() {
        val multiplier = PortionChangeValidator.calculateMultiplier(
            originalQuantity = 500.0,
            originalUnit = "mg",
            newQuantity = 0.5,
            newUnit = "g",
        )

        assertEquals(1.0, multiplier!!, 1e-12)
    }

    @Test
    fun `German spoon aliases use exact metric milliliters`() {
        val tablespoon = PortionChangeValidator.calculateMultiplier(15.0, "ml", 1.0, "EL")
        val teaspoons = PortionChangeValidator.calculateMultiplier(
            10.0,
            "ml",
            2.0,
            "Teel\u00f6ffel",
        )

        assertEquals(1.0, tablespoon!!, 1e-12)
        assertEquals(1.0, teaspoons!!, 1e-12)
    }

    @Test
    fun `spoon volume never assumes a mass density`() {
        assertNull(PortionChangeValidator.calculateMultiplier(1.0, "EL", 15.0, "g"))
    }

    @Test
    fun `mass to volume conversion is flagged as impossible`() {
        val result = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 200.0,
            newQuantity = 300.0,
            originalUnit = "g",
            newUnit = "ml",
        )

        assertFalse(result.isValid)
        assertNull(result.multiplier)
        assertNull(result.expectedNutrition)
        assertTrue(PortionValidationIssue.INCOMPATIBLE_UNITS in result.issues)
    }

    @Test
    fun `negative or zero quantities are rejected before scaling`() {
        val negative = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 2.0,
            newQuantity = -1.0,
            originalUnit = "slice",
        )
        val zeroOriginal = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 0.0,
            newQuantity = 1.0,
            originalUnit = "slice",
        )

        assertTrue(PortionValidationIssue.INVALID_NEW_QUANTITY in negative.issues)
        assertTrue(PortionValidationIssue.INVALID_ORIGINAL_QUANTITY in zeroOriginal.issues)
    }

    @Test
    fun `invalid and insane reported multipliers are flagged`() {
        val invalidReported = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 2.0,
            newQuantity = 3.0,
            originalUnit = "slice",
            reportedMultiplier = -1.5,
        )
        val extreme = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 1.0,
            newQuantity = 100.0,
            originalUnit = "slice",
            reportedMultiplier = 100.0,
        )

        assertTrue(PortionValidationIssue.INVALID_REPORTED_MULTIPLIER in invalidReported.issues)
        assertTrue(PortionValidationIssue.EXTREME_MULTIPLIER in extreme.issues)
        assertFalse(extreme.isValid)
    }

    @Test
    fun `unknown matching units can scale but changing unknown units requires confirmation`() {
        val bowls = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 1.0,
            newQuantity = 2.0 / 3.0,
            originalUnit = "bowl",
        )
        val ambiguous = PortionChangeValidator.validate(
            currentNutrition = twoSlices,
            originalQuantity = 1.0,
            newQuantity = 1.0,
            originalUnit = "bowl",
            newUnit = "plate",
        )

        assertTrue(bowls.isValid)
        assertEquals(2.0 / 3.0, bowls.multiplier!!, 1e-12)
        assertFalse(ambiguous.isValid)
        assertTrue(PortionValidationIssue.INCOMPATIBLE_UNITS in ambiguous.issues)
    }
}
