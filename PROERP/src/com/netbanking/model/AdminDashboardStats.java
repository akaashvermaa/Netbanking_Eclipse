package com.netbanking.model;

public record AdminDashboardStats(long totalCustomers, long pendingCustomers, long activeCustomers,
                                  long suspendedCustomers) {
}
