package com.netbanking.model;

import java.time.LocalDateTime;

public record AdminAuditEntry(String adminUsername, String action, String targetUsername, String details,
                              LocalDateTime createdAt, Long customerId) {
}
