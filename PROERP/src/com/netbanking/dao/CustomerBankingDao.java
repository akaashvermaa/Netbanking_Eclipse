package com.netbanking.dao;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.dao.CustomerAccessDao.Identity;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CustomerBankingDao {
    public enum TransferResult { POSTED, DUPLICATE, UNAVAILABLE, INSUFFICIENT, INVALID }
    public record Activity(long accountId, String account, String customer, String kind, BigDecimal amount,
                           BigDecimal balance, LocalDateTime postedAt, String reference, String description) {
        public boolean debit() {
            return List.of("LOAN_REPAYMENT", "ADJUSTMENT_DEBIT", "TRANSFER_OUT", "BILL_PAYMENT",
                    "DEPOSIT_OPEN", "CUSTOMER_REPAYMENT", "GOAL_CONTRIBUTION", "CARD_PURCHASE").contains(kind);
        }

        public String label() {
            return switch (kind) {
                case "DEPOSIT" -> "Deposit";
                case "LOAN_DISBURSEMENT" -> "Loan payout";
                case "LOAN_REPAYMENT", "CUSTOMER_REPAYMENT" -> "Loan repayment";
                case "ADJUSTMENT_CREDIT", "ADJUSTMENT_DEBIT" -> "Balance adjustment";
                case "TRANSFER_OUT" -> "Transfer sent";
                case "TRANSFER_IN" -> "Transfer received";
                case "BILL_PAYMENT" -> "Bill payment";
                case "DEPOSIT_OPEN" -> "Fixed deposit opened";
                case "DEPOSIT_REDEEM" -> "Fixed deposit redeemed";
                case "GOAL_CONTRIBUTION" -> "Savings set aside";
                case "GOAL_WITHDRAWAL" -> "Savings returned";
                case "CARD_PURCHASE" -> "Card purchase";
                default -> "Account activity";
            };
        }
    }
    public record Receipt(long id, String source, String destination, BigDecimal amount, LocalDateTime date,
                          boolean sent) {}
    public record DailyFlow(LocalDate date, BigDecimal credits, BigDecimal debits) {}
    public enum Category {
        ALL("All types"),
        TRANSFER("Transfers", "TRANSFER_OUT", "TRANSFER_IN"),
        BILL("Bill payments", "BILL_PAYMENT"),
        LOAN("Loans", "LOAN_DISBURSEMENT", "LOAN_REPAYMENT", "CUSTOMER_REPAYMENT"),
        DEPOSIT("Deposits", "DEPOSIT", "DEPOSIT_OPEN", "DEPOSIT_REDEEM"),
        GOAL("Savings goals", "GOAL_CONTRIBUTION", "GOAL_WITHDRAWAL"),
        CARD("Card purchases", "CARD_PURCHASE"),
        ADJUSTMENT("Balance adjustments", "ADJUSTMENT_CREDIT", "ADJUSTMENT_DEBIT");

        private final String label;
        private final List<String> kinds;

        Category(String label, String... kinds) {
            this.label = label;
            this.kinds = List.of(kinds);
        }

        public String label() { return label; }
    }
    public record ActivityFilter(LocalDate from, LocalDate before, Category category, String search) {
        private static final ActivityFilter ANY = new ActivityFilter(null, null, Category.ALL, "");
    }
    private record Account(long id, long customerId, BigDecimal balance) {}

    public List<Activity> activity(long customerId, Long accountId, int limit, int offset) throws SQLException {
        return activity(customerId, accountId, ActivityFilter.ANY, limit, offset);
    }

    public List<Activity> activity(long customerId, Long accountId, ActivityFilter filter,
                                   int limit, int offset) throws SQLException {
        return readActivity(customerId, accountId, filter, limit, offset);
    }

    public List<Activity> adminActivity(Long accountId, Long customerId) throws SQLException {
        return readActivity(customerId, accountId, ActivityFilter.ANY, 30, 0);
    }

    public List<DailyFlow> recentFlow(long customerId) throws SQLException {
        LocalDate today;
        try (var db = DatabaseConnection.getConnection(); var query = db.createStatement();
             var currentDate = query.executeQuery("SELECT CURRENT_DATE")) {
            currentDate.next();
            today = currentDate.getObject(1, LocalDate.class);
        }
        LocalDate first = today.minusDays(6);
        ActivityFilter filter = new ActivityFilter(first, today.plusDays(1), Category.ALL, "");
        var activity = readActivity(customerId, null, filter, Integer.MAX_VALUE, 0);
        var totals = new java.util.LinkedHashMap<LocalDate, DailyFlow>();
        for (int day = 0; day < 7; day++) {
            LocalDate date = first.plusDays(day);
            totals.put(date, new DailyFlow(date, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        for (Activity row : activity) {
            var day = totals.get(row.postedAt().toLocalDate());
            var credits = row.debit() ? day.credits() : day.credits().add(row.amount());
            var debits = row.debit() ? day.debits().add(row.amount()) : day.debits();
            totals.put(day.date(), new DailyFlow(day.date(), credits, debits));
        }
        return List.copyOf(totals.values());
    }

    private List<Activity> readActivity(Long customerId, Long accountId, ActivityFilter filter,
                                        int limit, int offset) throws SQLException {
        List<String> kinds = filter.category().kinds;
        String kindClause = kinds.isEmpty() ? "" : " AND kind IN ("
                + String.join(", ", java.util.Collections.nCopies(kinds.size(), "?")) + ")";
        try (var db = DatabaseConnection.getConnection();
             var statement = db.prepareStatement("""
                     WITH scoped_accounts AS (
                         SELECT a.id, a.account_number, COALESCE(p.full_name, u.username) AS customer
                         FROM bank_accounts a JOIN users u ON a.customer_id=u.id
                         LEFT JOIN customer_profiles p ON p.customer_id=u.id
                         WHERE (? IS NULL OR a.customer_id=?) AND (? IS NULL OR a.id=?)
                     )
                     SELECT account_id, account_number, customer, kind, amount, balance_after, posted_at, reference, description
                     FROM (
                         SELECT a.id AS account_id, a.account_number, a.customer, m.kind, m.amount, m.balance_after, m.posted_at,
                                CONCAT('M-', m.id) AS reference, m.id AS entry_id, NULL AS description
                         FROM account_movements m JOIN scoped_accounts a ON a.id=m.account_id
                         UNION ALL
                         SELECT a.id, a.account_number, a.customer, 'TRANSFER_OUT', t.amount, t.source_balance_after, t.posted_at,
                                CONCAT('T-', t.id), t.id, NULL
                         FROM customer_transfers t JOIN scoped_accounts a ON a.id=t.source_account_id
                         UNION ALL
                         SELECT a.id, a.account_number, a.customer, 'TRANSFER_IN', t.amount, t.destination_balance_after, t.posted_at,
                                CONCAT('T-', t.id), t.id, NULL
                         FROM customer_transfers t JOIN scoped_accounts a ON a.id=t.destination_account_id
                         UNION ALL
                         SELECT a.id, a.account_number, a.customer, c.kind, c.amount, c.balance_after, c.posted_at,
                                CONCAT('C-', c.id), c.id, c.description
                         FROM customer_postings c JOIN scoped_accounts a ON a.id=c.account_id
                     ) AS activity WHERE (? IS NULL OR posted_at>=?) AND (? IS NULL OR posted_at<?)
                       AND (?='' OR LOCATE(?, reference)>0 OR LOCATE(?, description)>0)
                     """ + kindClause + """
                      ORDER BY posted_at DESC, entry_id DESC, reference DESC, account_number DESC LIMIT ? OFFSET ?
                     """)) {
            statement.setObject(1, customerId, Types.BIGINT);
            statement.setObject(2, customerId, Types.BIGINT);
            statement.setObject(3, accountId, Types.BIGINT);
            statement.setObject(4, accountId, Types.BIGINT);
            statement.setObject(5, filter.from());
            statement.setObject(6, filter.from());
            statement.setObject(7, filter.before());
            statement.setObject(8, filter.before());
            statement.setString(9, filter.search());
            statement.setString(10, filter.search());
            statement.setString(11, filter.search());
            int parameter = 12;
            for (String kind : kinds) statement.setString(parameter++, kind);
            statement.setInt(parameter++, limit);
            statement.setInt(parameter, offset);
            List<Activity> result = new ArrayList<>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new Activity(rows.getLong(1), rows.getString(2), rows.getString(3), rows.getString(4),
                            rows.getBigDecimal(5), rows.getBigDecimal(6), rows.getObject(7, LocalDateTime.class),
                            rows.getString(8), rows.getString(9)));
                }
            }
            return result;
        }
    }

    public Receipt receipt(long customerId, long transferId) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var query = db.prepareStatement("""
                     SELECT s.account_number, d.account_number, t.amount, t.posted_at, s.customer_id
                     FROM customer_transfers t JOIN bank_accounts s ON s.id=t.source_account_id
                     JOIN bank_accounts d ON d.id=t.destination_account_id
                     WHERE t.id=? AND (s.customer_id=? OR d.customer_id=?)
                     """)) {
            query.setLong(1, transferId);
            query.setLong(2, customerId);
            query.setLong(3, customerId);
            try (var row = query.executeQuery()) {
                if (!row.next()) return null;
                boolean sent = row.getLong(5) == customerId;
                String source = row.getString(1);
                if (!sent) source = "Ending " + source.substring(Math.max(0, source.length() - 4));
                return new Receipt(transferId, source, row.getString(2), row.getBigDecimal(3),
                        row.getObject(4, LocalDateTime.class), sent);
            }
        }
    }

    public TransferResult transfer(Identity customer, long sourceId, String destinationNumber, BigDecimal amount,
                                   String requestKey) throws SQLException {
        if (amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 9) {
            return TransferResult.INVALID;
        }
        try (var db = DatabaseConnection.getConnection()) {
            db.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            db.setAutoCommit(false);
            try {
                long destinationId;
                try (var lookup = db.prepareStatement("SELECT id FROM bank_accounts WHERE account_number=?")) {
                    lookup.setString(1, destinationNumber);
                    try (var row = lookup.executeQuery()) {
                        if (!row.next()) return rollback(db, TransferResult.UNAVAILABLE);
                        destinationId = row.getLong(1);
                    }
                }
                if (sourceId == destinationId) return rollback(db, TransferResult.INVALID);
                Account first = lockAccount(db, Math.min(sourceId, destinationId));
                Account second = lockAccount(db, Math.max(sourceId, destinationId));
                if (first == null || second == null) return rollback(db, TransferResult.UNAVAILABLE);
                Account source = first.id() == sourceId ? first : second;
                Account destination = first.id() == sourceId ? second : first;
                if (source.customerId() != customer.id()
                        || !lockCustomers(db, customer, destination.customerId())) {
                    return rollback(db, TransferResult.UNAVAILABLE);
                }
                try (var previous = db.prepareStatement("""
                        SELECT source_account_id, destination_account_id, amount FROM customer_transfers
                        WHERE request_key=?
                        """)) {
                    previous.setString(1, requestKey);
                    try (var row = previous.executeQuery()) {
                        if (row.next()) {
                            boolean same = row.getLong(1) == sourceId && row.getLong(2) == destinationId
                                    && row.getBigDecimal(3).compareTo(amount) == 0;
                            return rollback(db, same ? TransferResult.DUPLICATE : TransferResult.INVALID);
                        }
                    }
                }
                BigDecimal remaining = source.balance().subtract(amount);
                if (remaining.signum() < 0) return rollback(db, TransferResult.INSUFFICIENT);
                BigDecimal received = destination.balance().add(amount);
                if (received.precision() - received.scale() > 17) return rollback(db, TransferResult.UNAVAILABLE);

                try (var movement = db.prepareStatement("""
                        INSERT INTO customer_transfers(source_account_id, destination_account_id, amount,
                            source_balance_after, destination_balance_after, request_key) VALUES (?,?,?,?,?,?)
                        """);
                     var update = db.prepareStatement("UPDATE bank_accounts SET balance=? WHERE id=?")) {
                    movement.setLong(1, sourceId);
                    movement.setLong(2, destinationId);
                    movement.setBigDecimal(3, amount);
                    movement.setBigDecimal(4, remaining);
                    movement.setBigDecimal(5, received);
                    movement.setString(6, requestKey);
                    movement.executeUpdate();
                    update.setBigDecimal(1, remaining);
                    update.setLong(2, sourceId);
                    update.executeUpdate();
                    update.setBigDecimal(1, received);
                    update.setLong(2, destinationId);
                    update.executeUpdate();
                }
                db.commit();
                return TransferResult.POSTED;
            } catch (SQLException exception) {
                db.rollback();
                if (exception.getErrorCode() == 1062) return TransferResult.INVALID;
                if ("40001".equals(exception.getSQLState()) || exception.getErrorCode() == 1205) {
                    return TransferResult.UNAVAILABLE;
                }
                throw exception;
            }
        }
    }

    private Account lockAccount(Connection db, long id) throws SQLException {
        try (var query = db.prepareStatement("""
                SELECT customer_id, balance FROM bank_accounts WHERE id=? AND status='ACTIVE' FOR UPDATE
                """)) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                return row.next() ? new Account(id, row.getLong(1), row.getBigDecimal(2)) : null;
            }
        }
    }

    private boolean lockCustomers(Connection db, Identity sender, long recipientId) throws SQLException {
        try (var query = db.prepareStatement("""
                SELECT id, credential_version, password_change_required FROM users
                WHERE id IN (?, ?) AND role='CUSTOMER' AND status='ACTIVE' ORDER BY id FOR UPDATE
                """)) {
            query.setLong(1, sender.id());
            query.setLong(2, recipientId);
            int count = 0;
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    count++;
                    if (rows.getLong(1) == sender.id()
                            && (rows.getLong(2) != sender.credentialVersion() || rows.getBoolean(3))) return false;
                }
            }
            return count == (sender.id() == recipientId ? 1 : 2);
        }
    }

    private TransferResult rollback(Connection db, TransferResult result) throws SQLException {
        db.rollback();
        return result;
    }
}
