package com.netbanking.setup;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.security.PasswordHasher;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

/** Adds fictional demo customers. Existing records are never overwritten. */
public final class SampleCustomerSetup {
    public static void main(String[] args) throws Exception {
        String[] names = {"aarav.shah", "diya.nair", "arjun.rao", "ananya.sen", "vivaan.mehta",
                "isha.kapoor", "aditya.joshi", "sara.khan", "rohan.das", "meera.iyer",
                "kabir.verma", "riya.patel", "dev.menon", "tara.bose", "neel.reddy"};
        int inserted = 0;
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (var exists = connection.prepareStatement("SELECT 1 FROM users WHERE username IN (?, ?) OR email=?");
                 var insert = connection.prepareStatement("INSERT INTO users (username,email,password_hash,password_salt,password_iterations,role,status,created_at) VALUES (?,?,?,?,?,'CUSTOMER',?,?)", Statement.RETURN_GENERATED_KEYS);
                 var profile = connection.prepareStatement("""
                         INSERT INTO customer_profiles(customer_id,full_name,phone,address_line,city,region,postal_code)
                         VALUES (?,?,?,?,?,?,?)
                         """)) {
                for (int i = 0; i < names.length; i++) {
                    String username = names[i];
                    String email = username + "@example.test";
                    exists.setString(1, username);
                    exists.setString(2, "demo." + username);
                    exists.setString(3, email);
                    try (var result = exists.executeQuery()) { if (result.next()) continue; }
                    var password = PasswordHasher.create(UUID.randomUUID().toString().toCharArray());
                    insert.setString(1, username);
                    insert.setString(2, email);
                    insert.setString(3, password.hashHex()); insert.setString(4, password.saltHex());
                    insert.setInt(5, password.iterations());
                    insert.setString(6, i < 5 ? "PENDING" : (i < 12 ? "ACTIVE" : "SUSPENDED"));
                    insert.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now().minusDays(i * 3L + 1)));
                    inserted += insert.executeUpdate();
                    try (var key = insert.getGeneratedKeys()) {
                        if (!key.next()) throw new IllegalStateException("Sample customer ID missing.");
                        String[] parts = username.split("\\.");
                        String name = Character.toUpperCase(parts[0].charAt(0)) + parts[0].substring(1) + " "
                                + Character.toUpperCase(parts[1].charAt(0)) + parts[1].substring(1);
                        var contact = SampleContact.profile(name, i + 11);
                        profile.setLong(1, key.getLong(1));
                        profile.setString(2, contact.fullName());
                        profile.setString(3, contact.phone());
                        profile.setString(4, contact.addressLine());
                        profile.setString(5, contact.city());
                        profile.setString(6, contact.region());
                        profile.setString(7, contact.postalCode());
                        profile.executeUpdate();
                    }
                }
                connection.commit();
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
        System.out.println("Added " + inserted + " fictional customers. Existing accounts preserved.");
    }
}
