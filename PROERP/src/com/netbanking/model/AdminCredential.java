package com.netbanking.model;

public record AdminCredential(long id, String username, String passwordHash, String passwordSalt, int iterations) {
}
