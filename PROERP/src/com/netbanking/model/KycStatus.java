package com.netbanking.model;

public enum KycStatus {
    NOT_STARTED("Not started"), PENDING("Pending"), VERIFIED("Verified");

    private final String label;
    KycStatus(String label) { this.label = label; }
    public String label() { return label; }
}
