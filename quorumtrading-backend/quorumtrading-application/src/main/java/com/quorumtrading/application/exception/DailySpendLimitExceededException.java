package com.quorumtrading.application.exception;

import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;

/**
 * Exception thrown when a user's daily spend limit has been exceeded.
 */
public class DailySpendLimitExceededException extends RuntimeException {

    private final UserId userId;
    private final BigDecimal dailyLimit;
    private final BigDecimal todaySpend;

    public DailySpendLimitExceededException(UserId userId, BigDecimal dailyLimit, BigDecimal todaySpend) {
        super("Daily spend limit of $" + dailyLimit + " exceeded for user: " + userId
                + " (today's spend: $" + todaySpend + ")");
        this.userId = userId;
        this.dailyLimit = dailyLimit;
        this.todaySpend = todaySpend;
    }

    public UserId getUserId() { return userId; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public BigDecimal getTodaySpend() { return todaySpend; }
}
