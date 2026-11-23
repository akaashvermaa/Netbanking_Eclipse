package com.netbanking.dao;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.dao.CustomerAccessDao.Identity;
import com.netbanking.model.LoanTerms;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CustomerRequestsDao {
    public record Application(long id, long customerId, String customer, String account, BigDecimal amount,
                              String purpose, String status, Long loanId, LocalDateTime submittedAt,
                              LocalDateTime decidedAt, LoanTerms terms) {}
    public record Ticket(long id, long customerId, String customer, String category, String details, String status,
                         LocalDateTime submittedAt, LocalDateTime resolvedAt) {}

    public List<Application> applications(Long customerId) throws SQLException {
        return applications(customerId, false);
    }

    public List<Application> applications(Long customerId, boolean completed) throws SQLException {
        String scope = customerId != null ? "u.id=?" : completed ? "l.status IN ('APPROVED','DECLINED')" : "l.status='PENDING'";
        String order = customerId != null ? " ORDER BY l.id DESC LIMIT 50" : completed
                ? " ORDER BY l.decided_at DESC,l.id DESC LIMIT 100" : " ORDER BY l.id LIMIT 100";
        try (var db = DatabaseConnection.getConnection();
             var query = db.prepareStatement("""
                     SELECT l.id, u.id, COALESCE(p.full_name,u.username), a.account_number, l.amount,
                            l.purpose, l.status, l.loan_id, l.submitted_at, l.decided_at, l.term_months, l.annual_rate
                     FROM loan_applications l JOIN bank_accounts a ON a.id=l.account_id
                     JOIN users u ON u.id=a.customer_id LEFT JOIN customer_profiles p ON p.customer_id=u.id
                     WHERE
                     """ + scope + order)) {
            if (customerId != null) query.setLong(1, customerId);
            List<Application> result = new ArrayList<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    result.add(new Application(rows.getLong(1), rows.getLong(2), rows.getString(3), rows.getString(4),
                            rows.getBigDecimal(5), rows.getString(6), rows.getString(7), rows.getObject(8, Long.class),
                            rows.getObject(9, LocalDateTime.class), rows.getObject(10, LocalDateTime.class),
                            LoanTerms.stored(rows.getObject(11, Integer.class), rows.getBigDecimal(12))));
                }
            }
            return result;
        }
    }

    public boolean apply(Identity customer, long account, BigDecimal amount, String purpose, LoanTerms terms, String key)
            throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var insert = db.prepareStatement("""
                     INSERT INTO loan_applications(account_id,amount,purpose,request_key,term_months,annual_rate)
                     SELECT a.id, ?, ?, ?, ?, ? FROM bank_accounts a JOIN users u ON u.id=a.customer_id
                     WHERE a.id=? AND a.status='ACTIVE' AND u.id=? AND u.role='CUSTOMER' AND u.status='ACTIVE'
                       AND u.credential_version=? AND NOT u.password_change_required
                     """)) {
            insert.setBigDecimal(1, amount);
            insert.setString(2, purpose);
            insert.setString(3, key);
            insert.setInt(4, terms.months());
            insert.setBigDecimal(5, terms.annualRate());
            insert.setLong(6, account);
            insert.setLong(7, customer.id());
            insert.setLong(8, customer.credentialVersion());
            return insertOnce(insert);
        }
    }

    public List<Ticket> tickets(Long customerId) throws SQLException {
        return tickets(customerId, false);
    }

    public List<Ticket> tickets(Long customerId, boolean completed) throws SQLException {
        String scope = customerId != null ? "u.id=?" : completed ? "r.status='RESOLVED'" : "r.status='OPEN'";
        String order = customerId != null ? " ORDER BY r.id DESC LIMIT 50" : completed
                ? " ORDER BY r.resolved_at DESC,r.id DESC LIMIT 100" : " ORDER BY r.id LIMIT 100";
        try (var db = DatabaseConnection.getConnection();
             var query = db.prepareStatement("""
                     SELECT r.id, u.id, COALESCE(p.full_name,u.username), r.category, r.details, r.status, r.submitted_at, r.resolved_at
                     FROM customer_service_requests r JOIN users u ON u.id=r.customer_id
                     LEFT JOIN customer_profiles p ON p.customer_id=u.id WHERE
                     """ + scope + order)) {
            if (customerId != null) query.setLong(1, customerId);
            List<Ticket> result = new ArrayList<>();
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    result.add(new Ticket(rows.getLong(1), rows.getLong(2), rows.getString(3), rows.getString(4),
                            rows.getString(5), rows.getString(6), rows.getObject(7, LocalDateTime.class),
                            rows.getObject(8, LocalDateTime.class)));
                }
            }
            return result;
        }
    }

    public boolean submitTicket(Identity customer, String category, String details, String key) throws SQLException {
        try (var db = DatabaseConnection.getConnection();
             var insert = db.prepareStatement("""
                     INSERT INTO customer_service_requests(customer_id,category,details,request_key)
                     SELECT id, ?, ?, ? FROM users WHERE id=? AND role='CUSTOMER' AND status='ACTIVE'
                        AND credential_version=? AND NOT password_change_required
                     """)) {
            insert.setString(1, category);
            insert.setString(2, details);
            insert.setString(3, key);
            insert.setLong(4, customer.id());
            insert.setLong(5, customer.credentialVersion());
            return insertOnce(insert);
        }
    }

    private boolean insertOnce(java.sql.PreparedStatement insert) throws SQLException {
        try {
            return insert.executeUpdate() == 1;
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1062) return false;
            throw exception;
        }
    }

    public boolean closeRequest(long id, long adminId, boolean loanApplication) throws SQLException {
        String select = loanApplication ? """
                SELECT a.customer_id FROM loan_applications l JOIN bank_accounts a ON a.id=l.account_id
                WHERE l.id=? AND l.status='PENDING' FOR UPDATE
                """ : """
                SELECT customer_id FROM customer_service_requests WHERE id=? AND status='OPEN' FOR UPDATE
                """;
        String update = loanApplication ? """
                UPDATE loan_applications SET status='DECLINED', decided_at=CURRENT_TIMESTAMP, decided_by=? WHERE id=?
                """ : """
                UPDATE customer_service_requests SET status='RESOLVED', resolved_at=CURRENT_TIMESTAMP WHERE id=?
                """;
        try (var db = DatabaseConnection.getConnection()) {
            db.setAutoCommit(false);
            try (var lookup = db.prepareStatement(select);
                 var decision = db.prepareStatement(update);
                 var audit = db.prepareStatement("""
                        INSERT INTO admin_audit_log(admin_user_id,action,target_user_id,details) VALUES (?,?,?,?)
                        """)) {
                long customerId;
                lookup.setLong(1, id);
                try (var row = lookup.executeQuery()) {
                    if (!row.next()) {
                        db.rollback();
                        return false;
                    }
                    customerId = row.getLong(1);
                }
                if (loanApplication) decision.setLong(1, adminId);
                decision.setLong(loanApplication ? 2 : 1, id);
                decision.executeUpdate();
                audit.setLong(1, adminId);
                audit.setString(2, loanApplication ? "LOAN_APPLICATION_DECLINED" : "SERVICE_REQUEST_RESOLVED");
                audit.setLong(3, customerId);
                audit.setString(4, (loanApplication ? "Loan application #" : "Service request #") + id);
                audit.executeUpdate();
                db.commit();
                return true;
            } catch (SQLException exception) {
                db.rollback();
                throw exception;
            }
        }
    }
}
