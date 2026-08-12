package com.nomi.app.ui.capture

import com.nomi.app.ai.model.MenuDish
import com.nomi.app.ai.model.MenuScanResult
import com.nomi.app.ai.prompt.AiPrompts
import com.nomi.app.ai.validation.AiResponseValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuScanTest {
    private val dishes = listOf(
        MenuDish("12", "Cheeseburger", "Beef, cheese and pickles", "Burgers", "5,90 €"),
        MenuDish("24", "Caesar Salad", "Chicken, lettuce and parmesan", "Salads", "9,50 €"),
        MenuDish("D3", "Cola Zero", "500 ml", "Drinks", "3,20 €"),
    )

    @Test
    fun `search matches number name category and description with all terms`() {
        assertEquals(listOf("Cheeseburger"), filteredMenuDishes(dishes, "12 beef").map(MenuDish::name))
        assertEquals(listOf("Caesar Salad"), filteredMenuDishes(dishes, "salads parmesan").map(MenuDish::name))
        assertEquals(listOf("Cola Zero"), filteredMenuDishes(dishes, "D3 500").map(MenuDish::name))
        assertTrue(filteredMenuDishes(dishes, "pizza").isEmpty())
    }

    @Test
    fun `additional page keeps unique dishes and richer duplicate description`() {
        val merged = mergeMenuDishes(
            dishes.take(1),
            listOf(
                dishes.first().copy(description = "Beef patty, cheddar cheese, pickles and burger sauce"),
                dishes[1],
            ),
        )

        assertEquals(2, merged.size)
        assertTrue(merged.first().description!!.contains("burger sauce"))
    }

    @Test
    fun `dish identity remains stable while selecting richer merged text`() {
        assertEquals(
            menuDishKey(dishes.first()),
            menuDishKey(dishes.first().copy(description = "Richer description")),
        )
        assertTrue(menuDishKey(dishes.first()) != menuDishKey(dishes[1]))
    }

    @Test
    fun `menu response and prompt preserve printed metadata`() {
        val result = AiResponseValidator.validate(
            MenuScanResult(restaurantName = "Nomi Grill", items = dishes),
        )
        val prompt = AiPrompts.readRestaurantMenu()

        assertEquals("Nomi Grill", result.restaurantName)
        assertTrue(prompt.contains("complete list"))
        assertTrue(prompt.contains("number"))
        assertTrue(prompt.contains("description"))
        assertTrue(prompt.contains("price"))
        assertTrue(prompt.contains("Never invent"))
    }
}
