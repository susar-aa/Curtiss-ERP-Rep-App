package com.example.curtiss;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

public class CurrencyUtilsTest {

    @Test
    public void testToBigDecimalDouble() {
        BigDecimal val = CurrencyUtils.toBigDecimal(123.456);
        assertEquals("123.46", val.toString());
    }

    @Test
    public void testToBigDecimalString() {
        BigDecimal val1 = CurrencyUtils.toBigDecimal("1,234.56");
        assertEquals("1234.56", val1.toString());

        BigDecimal val2 = CurrencyUtils.toBigDecimal("");
        assertEquals("0.00", val2.toString());

        BigDecimal val3 = CurrencyUtils.toBigDecimal(null);
        assertEquals("0.00", val3.toString());

        BigDecimal val4 = CurrencyUtils.toBigDecimal("invalid");
        assertEquals("0.00", val4.toString());
    }

    @Test
    public void testAdd() {
        BigDecimal a = CurrencyUtils.toBigDecimal(10.50);
        BigDecimal b = CurrencyUtils.toBigDecimal(20.25);
        BigDecimal result = CurrencyUtils.add(a, b);
        assertEquals("30.75", result.toString());
    }

    @Test
    public void testSubtract() {
        BigDecimal a = CurrencyUtils.toBigDecimal(50.00);
        BigDecimal b = CurrencyUtils.toBigDecimal(15.75);
        BigDecimal result = CurrencyUtils.subtract(a, b);
        assertEquals("34.25", result.toString());
    }

    @Test
    public void testMultiply() {
        BigDecimal a = CurrencyUtils.toBigDecimal(5.50);
        BigDecimal b = CurrencyUtils.toBigDecimal(2.00);
        BigDecimal result = CurrencyUtils.multiply(a, b);
        assertEquals("11.00", result.toString());
    }

    @Test
    public void testCalculatePercentage() {
        BigDecimal value = CurrencyUtils.toBigDecimal(250.00);
        BigDecimal percent = CurrencyUtils.toBigDecimal(12.00);
        BigDecimal result = CurrencyUtils.calculatePercentage(value, percent);
        assertEquals("30.00", result.toString());
    }

    @Test
    public void testFormatLKR() {
        BigDecimal val = CurrencyUtils.toBigDecimal(1234567.89);
        String formatted = CurrencyUtils.formatLKR(val);
        // Replace non-breaking spaces if any
        formatted = formatted.replace('\u00A0', ' ');
        assertEquals("LKR 1,234,567.89", formatted);
    }
}
