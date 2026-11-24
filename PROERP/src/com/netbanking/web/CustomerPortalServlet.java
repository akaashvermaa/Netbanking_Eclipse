package com.netbanking.web;

import com.netbanking.dao.BankAccountDao;
import com.netbanking.dao.CustomerAccessDao;
import com.netbanking.dao.CustomerAccessDao.Identity;
import com.netbanking.dao.CustomerAccessDao.Pending;
import com.netbanking.config.LocalDemo;
import com.netbanking.model.AccountChoice;
import com.netbanking.model.LoanTerms;
import com.netbanking.dao.CustomerBankingDao;
import com.netbanking.dao.CustomerBankingDao.ActivityFilter;
import com.netbanking.dao.CustomerBankingDao.Category;
import com.netbanking.dao.CustomerProductsDao;
import com.netbanking.dao.CustomerProductsDao.Posting;
import com.netbanking.dao.CustomerRequestsDao;
import com.netbanking.dao.FinanceDao;
import com.netbanking.model.BankAccount;
import com.netbanking.model.CustomerProfile;
import com.netbanking.security.CsrfTokens;
import com.netbanking.security.PortalSession;
import static com.netbanking.security.CsrfTokens.Portal.CUSTOMER;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@WebServlet("/customer/*")
public class CustomerPortalServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String VIEWS = "/WEB-INF/views/customer/";
    private static final int PAGE_SIZE = 25;
    private final CustomerAccessDao access = new CustomerAccessDao();
    private final CustomerBankingDao banking = new CustomerBankingDao();
    private final BankAccountDao accounts = new BankAccountDao();
    private final FinanceDao finance = new FinanceDao();
    private final CustomerProductsDao products = new CustomerProductsDao();
    private final CustomerRequestsDao requests = new CustomerRequestsDao();
    public static final List<String> BILLERS = List.of("Electricity", "Water", "Broadband", "Mobile");

    public static String serviceLabel(String category) {
        return switch (category) {
            case "CHEQUE_BOOK" -> "Cheque book";
            case "CONTACT_UPDATE" -> "Update contact details";
            case "TRANSACTION_REVIEW" -> "Review a transaction";
            default -> "Service request";
        };
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Identity customer = (Identity) request.getAttribute("customerIdentity");
        String path = path(request);
        if ("/login".equals(path) || "/register".equals(path)) {
            boolean registration = "/register".equals(path);
            if (registration && customer != null) redirect(request, response, "/overview");
            else showAccessPage(request, response, registration);
            return;
        }
        if ("/approval".equals(path)) {
            if (customer != null) redirect(request, response, "/overview");
            else if (request.getSession().getAttribute("pendingCustomer") instanceof Pending) showApproval(request, response);
            else redirect(request, response, "/login?approvalExpired=1");
            return;
        }
        try {
            switch (path) {
                case "/overview", "/transfer", "/activity", "/statement", "/loans", "/cards",
                     "/deposits", "/bills", "/payees", "/goals" -> {
                    List<BankAccount> owned = accounts.list(customer.id(), "", "");
                    request.setAttribute("accounts", owned);
                    request.setAttribute("activeAccounts", owned.stream().filter(a -> "ACTIVE".equals(a.status())).toList());
                    if (request.getAttribute("requestKey") == null) request.setAttribute("requestKey", UUID.randomUUID());
                    if ("/overview".equals(path)) {
                        if (!loadOverview(request, response, customer, owned)) return;
                        request.setAttribute("outstanding", finance.lendingTotals(null, customer.id()).outstanding());
                        request.setAttribute("applications", requests.applications(customer.id()));
                        request.setAttribute("deposits", products.deposits(customer.id()));
                        request.setAttribute("goals", products.goals(customer.id()));
                    } else if ("/activity".equals(path) || "/statement".equals(path)) {
                        if (!loadActivity(request, response, customer.id(), owned)) return;
                        if ("/statement".equals(path)) {
                            if (request.getAttribute("printStatement") != null) {
                                request.getRequestDispatcher(VIEWS + "statement.jsp").forward(request, response);
                            }
                            return;
                        }
                    } else if ("/loans".equals(path)) {
                        request.setAttribute("loans", finance.loansByCustomer(customer.id()));
                        request.setAttribute("applications", requests.applications(customer.id()));
                    } else if ("/transfer".equals(path) || "/payees".equals(path)) {
                        request.setAttribute("payees", products.payees(customer.id()));
                        if ("/transfer".equals(path)) {
                            request.setAttribute("recentTransfers", banking.activity(customer.id(), null,
                                    new ActivityFilter(null, null, Category.TRANSFER, ""), 5, 0));
                        }
                    } else if ("/cards".equals(path)) {
                        if (!loadCards(request, response, customer, true)) return;
                    } else if ("/deposits".equals(path)) {
                        request.setAttribute("deposits", products.deposits(customer.id()));
                    } else if ("/goals".equals(path)) {
                        request.setAttribute("goals", products.goals(customer.id()));
                    }
                }
                case "/services" -> {
                    request.setAttribute("tickets", requests.tickets(customer.id()));
                    request.setAttribute("requestKey", UUID.randomUUID());
                }
                case "/updates" -> {
                    request.setAttribute("updates", banking.activity(customer.id(), null, 10, 0));
                    request.setAttribute("applications", requests.applications(customer.id()));
                    request.setAttribute("tickets", requests.tickets(customer.id()));
                    if ("popup".equals(request.getParameter("view"))) {
                        request.getRequestDispatcher(VIEWS + "notifications.jsp").forward(request, response);
                        return;
                    }
                }
                case "/receipt" -> {
                    Long id = positiveNumber(request.getParameter("id"));
                    var receipt = id == null ? null : banking.receipt(customer.id(), id);
                    if (receipt == null) {
                        response.sendError(404, "Transfer receipt unavailable.");
                        return;
                    }
                    request.setAttribute("receipt", receipt);
                }
                case "/profile" -> request.setAttribute("accountCount", accounts.list(customer.id(), "", "").size());
                case "/password" -> { }
                default -> {
                    response.sendError(404);
                    return;
                }
            }
            request.setAttribute("customerPage", path.substring(1));
            request.getRequestDispatcher(VIEWS + "portal.jsp").forward(request, response);
        } catch (SQLException exception) {
            unavailable(response, exception);
        }
    }

    private boolean loadOverview(HttpServletRequest request, HttpServletResponse response, Identity customer,
                                 List<BankAccount> owned) throws IOException, SQLException {
        if (!loadCards(request, response, customer, false)) return false;
        var recentByAccount = new java.util.LinkedHashMap<Long, List<CustomerBankingDao.Activity>>();
        for (BankAccount account : owned) {
            recentByAccount.put(account.id(), banking.activity(customer.id(), account.id(), 10, 0));
        }
        request.setAttribute("recentByAccount", recentByAccount);
        request.setAttribute("dailyFlow", banking.recentFlow(customer.id()));
        return true;
    }

    private boolean loadCards(HttpServletRequest request, HttpServletResponse response, Identity customer,
                              boolean selectFirst) throws IOException, SQLException {
        var cards = products.cards(customer.id());
        request.setAttribute("cards", cards);
        String cardParam = request.getParameter("card");
        var card = selectFirst && !cards.isEmpty() ? cards.get(0) : null;
        if (cardParam != null) {
            Long id = positiveNumber(cardParam);
            card = cards.stream().filter(item -> Long.valueOf(item.accountId()).equals(id)).findFirst().orElse(null);
            if (card == null) {
                response.sendError(404, "Card unavailable.");
                return false;
            }
        }
        if (card != null) {
            String pageParam = request.getParameter("cardPage");
            Long page = pageParam == null ? Long.valueOf(1) : positiveNumber(pageParam);
            int size = 10;
            if (page == null || page > Integer.MAX_VALUE / size) {
                response.sendError(400, "Invalid card activity page.");
                return false;
            }
            var cardActivity = banking.activity(customer.id(), card.accountId(), new ActivityFilter(null, null, Category.CARD, ""),
                    size + 1, (page.intValue() - 1) * size);
            request.setAttribute("selectedCard", card);
            request.setAttribute("cardActivity", cardActivity.subList(0, Math.min(size, cardActivity.size())));
            request.setAttribute("cardPage", page);
            request.setAttribute("cardMore", cardActivity.size() > size);
            request.setAttribute("cardSpentToday", products.cardSpentToday(card.accountId()));
        }
        return true;
    }

    private boolean loadActivity(HttpServletRequest request, HttpServletResponse response, long customerId,
                                 List<BankAccount> owned) throws IOException, SQLException {
        String accountFilter = request.getParameter("account");
        Long selected = accountFilter == null || accountFilter.isEmpty() ? null : positiveNumber(accountFilter);
        if (accountFilter != null && !accountFilter.isEmpty()
                && (selected == null || owned.stream().noneMatch(account -> account.id() == selected))) {
            response.sendError(404, "Account unavailable.");
            return false;
        }
        LocalDate from;
        LocalDate to;
        try {
            from = parseDate(request.getParameter("from"));
            to = parseDate(request.getParameter("to"));
            if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            response.sendError(400, "Enter a valid statement date range.");
            return false;
        }
        String search = registrationField(request, "q");
        String kind = registrationField(request, "kind");
        Category category;
        try {
            category = kind.isEmpty() ? Category.ALL : Category.valueOf(kind);
        } catch (IllegalArgumentException exception) {
            response.sendError(400, "Choose a valid transaction type.");
            return false;
        }
        if (search.length() > 80) {
            response.sendError(400, "Enter up to 80 characters to search transactions.");
            return false;
        }
        ActivityFilter filter = new ActivityFilter(from, to == null ? null : to.plusDays(1), category, search);
        request.setAttribute("selectedAccount", selected);
        request.setAttribute("fromDate", from);
        request.setAttribute("toDate", to);
        request.setAttribute("activityCategory", category);
        request.setAttribute("activitySearch", search);
        if ("/statement".equals(path(request))) {
            if ("print".equals(request.getParameter("format"))) {
                request.setAttribute("activity", banking.activity(customerId, selected, filter, Integer.MAX_VALUE, 0));
                request.setAttribute("printStatement", true);
            } else {
                downloadStatement(response, customerId, selected, filter);
            }
            return true;
        }
        String page = request.getParameter("page");
        Long number = page == null ? Long.valueOf(1) : positiveNumber(page);
        if (number == null || number > Integer.MAX_VALUE / PAGE_SIZE) {
            response.sendError(400, "Invalid activity page.");
            return false;
        }
        List<CustomerBankingDao.Activity> activity = banking.activity(customerId, selected, filter, PAGE_SIZE + 1,
                (number.intValue() - 1) * PAGE_SIZE);
        request.setAttribute("activity", activity.subList(0, Math.min(PAGE_SIZE, activity.size())));
        request.setAttribute("activityPage", number);
        request.setAttribute("hasMore", activity.size() > PAGE_SIZE);
        return true;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isEmpty()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
        LocalDate date = LocalDate.parse(value);
        if (date.getYear() < 1900 || date.getYear() > 9998) throw new IllegalArgumentException();
        return date;
    }

    private void downloadStatement(HttpServletResponse response, long customerId, Long account,
                                   ActivityFilter filter) throws SQLException, IOException {
        int pageSize = 500;
        var rows = banking.activity(customerId, account, filter, pageSize, 0);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"proerp-statement.csv\"");
        var output = response.getWriter();
        output.println("Reference,Date,Account,Type,Amount INR,Balance INR");
        for (int offset = 0; ; offset += pageSize) {
            for (var row : rows) {
                output.println(row.reference() + "," + row.postedAt() + "," + row.account() + "," + row.kind()
                        + "," + (row.debit() ? "-" : "") + row.amount().toPlainString() + "," + row.balance().toPlainString());
            }
            if (rows.size() < pageSize || output.checkError()) return;
            rows = banking.activity(customerId, account, filter, pageSize, offset + pageSize);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        Identity customer = (Identity) request.getAttribute("customerIdentity");
        try {
            switch (path(request)) {
                case "/login" -> signIn(request, response);
                case "/register" -> {
                    if (customer != null) redirect(request, response, "/overview");
                    else register(request, response);
                }
                case "/logout" -> {
                    PortalSession.clearCustomer(request.getSession());
                    redirect(request, response, "/login");
                }
                case "/approval" -> checkApproval(request, response, customer);
                case "/password" -> changePassword(request, response, customer);
                case "/profile" -> completePhone(request, response, customer);
                case "/transfer" -> transfer(request, response, customer);
                case "/loans", "/bills", "/deposits" -> financialRequest(request, response, customer);
                case "/goals" -> savingsGoal(request, response, customer);
                case "/overview", "/cards" -> cardAction(request, response, customer);
                case "/payees", "/services" -> serviceRequest(request, response, customer);
                default -> response.sendError(405);
            }
        } catch (SQLException exception) {
            unavailable(response, exception);
        }
    }

    private void cardAction(HttpServletRequest request, HttpServletResponse response, Identity customer)
            throws SQLException, IOException {
        Long account = positiveNumber(request.getParameter("source"));
        String action = registrationField(request, "action");
        if (account == null) {
            response.sendError(400, "Choose an account.");
            return;
        }
        boolean cardsView = "cards".equals(request.getParameter("view"));
        boolean cardsPage = "/cards".equals(path(request));
        String page = cardsPage ? "/cards?" : cardsView ? "/overview?view=cards&" : "/overview?";
        String cardPath = page + "card=" + account;
        if ("issue".equals(action)) {
            boolean issued = products.issueCard(customer.id(), account);
            redirect(request, response, issued ? cardPath + "&result=card_issued#card-details"
                    : page + "result=card_unavailable" + (cardsPage ? "" : "#overview-cards"));
            return;
        }
        if (products.cards(customer.id()).stream().noneMatch(item -> item.accountId() == account)) {
            response.sendError(404, "Card unavailable.");
            return;
        }
        String result = "invalid";
        if ("lock".equals(action) || "unlock".equals(action)) {
            result = products.changeCard(customer.id(), account, "lock".equals(action), null) ? action : "unavailable";
        } else if ("limit".equals(action)) {
            String limit = registrationField(request, "limit");
            if (limit.matches("[0-9]{1,6}(\\.[0-9]{1,2})?")) {
                result = products.changeCard(customer.id(), account, null, new BigDecimal(limit)) ? "limit_saved" : "unavailable";
            }
        } else if ("purchase".equals(action)) {
            String amount = registrationField(request, "amount");
            String merchant = registrationField(request, "merchant");
            String key = request.getParameter("requestKey");
            if (!merchant.isEmpty() && merchant.length() <= 60 && amount.matches("[0-9]{1,9}(\\.[0-9]{1,2})?")
                    && validRequestKey(key)) {
                var outcome = products.post(customer, account, Posting.CARD_PURCHASE, new BigDecimal(amount),
                        0, 0, merchant, key);
                result = outcome == CustomerProductsDao.Result.CARD_LIMIT ? "daily_limit"
                        : outcome == CustomerProductsDao.Result.CARD_BLOCKED ? "blocked"
                        : outcome.name().toLowerCase(Locale.ROOT);
            }
        }
        redirect(request, response, cardPath + "&result=card_" + result + "#card-details");
    }

    private void savingsGoal(HttpServletRequest request, HttpServletResponse response, Identity customer)
            throws SQLException, IOException {
        String action = registrationField(request, "action");
        Long goal = positiveNumber(request.getParameter("productId"));
        if ("close".equals(action) && goal != null) {
            var outcome = products.closeGoal(customer, goal);
            redirect(request, response, "/goals?result=" + (outcome == CustomerProductsDao.Result.POSTED ? "closed" : "unavailable"));
            return;
        }

        Long account = positiveNumber(request.getParameter("source"));
        String entered = registrationField(request, "amount");
        BigDecimal amount = entered.matches("[0-9]{1,9}(\\.[0-9]{1,2})?") ? new BigDecimal(entered) : null;
        String key = request.getParameter("requestKey");
        String result = "invalid";
        if (account != null && amount != null && amount.signum() > 0 && validRequestKey(key)) {
            String name = registrationField(request, "name");
            if ("create".equals(action) && !name.isEmpty() && name.length() <= 60) {
                var outcome = products.createGoal(customer, account, name, amount, key);
                result = outcome == CustomerProductsDao.Result.POSTED ? "created" : outcome.name().toLowerCase(Locale.ROOT);
            } else if (goal != null && ("fund".equals(action) || "withdraw".equals(action))) {
                Posting kind = "fund".equals(action) ? Posting.GOAL_CONTRIBUTION : Posting.GOAL_WITHDRAWAL;
                result = products.post(customer, account, kind, amount, goal, 0, "Savings goal #" + goal, key)
                        .name().toLowerCase(Locale.ROOT);
            }
        }
        redirect(request, response, "/goals?result=" + result);
    }

    private void financialRequest(HttpServletRequest request, HttpServletResponse response, Identity customer)
            throws IOException, SQLException, ServletException {
        String path = path(request);
        String action = registrationField(request, "action");
        String key = request.getParameter("requestKey");
        Long source = positiveNumber(request.getParameter("source"));
        String entered = registrationField(request, "amount");
        boolean loanRepayment = "/loans".equals(path) && "repay".equals(action);
        String amountPattern = loanRepayment ? "[0-9]{1,17}(\\.[0-9]{1,2})?" : "[0-9]{1,9}(\\.[0-9]{1,2})?";
        BigDecimal amount = entered.matches(amountPattern) ? new BigDecimal(entered) : null;
        String result = "invalid";
        if (source == null || !validRequestKey(key)
                || (!"redeem".equals(action) && (amount == null || amount.signum() <= 0))) {
            redirect(request, response, path + "?result=invalid");
            return;
        }
        Long id = positiveNumber(request.getParameter("productId"));
        String option = registrationField(request, "option");
        if ("/loans".equals(path) && ("quote".equals(action) || "apply".equals(action))) {
            LoanTerms terms = LoanTerms.offer(option, request.getParameter("duration"));
            if (terms != null && "quote".equals(action)) {
                var account = accounts.find(source).filter(item -> item.customerId() == customer.id()
                        && "ACTIVE".equals(item.status())).orElse(null);
                if (account != null) {
                    request.setAttribute("loanQuote", terms);
                    request.setAttribute("loanQuoteAccount", account);
                    request.setAttribute("loanQuoteAmount", amount);
                    request.setAttribute("loanQuotePurpose", option);
                    request.setAttribute("requestKey", key);
                    doGet(request, response);
                    return;
                }
                result = "unavailable";
            } else if (terms != null && terms.annualRate().toPlainString().equals(request.getParameter("reviewedRate"))) {
                result = requests.apply(customer, source, amount, option, terms, key) ? "submitted" : "unavailable";
            }
        } else if (loanRepayment && id != null) {
            result = products.post(customer, source, Posting.CUSTOMER_REPAYMENT, amount, id, 0, "Loan #" + id, key).name();
        } else if ("/bills".equals(path) && "pay".equals(action) && !option.isBlank()
                && option.length() <= 50 && option.codePoints().noneMatch(Character::isISOControl)) {
            String reference = registrationField(request, "reference");
            if (reference.matches("[A-Za-z0-9_-]{3,30}")) {
                result = products.post(customer, source, Posting.BILL_PAYMENT, amount, 0, 0,
                        option + " bill " + reference, key).name();
            }
        } else if ("/deposits".equals(path) && "open".equals(action) && List.of("3", "6", "12").contains(option)) {
            result = products.post(customer, source, Posting.DEPOSIT_OPEN, amount, 0, Integer.parseInt(option),
                    "Fixed deposit (" + option + " months)", key).name();
        } else if ("/deposits".equals(path) && "redeem".equals(action) && id != null) {
            result = products.post(customer, source, Posting.DEPOSIT_REDEEM, null, id, 0, "Fixed deposit #" + id, key).name();
        }
        redirect(request, response, path + "?result=" + result.toLowerCase(Locale.ROOT));
    }

    private void serviceRequest(HttpServletRequest request, HttpServletResponse response, Identity customer)
            throws SQLException, IOException {
        String path = path(request);
        String action = registrationField(request, "action");
        Long account = positiveNumber(request.getParameter("source"));
        boolean saved = false;
        if ("/payees".equals(path)) {
            if ("remove".equals(action) && account != null) saved = products.removePayee(customer.id(), account);
            else if ("save".equals(action)) {
                String number = registrationField(request, "destination");
                String name = registrationField(request, "label");
                if (number.matches("[0-9]{1,18}") && !name.isEmpty() && name.length() <= 60) {
                    saved = products.savePayee(customer.id(), number, name);
                }
            }
        } else if ("/services".equals(path)) {
            String category = registrationField(request, "category");
            String details = registrationField(request, "details");
            String key = request.getParameter("requestKey");
            if (List.of("CHEQUE_BOOK", "CONTACT_UPDATE", "TRANSACTION_REVIEW").contains(category)
                    && !details.isEmpty() && details.length() <= 240 && validRequestKey(key)) {
                saved = requests.submitTicket(customer, category, details, key);
            }
        }
        redirect(request, response, path + (saved ? "?result=saved" : "?result=unavailable"));
    }

    private void signIn(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, ServletException, IOException {
        boolean demo = "demo".equals(request.getParameter("action"));
        LocalDemo demoSettings = demo ? LocalDemo.customer(request) : null;
        if (demo && demoSettings == null) {
            response.sendError(404);
            return;
        }
        String username = demo ? demoSettings.username() : request.getParameter("username");
        char[] password = password(demo ? demoSettings.password() : request.getParameter("password"));
        CustomerAccessDao.SignIn signIn;
        try {
            signIn = access.authenticate(username, password);
        } finally {
            Arrays.fill(password, '\0');
        }
        Identity customer = signIn.customer();
        if (customer == null) {
            if (signIn.pending() != null && !demo) {
                startWaiting(request, response, signIn.pending());
                return;
            }
            response.setStatus(401);
            request.setAttribute("accessMessage", demo ? "The demo customer could not sign in. Check the local demo setup."
                    : "Sign-in failed. Check your username and password, or contact your administrator for help.");
            showAccessPage(request, response, false);
            return;
        }
        startCustomerSession(request, response, customer);
    }

    private void startCustomerSession(HttpServletRequest request, HttpServletResponse response, Identity customer)
            throws IOException {
        request.changeSessionId();
        HttpSession session = request.getSession();
        session.removeAttribute("pendingCustomer");
        session.setAttribute("customerId", customer.id());
        session.setAttribute("customerCredentialVersion", customer.credentialVersion());
        PortalSession.touchCustomer(session);
        CsrfTokens.rotate(session, CUSTOMER);
        redirect(request, response, customer.passwordChangeRequired() ? "/password" : "/overview");
    }

    private void startWaiting(HttpServletRequest request, HttpServletResponse response, Pending pending) throws IOException {
        request.changeSessionId();
        HttpSession session = request.getSession();
        PortalSession.clearCustomer(session);
        session.setAttribute("pendingCustomer", pending);
        redirect(request, response, "/approval");
    }

    private void showApproval(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        request.getRequestDispatcher(VIEWS + "approval.jsp").forward(request, response);
    }

    private void checkApproval(HttpServletRequest request, HttpServletResponse response, Identity customer)
            throws IOException, ServletException, SQLException {
        if (customer != null) {
            redirect(request, response, "/overview");
            return;
        }
        HttpSession session = request.getSession();
        if (session.getAttribute("pendingCustomer") instanceof Pending pending) {
            var result = access.checkApproval(pending);
            if (result.customer() != null) {
                startCustomerSession(request, response, result.customer());
                return;
            }
            if (result.pending() != null) {
                if ("1".equals(request.getParameter("poll"))) response.setStatus(204);
                else redirect(request, response, "/approval");
                return;
            }
        }
        PortalSession.clearCustomer(session);
        redirect(request, response, "/login?approvalExpired=1");
    }

    private void register(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException, SQLException {
        String name = registrationField(request, "fullName");
        String username = registrationField(request, "username");
        String email = registrationField(request, "email");
        String phone = registrationField(request, "phone");
        var requestedAccounts = AccountChoice.from(request.getParameter("accountChoice"));
        String submitted = request.getParameter("password");
        String error;
        if (name.isEmpty() || name.length() > 100 || !username.matches("[A-Za-z0-9][A-Za-z0-9._-]{2,49}")
                || !AdminCustomerServlet.validEmail(email) || !CustomerProfile.validPhone(phone)) {
            response.setStatus(400);
            error = "Enter a name, a valid email and phone number, and a username of 3 to 50 characters.";
        } else if (requestedAccounts == null || requestedAccounts == AccountChoice.NONE) {
            response.setStatus(400);
            error = "Choose the type of account you want to request.";
        } else if (!validNewPassword(submitted) || !submitted.equals(request.getParameter("confirmPassword"))) {
            response.setStatus(400);
            error = "Enter a password of 10 to 128 characters and confirm it.";
        } else {
            char[] password = password(submitted);
            try {
                long id = access.register(username, email, new CustomerProfile(name, phone), password, requestedAccounts);
                if (id > 0) {
                    startWaiting(request, response, new Pending(id, 0));
                    return;
                }
            } finally {
                Arrays.fill(password, '\0');
            }
            response.setStatus(409);
            error = "That username or email is already in use. Use different details or sign in.";
        }
        request.setAttribute("accessMessage", error);
        showAccessPage(request, response, true);
    }

    private void completePhone(HttpServletRequest request, HttpServletResponse response, Identity customer)
            throws IOException, SQLException {
        String phone = registrationField(request, "phone");
        String result = "phone_invalid";
        if (CustomerProfile.validPhone(phone)) {
            if (customer.phone() != null && !customer.phone().isBlank()) result = "phone_existing";
            else result = access.addMissingPhone(customer, phone) ? "phone_saved" : "phone_unavailable";
        }
        redirect(request, response, "/profile?result=" + result + "#profile-contact");
    }

    private String registrationField(HttpServletRequest request, String field) {
        String value = request.getParameter(field);
        return value == null ? "" : value.strip();
    }

    private void showAccessPage(HttpServletRequest request, HttpServletResponse response, boolean register)
            throws ServletException, IOException {
        request.setAttribute("registrationPage", register);
        LocalDemo demo = register ? null : LocalDemo.customer(request);
        if (demo != null) request.setAttribute("demoUsername", demo.username());
        request.getRequestDispatcher(VIEWS + "login.jsp").forward(request, response);
    }

    private void changePassword(HttpServletRequest request, HttpServletResponse response, Identity customer)
            throws IOException, SQLException {
        String next = request.getParameter("newPassword");
        String current = request.getParameter("currentPassword");
        if (!validNewPassword(next) || current == null || current.length() > 128
                || current.equals(next) || !next.equals(request.getParameter("confirmPassword"))) {
            redirect(request, response, "/password?result=invalid");
            return;
        }
        char[] oldPassword = password(current);
        char[] newPassword = password(next);
        try {
            if (!access.changePassword(customer, oldPassword, newPassword)) {
                redirect(request, response, "/password?result=incorrect");
                return;
            }
        } finally {
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
        }
        request.changeSessionId();
        request.getSession().setAttribute("customerCredentialVersion", customer.credentialVersion() + 1);
        CsrfTokens.rotate(request.getSession(), CUSTOMER);
        redirect(request, response, "/overview?result=password_changed");
    }

    private void transfer(HttpServletRequest request, HttpServletResponse response, Identity customer)
            throws IOException, SQLException, ServletException {
        Long source = positiveNumber(request.getParameter("source"));
        String destination = registrationField(request, "destination");
        String amount = request.getParameter("amount");
        String key = request.getParameter("requestKey");
        boolean review = "review".equals(request.getParameter("stage"));
        String payeeId = registrationField(request, "payee");
        CustomerProductsDao.Payee recipient = null;
        if (review && !payeeId.isEmpty()) {
            Long id = positiveNumber(payeeId);
            recipient = products.payees(customer.id()).stream()
                    .filter(payee -> Long.valueOf(payee.accountId()).equals(id)).findFirst().orElse(null);
            if (recipient == null || !destination.isEmpty()) {
                redirect(request, response, "/transfer?result=recipient_choice");
                return;
            }
            destination = recipient.number();
        }
        if (source == null || !destination.matches("[0-9]{1,18}")
                || amount == null || !amount.matches("[0-9]{1,9}(\\.[0-9]{1,2})?") || !validRequestKey(key)) {
            redirect(request, response, "/transfer?result=invalid");
            return;
        }
        if (review) {
            var owned = accounts.list(customer.id(), "", "");
            var sender = owned.stream().filter(account -> account.id() == source && "ACTIVE".equals(account.status()))
                    .findFirst().orElse(null);
            if (sender == null || destination.equals(sender.number()) || new BigDecimal(amount).signum() <= 0) {
                redirect(request, response, "/transfer?result=invalid");
                return;
            }
            request.setAttribute("accounts", owned);
            request.setAttribute("customerPage", "transfer");
            request.setAttribute("transferSource", sender);
            request.setAttribute("transferDestination", destination);
            if (recipient != null) request.setAttribute("transferRecipient", recipient.name());
            request.setAttribute("transferAmount", new BigDecimal(amount));
            request.setAttribute("requestKey", key);
            request.getRequestDispatcher(VIEWS + "portal.jsp").forward(request, response);
            return;
        }
        var result = banking.transfer(customer, source, destination, new BigDecimal(amount), key);
        String next = result == CustomerBankingDao.TransferResult.POSTED
                || result == CustomerBankingDao.TransferResult.DUPLICATE ? "/activity" : "/transfer";
        redirect(request, response, next + "?result=" + result.name().toLowerCase(Locale.ROOT));
    }

    static boolean validNewPassword(String value) {
        return value != null && value.length() >= 10 && value.length() <= 128 && !value.isBlank();
    }

    private char[] password(String value) {
        return value == null || value.length() > 128 ? new char[0] : value.toCharArray();
    }

    private boolean validRequestKey(String key) {
        try {
            return key != null && UUID.fromString(key).toString().equals(key);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Long positiveNumber(String value) {
        try {
            long number = Long.parseLong(value);
            return number > 0 ? number : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String path(HttpServletRequest request) {
        String path = request.getPathInfo();
        return path == null || "/".equals(path) ? "/overview" : path;
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, String path) throws IOException {
        response.sendRedirect(request.getContextPath() + "/customer" + path);
    }

    private void unavailable(HttpServletResponse response, SQLException exception) throws IOException {
        getServletContext().log("Customer banking database request failed", exception);
        response.sendError(503, "Customer banking is temporarily unavailable.");
    }
}
