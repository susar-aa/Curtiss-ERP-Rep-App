package com.example.curtiss;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public class CurrencyUtils {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public static BigDecimal toBigDecimal(double value) {
        // Use valueOf to prevent float-point conversion inaccuracies of new BigDecimal(double)
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING_MODE);
    }

    public static BigDecimal toBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
        try {
            // Remove commas in case value is formatted
            String cleanVal = value.trim().replace(",", "");
            return new BigDecimal(cleanVal).setScale(SCALE, ROUNDING_MODE);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.add(b).setScale(SCALE, ROUNDING_MODE);
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.subtract(b).setScale(SCALE, ROUNDING_MODE);
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.multiply(b).setScale(SCALE, ROUNDING_MODE);
    }

    public static BigDecimal calculatePercentage(BigDecimal value, BigDecimal percent) {
        if (value == null || percent == null) return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        return value.multiply(percent).divide(BigDecimal.valueOf(100), SCALE, ROUNDING_MODE);
    }

    public static BigDecimal round(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        return value.setScale(SCALE, ROUNDING_MODE);
    }

    public static String formatLKR(BigDecimal val) {
        if (val == null) val = BigDecimal.ZERO;
        return String.format(Locale.getDefault(), "LKR %,.2f", val.setScale(SCALE, ROUNDING_MODE));
    }
}
