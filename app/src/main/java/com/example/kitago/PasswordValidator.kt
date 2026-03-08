package com.example.kitago

object PasswordValidator {

    data class Result(
        val isValid: Boolean,
        val strength: Strength,
        val errors: List<String>
    )

    enum class Strength(val label: String, val color: Int) {
        WEAK("WEAK", 0xFFF44336.toInt()),
        FAIR("FAIR", 0xFFFF9800.toInt()),
        GOOD("GOOD", 0xFF2196F3.toInt()),
        STRONG("STRONG", 0xFF4CAF50.toInt())
    }

    fun validate(password: String): Result {
        val errors = mutableListOf<String>()

        if (password.length < 8) errors.add("MIN 8 CHARACTERS")
        if (!password.any { it.isUpperCase() }) errors.add("1 UPPERCASE LETTER")
        if (!password.any { it.isLowerCase() }) errors.add("1 LOWERCASE LETTER")
        if (!password.any { it.isDigit() }) errors.add("1 NUMBER")
        if (!password.any { !it.isLetterOrDigit() }) errors.add("1 SPECIAL CHARACTER")

        val strength = when {
            errors.size >= 4 -> Strength.WEAK
            errors.size >= 2 -> Strength.FAIR
            errors.size == 1 -> Strength.GOOD
            else -> Strength.STRONG
        }

        return Result(
            isValid = errors.isEmpty(),
            strength = strength,
            errors = errors
        )
    }
}

