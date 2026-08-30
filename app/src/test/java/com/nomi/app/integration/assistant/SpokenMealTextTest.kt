package com.nomi.app.integration.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenMealTextTest {
    @Test
    fun `german add banana strips the command`() {
        assertEquals("eine Banane", SpokenMealText.mealText("füge eine Banane hinzu"))
    }

    @Test
    fun `hey google prefix is dropped`() {
        assertEquals("eine Banane", SpokenMealText.mealText("Hey Google, füge eine Banane hinzu"))
    }

    @Test
    fun `english add banana strips the command`() {
        assertEquals("a banana", SpokenMealText.mealText("add a banana"))
    }

    @Test
    fun `in nomi suffix is dropped`() {
        assertEquals("200 g rice", SpokenMealText.mealText("log 200 g rice in Nomi"))
    }

    @Test
    fun `plain food is unchanged`() {
        assertEquals("Banane", SpokenMealText.mealText("Banane"))
    }

    @Test
    fun `calorie questions are not meals`() {
        assertTrue(SpokenMealText.isCalorieQuery("wie viele Kalorien bleiben"))
        assertTrue(SpokenMealText.isCalorieQuery("how many kcal left"))
        assertNull(SpokenMealText.mealText("wie viele Kalorien bleiben übrig"))
        assertFalse(SpokenMealText.isCalorieQuery("eine Banane"))
    }
}
