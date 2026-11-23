package com.netbanking.model;

public record CustomerProfile(String fullName, String phone, String addressLine, String city, String region,
                              String postalCode) {
    public CustomerProfile(String fullName, String phone) {
        this(fullName, phone, null, null, null, null);
    }

    public static boolean validPhone(String value) {
        return value != null && value.length() <= 25 && value.matches("[+]?[0-9() .-]{7,25}")
                && value.replaceAll("[^0-9]", "").matches("[0-9]{7,15}");
    }
}
