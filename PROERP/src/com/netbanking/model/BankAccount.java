package com.netbanking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankAccount(long id, long customerId, String customer, String number, String type,
                          String status, BigDecimal balance, LocalDateTime openedAt) {
    public record Counts(long total, long active, long frozen) {}
}
