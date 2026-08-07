package com.nomi.app.domain

import java.time.LocalDate

object AgeCalculator {
    fun calculate(dateOfBirth: LocalDate, today: LocalDate): Int {
        require(!dateOfBirth.isAfter(today)) { "Date of birth cannot be in the future." }

        var age = today.year - dateOfBirth.year
        val birthdayThisYear = dateOfBirth.withYear(today.year)
        if (today.isBefore(birthdayThisYear)) age -= 1
        return age
    }

    fun ageOn(dateOfBirth: LocalDate, date: LocalDate): Int = calculate(dateOfBirth, date)
}
