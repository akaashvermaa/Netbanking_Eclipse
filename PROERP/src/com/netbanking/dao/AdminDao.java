package com.netbanking.dao;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.model.AdminAuditEntry;
import com.netbanking.model.AccountChoice;
import com.netbanking.model.AdminDashboardStats;
import com.netbanking.model.AdminCredential;
import com.netbanking.model.BankAccount;
import com.netbanking.model.CustomerDetails;
import com.netbanking.model.CustomerSummary;
import com.netbanking.model.CustomerProfile;
import com.netbanking.model.KycStatus;
import com.netbanking.security.PasswordHasher;
import com.netbanking.security.PasswordHasher.PasswordHash;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public List<CustomerDetails> findCustomerDetailsList(String search, String filter) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.username, u.email, u.status, u.created_at,
                       p.full_name, p.phone, COALESCE(k.status, 'NOT_STARTED') AS kyc_status,
                       COALESCE(acc.total_balance, 0.00) AS total_balance,
                       COALESCE(acc.account_count, 0) AS account_count,
                       COALESCE(acc.frozen_count, 0) AS frozen_count,
                       COALESCE(loan.total_debt, 0.00) AS total_debt,
                       COALESCE(loan.active_loans, 0) AS active_loans
                FROM users u
                LEFT JOIN customer_profiles p ON p.customer_id = u.id
                LEFT JOIN customer_kyc k ON k.customer_id = u.id
                LEFT JOIN (
                    SELECT customer_id,
                           SUM(balance) AS total_balance,
                           COUNT(*) AS account_count,
                           SUM(status = 'FROZEN') AS frozen_count
                    FROM bank_accounts
                    GROUP BY customer_id
                ) acc ON acc.customer_id = u.id
                LEFT JOIN (
                    SELECT a.customer_id,
                           SUM(l.outstanding) AS total_debt,
                           COUNT(CASE WHEN l.outstanding > 0 THEN 1 END) AS active_loans
                    FROM bank_loans l
                    JOIN bank_accounts a ON a.id = l.account_id
                    GROUP BY a.customer_id
                ) loan ON loan.customer_id = u.id
                WHERE u.role = 'CUSTOMER'
                """);
        List<String> parameters = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (u.username LIKE ? OR u.email LIKE ? OR p.full_name LIKE ?"
                    + " OR EXISTS (SELECT 1 FROM bank_accounts match_account WHERE match_account.customer_id=u.id"
                    + " AND match_account.account_number LIKE ?))");
            String pattern = "%" + search.trim() + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if ("PENDING".equalsIgnoreCase(filter)) {
            sql.append(" AND u.status = 'PENDING'");
        } else if ("ACTIVE".equalsIgnoreCase(filter)) {
            sql.append(" AND u.status = 'ACTIVE'");
        } else if ("SUSPENDED".equalsIgnoreCase(filter)) {
            sql.append(" AND u.status = 'SUSPENDED'");
        } else if ("WITH_LOANS".equalsIgnoreCase(filter)) {
            sql.append(" AND COALESCE(loan.active_loans, 0) > 0");
        } else if ("FROZEN".equalsIgnoreCase(filter)) {
            sql.append(" AND COALESCE(acc.frozen_count, 0) > 0");
        } else if ("NO_ACCOUNTS".equalsIgnoreCase(filter)) {
            sql.append(" AND COALESCE(acc.account_count, 0) = 0");
        }

        sql.append(" ORDER BY CASE u.status WHEN 'PENDING' THEN 0 WHEN 'ACTIVE' THEN 1 ELSE 2 END,"
                + " CASE WHEN u.status='PENDING' THEN u.created_at END ASC, total_debt DESC, u.created_at DESC, u.id ASC LIMIT 200");

        List<CustomerDetails> list = new ArrayList<>();
        List<Long> customerIds = new ArrayList<>();

        record TempRow(long id, String username, String email, String status, LocalDateTime createdAt,
                       String fullName, String phone, BigDecimal totalBalance, BigDecimal totalDebt,
                       long activeLoans, boolean hasFrozen, KycStatus kycStatus) {}
        List<TempRow> rows = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setString(i + 1, parameters.get(i));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long id = result.getLong("id");
                    customerIds.add(id);
                    Timestamp created = result.getTimestamp("created_at");
                    rows.add(new TempRow(
                            id,
                            result.getString("username"),
                            result.getString("email"),
                            result.getString("status"),
                            created == null ? null : created.toLocalDateTime(),
                            result.getString("full_name"),
                            result.getString("phone"),
                            result.getBigDecimal("total_balance"),
                            result.getBigDecimal("total_debt"),
                            result.getLong("active_loans"),
                            result.getLong("frozen_count") > 0,
                            KycStatus.valueOf(result.getString("kyc_status"))
                    ));
                }
            }
        }

        Map<Long, List<BankAccount>> accountsByCustomer = new HashMap<>();
        if (!customerIds.isEmpty()) {
            StringBuilder accSql = new StringBuilder("""
                    SELECT a.id, a.customer_id, u.username, a.account_number, a.account_type, a.status, a.balance, a.opened_at
                    FROM bank_accounts a JOIN users u ON u.id = a.customer_id
                    WHERE a.customer_id IN (
                    """);
            for (int i = 0; i < customerIds.size(); i++) {
                accSql.append(i == 0 ? "?" : ", ?");
            }
            accSql.append(") ORDER BY a.id ASC");

            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(accSql.toString())) {
                for (int i = 0; i < customerIds.size(); i++) {
                    statement.setLong(i + 1, customerIds.get(i));
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        long customerId = result.getLong("customer_id");
                        Timestamp opened = result.getTimestamp("opened_at");
                        BankAccount acc = new BankAccount(
                                result.getLong("id"),
                                customerId,
                                result.getString("username"),
                                result.getString("account_number"),
                                result.getString("account_type"),
                                result.getString("status"),
                                result.getBigDecimal("balance"),
                                opened == null ? null : opened.toLocalDateTime()
                        );
                        accountsByCustomer.computeIfAbsent(customerId, k -> new ArrayList<>()).add(acc);
                    }
                }
            }
        }

        for (TempRow row : rows) {
            list.add(new CustomerDetails(
                    row.id,
                    row.username,
                    row.email,
                    row.status,
                    row.createdAt,
                    row.fullName,
                    row.phone,
                    accountsByCustomer.getOrDefault(row.id, List.of()),
                    row.totalBalance,
                    row.totalDebt,
                    row.activeLoans,
                    row.hasFrozen,
                    row.kycStatus
            ));
        }
        return list;
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

    public long addCustomer(String username, String email, CustomerProfile profile, String status, long adminId)
            throws SQLException {
        return addCustomer(username, email, profile, status, adminId, null, KycStatus.NOT_STARTED, new char[0]);
    }

    public long addCustomer(String username, String email, CustomerProfile profile, String status, long adminId,
                            AccountChoice accounts, KycStatus kyc, char[] temporaryPassword) throws SQLException {
        boolean temporary = temporaryPassword.length > 0;
        var hash = PasswordHasher.create(temporary ? temporaryPassword : java.util.UUID.randomUUID().toString().toCharArray());
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long id = CustomerAccessDao.insertCustomer(connection, username, email, profile, status, hash, accounts,
                        temporary);
                if (id == 0) {
                    connection.rollback();
                    return 0;
                }
                if (kyc != KycStatus.NOT_STARTED) {
                    try (var verification = connection.prepareStatement(
                            "INSERT INTO customer_kyc(customer_id,status) VALUES (?,?)")) {
                        verification.setLong(1, id);
                        verification.setString(2, kyc.name());
                        verification.executeUpdate();
                    }
                }
                try (PreparedStatement audit = connection.prepareStatement("""
                        INSERT INTO admin_audit_log(admin_user_id,action,target_user_id,details)
                        VALUES (?,'CUSTOMER_CREATED',?,?)
                        """)) {
                    audit.setLong(1, adminId);
                    audit.setLong(2, id);
                    audit.setString(3, "Customer added with " + status + " access"
                            + (accounts == null ? "" : " | " + accounts.label()) + " | KYC: " + kyc.label()
                            + (temporary ? " | Temporary password set" : ""));
                    audit.executeUpdate();
                }
                if ("ACTIVE".equals(status) && accounts != null) {
                    for (String type : accounts.types()) BankAccountDao.openIfMissing(connection, id, adminId, type, "");
                }
                connection.commit();
                return id;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public Optional<CustomerProfile> findCustomerProfile(long id) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT full_name,phone,address_line,city,region,postal_code FROM customer_profiles WHERE customer_id=?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new CustomerProfile(result.getString(1), result.getString(2),
                        result.getString(3), result.getString(4), result.getString(5), result.getString(6))) : Optional.empty();
            }
        }
    }

    public KycStatus findKycStatus(long customerId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT status FROM customer_kyc WHERE customer_id=?")) {
            statement.setLong(1, customerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? KycStatus.valueOf(result.getString(1)) : KycStatus.NOT_STARTED;
            }
        }
    }

    public AccountChoice findRequestedAccounts(long customerId) throws SQLException {
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.prepareStatement("SELECT requested_accounts FROM users WHERE id=? AND role='CUSTOMER'")) {
            statement.setLong(1, customerId);
            try (var result = statement.executeQuery()) {
                return result.next() ? AccountChoice.from(result.getString(1)) : null;
            }
        }
    }

    public boolean updateCustomerProfile(long id, long adminId, CustomerProfile profile, String reason)
            throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT 1 FROM users WHERE id=? AND role='CUSTOMER' FOR UPDATE")) {
                    check.setLong(1, id);
                    try (ResultSet result = check.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        INSERT INTO customer_profiles (customer_id, full_name, phone, address_line, city, region, postal_code)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone),
                            address_line=VALUES(address_line), city=VALUES(city), region=VALUES(region), postal_code=VALUES(postal_code)
                        """)) {
                    update.setLong(1, id);
                    update.setString(2, profile.fullName());
                    update.setString(3, profile.phone());
                    update.setString(4, profile.addressLine());
                    update.setString(5, profile.city());
                    update.setString(6, profile.region());
                    update.setString(7, profile.postalCode());
                    update.executeUpdate();
                }
                try (PreparedStatement audit = connection.prepareStatement("""
                        INSERT INTO admin_audit_log (admin_user_id, action, target_user_id, details)
                        VALUES (?, 'CUSTOMER_PROFILE_UPDATED', ?, ?)
                        """)) {
                    audit.setLong(1, adminId);
                    audit.setLong(2, id);
                    audit.setString(3, "Customer profile saved"
                            + (reason == null || reason.isBlank() ? "" : " | Note: " + reason.strip()));
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

    public boolean changeAdminPassword(long adminId, char[] currentPassword, char[] newPassword) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String hashHex;
                String saltHex;
                int iterations;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT password_hash, password_salt, password_iterations FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE' FOR UPDATE")) {
                    select.setLong(1, adminId);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                        hashHex = result.getString(1);
                        saltHex = result.getString(2);
                        iterations = result.getInt(3);
                    }
                }
                if (!PasswordHasher.verify(currentPassword, hashHex, saltHex, iterations)) {
                    connection.rollback();
                    return false;
                }
                PasswordHash newHash = PasswordHasher.create(newPassword);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE users SET password_hash=?, password_salt=?, password_iterations=? WHERE id=?")) {
                    update.setString(1, newHash.hashHex());
                    update.setString(2, newHash.saltHex());
                    update.setInt(3, newHash.iterations());
                    update.setLong(4, adminId);
                    update.executeUpdate();
                }
                try (PreparedStatement audit = connection.prepareStatement("""
                        INSERT INTO admin_audit_log (admin_user_id, action, target_user_id, details)
                        VALUES (?, 'ADMIN_PASSWORD_CHANGED', ?, ?)
                        """)) {
                    audit.setLong(1, adminId);
                    audit.setLong(2, adminId);
                    audit.setString(3, "Administrator password changed");
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
                    audit.setString(3, reason.isBlank() ? "Email corrected" : "Email corrected | Note: " + reason);
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

    public boolean changeCustomerStatus(long customerId, long adminId, String newStatus, String expectedStatus,
                                        String reason, AccountChoice approvedAccounts) throws SQLException {
        if (reason != null && reason.length() > 140) return false;
        if (!List.of("ACTIVE", "SUSPENDED").contains(newStatus)) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String currentStatus;
                AccountChoice requestedAccounts;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT status, requested_accounts FROM users WHERE id = ? AND role = 'CUSTOMER' FOR UPDATE")) {
                    select.setLong(1, customerId);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                        currentStatus = result.getString("status");
                        requestedAccounts = AccountChoice.from(result.getString("requested_accounts"));
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
                        "UPDATE users SET status = ?, credential_version=credential_version+1 WHERE id = ? AND role = 'CUSTOMER'")) {
                    update.setString(1, newStatus);
                    update.setLong(2, customerId);
                    update.executeUpdate();
                }

                String action = "ACTIVE".equals(newStatus)
                        ? ("PENDING".equals(currentStatus) ? "CUSTOMER_APPROVED" : "CUSTOMER_REACTIVATED")
                        : "CUSTOMER_SUSPENDED";
                String accountDecision = "";
                if ("CUSTOMER_APPROVED".equals(action)) {
                    AccountChoice choice = approvedAccounts != null ? approvedAccounts
                            : requestedAccounts != null ? requestedAccounts : AccountChoice.NONE;
                    for (String type : choice.types()) {
                        BankAccountDao.openIfMissing(connection, customerId, adminId, type, "");
                    }
                    accountDecision = " | Account decision: " + choice.label();
                }
                try (PreparedStatement audit = connection.prepareStatement(
                        "INSERT INTO admin_audit_log (admin_user_id, action, target_user_id, details) VALUES (?, ?, ?, ?)")) {
                    audit.setLong(1, adminId);
                    audit.setString(2, action);
                    audit.setLong(3, customerId);
                    audit.setString(4, currentStatus + " to " + newStatus + accountDecision
                            + (reason == null || reason.isBlank() ? "" : " | Note: " + reason.strip()));
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
        return findAuditEntries(limit, null, "", "");
    }

    public List<AdminAuditEntry> findRecentAuditEntries(int limit, String group) throws SQLException {
        return findAuditEntries(limit, null, group, "");
    }

    public List<AdminAuditEntry> findRecentAuditEntries(int limit, String group, String search) throws SQLException {
        return findAuditEntries(limit, null, group, search);
    }

    public List<AdminAuditEntry> findCustomerAuditEntries(long customerId) throws SQLException {
        return findAuditEntries(100, customerId, "", "");
    }

    private List<AdminAuditEntry> findAuditEntries(int limit, Long customerId, String group, String search) throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        StringBuilder sql = new StringBuilder("SELECT a.action, a.details, a.created_at, admin.username AS admin_username, "
                + "target.username AS target_username, CASE WHEN target.role='CUSTOMER' THEN target.id END AS customer_id FROM admin_audit_log a "
                + "JOIN users admin ON admin.id = a.admin_user_id "
                + "LEFT JOIN users target ON target.id = a.target_user_id "
                + "WHERE 1=1 ");
        List<Object> parameters = new ArrayList<>();
        if (customerId != null) {
            sql.append("AND a.target_user_id = ? ");
            parameters.add(customerId);
        }
        if ("REQUEST_".equals(group)) {
            sql.append("AND a.action IN ('LOAN_APPLICATION_APPROVED','LOAN_APPLICATION_DECLINED','SERVICE_REQUEST_RESOLVED') ");
        } else if (group != null && !group.isEmpty()) {
            sql.append("AND LEFT(a.action, ?) = ? ");
            parameters.add(group.length());
            parameters.add(group);
        }
        if (search != null && !search.isBlank()) {
            sql.append("AND (LOCATE(?,admin.username)>0 OR LOCATE(?,target.username)>0"
                    + " OR LOCATE(?,a.details)>0 OR LOCATE(?,a.action)>0) ");
            for (int i = 0; i < 4; i++) parameters.add(search.strip());
        }
        sql.append("ORDER BY a.created_at DESC, a.id DESC LIMIT ?");
        parameters.add(safeLimit);

        List<AdminAuditEntry> entries = new ArrayList<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                Object p = parameters.get(i);
                if (p instanceof Long l) statement.setLong(i + 1, l);
                else if (p instanceof Integer n) statement.setInt(i + 1, n);
                else if (p instanceof String s) statement.setString(i + 1, s);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp created = result.getTimestamp("created_at");
                    LocalDateTime createdAt = created == null ? null : created.toLocalDateTime();
                    entries.add(new AdminAuditEntry(result.getString("admin_username"), result.getString("action"),
                            result.getString("target_username"), result.getString("details"), createdAt,
                            result.getObject("customer_id", Long.class)));
                }
            }
        }
        return entries;
    }
}
