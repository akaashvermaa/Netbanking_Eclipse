import com.netbanking.config.DatabaseConnection;
import com.netbanking.dao.AdminDao;
import com.netbanking.dao.BankAccountDao;
import com.netbanking.dao.CustomerAccessDao;
import com.netbanking.dao.CustomerProductsDao;
import com.netbanking.dao.FinanceDao;
import com.netbanking.model.AccountChoice;
import com.netbanking.model.CustomerProfile;
import com.netbanking.security.PasswordHasher;
import com.netbanking.service.AdminAuthService;
import com.netbanking.setup.SamplePortfolioSetup;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

final class StandaloneDemoSetup {
    public static void main(String[] args) throws Exception {
        var settings = new Properties();
        try (var input = StandaloneDemoSetup.class.getClassLoader().getResourceAsStream("demo.properties")) {
            if (input == null) throw new IllegalStateException("Standalone demo configuration is missing.");
            settings.load(input);
        }
        var admin = new AdminDao();
        String adminName = settings.getProperty("demo.admin.username");
        char[] adminPassword = settings.getProperty("demo.admin.password").toCharArray();
        long adminId;
        try {
            if (admin.findActiveByUsername(adminName).isEmpty()) {
                admin.createInitialAdmin(adminName, PasswordHasher.create(adminPassword));
            }
            adminId = new AdminAuthService().authenticate(adminName, adminPassword).orElseThrow().id();
        } finally {
            Arrays.fill(adminPassword, '\0');
        }

        SamplePortfolioSetup.main(args);
        var access = new CustomerAccessDao();
        String username = settings.getProperty("demo.customer.username");
        char[] password = settings.getProperty("demo.customer.password").toCharArray();
        long customerId;
        try {
            var signIn = access.authenticate(username, password);
            if (signIn.customer() != null) {
                customerId = signIn.customer().id();
            } else {
                customerId = signIn.pending() == null
                        ? access.register(username, username + "@example.test",
                                new CustomerProfile("Aditi Menon", "+1 202 555 0140"), password, AccountChoice.SAVINGS)
                        : signIn.pending().id();
                if (customerId == 0 || !admin.changeCustomerStatus(customerId, adminId, "ACTIVE", "PENDING",
                        "", AccountChoice.SAVINGS)) {
                    throw new IllegalStateException("Could not activate the standalone demo customer.");
                }
            }
        } finally {
            Arrays.fill(password, '\0');
        }

        var accounts = new BankAccountDao();
        var savings = accounts.list(customerId, "", "ACTIVE").stream()
                .filter(account -> "SAVINGS".equals(account.type())).findFirst().orElseThrow();
        String key = UUID.nameUUIDFromBytes("proerp-standalone-opening-deposit".getBytes(StandardCharsets.UTF_8)).toString();
        var outcome = new FinanceDao().post(FinanceDao.Operation.DEPOSIT, savings.id(), null, adminId,
                new BigDecimal("12500.00"), key);
        if (outcome != FinanceDao.Outcome.POSTED && outcome != FinanceDao.Outcome.DUPLICATE) {
            throw new IllegalStateException("Could not fund the standalone demo account.");
        }
        var products = new CustomerProductsDao();
        if (products.cards(customerId).stream().noneMatch(card -> card.accountId() == savings.id())) {
            products.issueCard(customerId, savings.id());
        }
        try (var db = DatabaseConnection.getConnection();
             var select = db.prepareStatement("""
                     SELECT a.account_number,p.full_name FROM bank_accounts a JOIN users u ON a.customer_id=u.id
                     JOIN customer_profiles p ON p.customer_id=u.id
                     WHERE u.username=? AND u.email=? AND a.status='ACTIVE' AND a.account_type='SAVINGS'
                     """)) {
            for (String payee : List.of("rahul.kumar", "ananya.iyer", "priya.nair")) {
                select.setString(1, payee);
                select.setString(2, payee + "@example.test");
                try (var row = select.executeQuery()) {
                    if (row.next()) products.savePayee(customerId, row.getString(1), row.getString(2));
                }
            }
        }
    }
}
