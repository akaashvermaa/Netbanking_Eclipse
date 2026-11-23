package com.netbanking.dao;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.dao.CustomerAccessDao.Identity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerProductsDao {
    public enum Posting { BILL_PAYMENT, DEPOSIT_OPEN, DEPOSIT_REDEEM, CUSTOMER_REPAYMENT, GOAL_CONTRIBUTION, GOAL_WITHDRAWAL, CARD_PURCHASE }
    public enum Result { POSTED, UNAVAILABLE, INSUFFICIENT, INVALID, DUPLICATE, CARD_BLOCKED, CARD_LIMIT }
    public record Payee(long accountId, String name, String number) {}
    public record Card(long accountId, String account, String lastFour, boolean locked, BigDecimal limit,
                       boolean accountActive) {}
    public record Deposit(long id, long accountId, String account, BigDecimal principal, BigDecimal payout,
                          LocalDate maturity, boolean redeemed, boolean redeemable) {}
    public record Goal(long id, long accountId, String account, String name, BigDecimal target, BigDecimal saved,
                       boolean accountActive) {}

    public List<Payee> payees(long customerId) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var query = db.prepareStatement("""
                     SELECT a.id, p.label, a.account_number FROM customer_payees p
                     JOIN bank_accounts a ON p.account_id=a.id WHERE p.customer_id=? ORDER BY p.label,a.id
                     """)) {
            query.setLong(1, customerId);
            List<Payee> result = new ArrayList<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(new Payee(rows.getLong(1), rows.getString(2), rows.getString(3)));
            }
            return result;
        }
    }

    public boolean savePayee(long customerId, String number, String name) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var insert = db.prepareStatement("""
                     INSERT INTO customer_payees(customer_id,account_id,label)
                     SELECT ?, a.id, ? FROM bank_accounts a JOIN users u ON a.customer_id=u.id
                     WHERE a.account_number=? AND a.status='ACTIVE' AND u.status='ACTIVE' AND u.role='CUSTOMER'
                     ON DUPLICATE KEY UPDATE label=?
                     """)) {
            insert.setLong(1, customerId);
            insert.setString(2, name);
            insert.setString(3, number);
            insert.setString(4, name);
            return insert.executeUpdate() > 0;
        }
    }

    public boolean removePayee(long customerId, long account) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var remove = db.prepareStatement("DELETE FROM customer_payees WHERE customer_id=? AND account_id=?")) {
            remove.setLong(1, customerId);
            remove.setLong(2, account);
            return remove.executeUpdate() == 1;
        }
    }

    public List<Card> cards(long customerId) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var query = db.prepareStatement("""
                     SELECT a.id, a.account_number, c.last_four, c.locked, c.daily_limit, a.status='ACTIVE'
                     FROM customer_cards c JOIN bank_accounts a ON a.id=c.account_id
                     WHERE a.customer_id=? ORDER BY a.id
                     """)) {
            query.setLong(1, customerId);
            List<Card> result = new ArrayList<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(new Card(rows.getLong(1), rows.getString(2), rows.getString(3),
                        rows.getBoolean(4), rows.getBigDecimal(5), rows.getBoolean(6)));
            }
            return result;
        }
    }

    public boolean issueCard(long customerId, long account) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var issue = db.prepareStatement("""
                     INSERT INTO customer_cards(account_id,last_four)
                     SELECT id, ? FROM bank_accounts WHERE id=? AND customer_id=? AND status='ACTIVE'
                     ON DUPLICATE KEY UPDATE last_four=customer_cards.last_four
                     """)) {
            issue.setString(1, String.format(Locale.ROOT, "%04d", new java.security.SecureRandom().nextInt(10000)));
            issue.setLong(2, account);
            issue.setLong(3, customerId);
            return issue.executeUpdate() > 0;
        }
    }

    public boolean changeCard(long customerId, long account, Boolean locked, BigDecimal limit) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var update = db.prepareStatement("""
                     UPDATE customer_cards c JOIN bank_accounts a ON a.id=c.account_id
                     SET c.locked=COALESCE(?,c.locked), c.daily_limit=COALESCE(?,c.daily_limit)
                     WHERE a.id=? AND a.customer_id=? AND a.status='ACTIVE'
                     """)) {
            update.setObject(1, locked, java.sql.Types.BOOLEAN);
            update.setBigDecimal(2, limit);
            update.setLong(3, account);
            update.setLong(4, customerId);
            return update.executeUpdate() > 0;
        }
    }

    public BigDecimal cardSpentToday(long accountId) throws SQLException {
        try (var db = DatabaseConnection.getConnection()) {
            return cardSpentToday(db, accountId);
        }
    }

    private BigDecimal cardSpentToday(Connection db, long accountId) throws SQLException {
        try (var query = db.prepareStatement("""
                SELECT COALESCE(SUM(amount),0.00) FROM customer_postings
                WHERE account_id=? AND kind='CARD_PURCHASE'
                  AND posted_at>=CURRENT_DATE AND posted_at<CURRENT_DATE + INTERVAL 1 DAY
                """)) {
            query.setLong(1, accountId);
            try (var row = query.executeQuery()) {
                row.next();
                return row.getBigDecimal(1);
            }
        }
    }

    private Result checkCardPurchase(Connection db, long accountId, BigDecimal amount) throws SQLException {
        try (var query = db.prepareStatement("""
                SELECT locked,daily_limit FROM customer_cards WHERE account_id=? FOR UPDATE
                """)) {
            query.setLong(1, accountId);
            try (var row = query.executeQuery()) {
                if (!row.next() || row.getBoolean(1)) return Result.CARD_BLOCKED;
                BigDecimal limit = row.getBigDecimal(2);
                return cardSpentToday(db, accountId).add(amount).compareTo(limit) > 0 ? Result.CARD_LIMIT : null;
            }
        }
    }

    public List<Deposit> deposits(long customerId) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var query = db.prepareStatement("""
                     SELECT d.id, a.id, a.account_number, d.principal, d.payout, d.maturity_date, d.redeemed,
                            NOT d.redeemed AND a.status='ACTIVE' AND d.maturity_date<=CURRENT_DATE
                     FROM customer_deposits d JOIN bank_accounts a ON d.account_id=a.id
                     WHERE a.customer_id=? ORDER BY d.id DESC LIMIT 100
                     """)) {
            query.setLong(1, customerId);
            List<Deposit> result = new ArrayList<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(new Deposit(rows.getLong(1), rows.getLong(2), rows.getString(3),
                        rows.getBigDecimal(4), rows.getBigDecimal(5), rows.getObject(6, LocalDate.class),
                        rows.getBoolean(7), rows.getBoolean(8)));
            }
            return result;
        }
    }

    public List<Goal> goals(long customerId) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var query = db.prepareStatement("""
                     SELECT g.id, a.id, a.account_number, g.name, g.target_amount, g.saved_amount, a.status='ACTIVE'
                     FROM customer_savings_goals g JOIN bank_accounts a ON a.id=g.account_id
                     WHERE a.customer_id=? AND g.closed_at IS NULL ORDER BY g.id DESC
                     """)) {
            query.setLong(1, customerId);
            List<Goal> result = new ArrayList<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(new Goal(rows.getLong(1), rows.getLong(2), rows.getString(3),
                        rows.getString(4), rows.getBigDecimal(5), rows.getBigDecimal(6), rows.getBoolean(7)));
            }
            return result;
        }
    }

    public Result createGoal(Identity customer, long account, String name, BigDecimal target, String key)
            throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var insert = db.prepareStatement("""
                     INSERT INTO customer_savings_goals(account_id,name,target_amount,request_key)
                     SELECT a.id, ?, ?, ? FROM bank_accounts a JOIN users u ON u.id=a.customer_id
                     WHERE a.id=? AND a.status='ACTIVE' AND u.id=? AND u.role='CUSTOMER' AND u.status='ACTIVE'
                       AND u.credential_version=? AND NOT u.password_change_required
                     """)) {
            insert.setString(1, name);
            insert.setBigDecimal(2, target);
            insert.setString(3, key);
            insert.setLong(4, account);
            insert.setLong(5, customer.id());
            insert.setLong(6, customer.credentialVersion());
            return insert.executeUpdate() == 1 ? Result.POSTED : Result.UNAVAILABLE;
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1062) return Result.DUPLICATE;
            throw exception;
        }
    }

    public Result closeGoal(Identity customer, long goal) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var close = db.prepareStatement("""
                     UPDATE customer_savings_goals g JOIN bank_accounts a ON a.id=g.account_id
                     JOIN users u ON u.id=a.customer_id SET g.closed_at=CURRENT_TIMESTAMP
                     WHERE g.id=? AND g.saved_amount=0 AND g.closed_at IS NULL
                       AND u.id=? AND u.role='CUSTOMER' AND u.status='ACTIVE'
                       AND u.credential_version=? AND NOT u.password_change_required
                     """)) {
            close.setLong(1, goal);
            close.setLong(2, customer.id());
            close.setLong(3, customer.credentialVersion());
            return close.executeUpdate() == 1 ? Result.POSTED : Result.UNAVAILABLE;
        }
    }

    public Result post(Identity customer, long accountId, Posting kind, BigDecimal amount, long productId,
                       int months, String description, String key) throws SQLException {
        try (var db = DatabaseConnection.getConnection()) {
            db.setAutoCommit(false);
            try {
                BigDecimal balance = lockBalance(db, customer, accountId);
                if (balance == null) return rollback(db, Result.UNAVAILABLE);
                if (posted(db, accountId, key)) return rollback(db, Result.DUPLICATE);
                if (kind == Posting.DEPOSIT_REDEEM) {
                    amount = redeem(db, accountId, productId);
                    if (amount == null) return rollback(db, Result.UNAVAILABLE);
                } else if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                        || amount.precision() - amount.scale() > (kind == Posting.CUSTOMER_REPAYMENT ? 17 : 9)) {
                    return rollback(db, Result.INVALID);
                }
                if (kind == Posting.CARD_PURCHASE) {
                    Result rejection = checkCardPurchase(db, accountId, amount);
                    if (rejection != null) return rollback(db, rejection);
                }

                boolean credit = kind == Posting.DEPOSIT_REDEEM || kind == Posting.GOAL_WITHDRAWAL;
                BigDecimal next = credit ? balance.add(amount) : balance.subtract(amount);
                if (next.signum() < 0) return rollback(db, Result.INSUFFICIENT);
                if (next.precision() - next.scale() > 17) return rollback(db, Result.INVALID);
                if (kind == Posting.CUSTOMER_REPAYMENT && !FinanceDao.repay(db, accountId, productId, amount, false)) {
                    return rollback(db, Result.INVALID);
                }
                if (kind == Posting.DEPOSIT_OPEN) openDeposit(db, accountId, amount, months);
                if (kind == Posting.GOAL_CONTRIBUTION || kind == Posting.GOAL_WITHDRAWAL) {
                    BigDecimal change = credit ? amount.negate() : amount;
                    if (!changeGoal(db, accountId, productId, change)) return rollback(db, Result.INVALID);
                }
                try (var update = db.prepareStatement("UPDATE bank_accounts SET balance=? WHERE id=?");
                     var movement = db.prepareStatement("""
                             INSERT INTO customer_postings(account_id,kind,description,amount,balance_after,request_key)
                             VALUES (?,?,?,?,?,?)
                             """)) {
                    update.setBigDecimal(1, next);
                    update.setLong(2, accountId);
                    update.executeUpdate();
                    movement.setLong(1, accountId);
                    movement.setString(2, kind.name());
                    movement.setString(3, description);
                    movement.setBigDecimal(4, amount);
                    movement.setBigDecimal(5, next);
                    movement.setString(6, key);
                    movement.executeUpdate();
                }
                db.commit();
                return Result.POSTED;
            } catch (SQLException exception) {
                db.rollback();
                if (exception.getErrorCode() == 1062) return Result.INVALID;
                throw exception;
            }
        }
    }

    private boolean changeGoal(Connection db, long account, long goal, BigDecimal change) throws SQLException {
        try (var update = db.prepareStatement("""
                UPDATE customer_savings_goals SET saved_amount=saved_amount+?
                WHERE id=? AND account_id=? AND closed_at IS NULL AND saved_amount+? BETWEEN 0 AND target_amount
                """)) {
            update.setBigDecimal(1, change);
            update.setLong(2, goal);
            update.setLong(3, account);
            update.setBigDecimal(4, change);
            return update.executeUpdate() == 1;
        }
    }

    private BigDecimal lockBalance(Connection db, Identity customer, long account) throws SQLException {
        try (var query = db.prepareStatement("""
                SELECT a.balance FROM bank_accounts a JOIN users u ON a.customer_id=u.id
                WHERE a.id=? AND a.status='ACTIVE' AND u.id=? AND u.role='CUSTOMER' AND u.status='ACTIVE'
                  AND u.credential_version=? AND NOT u.password_change_required FOR UPDATE
                """)) {
            query.setLong(1, account);
            query.setLong(2, customer.id());
            query.setLong(3, customer.credentialVersion());
            try (var row = query.executeQuery()) { return row.next() ? row.getBigDecimal(1) : null; }
        }
    }

    private boolean posted(Connection db, long account, String key) throws SQLException {
        try (var query = db.prepareStatement("SELECT 1 FROM customer_postings WHERE account_id=? AND request_key=?")) {
            query.setLong(1, account);
            query.setString(2, key);
            try (var row = query.executeQuery()) { return row.next(); }
        }
    }

    private void openDeposit(Connection db, long account, BigDecimal amount, int months) throws SQLException {
        BigDecimal interest = amount.multiply(new BigDecimal("0.05")).multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        try (var insert = db.prepareStatement("""
                INSERT INTO customer_deposits(account_id,principal,payout,maturity_date)
                VALUES (?,?,?,DATE_ADD(CURRENT_DATE, INTERVAL ? MONTH))
                """)) {
            insert.setLong(1, account);
            insert.setBigDecimal(2, amount);
            insert.setBigDecimal(3, amount.add(interest));
            insert.setInt(4, months);
            insert.executeUpdate();
        }
    }

    private BigDecimal redeem(Connection db, long account, long deposit) throws SQLException {
        try (var update = db.prepareStatement("""
                UPDATE customer_deposits SET redeemed=TRUE WHERE id=? AND account_id=?
                AND NOT redeemed AND maturity_date<=CURRENT_DATE
                """);
             var query = db.prepareStatement("SELECT payout FROM customer_deposits WHERE id=?")) {
            update.setLong(1, deposit);
            update.setLong(2, account);
            if (update.executeUpdate() != 1) return null;
            query.setLong(1, deposit);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("Missing deposit");
                return row.getBigDecimal(1);
            }
        }
    }

    private Result rollback(Connection db, Result result) throws SQLException {
        db.rollback();
        return result;
    }
}
