package com.netbanking.dao;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.model.AdminAuditEntry;
import com.netbanking.model.AdminDashboardStats;
import com.netbanking.model.AdminCredential;
import com.netbanking.model.CustomerSummary;
import com.netbanking.security.PasswordHasher.PasswordHash;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdminDao {
    public Optional<AdminCredential> findActiveByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, password_hash, password_salt, password_iterations "
                + "FROM users WHERE username = ? AND role = 'ADMIN' AND status = 'ACTIVE'";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AdminCredential(result.getLong("id"), result.getString("username"),
                        result.getString("password_hash"), result.getString("password_salt"),
                        result.getInt("password_iterations")));
            }
        }
    }

    public boolean hasAnyAdmin() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 1 FROM users WHERE role = 'ADMIN' LIMIT 1")) {
            return result.next();
        }
    }

    public void createInitialAdmin(String username, PasswordHash passwordHash) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, password_salt, password_iterations, role, status) "
                + "VALUES (?, ?, ?, ?, 'ADMIN', 'ACTIVE')";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash.hashHex());
            statement.setString(3, passwordHash.saltHex());
            statement.setInt(4, passwordHash.iterations());
            statement.executeUpdate();
        }
    }

    public AdminDashboardStats getDashboardStats() throws SQLException {
        String sql = "SELECT COUNT(*) AS total_customers, "
                + "SUM(status = 'PENDING') AS pending_customers, "
                + "SUM(status = 'ACTIVE') AS active_customers, "
                + "SUM(status = 'SUSPENDED') AS suspended_customers "
                + "FROM users WHERE role = 'CUSTOMER'";
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return new AdminDashboardStats(result.getLong("total_customers"),
                    result.getLong("pending_customers"), result.getLong("active_customers"),
                    result.getLong("suspended_customers"));
        }
    }

    public List<CustomerSummary> findCustomers(String search, String status) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, username, email, status, created_at FROM users WHERE role = 'CUSTOMER'");
        List<String> parameters = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (username LIKE ? OR email LIKE ?)");
            String pattern = "%" + search.trim() + "%";
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (status != null && List.of("PENDING", "ACTIVE", "SUSPENDED").contains(status)) {
            sql.append(" AND status = ?");
            parameters.add(status);
        }
        sql.append(" ORDER BY CASE status WHEN 'PENDING' THEN 0 WHEN 'ACTIVE' THEN 1 ELSE 2 END, "
                + "CASE WHEN status='PENDING' THEN created_at END ASC, created_at DESC, id ASC LIMIT 200");

        List<CustomerSummary> customers = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setString(i + 1, parameters.get(i));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp created = result.getTimestamp("created_at");
                    customers.add(new CustomerSummary(result.getLong("id"), result.getString("username"),
                            result.getString("email"), result.getString("status"),
                            created == null ? null : created.toLocalDateTime()));
                }
            }
        }
        return customers;
    }

    public Optional<CustomerSummary> findCustomer(long id) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, username, email, status, created_at FROM users WHERE id=? AND role='CUSTOMER'")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new CustomerSummary(result.getLong("id"), result.getString("username"),
                        result.getString("email"), result.getString("status"), result.getTimestamp("created_at").toLocalDateTime()));
            }
        }
    }

    public enum EmailUpdate { UPDATED, UNCHANGED, STALE, DUPLICATE, MISSING }

    public EmailUpdate updateCustomerEmail(long id, long adminId, String originalEmail, String email, String reason)
            throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT email FROM users WHERE id=? AND role='CUSTOMER' FOR UPDATE")) {
                select.setLong(1, id);
                String currentEmail;
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return EmailUpdate.MISSING;
                    }
                    currentEmail = result.getString("email");
                    if ("".equals(currentEmail)) currentEmail = null;
                }
                if (!java.util.Objects.equals(currentEmail, originalEmail)) {
                    connection.rollback();
                    return EmailUpdate.STALE;
                }
                if (java.util.Objects.equals(currentEmail, email)) {
                    connection.rollback();
                    return EmailUpdate.UNCHANGED;
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE users SET email=? WHERE id=?")) {
                    update.setString(1, email);
                    update.setLong(2, id);
                    update.executeUpdate();
                } catch (SQLException exception) {
                    if (exception.getErrorCode() != 1062) throw exception;
                    connection.rollback();
                    return EmailUpdate.DUPLICATE;
                }
                try (PreparedStatement audit = connection.prepareStatement("""
                        INSERT INTO admin_audit_log (admin_user_id, action, target_user_id, details)
                        VALUES (?, 'CUSTOMER_EMAIL_UPDATED', ?, ?)
                        """)) {
                    audit.setLong(1, adminId);
                    audit.setLong(2, id);
                    audit.setString(3, "Email corrected | Reason: " + reason);
                    audit.executeUpdate();
                }
                connection.commit();
                return EmailUpdate.UPDATED;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public java.util.OptionalLong findNextPendingCustomer(long excludeId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id FROM users WHERE role='CUSTOMER' AND status='PENDING' AND id<>?
                     ORDER BY created_at ASC, id ASC LIMIT 1
                     """)) {
            statement.setLong(1, excludeId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? java.util.OptionalLong.of(result.getLong(1)) : java.util.OptionalLong.empty();
            }
        }
    }

    public boolean changeCustomerStatus(long customerId, long adminId, String newStatus, String expectedStatus, String reason) throws SQLException {
        if (reason == null || reason.isBlank() || reason.length() > 140) return false;
        if (!List.of("ACTIVE", "SUSPENDED").contains(newStatus)) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String currentStatus;
                String username;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT username, status FROM users WHERE id = ? AND role = 'CUSTOMER' FOR UPDATE")) {
                    select.setLong(1, customerId);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                        username = result.getString("username");
                        currentStatus = result.getString("status");
                    }
                }

                boolean validTransition = ("PENDING".equals(currentStatus) && "ACTIVE".equals(newStatus))
                        || ("ACTIVE".equals(currentStatus) && "SUSPENDED".equals(newStatus))
                        || ("SUSPENDED".equals(currentStatus) && "ACTIVE".equals(newStatus));
                if (!validTransition || !currentStatus.equals(expectedStatus)) {
                    connection.rollback();
                    return false;
                }

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE users SET status = ? WHERE id = ? AND role = 'CUSTOMER'")) {
                    update.setString(1, newStatus);
                    update.setLong(2, customerId);
                    update.executeUpdate();
                }

                String action = "ACTIVE".equals(newStatus)
                        ? ("PENDING".equals(currentStatus) ? "CUSTOMER_APPROVED" : "CUSTOMER_REACTIVATED")
                        : "CUSTOMER_SUSPENDED";
                try (PreparedStatement audit = connection.prepareStatement(
                        "INSERT INTO admin_audit_log (admin_user_id, action, target_user_id, details) VALUES (?, ?, ?, ?)")) {
                    audit.setLong(1, adminId);
                    audit.setString(2, action);
                    audit.setLong(3, customerId);
                    audit.setString(4, currentStatus + " to " + newStatus + " | Reason: " + reason.trim());
                    audit.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<AdminAuditEntry> findRecentAuditEntries(int limit) throws SQLException {
        return findAuditEntries(limit, null, "");
    }

    public List<AdminAuditEntry> findRecentAuditEntries(int limit, String group) throws SQLException {
        return findAuditEntries(limit, null, group);
    }

    public List<AdminAuditEntry> findCustomerAuditEntries(long customerId) throws SQLException {
        return findAuditEntries(100, customerId, "");
    }

    private List<AdminAuditEntry> findAuditEntries(int limit, Long customerId, String group) throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String sql = "SELECT a.action, a.details, a.created_at, admin.username AS admin_username, "
                + "target.username AS target_username FROM admin_audit_log a "
                + "JOIN users admin ON admin.id = a.admin_user_id "
                + "LEFT JOIN users target ON target.id = a.target_user_id "
                + "WHERE 1=1 "
                + (customerId == null ? "" : "AND a.target_user_id = ? ")
                + (group.isEmpty() ? "" : "AND LEFT(a.action, ?) = ? ")
                + "ORDER BY a.created_at DESC, a.id DESC LIMIT ?";
        List<AdminAuditEntry> entries = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            if (customerId != null) statement.setLong(parameter++, customerId);
            if (!group.isEmpty()) {
                statement.setInt(parameter++, group.length());
                statement.setString(parameter++, group);
            }
            statement.setInt(parameter, safeLimit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp created = result.getTimestamp("created_at");
                    LocalDateTime createdAt = created == null ? null : created.toLocalDateTime();
                    entries.add(new AdminAuditEntry(result.getString("admin_username"), result.getString("action"),
                            result.getString("target_username"), result.getString("details"), createdAt));
                }
            }
        }
        return entries;
    }
}
