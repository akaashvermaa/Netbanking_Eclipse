package com.netbanking.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record LoanTerms(int months, BigDecimal annualRate) {
    public record Plan(String purpose, BigDecimal annualRate) {}

    public static final List<Integer> DURATIONS = List.of(6, 12, 24, 36);
    public static final List<Plan> PLANS = List.of(
            new Plan("Personal expenses", new BigDecimal("12.00")),
            new Plan("Education", new BigDecimal("8.50")),
            new Plan("Home improvement", new BigDecimal("10.00")),
            new Plan("Vehicle", new BigDecimal("9.50")));

    public static LoanTerms offer(String purpose, String duration) {
        return PLANS.stream().filter(plan -> plan.purpose().equals(purpose))
                .flatMap(plan -> DURATIONS.stream().filter(months -> months.toString().equals(duration))
                        .map(months -> new LoanTerms(months, plan.annualRate())))
                .findFirst().orElse(null);
    }

    public static LoanTerms stored(Integer months, BigDecimal annualRate) {
        return months == null || annualRate == null ? null : new LoanTerms(months, annualRate);
    }

    public BigDecimal interest(BigDecimal principal) {
        return principal.multiply(annualRate).multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal total(BigDecimal principal) {
        return principal.add(interest(principal));
    }

    public BigDecimal monthly(BigDecimal principal) {
        return total(principal).divide(BigDecimal.valueOf(months), 2, RoundingMode.DOWN);
    }

    public BigDecimal finalPayment(BigDecimal principal) {
        return total(principal).subtract(monthly(principal).multiply(BigDecimal.valueOf(months - 1L)));
    }
}
