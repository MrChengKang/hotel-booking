package com.hotel.hotel_booking.hotel;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class ModelRules {
    private ModelRules() {}
    static String text(String value, String field, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw new IllegalArgumentException(field + " is required and must be at most " + max + " characters.");
        return value.strip();
    }
    static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new IllegalArgumentException("Price must be between 0 and 9999999999.99.");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException failure) { throw new IllegalArgumentException("Price must have at most two decimal places."); }
    }
}
