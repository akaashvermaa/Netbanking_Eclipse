package com.netbanking.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CustomerDetails(
        long id,
        String username,
        String email,
        String status,
        LocalDateTime createdAt,
        String fullName,
        String phone,
        List<BankAccount> accounts,
        BigDecimal totalBalance,
        BigDecimal totalDebt,
        long activeLoans,
        boolean hasFrozenAccounts,
        KycStatus kycStatus
) {}
