package com.tanglycohort.greenflow.util

object PhoneUtils {

    private const val MIN_DIGITS = 10
    private const val MAX_DIGITS = 15
    private const val ISRAEL_PREFIX = "972"

    /**
     * Normalizes phone: remove spaces/dashes, replace leading 0 with 972.
     * @return normalized digits-only string, or null if empty after normalization
     */
    fun normalize(phone: String): String? {
        val digits = phone.replace(" ", "").replace("-", "").replace("\u200E", "").trim()
        if (digits.isEmpty()) return null
        val normalized = if (digits.startsWith("0")) ISRAEL_PREFIX + digits.drop(1) else digits
        return normalized.filter { it.isDigit() }.takeIf { it.isNotEmpty() }
    }

    /**
     * Validates that after normalization the phone has 10-15 digits.
     */
    fun isValid(phone: String): Boolean {
        val normalized = normalize(phone) ?: return false
        return normalized.length in MIN_DIGITS..MAX_DIGITS
    }

    /**
     * Normalizes and validates. Returns normalized string or null if invalid.
     */
    fun normalizeAndValidate(phone: String): String? {
        val n = normalize(phone) ?: return null
        return n.takeIf { it.length in MIN_DIGITS..MAX_DIGITS }
    }
}
