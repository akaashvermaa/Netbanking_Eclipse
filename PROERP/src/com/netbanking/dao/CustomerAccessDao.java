package com.netbanking.dao;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.model.CustomerProfile;
import com.netbanking.model.AccountChoice;
import com.netbanking.security.PasswordHasher;
import com.netbanking.security.PasswordHasher.PasswordHash;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

public class CustomerAccessDao {
    public record Identity(long id, String username, String name, String email, String phone, String kyc,
                           long credentialVersion, boolean passwordChangeRequired, String addressLine, String city,
                           String region, String postalCode, LocalDateTime joinedAt, LocalDateTime kycRecordedAt) {}
    public record Pending(long id, long credentialVersion) implements java.io.Serializable {}
    public record SignIn(Identity customer, Pending pending) {}

    private static final SignIn DENIED = new SignIn(null, null);
    private static final PasswordHash UNMATCHED = PasswordHasher.create(
            java.util.UUID.randomUUID().toString().toCharArray());

    public SignIn authenticate(String username, char[] password) throws SQLException {
        if (username == null || username.isBlank() || username.length() > 50
                || password.length == 0 || password.length > 128) return DENIED;
        try (var db = DatabaseConnection.getConnection();
             var query = db.prepareStatement("""
                     SELECT id, password_hash, password_salt, password_iterations, status, credential_version FROM users
                     WHERE username=? AND role='CUSTOMER' AND status IN ('PENDING', 'ACTIVE')
                     """)) {
            query.setString(1, username.strip());
            try (var row = query.executeQuery()) {
                boolean found = row.next();
                PasswordHash hash = found
                        ? new PasswordHash(row.getString(2), row.getString(3), row.getInt(4)) : UNMATCHED;
                if (!PasswordHasher.verify(password, hash.hashHex(), hash.saltHex(), hash.iterations()) || !found) return DENIED;
                if ("PENDING".equals(row.getString(5))) return new SignIn(null, new Pending(row.getLong(1), row.getLong(6)));
                return new SignIn(activeCustomer(db, row.getLong(1), hash.hashHex()), null);
            }
        }
    }

    public SignIn checkApproval(Pending pending) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var check = db.prepareStatement("SELECT status, credential_version FROM users WHERE role='CUSTOMER' AND id=?")) {
            check.setLong(1, pending.id());
            try (var row = check.executeQuery()) {
                if (!row.next()) return DENIED;
                long version = row.getLong(2);
                if ("PENDING".equals(row.getString(1)) && version == pending.credentialVersion()) {
                    return new SignIn(null, pending);
                }
                // Admin approval increments the access version once. A later reset or suspension requires a new login.
                if (!"ACTIVE".equals(row.getString(1)) || version != pending.credentialVersion() + 1) return DENIED;
                Identity customer = activeCustomer(db, pending.id(), null);
                return customer != null && customer.credentialVersion() == version ? new SignIn(customer, null) : DENIED;
            }
        }
    }

    public long register(String username, String email, CustomerProfile profile, char[] password, AccountChoice accounts) throws SQLException {
        PasswordHash hash = PasswordHasher.create(password);
        try (var db = DatabaseConnection.getConnection()) {
            db.setAutoCommit(false);
            try {
                long id = insertCustomer(db, username, email, profile, "PENDING", hash, accounts, false);
                if (id == 0) {
                    db.rollback();
                    return 0;
                }
                db.commit();
                return id;
            } catch (SQLException exception) {
                db.rollback();
                throw exception;
            }
        }
    }

    static long insertCustomer(Connection db, String username, String email, CustomerProfile profile,
                               String status, PasswordHash hash, AccountChoice accounts, boolean temporaryPassword) throws SQLException {
        long id;
        try (var user = db.prepareStatement("""
                INSERT INTO users(username,email,password_hash,password_salt,password_iterations,role,status,
                    requested_accounts,password_change_required)
                VALUES (?,?,?,?,?,'CUSTOMER',?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            user.setString(1, username);
            user.setString(2, email);
            user.setString(3, hash.hashHex());
            user.setString(4, hash.saltHex());
            user.setInt(5, hash.iterations());
            user.setString(6, status);
            user.setString(7, accounts == null ? null : accounts.name());
            user.setBoolean(8, temporaryPassword);
            try {
                user.executeUpdate();
            } catch (SQLException exception) {
                if (exception.getErrorCode() == 1062) return 0;
                throw exception;
            }
            try (var keys = user.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing customer ID");
                id = keys.getLong(1);
            }
        }
        try (var details = db.prepareStatement("""
                INSERT INTO customer_profiles(customer_id,full_name,phone,address_line,city,region,postal_code)
                VALUES (?,?,?,?,?,?,?)
                """)) {
            details.setLong(1, id);
            details.setString(2, profile.fullName());
            details.setString(3, profile.phone());
            details.setString(4, profile.addressLine());
            details.setString(5, profile.city());
            details.setString(6, profile.region());
            details.setString(7, profile.postalCode());
            details.executeUpdate();
        }
        return id;
    }

    public Identity activeCustomer(long id) throws SQLException {
        try (var db = DatabaseConnection.getConnection()) {
            return activeCustomer(db, id, null);
        }
    }

    private Identity activeCustomer(Connection db, long id, String verifiedHash) throws SQLException {
        try (var query = db.prepareStatement("""
                SELECT u.id, u.username, COALESCE(p.full_name, u.username), u.email, p.phone,
                       COALESCE(k.status, 'NOT_STARTED'), u.credential_version, u.password_change_required, u.password_hash,
                       p.address_line, p.city, p.region, p.postal_code, u.created_at, k.recorded_at
                FROM users u LEFT JOIN customer_profiles p ON p.customer_id=u.id
                LEFT JOIN customer_kyc k ON k.customer_id=u.id
                WHERE u.id=? AND u.role='CUSTOMER' AND u.status='ACTIVE'
                """)) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                if (!row.next() || (verifiedHash != null && !verifiedHash.equals(row.getString(9)))) return null;
                return new Identity(row.getLong(1), row.getString(2), row.getString(3), row.getString(4),
                        row.getString(5), row.getString(6), row.getLong(7), row.getBoolean(8),
                        row.getString(10), row.getString(11), row.getString(12), row.getString(13),
                        row.getObject(14, LocalDateTime.class), row.getObject(15, LocalDateTime.class));
            }
        }
    }

    public boolean addMissingPhone(Identity customer, String phone) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var update = db.prepareStatement("""
                     INSERT INTO customer_profiles(customer_id,full_name,phone)
                     SELECT id, username, ? FROM users
                     WHERE id=? AND role='CUSTOMER' AND status='ACTIVE' AND credential_version=?
                       AND NOT password_change_required
                     ON DUPLICATE KEY UPDATE phone=IF(NULLIF(TRIM(customer_profiles.phone), '') IS NULL,
                                                        VALUES(phone), customer_profiles.phone)
                     """)) {
            update.setString(1, phone);
            update.setLong(2, customer.id());
            update.setLong(3, customer.credentialVersion());
            update.executeUpdate();
            Identity fresh = activeCustomer(db, customer.id(), null);
            return fresh != null && CustomerProfile.validPhone(fresh.phone());
        }
    }

    public boolean resetPassword(long customerId, long adminId, char[] password) throws SQLException {
        PasswordHash hash = PasswordHasher.create(password);
        try (var db = DatabaseConnection.getConnection()) {
            db.setAutoCommit(false);
            try {
                if (!writePassword(db, customerId, hash, null)) {
                    db.rollback();
                    return false;
                }
                try (var audit = db.prepareStatement("""
                        INSERT INTO admin_audit_log(admin_user_id, action, target_user_id, details)
                        VALUES (?, 'CUSTOMER_PASSWORD_RESET', ?, 'Temporary password set')
                        """)) {
                    audit.setLong(1, adminId);
                    audit.setLong(2, customerId);
                    audit.executeUpdate();
                }
                db.commit();
                return true;
            } catch (SQLException exception) {
                db.rollback();
                throw exception;
            }
        }
    }

    public boolean changePassword(Identity customer, char[] current, char[] next) throws SQLException {
        Identity verified = authenticate(customer.username(), current).customer();
        if (verified == null || verified.id() != customer.id()
                || verified.credentialVersion() != customer.credentialVersion()) return false;
        try (var db = DatabaseConnection.getConnection()) {
            return writePassword(db, customer.id(), PasswordHasher.create(next), customer.credentialVersion());
        }
    }

    private boolean writePassword(Connection db, long id, PasswordHash hash, Long expectedVersion) throws SQLException {
        try (var update = db.prepareStatement("""
                UPDATE users SET password_hash=?, password_salt=?, password_iterations=?,
                    credential_version=credential_version+1, password_change_required=?
                WHERE id=? AND role='CUSTOMER' AND status='ACTIVE'
                """ + (expectedVersion == null ? "" : " AND credential_version=?"))) {
            update.setString(1, hash.hashHex());
            update.setString(2, hash.saltHex());
            update.setInt(3, hash.iterations());
            update.setBoolean(4, expectedVersion == null);
            update.setLong(5, id);
            if (expectedVersion != null) update.setLong(6, expectedVersion);
            return update.executeUpdate() == 1;
        }
    }
}
