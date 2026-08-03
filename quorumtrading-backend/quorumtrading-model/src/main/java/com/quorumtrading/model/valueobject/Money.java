package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount, Currency currency) {

    public static final Money ZERO = new Money(BigDecimal.ZERO, Currency.USD);
    public static final Money ZERO_USD = new Money(BigDecimal.ZERO, Currency.USD);

    public Money {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("Amount and currency cannot be null");
        }
    }

    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        validateSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(int multiplier) {
        return new Money(amount.multiply(BigDecimal.valueOf(multiplier)), currency);
    }

    public Money multiply(BigDecimal multiplier) {
        return new Money(amount.multiply(multiplier), currency);
    }

    public Money divide(BigDecimal divisor) {
        return new Money(amount.divide(divisor, 8, RoundingMode.HALF_UP), currency);
    }

    public boolean isLessThan(Money other) {
        validateSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public int compareTo(Money other) {
        validateSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void validateSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot operate on different currencies");
        }
    }

    /**
     * Create a zero-value Money instance for the specified currency.
     *
     * @param currency the currency for the zero amount
     * @return a Money instance with zero amount
     */
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Create a Money instance in an arbitrary currency.
     *
     * <p>Currency-aware counterpart to {@link #usd(BigDecimal)}: use this on the
     * settlement-currency paths (EUR Saxo accounts, EUR-priced EU instruments) so a
     * value can be minted in whatever currency the surrounding account / instrument
     * settles in, without hard-coding USD. The USD path keeps using {@link #usd}.</p>
     *
     * @param amount   the monetary amount
     * @param currency the settlement currency
     * @return a Money instance in the given currency
     */
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /**
     * Create a Money instance with USD currency.
     */
    public static Money usd(BigDecimal amount) {
        return new Money(amount, Currency.USD);
    }

    /**
     * Create a Money instance with USD currency from a double value.
     */
    public static Money usd(double amount) {
        return new Money(BigDecimal.valueOf(amount), Currency.USD);
    }

    /**
     * Human-readable representation for notification copy, email templates, etc.
     * Examples: {@code $200.00}, {@code €1,234.50}, {@code 0.05400000 BTC}.
     *
     * <p>Do NOT use {@link #toString()} in user-facing text — Java's record
     * default returns {@code Money[amount=200.00000000, currency=USD]}, which
     * leaks the type representation into notifications.</p>
     */
    public String format() {
        return switch (currency) {
            case USD -> "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
            case EUR -> "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
            case BTC -> amount.setScale(8, RoundingMode.HALF_UP).toPlainString() + " BTC";
        };
    }

    public enum Currency {
        USD, EUR, BTC
    }
}
