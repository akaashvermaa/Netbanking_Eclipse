package com.netbanking.setup;

import com.netbanking.config.DatabaseConnection;
import com.netbanking.dao.AccountReviewDao;
import com.netbanking.dao.AdminDao;
import com.netbanking.dao.BankAccountDao;
import com.netbanking.dao.FinanceDao;
import com.netbanking.dao.FinanceDao.Operation;
import com.netbanking.dao.FinanceDao.Outcome;
import com.netbanking.model.KycStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Creates a fictional project portfolio. Emails under example.test are reserved for the fixtures below. */
public final class SamplePortfolioSetup {
    private record Fixture(String name, String type, String status, KycStatus kyc, long deposit,
                           int daysAgo, long loan, long repaid) {
        String username() { return name.toLowerCase(Locale.ROOT).replace(' ', '.'); }
        String email() { return username() + "@example.test"; }
    }

    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("Rahul Kumar", "SAVINGS", "ACTIVE", KycStatus.VERIFIED, 185000, 6, 80000, 15000),
            new Fixture("Ananya Iyer", "SAVINGS", "ACTIVE", KycStatus.VERIFIED, 69000, 5, 24000, 0),
            new Fixture("Vikram Reddy", "CURRENT", "ACTIVE", KycStatus.VERIFIED, 315000, 4, 120000, 30000),
            new Fixture("Priya Nair", "SAVINGS", "ACTIVE", KycStatus.VERIFIED, 94500, 3, 0, 0),
            new Fixture("Arjun Malhotra", "CURRENT", "ACTIVE", KycStatus.VERIFIED, 148000, 2, 0, 0),
            new Fixture("Farah Khan", "SAVINGS", "ACTIVE", KycStatus.VERIFIED, 38500, 1, 0, 0),
            new Fixture("Sanjay Mehta", "SAVINGS", "ACTIVE", KycStatus.PENDING, 56200, 0, 0, 0),
            new Fixture("Sneha Rao", "SAVINGS", "PENDING", KycStatus.PENDING, 0, 0, 0, 0),
            new Fixture("Karan Patel", "SAVINGS", "PENDING", KycStatus.NOT_STARTED, 0, 0, 0, 0)
    );

    private final AdminDao customers = new AdminDao();
    private final BankAccountDao accounts = new BankAccountDao();
    private final FinanceDao finance = new FinanceDao();

    public static void main(String[] args) throws SQLException {
        new SamplePortfolioSetup().seed();
    }

    private void seed() throws SQLException {
        long adminId;
        LocalDate today;
        try (var connection = DatabaseConnection.getConnection();
             var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT id,CURRENT_DATE FROM users WHERE role='ADMIN' AND status='ACTIVE' ORDER BY id LIMIT 1")) {
            if (!row.next()) throw new SQLException("Add an active administrator before seeding the portfolio.");
            adminId = row.getLong(1);
            today = row.getDate(2).toLocalDate();
        }
        enrichPreviousSamples();
        int added = 0;
        int funded = 0;
        for (int index = 0; index < FIXTURES.size(); index++) {
            Fixture fixture = FIXTURES.get(index);
            long id = fixtureId(fixture);
            boolean newCustomer = id == 0;
            if (newCustomer) {
                id = customers.addCustomer(fixture.username(), fixture.email(), SampleContact.profile(fixture.name(), index + 2),
                        fixture.status(), adminId);
                if (id == 0) throw new SQLException("Sample customer conflicts with an existing record: " + fixture.name());
                added++;
                try (var connection = DatabaseConnection.getConnection();
                     var update = connection.prepareStatement("UPDATE users SET created_at=? WHERE id=?")) {
                    update.setTimestamp(1, Timestamp.valueOf(today.minusDays(fixture.daysAgo() + 45L).atTime(10, 0)));
                    update.setLong(2, id);
                    update.executeUpdate();
                }
            }
            try (var connection = DatabaseConnection.getConnection()) {
                insertKyc(connection, id, fixture.kyc());
            }
            if (fixture.deposit() == 0) continue;
            var account = accounts.list(id, "", "").stream().filter(item -> fixture.type().equals(item.type())).findFirst();
            long accountId;
            if (account.isPresent()) {
                accountId = account.get().id();
                // Resume only the original portfolio account. An unrelated existing balance is left alone.
                if (movementId(key(fixture, Operation.DEPOSIT), accountId) == 0 && !newCustomer) continue;
            } else {
                if (!newCustomer) continue;
                accountId = accounts.open(id, adminId, fixture.type(), null);
                if (accountId <= 0) throw new SQLException("Could not open the portfolio account for " + fixture.name());
                try (var connection = DatabaseConnection.getConnection();
                     var update = connection.prepareStatement("UPDATE bank_accounts SET opened_at=? WHERE id=?")) {
                    update.setTimestamp(1, Timestamp.valueOf(today.minusDays(fixture.daysAgo() + 1L).atTime(11, 0)));
                    update.setLong(2, accountId);
                    update.executeUpdate();
                }
            }
            LocalDateTime depositAt = today.minusDays(fixture.daysAgo()).atTime(9, 30);
            post(fixture, Operation.DEPOSIT, accountId, null, adminId, fixture.deposit(), depositAt);
            funded++;
            if (fixture.loan() > 0) {
                post(fixture, Operation.LOAN_DISBURSEMENT, accountId, null, adminId, fixture.loan(), depositAt.plusHours(2));
                if (fixture.repaid() > 0) {
                    long loanId = movementId(key(fixture, Operation.LOAN_DISBURSEMENT), accountId);
                    if (loanId <= 0) throw new SQLException("Expected a posted portfolio loan.");
                    post(fixture, Operation.LOAN_REPAYMENT, accountId, loanId, adminId, fixture.repaid(), depositAt.plusDays(1));
                }
            }
            if (newCustomer && "Sanjay Mehta".equals(fixture.name())) {
                accounts.setFrozen(accountId, adminId, true, "Account balance review");
            } else if (newCustomer && "Ananya Iyer".equals(fixture.name())) {
                new AccountReviewDao().changeFlag(accountId, adminId, AccountReviewDao.Category.LOAN_FOLLOW_UP);
            }
        }
        System.out.printf("Added %d customers and verified portfolio postings for %d funded accounts.%n", added, funded);
    }

    private long fixtureId(Fixture fixture) throws SQLException {
        try (var connection = DatabaseConnection.getConnection();
             var query = connection.prepareStatement("SELECT id FROM users WHERE username=? AND email=? AND role='CUSTOMER'")) {
            query.setString(1, fixture.username());
            query.setString(2, fixture.email());
            try (var row = query.executeQuery()) { return row.next() ? row.getLong(1) : 0; }
        }
    }

    private void post(Fixture fixture, Operation operation, long accountId, Long loanId, long adminId,
                      long amount, LocalDateTime when) throws SQLException {
        String key = key(fixture, operation);
        if (movementId(key, accountId) != 0) return;
        Outcome outcome = finance.postSample(operation, accountId, loanId, adminId, BigDecimal.valueOf(amount), key, when);
        if (outcome != Outcome.POSTED && outcome != Outcome.DUPLICATE) {
            throw new SQLException("Could not post " + operation + " for " + fixture.name() + ": " + outcome);
        }
    }

    private long movementId(String key, long accountId) throws SQLException {
        try (var connection = DatabaseConnection.getConnection();
             var query = connection.prepareStatement("SELECT COALESCE(loan_id,id) FROM account_movements WHERE request_key=? AND account_id=?")) {
            query.setString(1, key);
            query.setLong(2, accountId);
            try (var row = query.executeQuery()) { return row.next() ? row.getLong(1) : 0; }
        }
    }

    private String key(Fixture fixture, Operation operation) {
        return UUID.nameUUIDFromBytes(("proerp-portfolio-v1:" + fixture.email() + ":" + operation)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void insertKyc(java.sql.Connection connection, long id, KycStatus status) throws SQLException {
        if (status == KycStatus.NOT_STARTED) return;
        try (var statement = connection.prepareStatement("""
                INSERT INTO customer_kyc(customer_id,status) VALUES (?,?)
                ON DUPLICATE KEY UPDATE customer_id=customer_id
                """)) {
            statement.setLong(1, id);
            statement.setString(2, status.name());
            statement.executeUpdate();
        }
    }

    private void enrichPreviousSamples() throws SQLException {
        List<String> names = List.of("Aarav Shah", "Diya Nair", "Arjun Rao", "Ananya Sen", "Vivaan Mehta",
                "Isha Kapoor", "Aditya Joshi", "Sara Khan", "Rohan Das", "Meera Iyer", "Kabir Verma",
                "Riya Patel", "Dev Menon", "Tara Bose", "Neel Reddy");
        try (var connection = DatabaseConnection.getConnection();
             var find = connection.prepareStatement("""
                     SELECT id,status,username FROM users WHERE role='CUSTOMER' AND email=? AND username IN (?,?)
                     """);
             var stripPrefix = connection.prepareStatement("UPDATE users SET username=? WHERE id=? AND username=?");
             var profile = connection.prepareStatement("""
                     INSERT INTO customer_profiles(customer_id,full_name) VALUES (?,?)
                     ON DUPLICATE KEY UPDATE customer_id=customer_id
                     """)) {
            for (String name : names) {
                String username = name.toLowerCase(Locale.ROOT).replace(' ', '.');
                find.setString(1, username + "@example.test");
                find.setString(2, username);
                find.setString(3, "demo." + username);
                try (var row = find.executeQuery()) {
                    if (!row.next()) continue;
                    long id = row.getLong(1);
                    String currentUsername = row.getString(3);
                    if (!username.equals(currentUsername)) {
                        stripPrefix.setString(1, username);
                        stripPrefix.setLong(2, id);
                        stripPrefix.setString(3, currentUsername);
                        stripPrefix.executeUpdate();
                    }
                    profile.setLong(1, id);
                    profile.setString(2, name);
                    profile.executeUpdate();
                    insertKyc(connection, id, "ACTIVE".equals(row.getString(2)) ? KycStatus.VERIFIED : KycStatus.PENDING);
                }
            }
            renamePreviousPortfolioCustomer(connection);
        }
    }

    private void renamePreviousPortfolioCustomer(java.sql.Connection connection) throws SQLException {
        try (var rename = connection.prepareStatement("""
                UPDATE users SET username='meera.sethi',email='meera.sethi@example.test'
                WHERE role='CUSTOMER' AND ((username='neha.joshi' AND email='neha.joshi@example.test')
                   OR (username='nisha.joshi' AND email='nisha.joshi@example.test'))
                """)) {
            if (rename.executeUpdate() == 0) return;
        }
        try (var update = connection.prepareStatement("""
                UPDATE customer_profiles p JOIN users u ON u.id=p.customer_id
                SET p.full_name='Meera Sethi' WHERE u.username='meera.sethi' AND u.email='meera.sethi@example.test'
                """)) {
            update.executeUpdate();
        }
    }
}
