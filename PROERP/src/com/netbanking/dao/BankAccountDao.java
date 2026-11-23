package com.netbanking.dao;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.model.BankAccount;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.security.SecureRandom;

public class BankAccountDao {
    private static final SecureRandom ACCOUNT_NUMBERS = new SecureRandom();
    private static final String SELECT = """
            SELECT a.*, u.username FROM bank_accounts a JOIN users u ON u.id=a.customer_id
            """;

    public BankAccount.Counts counts() throws SQLException {
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*), SUM(status='ACTIVE'), SUM(status='FROZEN') FROM bank_accounts")) {
            result.next();
            return new BankAccount.Counts(result.getLong(1), result.getLong(2), result.getLong(3));
        }
    }

    public List<BankAccount> list(Long customerId, String search, String status) throws SQLException {
        return list(customerId, search, status, false);
    }

    public List<BankAccount> eligibleForPosting() throws SQLException {
        return list(null, "", "ACTIVE", true);
    }

    private List<BankAccount> list(Long customerId, String search, String status, boolean activeCustomer) throws SQLException {
        String sql = SELECT + """
                WHERE (? IS NULL OR a.customer_id=?)
                  AND (?='' OR LOCATE(?,a.account_number)>0 OR LOCATE(?,u.username)>0)
                  AND (?='' OR a.status=?)
                """ + (activeCustomer ? "AND u.status='ACTIVE' AND u.role='CUSTOMER' " : "")
                + "ORDER BY a.id DESC LIMIT 200";
        List<BankAccount> accounts = new ArrayList<>();
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, customerId, java.sql.Types.BIGINT);
            statement.setObject(2, customerId, java.sql.Types.BIGINT);
            for (int parameter = 3; parameter <= 5; parameter++) statement.setString(parameter, search);
            statement.setString(6, status);
            statement.setString(7, status);
            try (var result = statement.executeQuery()) {
                while (result.next()) accounts.add(readAccount(result));
            }
        }
        return accounts;
    }

    public Optional<BankAccount> find(long id) throws SQLException {
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.prepareStatement(SELECT + " WHERE a.id=?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(readAccount(result)) : Optional.empty();
            }
        }
    }

    public long open(long customerId, long adminId, String type, String reason) throws SQLException {
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var customer = connection.prepareStatement(
                        "SELECT status FROM users WHERE id=? AND role='CUSTOMER' FOR UPDATE")) {
                    customer.setLong(1, customerId);
                    try (var result = customer.executeQuery()) {
                        if (!result.next() || !"ACTIVE".equals(result.getString(1))) {
                            connection.rollback();
                            return 0;
                        }
                    }
                }
                long id = openIfMissing(connection, customerId, adminId, type, reason);
                connection.commit();
                return id;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    // The caller holds the customer's row lock or has just inserted the customer in this transaction.
    static long openIfMissing(Connection connection, long customerId, long adminId, String type, String reason)
            throws SQLException {
        try (var existing = connection.prepareStatement(
                "SELECT id FROM bank_accounts WHERE customer_id=? AND account_type=?")) {
            existing.setLong(1, customerId);
            existing.setString(2, type);
            try (var result = existing.executeQuery()) {
                if (result.next()) return 0;
            }
        }
        String number = String.format(java.util.Locale.ROOT, "990%09d", ACCOUNT_NUMBERS.nextInt(1_000_000_000));
        try (var insert = connection.prepareStatement(
                "INSERT INTO bank_accounts(customer_id,account_number,account_type) VALUES (?,?,?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, customerId);
            insert.setString(2, number);
            insert.setString(3, type);
            insert.executeUpdate();
            try (var keys = insert.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing account ID");
                audit(connection, adminId, customerId, "ACCOUNT_OPENED", number, reason);
                return keys.getLong(1);
            }
        }
    }

    public boolean setFrozen(long id, long adminId, boolean frozen, String reason) throws SQLException {
        String expected = frozen ? "ACTIVE" : "FROZEN";
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long customerId;
                String number;
                try (var select = connection.prepareStatement(
                        "SELECT customer_id, account_number FROM bank_accounts WHERE id=? AND status=? FOR UPDATE")) {
                    select.setLong(1, id);
                    select.setString(2, expected);
                    try (var result = select.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                        customerId = result.getLong(1);
                        number = result.getString(2);
                    }
                }
                try (var update = connection.prepareStatement("UPDATE bank_accounts SET status=? WHERE id=?")) {
                    update.setString(1, frozen ? "FROZEN" : "ACTIVE");
                    update.setLong(2, id);
                    update.executeUpdate();
                }
                audit(connection, adminId, customerId, frozen ? "ACCOUNT_FROZEN" : "ACCOUNT_UNFROZEN", number, reason);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void audit(Connection connection, long adminId, long customerId, String event, String number, String reason)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO admin_audit_log(admin_user_id,action,target_user_id,details) VALUES (?,?,?,?)")) {
            statement.setLong(1, adminId);
            statement.setString(2, event);
            statement.setLong(3, customerId);
            statement.setString(4, "Account " + number + (reason == null || reason.isBlank() ? "" : " | Note: " + reason.strip()));
            statement.executeUpdate();
        }
    }

    private BankAccount readAccount(ResultSet result) throws SQLException {
        return new BankAccount(result.getLong("id"), result.getLong("customer_id"), result.getString("username"),
                result.getString("account_number"), result.getString("account_type"), result.getString("status"),
                result.getBigDecimal("balance"), result.getTimestamp("opened_at").toLocalDateTime());
    }
}
