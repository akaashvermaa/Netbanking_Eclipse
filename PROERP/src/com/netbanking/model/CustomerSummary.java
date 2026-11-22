package com.netbanking.model;

import java.time.LocalDateTime;

public record CustomerSummary(long id, String username, String email, String status, LocalDateTime createdAt) {
}
