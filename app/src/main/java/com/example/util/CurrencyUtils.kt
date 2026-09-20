package com.example.util

import java.util.Locale

object CurrencyUtils {
    /**
     * Formats an amount into Indian Rupees (INR) with the standard ₹ currency symbol.
     */
    fun formatInr(amount: Double, forceDecimals: Boolean = false): String {
        return if (forceDecimals || amount % 1.0 != 0.0) {
            "₹" + String.format(Locale.US, "%,.2f", amount)
        } else {
            "₹" + String.format(Locale.US, "%,.0f", amount)
        }
    }
}
