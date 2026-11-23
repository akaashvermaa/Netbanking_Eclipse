package com.netbanking.model;

import java.util.List;

public enum AccountChoice {
    NONE("No account", List.of()),
    SAVINGS("Savings account", List.of("SAVINGS")),
    CURRENT("Current account", List.of("CURRENT")),
    BOTH("Savings and current accounts", List.of("SAVINGS", "CURRENT"));

    private final String label;
    private final List<String> types;

    AccountChoice(String label, List<String> types) {
        this.label = label;
        this.types = types;
    }

    public String label() { return label; }
    public List<String> types() { return types; }

    public static AccountChoice from(String value) {
        for (AccountChoice choice : values()) {
            if (choice.name().equals(value)) return choice;
        }
        return null;
    }
}
