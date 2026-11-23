package com.netbanking.dao;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.model.LoanTerms;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class FinanceDao {
    public enum Operation { DEPOSIT, LOAN_DISBURSEMENT, LOAN_REPAYMENT, ADJUSTMENT_CREDIT, ADJUSTMENT_DEBIT }
    public enum Outcome { POSTED, DUPLICATE, UNAVAILABLE, INSUFFICIENT, INVALID_AMOUNT, STALE_BALANCE, UNCHANGED }
    public record Totals(BigDecimal balances, BigDecimal deposits, BigDecimal lent, BigDecimal outstanding, long openLoans) {}
    public record Loan(long id, long accountId, String customer, String account, BigDecimal principal,
                       BigDecimal outstanding, LocalDateTime date, boolean canRepay, LoanTerms terms, BigDecimal interestDue) {
        public BigDecimal totalDue() { return outstanding.add(interestDue); }
        public LocalDate firstPaymentDate() { return date.toLocalDate().plusMonths(1); }
        public LocalDate maturityDate() { return date.toLocalDate().plusMonths(terms.months()); }
    }
    public record DailyFlow(LocalDate date, BigDecimal deposits, BigDecimal payouts, BigDecimal repayments) {}
    public record LendingTotals(BigDecimal lent, BigDecimal outstanding) {}

    public LendingTotals lendingTotals(Long accountId, Long customerId) throws SQLException {
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT COALESCE(SUM(l.principal),0.00), COALESCE(SUM(l.outstanding),0.00)
                     FROM bank_loans l JOIN bank_accounts a ON a.id=l.account_id
                     WHERE (? IS NULL OR a.id=?) AND (? IS NULL OR a.customer_id=?)
                     """)) {
            bindScope(statement, accountId, customerId);
            try (var row = statement.executeQuery()) {
                row.next();
                return new LendingTotals(row.getBigDecimal(1), row.getBigDecimal(2));
            }
        }
    }

    private void bindScope(java.sql.PreparedStatement statement, Long accountId, Long customerId) throws SQLException {
        statement.setObject(1, accountId, java.sql.Types.BIGINT);
        statement.setObject(2, accountId, java.sql.Types.BIGINT);
        statement.setObject(3, customerId, java.sql.Types.BIGINT);
        statement.setObject(4, customerId, java.sql.Types.BIGINT);
    }

    public List<DailyFlow> recentFlow() throws SQLException {
        var flows = new java.util.HashMap<LocalDate, DailyFlow>();
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT DATE(posted_at), SUM(IF(kind='DEPOSIT',amount,0)),
                            SUM(IF(kind='LOAN_DISBURSEMENT',amount,0)), SUM(IF(kind='LOAN_REPAYMENT',amount,0))
                     FROM (
                         SELECT kind, amount, posted_at FROM account_movements
                         WHERE posted_at >= CURRENT_DATE - INTERVAL 6 DAY AND posted_at < CURRENT_DATE + INTERVAL 1 DAY
                         UNION ALL
                         SELECT 'LOAN_REPAYMENT', amount, posted_at FROM customer_postings WHERE kind='CUSTOMER_REPAYMENT'
                           AND posted_at >= CURRENT_DATE - INTERVAL 6 DAY AND posted_at < CURRENT_DATE + INTERVAL 1 DAY
                     ) AS financial_activity
                     GROUP BY DATE(posted_at)
                     """)) {
            while (rows.next()) {
                LocalDate date = rows.getDate(1).toLocalDate();
                flows.put(date, new DailyFlow(date, rows.getBigDecimal(2), rows.getBigDecimal(3), rows.getBigDecimal(4)));
            }
            try (var today = statement.executeQuery("SELECT CURRENT_DATE")) {
                today.next();
                LocalDate end = today.getDate(1).toLocalDate();
                List<DailyFlow> week = new ArrayList<>(7);
                for (int days = 6; days >= 0; days--) {
                    LocalDate date = end.minusDays(days);
                    week.add(flows.getOrDefault(date, new DailyFlow(date, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
                }
                return week;
            }
        }
    }

    public Totals totals() throws SQLException {
        String sql = """
                SELECT (SELECT COALESCE(SUM(balance),0.00) FROM bank_accounts),
                       (SELECT COALESCE(SUM(amount),0.00) FROM account_movements WHERE kind='DEPOSIT'),
                       COALESCE(SUM(principal),0.00), COALESCE(SUM(outstanding),0.00),
                       COUNT(CASE WHEN outstanding>0 THEN 1 END)
                FROM bank_loans
                """;
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return new Totals(result.getBigDecimal(1), result.getBigDecimal(2), result.getBigDecimal(3),
                    result.getBigDecimal(4), result.getLong(5));
        }
    }

    public List<Loan> loans(Long accountId) throws SQLException {
        return loans(accountId, "");
    }

    public List<Loan> loans(Long accountId, String status) throws SQLException {
        return loans(accountId, status, null);
    }

    public List<Loan> loans(Long accountId, String status, Long customerId) throws SQLException {
        List<Loan> loans = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT l.id,a.id,COALESCE(p.full_name,u.username),a.account_number,l.principal,l.outstanding,l.disbursed_at,
                       a.status='ACTIVE' AND u.status='ACTIVE',l.term_months,l.annual_rate,l.interest_due
                FROM bank_loans l JOIN bank_accounts a ON a.id=l.account_id JOIN users u ON u.id=a.customer_id
                LEFT JOIN customer_profiles p ON p.customer_id=u.id
                WHERE (? IS NULL OR a.id=?) AND (? IS NULL OR a.customer_id=?)
                """);
        if ("OPEN".equalsIgnoreCase(status)) {
            sql.append(" AND l.outstanding > 0 ");
        } else if ("PAID".equalsIgnoreCase(status)) {
            sql.append(" AND l.outstanding = 0 ");
        }
        sql.append(" ORDER BY l.id DESC LIMIT 100");
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.prepareStatement(sql.toString())) {
            bindScope(statement, accountId, customerId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    loans.add(readLoan(rows));
                }
            }
        }
        return loans;
    }

    public List<Loan> loansByCustomer(long customerId) throws SQLException {
        List<Loan> loans = new ArrayList<>();
        String sql = """
                SELECT l.id,a.id,COALESCE(p.full_name,u.username),a.account_number,l.principal,l.outstanding,l.disbursed_at,
                       a.status='ACTIVE' AND u.status='ACTIVE',l.term_months,l.annual_rate,l.interest_due
                FROM bank_loans l JOIN bank_accounts a ON a.id=l.account_id JOIN users u ON u.id=a.customer_id
                LEFT JOIN customer_profiles p ON p.customer_id=u.id
                WHERE u.id=? ORDER BY l.id DESC LIMIT 50
                """;
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, customerId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    loans.add(readLoan(rows));
                }
            }
        }
        return loans;
    }

    private Loan readLoan(ResultSet rows) throws SQLException {
        return new Loan(rows.getLong(1), rows.getLong(2), rows.getString(3), rows.getString(4),
                rows.getBigDecimal(5), rows.getBigDecimal(6), rows.getTimestamp(7).toLocalDateTime(), rows.getBoolean(8),
                LoanTerms.stored(rows.getObject(9, Integer.class), rows.getBigDecimal(10)), rows.getBigDecimal(11));
    }

    public Outcome post(Operation action, long accountId, Long loanId, long adminId, BigDecimal amount, String requestKey)
            throws SQLException {
        return post(action, accountId, loanId, adminId, amount, requestKey, false);
    }

    public Outcome post(Operation action, long accountId, Long loanId, long adminId, BigDecimal amount, String requestKey,
                        boolean settle) throws SQLException {
        return post(action, accountId, loanId, adminId, amount, requestKey, settle, null, null, null, null);
    }

    /** Used by the fictional portfolio setup to place its own transactions on past days. */
    public Outcome postSample(Operation action, long accountId, Long loanId, long adminId, BigDecimal amount,
                              String requestKey, LocalDateTime postedAt) throws SQLException {
        return post(action, accountId, loanId, adminId, amount, requestKey, false, java.sql.Timestamp.valueOf(postedAt), null, null, null);
    }

    public Outcome adjustBalance(long accountId, long adminId, BigDecimal expectedBalance, BigDecimal newBalance,
                                 String requestKey) throws SQLException {
        if (expectedBalance == null || newBalance == null || newBalance.signum() < 0
                || newBalance.scale() > 2 || newBalance.precision() - newBalance.scale() > 17) {
            return Outcome.INVALID_AMOUNT;
        }
        return post(Operation.ADJUSTMENT_CREDIT, accountId, null, adminId, newBalance, requestKey, false, null, expectedBalance, null, null);
    }

    public Outcome approveApplication(long applicationId, long adminId) throws SQLException {
        return post(Operation.LOAN_DISBURSEMENT, 0, null, adminId, null, java.util.UUID.randomUUID().toString(),
                false, null, null, applicationId, null);
    }

    public Outcome disburse(long accountId, long adminId, BigDecimal amount, String requestKey, LoanTerms terms)
            throws SQLException {
        return post(Operation.LOAN_DISBURSEMENT, accountId, null, adminId, amount, requestKey,
                false, null, null, null, terms);
    }

    private Outcome post(Operation action, long accountId, Long loanId, long adminId, BigDecimal amount, String requestKey,
                         boolean settle, java.sql.Timestamp sampleTime, BigDecimal expectedBalance, Long applicationId,
                         LoanTerms terms) throws SQLException {
        int integerDigits = action == Operation.LOAN_REPAYMENT ? 17 : 9;
        if (applicationId == null && expectedBalance == null && (action == Operation.ADJUSTMENT_CREDIT || action == Operation.ADJUSTMENT_DEBIT
                || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > integerDigits)) {
            return Outcome.INVALID_AMOUNT;
        }
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (applicationId != null) {
                    try (var application = connection.prepareStatement("""
                            SELECT account_id, amount, term_months, annual_rate FROM loan_applications
                            WHERE id=? AND status='PENDING' FOR UPDATE
                            """)) {
                        application.setLong(1, applicationId);
                        try (var row = application.executeQuery()) {
                            if (!row.next()) return rollback(connection, Outcome.UNAVAILABLE);
                            accountId = row.getLong(1);
                            amount = row.getBigDecimal(2);
                            terms = LoanTerms.stored(row.getObject(3, Integer.class), row.getBigDecimal(4));
                        }
                    }
                }
                long customerId;
                String number;
                BigDecimal balance;
                // Serialize all balance changes against the target account.
                try (var account = connection.prepareStatement("""
                        SELECT a.customer_id,a.account_number,a.balance FROM bank_accounts a
                        JOIN users u ON a.customer_id=u.id
                        WHERE a.id=? AND a.status='ACTIVE' AND u.status='ACTIVE' AND u.role='CUSTOMER' FOR UPDATE
                        """)) {
                    account.setLong(1, accountId);
                    try (var row = account.executeQuery()) {
                        if (!row.next()) return rollback(connection, Outcome.UNAVAILABLE);
                        customerId = row.getLong(1);
                        number = row.getString(2);
                        balance = row.getBigDecimal(3);
                    }
                }
                try (var duplicate = connection.prepareStatement("SELECT 1 FROM account_movements WHERE request_key=?")) {
                    duplicate.setString(1, requestKey);
                    try (var row = duplicate.executeQuery()) {
                        if (row.next()) return rollback(connection, Outcome.DUPLICATE);
                    }
                }
                BigDecimal nextBalance = balance.add(amount);
                if (expectedBalance != null) {
                    if (balance.compareTo(expectedBalance) != 0) return rollback(connection, Outcome.STALE_BALANCE);
                    nextBalance = amount;
                    BigDecimal difference = nextBalance.subtract(balance);
                    if (difference.signum() == 0) return rollback(connection, Outcome.UNCHANGED);
                    action = difference.signum() > 0 ? Operation.ADJUSTMENT_CREDIT : Operation.ADJUSTMENT_DEBIT;
                    amount = difference.abs();
                } else if (action == Operation.LOAN_REPAYMENT) {
                    nextBalance = balance.subtract(amount);
                    if (nextBalance.signum() < 0) return rollback(connection, Outcome.INSUFFICIENT);
                    if (loanId == null) return rollback(connection, Outcome.UNAVAILABLE);
                    if (!repay(connection, accountId, loanId, amount, settle)) return rollback(connection, Outcome.INVALID_AMOUNT);
                } else if (action == Operation.LOAN_DISBURSEMENT) {
                    try (var loan = connection.prepareStatement("""
                            INSERT INTO bank_loans(account_id,principal,outstanding,disbursed_at,term_months,annual_rate,interest_due)
                            VALUES (?,?,?,COALESCE(?,CURRENT_TIMESTAMP),?,?,?)
                            """, Statement.RETURN_GENERATED_KEYS)) {
                        loan.setLong(1, accountId);
                        loan.setBigDecimal(2, amount);
                        loan.setBigDecimal(3, amount);
                        loan.setTimestamp(4, sampleTime);
                        loan.setObject(5, terms == null ? null : terms.months(), java.sql.Types.SMALLINT);
                        loan.setBigDecimal(6, terms == null ? null : terms.annualRate());
                        loan.setBigDecimal(7, terms == null ? BigDecimal.ZERO : terms.interest(amount));
                        loan.executeUpdate();
                        try (var keys = loan.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Missing loan ID");
                            loanId = keys.getLong(1);
                        }
                    }
                }
                if (nextBalance.precision() - nextBalance.scale() > 17) return rollback(connection, Outcome.INVALID_AMOUNT);
                try (var update = connection.prepareStatement("UPDATE bank_accounts SET balance=? WHERE id=?");
                     var movement = connection.prepareStatement("""
                             INSERT INTO account_movements(account_id,admin_id,loan_id,kind,amount,balance_after,request_key,posted_at)
                             VALUES (?,?,?,?,?,?,?,COALESCE(?,CURRENT_TIMESTAMP))
                             """);
                     var audit = connection.prepareStatement("""
                             INSERT INTO admin_audit_log(admin_user_id,action,target_user_id,details) VALUES (?,?,?,?)
                             """)) {
                    update.setBigDecimal(1, nextBalance);
                    update.setLong(2, accountId);
                    update.executeUpdate();
                    movement.setLong(1, accountId);
                    movement.setLong(2, adminId);
                    movement.setObject(3, loanId, java.sql.Types.BIGINT);
                    movement.setString(4, action.name());
                    movement.setBigDecimal(5, amount);
                    movement.setBigDecimal(6, nextBalance);
                    movement.setString(7, requestKey);
                    movement.setTimestamp(8, sampleTime);
                    try {
                        movement.executeUpdate();
                    } catch (SQLException exception) {
                        if (exception.getErrorCode() != 1062) throw exception;
                        return rollback(connection, Outcome.DUPLICATE);
                    }
                    audit.setLong(1, adminId);
                    audit.setString(2, applicationId == null ? "ACCOUNT_" + action.name() : "LOAN_APPLICATION_APPROVED");
                    audit.setLong(3, customerId);
                    audit.setString(4, "Account " + number + " | INR " + amount.toPlainString()
                            + " | Balance " + balance.toPlainString() + " to " + nextBalance.toPlainString()
                            + (loanId == null ? "" : " | Loan #" + loanId)
                            + (applicationId == null ? "" : " | Application #" + applicationId));
                    audit.executeUpdate();
                }
                if (applicationId != null) {
                    try (var decision = connection.prepareStatement("""
                            UPDATE loan_applications SET status='APPROVED', decided_by=?, decided_at=CURRENT_TIMESTAMP,
                                loan_id=? WHERE id=?
                            """)) {
                        decision.setLong(1, adminId);
                        decision.setLong(2, loanId);
                        decision.setLong(3, applicationId);
                        decision.executeUpdate();
                    }
                }
                connection.commit();
                return Outcome.POSTED;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    static boolean repay(Connection connection, long accountId, long loanId, BigDecimal amount, boolean settle)
            throws SQLException {
        BigDecimal principalDue;
        BigDecimal interestDue;
        try (var query = connection.prepareStatement(
                "SELECT outstanding,interest_due FROM bank_loans WHERE id=? AND account_id=? FOR UPDATE")) {
            query.setLong(1, loanId);
            query.setLong(2, accountId);
            try (var row = query.executeQuery()) {
                if (!row.next()) return false;
                principalDue = row.getBigDecimal(1);
                interestDue = row.getBigDecimal(2);
            }
        }
        int comparison = amount.compareTo(principalDue.add(interestDue));
        if (comparison > 0 || (settle && comparison != 0)) return false;
        BigDecimal appliedInterest = amount.min(interestDue);
        BigDecimal appliedPrincipal = amount.subtract(appliedInterest);
        try (var update = connection.prepareStatement("UPDATE bank_loans SET outstanding=?,interest_due=? WHERE id=?")) {
            update.setBigDecimal(1, principalDue.subtract(appliedPrincipal));
            update.setBigDecimal(2, interestDue.subtract(appliedInterest));
            update.setLong(3, loanId);
            return update.executeUpdate() == 1;
        }
    }

    private Outcome rollback(Connection connection, Outcome outcome) throws SQLException {
        connection.rollback();
        return outcome;
    }
}
