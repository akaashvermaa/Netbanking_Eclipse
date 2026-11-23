package com.netbanking.dao;

import com.netbanking.config.DatabaseConnection;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccountReviewDao {
    public enum Category {
        ACCOUNT_REVIEW("Account review"), BALANCE_CHECK("Balance check"), LOAN_FOLLOW_UP("Loan follow-up");

        private final String label;
        Category(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record Item(long accountId, long customerId, String customer, String email, String number, String type,
                       BigDecimal balance, BigDecimal outstanding, boolean frozen, boolean suspended,
                       Category flag, LocalDateTime flaggedAt) {}

    private static final String PREDICATE = """
            WHERE u.role='CUSTOMER' AND (f.account_id IS NOT NULL OR a.status='FROZEN' OR u.status='SUSPENDED')
            """;
    private static final String JOINS = """
            FROM bank_accounts a JOIN users u ON a.customer_id=u.id
            LEFT JOIN account_review_flags f ON f.account_id=a.id
            """;

    public long count() throws SQLException {
        try (var connection = DatabaseConnection.getConnection();
             var query = connection.createStatement();
             var row = query.executeQuery("SELECT COUNT(*) " + JOINS + PREDICATE)) {
            row.next();
            return row.getLong(1);
        }
    }

    public List<Item> list() throws SQLException {
        String sql = """
                SELECT a.id,u.id,COALESCE(p.full_name,u.username),u.email,a.account_number,a.account_type,a.balance,
                       COALESCE((SELECT SUM(l.outstanding) FROM bank_loans l WHERE l.account_id=a.id),0.00),
                       a.status='FROZEN',u.status='SUSPENDED',f.category,f.flagged_at
                """ + JOINS + " LEFT JOIN customer_profiles p ON p.customer_id=u.id " + PREDICATE
                + "ORDER BY (a.status='FROZEN' OR u.status='SUSPENDED') DESC, f.flagged_at DESC, a.id DESC LIMIT 200";
        List<Item> items = new ArrayList<>();
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                String flag = rows.getString(11);
                var date = rows.getTimestamp(12);
                items.add(new Item(rows.getLong(1), rows.getLong(2), rows.getString(3), rows.getString(4), rows.getString(5),
                        rows.getString(6), rows.getBigDecimal(7), rows.getBigDecimal(8), rows.getBoolean(9), rows.getBoolean(10),
                        flag == null ? null : Category.valueOf(flag), date == null ? null : date.toLocalDateTime()));
            }
        }
        return items;
    }

    public boolean changeFlag(long accountId, long adminId, Category category) throws SQLException {
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long customerId;
                String number;
                try (var lock = connection.prepareStatement("SELECT customer_id,account_number FROM bank_accounts WHERE id=? FOR UPDATE")) {
                    lock.setLong(1, accountId);
                    try (var row = lock.executeQuery()) {
                        if (!row.next()) { connection.rollback(); return false; }
                        customerId = row.getLong(1);
                        number = row.getString(2);
                    }
                }
                String previous = null;
                try (var select = connection.prepareStatement("SELECT category FROM account_review_flags WHERE account_id=?")) {
                    select.setLong(1, accountId);
                    try (var row = select.executeQuery()) { if (row.next()) previous = row.getString(1); }
                }
                String next = category == null ? null : category.name();
                if (java.util.Objects.equals(previous, next)) { connection.rollback(); return false; }
                if (category == null) {
                    try (var delete = connection.prepareStatement("DELETE FROM account_review_flags WHERE account_id=?")) {
                        delete.setLong(1, accountId);
                        delete.executeUpdate();
                    }
                } else {
                    try (var write = connection.prepareStatement("""
                            INSERT INTO account_review_flags(account_id,category,flagged_by) VALUES (?,?,?)
                            ON DUPLICATE KEY UPDATE category=?, flagged_by=?, flagged_at=CURRENT_TIMESTAMP
                            """)) {
                        write.setLong(1, accountId);
                        write.setString(2, next);
                        write.setLong(3, adminId);
                        write.setString(4, next);
                        write.setLong(5, adminId);
                        write.executeUpdate();
                    }
                }
                try (var audit = connection.prepareStatement("""
                        INSERT INTO admin_audit_log(admin_user_id,action,target_user_id,details) VALUES (?,?,?,?)
                        """)) {
                    audit.setLong(1, adminId);
                    audit.setString(2, category == null ? "ACCOUNT_FLAG_RESOLVED" : "ACCOUNT_FLAGGED");
                    audit.setLong(3, customerId);
                    audit.setString(4, "Account " + number + " | " + (previous == null ? "No flag" : Category.valueOf(previous).label())
                            + " to " + (category == null ? "Resolved" : category.label()));
                    audit.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
