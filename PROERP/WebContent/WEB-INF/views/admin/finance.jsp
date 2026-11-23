<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Map,java.util.UUID,java.math.BigDecimal,java.time.format.DateTimeFormatter,com.netbanking.model.BankAccount,com.netbanking.model.CustomerSummary,com.netbanking.model.CustomerDetails,com.netbanking.dao.AccountReviewDao,com.netbanking.dao.FinanceDao,com.netbanking.dao.CustomerBankingDao.Activity,com.netbanking.util.Html" %>
<%@ page import="com.netbanking.model.LoanTerms" %>
<%
    String context = request.getContextPath();
    BankAccount account = (BankAccount) request.getAttribute("account");
    List<BankAccount> activeAccounts = (List<BankAccount>) request.getAttribute("activeAccounts");
    FinanceDao.Totals totals = (FinanceDao.Totals) request.getAttribute("totals");
    List<FinanceDao.Loan> loans = (List<FinanceDao.Loan>) request.getAttribute("loans");
    List<Activity> movements = (List<Activity>) request.getAttribute("movements");
    String currentLoanStatus = (String) request.getAttribute("loanStatus");
    if (currentLoanStatus == null) currentLoanStatus = "";

    DateTimeFormatter date = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    DateTimeFormatter dueDate = DateTimeFormatter.ofPattern("dd MMM yyyy");
    Map<String, String> results = Map.of(
        "posted", "Transaction recorded successfully in ledger and audit log.",
        "duplicate", "This transaction was already recorded.",
        "insufficient", "Insufficient balance in account to complete that repayment.",
        "unavailable", "Operation rejected: requires an active account and active customer.",
        "invalid_amount", "Check the amount, loan terms, or current total due. Refresh before retrying a settlement.",
        "flagged", "Account flagged. It is now listed in the review queue.",
        "flag_unchanged", "This review flag is already set or the account no longer exists."
    );
    String result = request.getParameter("result");
    String message = result == null ? "" : results.getOrDefault(result, "");
    boolean posted = "posted".equals(result) || "duplicate".equals(result) || "flagged".equals(result);
    boolean canPost = account == null ? (activeAccounts != null && !activeAccounts.isEmpty()) : Boolean.TRUE.equals(request.getAttribute("canPost"));

    CustomerSummary financeCustomer = (CustomerSummary) request.getAttribute("financeCustomer");
    String financeCustomerName = (String) request.getAttribute("financeCustomerName");
    List<CustomerDetails> matches = (List<CustomerDetails>) request.getAttribute("matches");
    List<BankAccount> customerAccounts = (List<BankAccount>) request.getAttribute("customerAccounts");
    FinanceDao.LendingTotals selectedTotals = (FinanceDao.LendingTotals) request.getAttribute("selectedTotals");
    String search = request.getParameter("q");
    boolean selected = account != null || financeCustomer != null;
    BigDecimal totalLent = selectedTotals != null ? selectedTotals.lent() : totals.lent();
    BigDecimal totalDebt = selectedTotals != null ? selectedTotals.outstanding() : totals.outstanding();
    BigDecimal totalRecovered = totalLent.subtract(totalDebt);
    if (totalRecovered.signum() < 0) totalRecovered = BigDecimal.ZERO;


    int recoveredPct = totalLent.signum() == 0 ? 0 : totalRecovered.multiply(BigDecimal.valueOf(100))
            .divide(totalLent, 0, java.math.RoundingMode.HALF_UP).intValue();
    String scopeParam = account != null ? "&amp;accountId=" + account.id()
            : financeCustomer != null ? "&amp;customerId=" + financeCustomer.id() : "";
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Balances &amp; Loans | PROERP Admin</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/app.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-console.css?v=workspace">
</head>
<body class="admin-shell">
<%@ include file="navigation.jspf" %>
<main id="admin-main" tabindex="-1" class="dashboard">
    <header class="console-heading">
        <div class="heading-split">
            <div>
                <% if (account != null) { %>
                    <a class="back-link" href="<%= context %>/admin/finance">All Balances &amp; Loans</a>
                    <h1><%= Html.escape(account.customer()) %> &middot; <%= Html.escape(account.number()) %></h1>
                    <p><%= account.type() %> Account &middot; Balance: <strong>₹<%= account.balance().toPlainString() %></strong> &middot; Status: <span class="badge <%= "FROZEN".equals(account.status()) ? "badge-suspended" : "badge-active" %>"><%= account.status() %></span></p>
                <% } else { %>
                    <h1>Balances &amp; Loans</h1>
                    <p>Choose a customer to manage their accounts and loans.</p>
                <% } %>
            </div>
            <% if (account != null) { %>
                <div class="console-actions">
                    <a class="button button-outline" href="<%= context %>/admin/customer?id=<%= account.customerId() %>">Customer profile</a>
                </div>
            <% } else { %>
                <a class="button button-outline" href="<%= context %>/admin/requests">Review loan applications</a>
            <% } %>
        </div>
    </header>

    <% if (!message.isEmpty()) { %>
        <p class="notice <%= posted ? "success" : "error" %>" role="<%= posted ? "status" : "alert" %>"><%= Html.escape(message) %></p>
    <% } %>

    <section class="panel workspace-picker" aria-labelledby="find-customer-heading">
        <div class="console-panel-heading"><h2 id="find-customer-heading">Find a customer</h2>
            <% if (selected) { %><a href="<%= context %>/admin/finance">Clear selection</a><% } %>
        </div>
        <form class="workspace-search" action="<%= context %>/admin/finance" method="get">
            <label for="finance-search">Name, username or email</label>
            <div class="console-actions"><input id="finance-search" name="q" type="search" maxlength="100" value="<%= Html.escape(search) %>" required>
                <button class="button" type="submit">Search</button></div>
        </form>
        <% if (matches != null) {
               if (matches.isEmpty()) { %><p class="workspace-empty">No matching customers.</p><% }
               else { %><ul class="overview-list"><% for (CustomerDetails match : matches) { %>
                    <li class="queue-item">
                        <a class="workspace-record-link" href="<%= context %>/admin/finance?customerId=<%= match.id() %>"><%= Html.escape(match.fullName() == null ? match.username() : match.fullName()) %></a>
                        <span><%= match.accounts().size() %> accounts | ₹<%= match.totalBalance().toPlainString() %></span>
                    </li>
                <% } %></ul><% }
           } %>
        <% if (financeCustomer != null) { %>
            <div class="workspace-selection">
                <h3><a href="<%= context %>/admin/customer?id=<%= financeCustomer.id() %>"><%= Html.escape(financeCustomerName == null ? financeCustomer.username() : financeCustomerName) %></a></h3>
                <% if (customerAccounts == null || customerAccounts.isEmpty()) { %>
                    <p class="muted">This customer has no bank accounts.</p>
                <% } else { %>
                    <div class="workspace-tags"><% for (BankAccount row : customerAccounts) { %>
                        <a class="button button-outline button-small" href="<%= context %>/admin/finance?accountId=<%= row.id() %>"><%= row.type() %> <%= Html.escape(row.number()) %> | ₹<%= row.balance().toPlainString() %></a>
                    <% } %></div>
                <% } %>
            </div>
        <% } %>
    </section>

    <section class="panel console-money" aria-labelledby="principal-heading">
        <div class="console-panel-heading"><h2 id="principal-heading">Loan principal</h2><span class="muted"><%= selected ? "Selected record" : "All customers" %></span></div>
        <div class="console-money-grid">
            <div><span>Paid out</span><strong>₹<%= totalLent.toPlainString() %></strong></div>
            <div><span>Repaid</span><strong>₹<%= totalRecovered.toPlainString() %></strong></div>
            <div><span>Outstanding</span><strong>₹<%= totalDebt.toPlainString() %></strong></div>
        </div>
        <% if (totalLent.signum() > 0) { %>
            <div class="workspace-principal-bar" aria-hidden="true">
                <span class="principal-repaid" style="width:<%= recoveredPct %>%"></span>
                <span class="principal-remaining" style="width:<%= 100 - recoveredPct %>%"></span>
            </div>
            <p class="workspace-subtitle"><%= recoveredPct %>% of disbursed principal repaid</p>
        <% } %>
    </section>

    <% if (selected) { %>
    <section class="panel console-money" aria-labelledby="flag-heading">
        <div class="console-panel-heading"><h2 id="flag-heading">Flag an account</h2><a href="<%= context %>/admin/queue">Review queue</a></div>
        <% if (account != null || (customerAccounts != null && !customerAccounts.isEmpty())) { %>
            <form class="console-money-form" method="post" action="<%= context %>/admin/finance">
                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                <input type="hidden" name="operation" value="FLAG">
                <% if (account != null) { %>
                    <input type="hidden" name="accountId" value="<%= account.id() %>">
                <% } else { %>
                    <div><label for="flag-account">Account</label><select id="flag-account" name="accountId" required>
                        <% for (BankAccount row : customerAccounts) { %><option value="<%= row.id() %>"><%= Html.escape(row.number()) %></option><% } %>
                    </select></div>
                <% } %>
                <div><label for="flag-category">Issue</label><select id="flag-category" name="category">
                    <% for (AccountReviewDao.Category category : AccountReviewDao.Category.values()) { %>
                        <option value="<%= category.name() %>"><%= category.label() %></option>
                    <% } %>
                </select></div>
                <button class="button button-outline" type="submit">Save flag</button>
            </form>
        <% } else { %><p class="workspace-empty">A bank account is required to create a flag.</p><% } %>
    </section>
    <% } %>

    <!-- OPERATIONAL ACTIONS: DISBURSE LOAN & RECORD DEPOSIT -->
    <% if (selected) { %>
    <div class="workspace-grid console-operations-grid">
        <!-- DISBURSE LOAN PANEL -->
        <section class="panel console-money">
            <div class="console-panel-heading">
                <h2>Assign loan</h2>
            </div>
            <p class="muted">Disburse a loan directly into an active customer's bank account. Increases account balance and records debt.</p>
            <% if (canPost) { %>
                <form class="console-money-form-stacked" method="post" action="<%= context %>/admin/finance">
                    <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                    <input type="hidden" name="requestKey" value="<%= UUID.randomUUID() %>">
                    <input type="hidden" name="operation" value="LOAN_DISBURSEMENT">

                    <% if (account != null) { %>
                        <input type="hidden" name="accountId" value="<%= account.id() %>">
                        <div class="field-item">
                            <label>Target Account</label>
                            <div class="account-fixed-badge"><%= Html.escape(account.customer()) %> &middot; <%= Html.escape(account.number()) %></div>
                        </div>
                    <% } else { %>
                        <div class="field-item">
                            <label for="disburseAccountId">Select Customer Account</label>
                            <select id="disburseAccountId" name="accountId" required>
                                <option value="">Select customer &amp; account...</option>
                                <% if (activeAccounts != null) { for (BankAccount row : activeAccounts) { %>
                                    <option value="<%= row.id() %>"><%= Html.escape(row.customer()) %> &middot; <%= row.type() %> (<%= Html.escape(row.number()) %>) &middot; Bal: ₹<%= row.balance().toPlainString() %></option>
                                <% } } %>
                            </select>
                        </div>
                    <% } %>

                    <div class="field-item">
                        <label for="loanAmount">Loan Principal (INR)</label>
                        <input id="loanAmount" name="amount" inputmode="decimal" placeholder="e.g. 50000.00" maxlength="12" required>
                    </div>
                    <div class="field-item">
                        <label for="disbursePurpose">Loan purpose and annual rate</label>
                        <select id="disbursePurpose" name="purpose" required>
                            <% for (LoanTerms.Plan plan : LoanTerms.PLANS) { %><option value="<%= Html.escape(plan.purpose()) %>"><%= Html.escape(plan.purpose()) %> (<%= plan.annualRate().toPlainString() %>% yearly)</option><% } %>
                        </select>
                    </div>
                    <div class="field-item">
                        <label for="disburseDuration">Repayment duration</label>
                        <select id="disburseDuration" name="duration" required>
                            <% for (int months : LoanTerms.DURATIONS) { %><option value="<%= months %>" <%= months == 12 ? "selected" : "" %>><%= months %> months</option><% } %>
                        </select>
                    </div>

                    <button class="button" type="submit">Disburse Loan to Account</button>
                </form>
            <% } else { %>
                <p class="empty-state">No eligible active accounts available for loan origination.</p>
            <% } %>
        </section>

        <!-- RECORD DEPOSIT PANEL -->
        <section class="panel console-money">
            <div class="console-panel-heading">
                <h2>Record deposit</h2>
            </div>
            <p class="muted">Credit funds to a customer's account to increase account balance.</p>
            <% if (canPost) { %>
                <form class="console-money-form-stacked" method="post" action="<%= context %>/admin/finance">
                    <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                    <input type="hidden" name="requestKey" value="<%= UUID.randomUUID() %>">
                    <input type="hidden" name="operation" value="DEPOSIT">

                    <% if (account != null) { %>
                        <input type="hidden" name="accountId" value="<%= account.id() %>">
                        <div class="field-item">
                            <label>Target Account</label>
                            <div class="account-fixed-badge"><%= Html.escape(account.customer()) %> &middot; <%= Html.escape(account.number()) %></div>
                        </div>
                    <% } else { %>
                        <div class="field-item">
                            <label for="depositAccountId">Select Customer Account</label>
                            <select id="depositAccountId" name="accountId" required>
                                <option value="">Select customer &amp; account...</option>
                                <% if (activeAccounts != null) { for (BankAccount row : activeAccounts) { %>
                                    <option value="<%= row.id() %>"><%= Html.escape(row.customer()) %> &middot; <%= row.type() %> (<%= Html.escape(row.number()) %>) &middot; Bal: ₹<%= row.balance().toPlainString() %></option>
                                <% } } %>
                            </select>
                        </div>
                    <% } %>

                    <div class="field-item">
                        <label for="depositAmount">Deposit Amount (INR)</label>
                        <input id="depositAmount" name="amount" inputmode="decimal" placeholder="e.g. 25000.00" maxlength="12" required>
                    </div>

                    <button class="button button-outline" type="submit">Record Deposit</button>
                </form>
            <% } else { %>
                <p class="empty-state">No eligible active accounts available for deposits.</p>
            <% } %>
        </section>
    </div>

    <% } %>
    <!-- LOANS DIRECTORY TABLE -->
    <section class="table-card console-money" id="loan-register">
        <div class="table-heading">
            <div>
                <h2>Loan register</h2>
                <span class="muted"><%= loans == null ? 0 : loans.size() %> loans in this view</span>
            </div>
            <nav class="console-tabs" aria-label="Filter loans by status">

                <a href="<%= context %>/admin/finance?loanStatus=<%= scopeParam %>" <%= currentLoanStatus.isEmpty() ? "aria-current=\"page\"" : "" %>>All Loans</a>
                <a href="<%= context %>/admin/finance?loanStatus=OPEN<%= scopeParam %>" <%= "OPEN".equalsIgnoreCase(currentLoanStatus) ? "aria-current=\"page\"" : "" %>>Active Loans</a>
                <a href="<%= context %>/admin/finance?loanStatus=PAID<%= scopeParam %>" <%= "PAID".equalsIgnoreCase(currentLoanStatus) ? "aria-current=\"page\"" : "" %>>Fully Settled</a>
            </nav>
        </div>
        <div class="table-scroll">
            <table>
                <thead>
                    <tr>
                        <th scope="col">Loan Ref</th>
                        <th scope="col">Borrower &amp; Account</th>
                        <th scope="col">Paid out</th>
                        <th scope="col">Terms</th>
                        <th scope="col">Total due</th>
                        <th scope="col">Actions</th>
                    </tr>
                </thead>
                <tbody>
                <% if (loans != null) { for (FinanceDao.Loan loan : loans) { 
                       boolean isSettled = loan.totalDue().signum() == 0;
                %>
                    <tr id="loan-<%= loan.id() %>">
                        <td>
                            <strong>#LN-<%= loan.id() %></strong>
                            <small class="record-id"><%= loan.date().format(date) %></small>
                        </td>
                        <td>
                            <a href="<%= context %>/admin/finance?accountId=<%= loan.accountId() %>"><strong><%= Html.escape(loan.customer()) %></strong></a>
                            <small class="record-id"><%= Html.escape(loan.account()) %></small>
                        </td>
                        <td>₹<%= loan.principal().toPlainString() %></td>
                        <td><% if (loan.terms() == null) { %>Principal only<% } else { %>
                            <strong><%= loan.terms().months() %> months at <%= loan.terms().annualRate().toPlainString() %>% yearly</strong>
                            <small class="record-id">&#8377;<%= loan.terms().monthly(loan.principal()).toPlainString() %> scheduled monthly</small>
                            <small class="record-id">Matures <%= loan.maturityDate().format(dueDate) %></small>
                        <% } %></td>
                        <td>
                            <% if (isSettled) { %>
                                <span class="badge badge-active">Settled (₹0.00)</span>
                            <% } else { %>
                                <strong class="text-danger">&#8377;<%= loan.totalDue().toPlainString() %></strong>
                                <small class="record-id">&#8377;<%= loan.outstanding().toPlainString() %> principal</small>
                                <% if (loan.terms() != null) { %><small class="record-id">&#8377;<%= loan.interestDue().toPlainString() %> interest</small><% } %>
                            <% } %>
                        </td>
                        <td>
                            <% if (isSettled) { %>
                                <span class="muted">Fully Paid &amp; Closed</span>
                            <% } else if (!loan.canRepay()) { %>
                                <span class="muted">Account Inactive / Frozen</span>
                            <% } else { %>
                                <div class="loan-action-flex">
                                    <!-- Settle loan Button -->
                                    <form method="post" action="<%= context %>/admin/finance" class="inline-settle-form">
                                        <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                                        <input type="hidden" name="requestKey" value="<%= UUID.randomUUID() %>">
                                        <input type="hidden" name="accountId" value="<%= loan.accountId() %>">
                                        <input type="hidden" name="loanId" value="<%= loan.id() %>">
                                        <input type="hidden" name="operation" value="LOAN_REPAYMENT">
                                        <input type="hidden" name="settle" value="1">
                                        <input type="hidden" name="amount" value="<%= loan.totalDue().toPlainString() %>">
                                        <button class="button button-small" type="submit" onclick="return confirm('Settle this loan in full for &#8377;<%= loan.totalDue().toPlainString() %> from customer account?');">
                                            Settle loan (&#8377;<%= loan.totalDue().toPlainString() %>)
                                        </button>
                                    </form>

                                    <!-- Partial Repayment Toggle -->
                                    <details class="console-repayment">
                                        <summary>Partial Repayment</summary>
                                        <form method="post" action="<%= context %>/admin/finance">
                                            <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                                            <input type="hidden" name="requestKey" value="<%= UUID.randomUUID() %>">
                                            <input type="hidden" name="accountId" value="<%= loan.accountId() %>">
                                            <input type="hidden" name="loanId" value="<%= loan.id() %>">
                                            <input type="hidden" name="operation" value="LOAN_REPAYMENT">
                                            <label for="repay-<%= loan.id() %>">Amount to debit (INR)</label>
                                            <input id="repay-<%= loan.id() %>" name="amount" inputmode="decimal" maxlength="20" placeholder="Max &#8377;<%= loan.totalDue().toPlainString() %>" required>
                                            <button class="button button-outline" type="submit">Post Repayment</button>
                                        </form>
                                    </details>
                                </div>
                            <% } %>
                        </td>
                    </tr>
                <% } } if (loans == null || loans.isEmpty()) { %>
                    <tr><td colspan="6" class="empty-state">No loans found matching this filter.</td></tr>
                <% } %>
                </tbody>
            </table>
        </div>
    </section>

    <section class="table-card console-money" id="account-activity">
        <div class="table-heading">
            <h2>Recent account activity</h2>
            <span class="muted">Latest 30 entries</span>
        </div>
        <div class="table-scroll">
            <table>
                <thead>
                    <tr>
                        <th scope="col">Timestamp</th>
                        <th scope="col">Customer &amp; Account</th>
                        <th scope="col">Operation</th>
                        <th scope="col">Amount</th>
                        <th scope="col">Balance After</th>
                    </tr>
                </thead>
                <tbody>
                <% if (movements != null) { for (Activity movement : movements) {
                       boolean isCredit = !movement.debit();
                %>
                    <tr>
                        <td><%= movement.postedAt().format(date) %></td>
                        <td>
                            <strong><%= Html.escape(movement.customer()) %></strong>
                            <small class="record-id"><a href="<%= context %>/admin/finance?accountId=<%= movement.accountId() %>#account-activity"><%= Html.escape(movement.account()) %></a></small>
                        </td>
                        <td>
                            <span class="badge <%= isCredit ? "badge-active" : "badge-suspended" %>">
                                <%= movement.label() %>
                            </span>
                            <small class="record-id"><%= Html.escape(movement.reference()) %></small>
                            <% if (movement.description() != null) { %>
                                <small class="record-id"><%= Html.escape(movement.description()) %></small>
                            <% } %>
                        </td>
                        <td>
                            <strong class="<%= isCredit ? "text-success" : "text-danger" %>">
                                <%= isCredit ? "+" : "-" %>₹<%= movement.amount().toPlainString() %>
                            </strong>
                        </td>
                        <td><strong>₹<%= movement.balance().toPlainString() %></strong></td>
                    </tr>
                <% } } if (movements == null || movements.isEmpty()) { %>
                    <tr><td colspan="5" class="empty-state">No transaction movements recorded.</td></tr>
                <% } %>
                </tbody>
            </table>
        </div>
    </section>
</main>
</body>
</html>
