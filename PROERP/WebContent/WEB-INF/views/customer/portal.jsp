<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal,java.text.NumberFormat,java.time.format.DateTimeFormatter,java.util.List,java.util.Locale,java.util.Map" %>
<%@ page import="com.netbanking.dao.CustomerAccessDao.Identity,com.netbanking.dao.CustomerBankingDao.Activity,com.netbanking.dao.FinanceDao.Loan,com.netbanking.model.BankAccount,com.netbanking.model.KycStatus,com.netbanking.util.Html" %>
<%@ page import="com.netbanking.dao.CustomerBankingDao.Receipt,com.netbanking.dao.CustomerProductsDao.Card,com.netbanking.dao.CustomerProductsDao.Deposit,com.netbanking.dao.CustomerProductsDao.Payee,com.netbanking.dao.CustomerRequestsDao.Application,com.netbanking.dao.CustomerRequestsDao.Ticket,com.netbanking.web.CustomerPortalServlet,java.util.UUID" %>
<%@ page import="com.netbanking.dao.CustomerBankingDao.Category,com.netbanking.dao.CustomerProductsDao.Goal" %>
<%@ page import="com.netbanking.dao.CustomerBankingDao.DailyFlow,java.math.RoundingMode" %>
<%@ page import="com.netbanking.model.LoanTerms" %>
<%
    Identity customer = (Identity) request.getAttribute("customerIdentity");
    String section = (String) request.getAttribute("customerPage");
    String base = request.getContextPath() + "/customer";
    List<String> sections = List.of("overview", "transfer", "activity", "cards", "loans", "bills", "deposits", "profile");
    List<String> serviceSections = List.of("deposits", "goals", "services");
    boolean inServiceArea = serviceSections.contains(section);
    Map<String, String> titles = Map.ofEntries(Map.entry("overview", "Overview"), Map.entry("transfer", "Send money"),
        Map.entry("activity", "Activity & statements"), Map.entry("loans", "Loans"), Map.entry("profile", "Profile"),
        Map.entry("password", "Change password"), Map.entry("cards", "Cards"), Map.entry("deposits", "Fixed deposits"),
        Map.entry("bills", "Bill payments"), Map.entry("services", "Service requests"), Map.entry("payees", "Saved payees"),
        Map.entry("receipt", "Transfer receipt"), Map.entry("updates", "Notifications"), Map.entry("goals", "Savings goals"));
    String title = titles.get(section);
    String result = request.getParameter("result");
    String feedback = "";
    boolean success = false;
    if ("activity".equals(section)) {
        if ("posted".equals(result)) { feedback = "Transfer complete. Find the transaction below."; success = true; }
        if ("duplicate".equals(result)) { feedback = "This transfer was already recorded."; success = true; }
    } else if ("transfer".equals(section)) {
        if ("recipient_choice".equals(result)) feedback = "Choose one of your saved payees or enter an account number.";
        if ("invalid".equals(result)) feedback = "Check the account number and enter a positive amount with up to two decimal places. Choose a different destination account.";
        if ("unavailable".equals(result)) feedback = "The transfer could not be completed. Check the account details or try again.";
        if ("insufficient".equals(result)) feedback = "The source account has insufficient funds.";
    } else if ("password".equals(section)) {
        if ("invalid".equals(result)) feedback = "Choose a different password with at least 10 characters and enter it again to confirm.";
        if ("incorrect".equals(result)) feedback = "The current password could not be verified.";
    } else if ("overview".equals(section) && "password_changed".equals(result)) {
        feedback = "Password updated."; success = true;
    } else if ("goals".equals(section)) {
        if ("created".equals(result)) { feedback = "Savings goal created."; success = true; }
        if ("closed".equals(result)) { feedback = "Savings goal closed."; success = true; }
        if ("posted".equals(result)) { feedback = "Your goal and account balances were updated."; success = true; }
        if ("duplicate".equals(result)) { feedback = "This request was already recorded."; success = true; }
        if ("invalid".equals(result)) feedback = "Check the amount. A contribution must fit within the target and a withdrawal must fit within the amount saved.";
        if ("insufficient".equals(result)) feedback = "Your account has insufficient funds for this contribution.";
        if ("unavailable".equals(result)) feedback = "This action is unavailable. Check the account status and make sure the goal is empty before closing it.";
    } else if (List.of("loans", "deposits", "bills", "services", "payees").contains(section)) {
        if ("saved".equals(result) || "posted".equals(result) || "submitted".equals(result)) {
            feedback = "submitted".equals(result) ? "Application submitted. Track the decision below."
                : "posted".equals(result) ? "Transaction posted. Your account activity has been updated." : "Saved.";
            success = true;
        } else if ("duplicate".equals(result)) {
            feedback = "This transaction was already recorded."; success = true;
        } else if ("unavailable".equals(result)) {
            feedback = "This action is unavailable. Check the details and the account status.";
        } else if ("invalid".equals(result)) {
            feedback = "Check the amount and the requested details.";
        } else if ("insufficient".equals(result)) {
            feedback = "The selected account has insufficient funds.";
        }
    }
    List<BankAccount> accounts = (List<BankAccount>) request.getAttribute("accounts");
    List<BankAccount> activeAccounts = (List<BankAccount>) request.getAttribute("activeAccounts");
    List<Activity> activity = (List<Activity>) request.getAttribute("activity");
    List<Loan> loans = (List<Loan>) request.getAttribute("loans");
    BigDecimal outstanding = (BigDecimal) request.getAttribute("outstanding");
    NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
    DateTimeFormatter date = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    DateTimeFormatter timestamp = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    String kyc = KycStatus.valueOf(customer.kyc()).label();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= title %> | PROERP</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/customer-portal.css?v=loan-terms">
    <% if (!customer.passwordChangeRequired()) { %>
        <script defer src="<%= request.getContextPath() %>/assets/js/customer-notifications.js"></script>
    <% } %>
    <% if ("overview".equals(section)) { %><link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/customer-overview.css?v=views"><% } %>
    <% if ("profile".equals(section)) { %><link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/customer-profile.css"><% } %>
    <% if ("cards".equals(section)) { %><link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/customer-cards.css"><% } %>
</head>
<body<%= "overview".equals(section) ? " class=\"customer-overview\"" : "profile".equals(section) ? " class=\"customer-profile\""
            : "transfer".equals(section) ? " class=\"customer-transfer\""
            : "cards".equals(section) ? " class=\"customer-cards\"" : "" %>>
<div class="bank-shell">
    <aside class="bank-sidebar">
        <a class="bank-wordmark" href="<%= base %>/overview">PROERP</a>
        <% if (!customer.passwordChangeRequired()) { %>
            <nav aria-label="Customer banking">
                <% for (String link : sections) { %>
                    <a href="<%= base + "/" + link %>" <% if (section.equals(link) || ("deposits".equals(link) && inServiceArea) || ("payees".equals(section) && "transfer".equals(link)) || ("receipt".equals(section) && "activity".equals(link))) { %>aria-current="page"<% } %>><%= "deposits".equals(link) ? "Savings &amp; services" : titles.get(link) %></a>
                <% } %>
            </nav>
        <% } %>
        <div class="bank-user">
            <strong><%= Html.escape(customer.name()) %></strong>
            <form method="post" action="<%= base %>/logout">
                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                <button class="bank-button secondary" type="submit">Sign out</button>
            </form>
        </div>
    </aside>
    <main class="bank-main">
        <header class="bank-heading">
            <div>
                <% if ("overview".equals(section)) { %>
                    <h1>Welcome, <%= Html.escape(customer.name()) %></h1>
                <% } else if (inServiceArea) { %>
                    <h1>Savings &amp; services</h1>
                <% } else { %>
                    <h1><%= title %></h1>
                <% } %>
            </div>
            <% if (!customer.passwordChangeRequired()) { %>
                <div class="customer-notifications" data-customer="<%= customer.id() %>">
                    <a class="bank-button secondary" href="<%= base %>/updates" data-notification-link>Notifications</a>
                    <button class="bank-button secondary" type="button" data-notification-toggle aria-expanded="false" aria-controls="notification-popup" hidden>Notifications</button>
                    <section class="bank-panel notification-popup" id="notification-popup" aria-labelledby="notification-title" hidden>
                        <div class="section-heading">
                            <h2 id="notification-title">Notifications</h2>
                            <button class="bank-button secondary" type="button" data-notification-close>Close</button>
                        </div>
                        <p class="notification-announcement" role="status" data-notification-announcement></p>
                        <div class="notification-content" data-notification-content>Loading notifications...</div>
                    </section>
                </div>
            <% } %>
        </header>
        <% if (inServiceArea) { %>
            <nav class="bank-service-tabs" aria-label="Savings and services">
                <% for (String service : serviceSections) { %>
                    <a href="<%= base + "/" + service %>" <%= service.equals(section) ? "aria-current=\"page\"" : "" %>><%= titles.get(service) %></a>
                <% } %>
            </nav>
        <% } %>
        <% if (!feedback.isEmpty()) { %>
            <p class="bank-notice <%= success ? "success" : "error" %>" role="<%= success ? "status" : "alert" %>"><%= Html.escape(feedback) %></p>
        <% } %>

        <% if ("overview".equals(section)) {
            BigDecimal total = accounts.stream().map(BankAccount::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        %>
            <%@ include file="overview.jspf" %>
        <% } else if ("transfer".equals(section)) { %>
            <% BankAccount transferSource = (BankAccount) request.getAttribute("transferSource"); %>
            <% if (transferSource != null) {
                String transferDestination = (String) request.getAttribute("transferDestination");
                BigDecimal transferAmount = (BigDecimal) request.getAttribute("transferAmount");
            %>
                <section class="bank-panel form-panel">
                    <h2>Review transfer</h2>
                    <dl class="identity-facts">
                        <div><dt>From account</dt><dd><%= Html.escape(transferSource.number()) %></dd></div>
                        <% if (request.getAttribute("transferRecipient") != null) { %>
                            <div><dt>Saved payee</dt><dd><%= Html.escape((String) request.getAttribute("transferRecipient")) %></dd></div>
                        <% } %>
                        <div><dt>Recipient account</dt><dd><%= Html.escape(transferDestination) %></dd></div>
                        <div><dt>Amount</dt><dd><%= currency.format(transferAmount) %></dd></div>
                    </dl>
                    <form method="post" action="<%= base %>/transfer" class="bank-actions">
                        <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                        <input type="hidden" name="requestKey" value="${requestScope.requestKey}">
                        <input type="hidden" name="source" value="<%= transferSource.id() %>">
                        <input type="hidden" name="destination" value="<%= Html.escape(transferDestination) %>">
                        <input type="hidden" name="amount" value="<%= transferAmount.toPlainString() %>">
                        <button class="bank-button" type="submit">Confirm transfer</button>
                        <a href="<%= base %>/transfer?to=<%= Html.escape(transferDestination) %>">Back to transfer</a>
                    </form>
                </section>
            <% } else { %>
            <div class="transfer-layout">
            <section class="bank-panel form-panel">
                <div class="section-heading"><h2>New transfer</h2><a href="<%= base %>/payees">Manage payees</a></div>
                <% if (accounts.stream().noneMatch(a -> "ACTIVE".equals(a.status()))) { %>
                    <p class="bank-notice">You need an active bank account to send money.</p>
                <% } else {
                    List<Payee> transferPayees = (List<Payee>) request.getAttribute("payees");
                    String requestedRecipient = request.getParameter("to");
                    String prefill = requestedRecipient != null && requestedRecipient.matches("[0-9]{1,18}") ? requestedRecipient : "";
                    boolean savedPrefill = transferPayees.stream().anyMatch(item -> item.number().equals(prefill));
                %>
                    <form method="post" action="<%= base %>/transfer" class="bank-form">
                        <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                        <input type="hidden" name="requestKey" value="${requestScope.requestKey}">
                        <label for="source">From account
                            <select name="source" id="source" required>
                                <% for (BankAccount account : accounts) { if ("ACTIVE".equals(account.status())) { %>
                                    <option value="<%= account.id() %>"><%= "SAVINGS".equals(account.type()) ? "Savings" : "Current" %> <%= Html.escape(account.number()) %> (<%= currency.format(account.balance()) %>)</option>
                                <% } } %>
                            </select>
                        </label>
                        <% if (!transferPayees.isEmpty()) { %>
                            <label for="payee">Saved payee
                                <select id="payee" name="payee">
                                    <option value="">Choose a saved payee</option>
                                    <% for (Payee payee : transferPayees) { %>
                                        <option value="<%= payee.accountId() %>" <%= payee.number().equals(prefill) ? "selected" : "" %>><%= Html.escape(payee.name()) %> (<%= Html.escape(payee.number()) %>)</option>
                                    <% } %>
                                </select>
                            </label>
                        <% } %>
                        <label for="destination"><%= transferPayees.isEmpty() ? "Recipient account number" : "Or enter a PROERP account number" %>
                            <input id="destination" name="destination" inputmode="numeric" pattern="[0-9]{1,18}" maxlength="18"
                                value="<%= savedPrefill ? "" : prefill %>" <%= transferPayees.isEmpty() ? "required" : "" %>>
                        </label>
                        <label for="amount">Amount (₹)
                            <input id="amount" name="amount" inputmode="decimal" pattern="[0-9]{1,9}([.][0-9]{1,2})?" maxlength="12" required>
                        </label>
                        <button class="bank-button" type="submit" name="stage" value="review">Review transfer</button>
                    </form>
                <% } %>
            </section>
            <section class="bank-panel transfer-history" aria-labelledby="recent-transfers">
                <h2 id="recent-transfers">Recent transfers</h2>
                <% List<Activity> recentTransfers = (List<Activity>) request.getAttribute("recentTransfers"); %>
                <% if (recentTransfers.isEmpty()) { %><p class="subtle panel-message">You have no transfers yet.</p><% } %>
                <ul class="bank-list">
                    <% for (Activity movement : recentTransfers) { %>
                        <li>
                            <div><strong><%= movement.label() %></strong><p class="subtle"><%= movement.postedAt().format(timestamp) %></p></div>
                            <div class="transfer-entry-amount">
                                <strong><%= movement.debit() ? "-" : "+" %><%= currency.format(movement.amount()) %></strong>
                                <a href="<%= base %>/receipt?id=<%= movement.reference().substring(2) %>">Receipt <%= Html.escape(movement.reference()) %></a>
                            </div>
                        </li>
                    <% } %>
                </ul>
            </section>
            </div>
            <% } %>
        <% } else if ("loans".equals(section)) { %>
            <%@ include file="loan-applications.jspf" %>
            <section class="bank-panel">
                <div class="section-heading"><h2>Loan history</h2></div>
                <% if (loans.isEmpty()) { %>
                    <p class="subtle panel-message">You have no loans on record.</p>
                <% } else { %>
                    <div class="bank-table-wrap">
                        <table class="bank-table">
                            <thead><tr><th scope="col">Loan</th><th scope="col">Terms</th><th scope="col" class="numeric">Total due</th><th scope="col">Payment</th></tr></thead>
                            <tbody>
                            <% for (Loan loan : loans) { %>
                                <tr><td><strong>LN-<%= loan.id() %></strong><small><%= Html.escape(loan.account()) %></small><small>Issued <%= loan.date().format(date) %></small></td>
                                    <td><% if (loan.terms() == null) { %>Principal-only loan
                                        <% } else { %><strong><%= loan.terms().months() %> months at <%= loan.terms().annualRate().toPlainString() %>% yearly</strong>
                                            <small><%= currency.format(loan.terms().monthly(loan.principal())) %> monthly, <%= currency.format(loan.terms().finalPayment(loan.principal())) %> final</small>
                                            <small>First payment <%= loan.firstPaymentDate().format(date) %></small>
                                            <small>Matures <%= loan.maturityDate().format(date) %></small>
                                        <% } %><small><%= currency.format(loan.principal()) %> borrowed</small></td>
                                    <td class="numeric"><strong><%= currency.format(loan.totalDue()) %></strong>
                                        <small><%= currency.format(loan.outstanding()) %> principal</small>
                                        <% if (loan.terms() != null) { %><small><%= currency.format(loan.interestDue()) %> interest</small><% } %></td>
                                    <td><% if (loan.canRepay() && loan.totalDue().signum() > 0) { %>
                                        <details><summary>Make a payment</summary>
                                            <form method="post" action="<%= base %>/loans" class="bank-form row-form">
                                                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                                                <input type="hidden" name="requestKey" value="<%= UUID.randomUUID() %>">
                                                <input type="hidden" name="action" value="repay">
                                                <input type="hidden" name="source" value="<%= loan.accountId() %>">
                                                <input type="hidden" name="productId" value="<%= loan.id() %>">
                                                <label>Amount (&#8377;)<input name="amount" inputmode="decimal" pattern="[0-9]{1,17}([.][0-9]{1,2})?" maxlength="20" required></label>
                                                <button class="bank-button" type="submit">Make payment</button>
                                            </form>
                                        </details>
                                    <% } else { %><%= loan.totalDue().signum() == 0 ? "Paid in full" : "Account unavailable" %><% } %></td>
                                </tr>
                            <% } %>
                            </tbody>
                        </table>
                    </div>
                <% } %>
            </section>
        <% } else if ("profile".equals(section)) { %>
            <%@ include file="profile.jspf" %>
        <% } else if ("password".equals(section)) { %>
            <section class="bank-panel form-panel">
                <h2><%= customer.passwordChangeRequired() ? "Choose a personal password" : "Update your password" %></h2>
                <p class="subtle"><%= customer.passwordChangeRequired() ? "Replace your temporary password before using customer banking." : "Use at least 10 characters." %></p>
                <form method="post" action="<%= base %>/password" class="bank-form">
                    <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                    <label for="currentPassword"><%= customer.passwordChangeRequired() ? "Temporary password" : "Current password" %>
                        <input id="currentPassword" name="currentPassword" type="password" autocomplete="current-password" maxlength="128" required>
                    </label>
                    <label for="newPassword">New password
                        <input id="newPassword" name="newPassword" type="password" autocomplete="new-password" minlength="10" maxlength="128" required>
                    </label>
                    <label for="confirmPassword">Confirm new password
                        <input id="confirmPassword" name="confirmPassword" type="password" autocomplete="new-password" minlength="10" maxlength="128" required>
                    </label>
                    <button class="bank-button" type="submit">Save password</button>
                </form>
            </section>
        <% } else if ("goals".equals(section)) { %>
            <%@ include file="goals.jspf" %>
        <% } else if ("cards".equals(section)) { %>
            <%@ include file="cards.jspf" %>
        <% } else if ("updates".equals(section)) { %>
            <jsp:include page="/WEB-INF/views/customer/notifications.jsp" />
        <% } else if (List.of("deposits", "bills", "payees", "services", "receipt").contains(section)) { %>
            <%@ include file="customer-services.jspf" %>
        <% } %>

        <% if (activity != null) { %>
            <%@ include file="activity.jspf" %>
        <% } %>
    </main>
</div>
</body>
</html>
