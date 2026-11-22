package com.netbanking.setup;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.security.PasswordHasher;
import java.sql.Timestamp;
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
            try (var exists = connection.prepareStatement("SELECT 1 FROM users WHERE username=?");
                 var insert = connection.prepareStatement("INSERT INTO users (username,email,password_hash,password_salt,password_iterations,role,status,created_at) VALUES (?,?,?,?,?,'CUSTOMER',?,?)")) {
                for (int i = 0; i < names.length; i++) {
                    String username = "demo." + names[i];
                    exists.setString(1, username);
                    try (var result = exists.executeQuery()) { if (result.next()) continue; }
                    var password = PasswordHasher.create(UUID.randomUUID().toString().toCharArray());
                    insert.setString(1, username);
                    insert.setString(2, names[i] + "@example.test");
                    insert.setString(3, password.hashHex()); insert.setString(4, password.saltHex());
                    insert.setInt(5, password.iterations());
                    insert.setString(6, i < 5 ? "PENDING" : (i < 12 ? "ACTIVE" : "SUSPENDED"));
                    insert.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now().minusDays(i * 3L + 1)));
                    inserted += insert.executeUpdate();
                }
                connection.commit();
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
        System.out.println("Added " + inserted + " fictional customers. Existing accounts preserved.");
    }
}
