<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.math.BigDecimal,java.time.format.DateTimeFormatter,com.netbanking.model.CustomerSummary,com.netbanking.model.AdminAuditEntry,com.netbanking.model.BankAccount,com.netbanking.dao.FinanceDao,com.netbanking.util.Html" %>
<%@ page import="com.netbanking.model.CustomerProfile,com.netbanking.model.AccountChoice" %>
<%
    CustomerSummary customer = (CustomerSummary) request.getAttribute("customer");
    CustomerProfile profile = (CustomerProfile) request.getAttribute("profile");
    com.netbanking.model.KycStatus kyc = (com.netbanking.model.KycStatus) request.getAttribute("kycStatus");
    List<BankAccount> accounts = (List<BankAccount>) request.getAttribute("accounts");
    List<FinanceDao.Loan> loans = (List<FinanceDao.Loan>) request.getAttribute("loans");
    List<AdminAuditEntry> history = (List<AdminAuditEntry>) request.getAttribute("entries");
    Long nextPending = (Long) request.getAttribute("nextPendingId");
    AccountChoice requestedAccounts = (AccountChoice) request.getAttribute("requestedAccounts");
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("dd MMM yyyy");
    String context = request.getContextPath();
    String username = customer.username();
    String fullName = profile != null && profile.fullName() != null && !profile.fullName().isBlank() ? profile.fullName() : username;

    boolean pending = "PENDING".equals(customer.status());
    boolean active = "ACTIVE".equals(customer.status());
    String statusLabel = pending ? "Pending review" : active ? "Active" : "Suspended";
    String action = pending ? "approve" : active ? "suspend" : "reactivate";
    String actionLabel = pending ? "Approve customer" : active ? "Suspend access" : "Restore access";

    BigDecimal totalBalance = BigDecimal.ZERO;
    if (accounts != null) {
        for (BankAccount a : accounts) totalBalance = totalBalance.add(a.balance());
    }

    BigDecimal totalDebt = BigDecimal.ZERO;
    long activeLoanCount = 0;
    if (loans != null) {
        for (FinanceDao.Loan l : loans) {
            totalDebt = totalDebt.add(l.outstanding());
            if (l.outstanding().signum() > 0) activeLoanCount++;
        }
    }

    String result = request.getParameter("result");
    String balanceAccount = request.getParameter("balanceAccount");
    boolean balanceFeedback = result != null && result.startsWith("balance-");
    String balanceMessage = "";
    switch (balanceFeedback ? result : "") {
        case "balance-posted": balanceMessage = "Balance updated and recorded in activity."; break;
        case "balance-duplicate": balanceMessage = "This balance adjustment was already recorded."; break;
        case "balance-stale_balance": balanceMessage = "The balance changed while you were editing. Review the current balance and try again."; break;
        case "balance-unchanged": balanceMessage = "The balance is already set to that amount."; break;
        case "balance-unavailable": balanceMessage = "Balance editing requires an active account and active customer access."; break;
        case "balance-invalid_amount": balanceMessage = "Enter a nonnegative amount with up to two decimal places."; break;
        default: break;
    }
    boolean balanceSaved = List.of("balance-posted", "balance-duplicate", "balance-unchanged").contains(result == null ? "" : result);
    java.text.NumberFormat moneyFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("en-IN"));
    moneyFormat.setMinimumFractionDigits(2);
    moneyFormat.setMaximumFractionDigits(2);
    boolean statusChanged = result != null && List.of("approved", "suspended", "reactivated").contains(result);
    String message;
    switch (result == null ? "" : result) {
        case "created": message = "Customer record created."; break;
        case "approved": message = "Customer approved."; break;
        case "previous-approved": message = "Previous customer approved. This is the next pending record."; break;
        case "suspended": message = "Customer access suspended."; break;
        case "reactivated": message = "Customer access restored."; break;
        case "unchanged": message = "The status was already changed by another admin. Refresh and review."; break;
        case "reason": message = "Notes can contain up to 140 characters."; break;
        case "email-updated": message = "Email updated and recorded in compliance log."; break;
        case "email-unchanged": message = "This is already the registered email address."; break;
        case "email-duplicate": message = "That email address is already registered to another user."; break;
        case "email-stale": message = "Email was changed in another session. Please reload."; break;
        case "invalid-email": message = "Enter a valid email address."; break;
        case "profile-updated": message = "Customer identity details updated."; break;
        case "invalid-profile": message = "Enter a full name and a valid phone number. Check the mailing address lengths."; break;
        case "account-opened": message = "Bank account opened successfully. Balance initialized to ₹0.00."; break;
        case "account-updated": message = "Bank account status updated and recorded in compliance trail."; break;
        case "account-unavailable": message = "Could not open account: customer must be active and can only have one account of each type."; break;
        default: message = ""; break;
    }
    boolean emailFeedback = result != null && (result.startsWith("email-") || "invalid-email".equals(result));
    boolean profileFeedback = result != null && (result.startsWith("profile-") || "invalid-profile".equals(result));
    boolean success = statusChanged || "email-updated".equals(result) || "profile-updated".equals(result)
            || "previous-approved".equals(result) || "created".equals(result) || "account-opened".equals(result)
            || "account-updated".equals(result);

    boolean hasSavings = accounts != null && accounts.stream().anyMatch(a -> "SAVINGS".equals(a.type()));
    boolean hasCurrent = accounts != null && accounts.stream().anyMatch(a -> "CURRENT".equals(a.type()));
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= Html.escape(fullName) %> (Customer profile) | PROERP Admin</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/app.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/customer-record.css?v=onboarding">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-console.css?v=profile">
</head>
<body class="admin-shell customer-record">
<%@ include file="navigation.jspf" %>
<main id="admin-main" tabindex="-1" class="dashboard">
    <nav class="record-breadcrumb" aria-label="Breadcrumb">
        <a href="<%= context %>/admin/customers">Customers and accounts</a>
        <span aria-hidden="true">/</span>
        <span aria-current="page">Record #<%= customer.id() %></span>
    </nav>

    <header class="profile-header">
        <div class="profile-identity">
            <div class="profile-initial" aria-hidden="true"><%= Html.escape(fullName.isEmpty() ? "?" : fullName.substring(0, fullName.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT)) %></div>
            <div>
                <p class="profile-record-id">Customer #<%= customer.id() %></p>
                <h1><%= Html.escape(fullName) %></h1>
                <p class="profile-contact"><%= customer.email() == null || customer.email().isBlank() ? "No email on file" : Html.escape(customer.email()) %></p>
            </div>
        </div>
        <nav class="profile-jumps" aria-label="Customer record sections">
            <a href="#profile-details">Details</a>
            <a href="#customer-accounts">Accounts</a>
            <a href="#customer-loans">Loans</a>
            <a href="#customer-history">Activity</a>
        </nav>
    </header>

    <dl class="profile-glance" aria-label="Customer at a glance">
        <div><dt>Access</dt><dd><span class="badge badge-<%= customer.status().toLowerCase(java.util.Locale.ROOT) %>"><%= statusLabel %></span></dd></div>
        <div><dt>Identity verification (KYC)</dt><dd><span class="badge kyc-<%= kyc.name().toLowerCase(java.util.Locale.ROOT) %>"><%= kyc.label() %></span></dd></div>
        <div><dt>Account balance</dt><dd>
            <% if (accounts == null || accounts.isEmpty()) { %>No accounts
            <% } else { %>₹<%= totalBalance.setScale(2).toPlainString() %><small><%= accounts.size() == 1 ? "1 linked account" : accounts.size() + " linked accounts" %></small><% } %>
        </dd></div>
        <div><dt>Loan principal due</dt><dd>
            <% if (loans == null || loans.isEmpty()) { %>No loans
            <% } else if (activeLoanCount == 0) { %>All loans settled
            <% } else { %>₹<%= totalDebt.setScale(2).toPlainString() %><small><%= activeLoanCount == 1 ? "1 open loan" : activeLoanCount + " open loans" %></small><% } %>
        </dd></div>
    </dl>

    <% if (!message.isEmpty()) { %>
        <p class="notice <%= success ? "success" : "error" %>" role="<%= success ? "status" : "alert" %>"><%= Html.escape(message) %></p>
    <% } %>

    <section class="panel profile-access <%= pending ? "profile-access-pending" : "" %>" aria-labelledby="access-heading">
        <div>
            <h2 id="access-heading"><%= pending ? "Review customer access" : "Manage customer access" %></h2>
            <p><%= pending ? (requestedAccounts == null ? "No account type was requested."
                        : "Requested at registration or setup: " + requestedAccounts.label() + ".")
                    : active ? "Suspending access blocks new financial postings for this customer. Account freezes are managed separately."
                    : "Restoring access leaves any frozen accounts frozen." %></p>
        </div>
        <form method="post" action="<%= context %>/admin/customers" class="profile-access-actions">
            <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
            <input type="hidden" name="customerId" value="<%= customer.id() %>">
            <input type="hidden" name="action" value="<%= action %>">
            <% if (pending) { %>
                <label class="profile-account-decision" for="accountChoice">Accounts to open on approval
                    <select id="accountChoice" name="accountChoice" required>
                        <% for (AccountChoice choice : AccountChoice.values()) { %>
                            <option value="<%= choice.name() %>" <%= choice == (requestedAccounts == null ? AccountChoice.NONE : requestedAccounts) ? "selected" : "" %>><%= choice == AccountChoice.NONE ? "Approve access only" : choice.label() %></option>
                        <% } %>
                    </select>
                </label>
            <% } %>
            <button class="button <%= active ? "button-outline profile-suspend" : "" %>" type="submit"><%= actionLabel %></button>
            <% if (pending) { %>
                <button class="button button-outline" type="submit" name="continueReview" value="1">Approve and continue</button>
                <% if (nextPending != null) { %>
                    <a class="profile-skip-link" href="<%= context %>/admin/customer?id=<%= nextPending %>">Skip to next pending</a>
                <% } %>
            <% } %>
        </form>
    </section>

    <div class="record-grid profile-sections">
        <section class="panel record-panel profile-details" id="profile-details">
            <div class="record-heading">
                <h2>Contact details</h2>
            </div>
            <dl class="record-facts">
                <div><dt>Username</dt><dd>@<%= Html.escape(username) %></dd></div>
                <div><dt>Phone</dt><dd><%= profile != null && profile.phone() != null ? Html.escape(profile.phone()) : "Not set" %></dd></div>
                <div><dt>Registered</dt><dd><%= customer.createdAt() == null ? "Unavailable" : customer.createdAt().format(dateFormat) %></dd></div>
            </dl>

            <details class="record-edit" <%= profileFeedback && !success ? "open" : "" %>>
                <summary>Edit name, phone and mailing address</summary>
                <form method="post" action="<%= context %>/admin/customer">
                    <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                    <input type="hidden" name="action" value="update-profile">
                    <input type="hidden" name="id" value="<%= customer.id() %>">
                    <label for="fullName">Full Name</label>
                    <input type="text" name="fullName" id="fullName" maxlength="100" value="<%= Html.escape(profile == null ? "" : profile.fullName()) %>" required>
                    <label for="phone">Phone Number</label>
                    <input type="tel" name="phone" id="phone" maxlength="25" value="<%= Html.escape(profile != null ? profile.phone() : "") %>" required>
                    <label for="addressLine">Mailing address</label>
                    <input name="addressLine" id="addressLine" maxlength="160" value="<%= Html.escape(profile != null ? profile.addressLine() : "") %>">
                    <label for="city">City</label>
                    <input name="city" id="city" maxlength="80" value="<%= Html.escape(profile != null ? profile.city() : "") %>">
                    <label for="region">State or region</label>
                    <input name="region" id="region" maxlength="80" value="<%= Html.escape(profile != null ? profile.region() : "") %>">
                    <label for="postalCode">Postal code</label>
                    <input name="postalCode" id="postalCode" maxlength="12" value="<%= Html.escape(profile != null ? profile.postalCode() : "") %>">
                    <button class="button button-outline" type="submit">Save Profile</button>
                </form>
            </details>

            <details class="record-edit" <%= emailFeedback && !success ? "open" : "" %>>
                <summary>Edit email</summary>
                <form method="post" action="<%= context %>/admin/customer">
                    <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                    <input type="hidden" name="id" value="<%= customer.id() %>">
                    <input type="hidden" name="originalEmail" value="<%= Html.escape(customer.email()) %>">
                    <label for="email">Customer Email</label>
                    <input type="email" name="email" id="email" maxlength="254" value="<%= Html.escape(customer.email()) %>" required>
                    <button class="button button-outline" type="submit">Save Email</button>
                </form>
            </details>
            <% if (active || "customer-password-unavailable".equals(result)) { %>
                <details class="record-edit" <%= result != null && (result.startsWith("customer-password-") || "invalid-customer-password".equals(result)) ? "open" : "" %>>
                    <summary>Set customer sign-in password</summary>
                    <% if ("customer-password-set".equals(result)) { %>
                        <p class="notice success" role="status">Temporary password set. The customer must change it at first sign-in.</p>
                    <% } else if ("invalid-customer-password".equals(result)) { %>
                        <p class="notice error" role="alert">Enter a temporary password of 10 to 128 characters.</p>
                    <% } else if ("customer-password-unavailable".equals(result)) { %>
                        <p class="notice error" role="alert">Activate customer access before setting a password.</p>
                    <% } %>
                    <% if (active) { %>
                        <form method="post" action="<%= context %>/admin/customer">
                            <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                            <input type="hidden" name="action" value="reset-password">
                            <input type="hidden" name="id" value="<%= customer.id() %>">
                            <label for="temporaryPassword">Temporary password</label>
                            <input type="password" name="temporaryPassword" id="temporaryPassword" autocomplete="new-password"
                                   minlength="10" maxlength="128" required>
                            <p class="record-hint">Setting a temporary password signs the customer out of existing sessions.</p>
                            <button class="button button-outline" type="submit">Set temporary password</button>
                        </form>
                    <% } %>
                </details>
            <% } %>
        </section>

        <section class="panel record-panel" id="customer-accounts">
            <div class="record-heading">
                <h2>Bank accounts</h2>
                <span class="muted"><%= accounts == null ? 0 : accounts.size() %> of 2 account slots used</span>
            </div>

            <% if (accounts == null || accounts.isEmpty()) { %>
                <p class="record-empty">
                    <%= active ? "No bank accounts opened." : "No bank accounts. Approve access to open one." %>
                </p>
            <% } else { %>
                <ul class="record-accounts">
                <% for (BankAccount acc : accounts) {
                       boolean isFrozen = "FROZEN".equals(acc.status());
                       boolean canEditBalance = active && "ACTIVE".equals(acc.status());
                       boolean showBalanceFeedback = Long.toString(acc.id()).equals(balanceAccount) && !balanceMessage.isEmpty();
                %>
                    <li class="profile-bank-account" id="account-<%= acc.id() %>">
                        <div class="profile-bank-summary">
                            <div>
                                <div class="profile-bank-title">
                                    <h3><%= "SAVINGS".equals(acc.type()) ? "Savings account" : "Current account" %></h3>
                                    <span class="badge <%= isFrozen ? "badge-suspended" : "badge-active" %>"><%= isFrozen ? "Frozen" : "Active" %></span>
                                </div>
                                <small>Account number <%= Html.escape(acc.number()) %></small>
                                <% if (acc.openedAt() != null) { %><small>Opened <%= acc.openedAt().format(dateOnly) %></small><% } %>
                            </div>
                            <div class="profile-bank-balance">
                                <small>Current balance</small>
                                <strong>₹<%= moneyFormat.format(acc.balance()) %></strong>
                            </div>
                        </div>
                        <div class="profile-bank-controls">
                            <% if (showBalanceFeedback) { %>
                                <p class="notice <%= balanceSaved ? "success" : "error" %>" role="<%= balanceSaved ? "status" : "alert" %>"><%= Html.escape(balanceMessage) %></p>
                            <% } %>
                            <% if (canEditBalance) { %>
                                <details class="profile-balance-edit" <%= showBalanceFeedback && !balanceSaved ? "open" : "" %>>
                                    <summary>Edit balance</summary>
                                    <form method="post" action="<%= context %>/admin/finance">
                                        <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                                        <input type="hidden" name="operation" value="ADJUST_BALANCE">
                                        <input type="hidden" name="requestKey" value="<%= java.util.UUID.randomUUID() %>">
                                        <input type="hidden" name="accountId" value="<%= acc.id() %>">
                                        <input type="hidden" name="expectedBalance" value="<%= acc.balance().toPlainString() %>">
                                        <div>
                                            <label for="balance-<%= acc.id() %>">New total balance (₹)</label>
                                            <input type="text" name="amount" id="balance-<%= acc.id() %>" inputmode="decimal"
                                                   maxlength="20" pattern="[0-9]{1,17}([.][0-9]{1,2})?"
                                                   value="<%= acc.balance().toPlainString() %>" required>
                                        </div>
                                        <button class="button button-small" type="submit">Save balance</button>
                                    </form>
                                </details>
                            <% } else { %>
                                <p class="record-hint"><%= isFrozen ? "Unfreeze this account to edit its balance." : "Restore customer access to edit the balance." %></p>
                            <% } %>
                            <div class="console-actions profile-bank-actions">
                                <a href="<%= context %>/admin/finance?accountId=<%= acc.id() %>">Account activity</a>
                                <form method="post" action="<%= context %>/admin/accounts" class="inline-form">
                                    <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                                    <input type="hidden" name="action" value="<%= isFrozen ? "unfreeze" : "freeze" %>">
                                    <input type="hidden" name="id" value="<%= acc.id() %>">
                                    <button class="button button-outline button-small" type="submit"><%= isFrozen ? "Unfreeze" : "Freeze" %></button>
                                </form>
                            </div>
                        </div>
                    </li>
                <% } %>
                </ul>
            <% } %>

            <% if (active && (!hasSavings || !hasCurrent)) { %>
                <details class="record-edit">
                    <summary>Open bank account</summary>
                    <form method="post" action="<%= context %>/admin/accounts">
                        <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                        <input type="hidden" name="action" value="open">
                        <input type="hidden" name="customerId" value="<%= customer.id() %>">
                        <label for="newAccountType">Account Type</label>
                        <select id="newAccountType" name="type">
                            <% if (!hasSavings) { %><option value="SAVINGS">Savings Account (SB)</option><% } %>
                            <% if (!hasCurrent) { %><option value="CURRENT">Current Account (CA)</option><% } %>
                        </select>
                        <button class="button button-outline" type="submit">Open Account (₹0.00)</button>
                    </form>
                </details>
            <% } %>
        </section>

        <section class="panel record-panel" id="customer-loans">
            <div class="record-heading">
                <h2>Loans</h2>
                <% if (active && accounts != null && !accounts.isEmpty()) { %>
                    <a href="<%= context %>/admin/finance?customerId=<%= customer.id() %>">Manage loans</a>
                <% } %>
            </div>

            <% if (loans == null || loans.isEmpty()) { %>
                <p class="record-empty">No loans on record.</p>
            <% } else { %>
                <ul class="record-accounts">
                <% for (FinanceDao.Loan l : loans) {
                       boolean isSettled = l.outstanding().signum() == 0;
                %>
                    <li class="account-item-card">
                        <div>
                            <strong>#LN-<%= l.id() %></strong>
                            <small class="muted"><%= l.account() %></small>
                            <span class="badge <%= isSettled ? "badge-active" : "badge-loan" %>"><%= isSettled ? "Settled" : "Active Debt" %></span>
                        </div>
                        <div class="account-card-right">
                            <div>
                                <small>Principal: ₹<%= l.principal().toPlainString() %></small>
                                <strong class="<%= isSettled ? "text-success" : "text-danger" %>">&#8377;<%= l.totalDue().toPlainString() %> owed</strong>
                                <% if (l.terms() != null) { %><small><%= l.terms().months() %> months at <%= l.terms().annualRate().toPlainString() %>% yearly</small><% } %>
                            </div>
                            <% if (!isSettled) { %>
                                <a class="button button-outline button-small" href="<%= context %>/admin/finance?accountId=<%= l.accountId() %>#loan-<%= l.id() %>">Manage loan</a>
                            <% } %>
                        </div>
                    </li>
                <% } %>
                </ul>
            <% } %>
        </section>

        <section class="panel record-panel record-panel-full" id="customer-history" aria-labelledby="history-heading">
            <div class="record-heading">
                <h2 id="history-heading">Activity</h2>
                <a href="<%= context %>/admin/audit?q=<%= java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8) %>">View in audit history</a>
            </div>
            <% if (history == null || history.isEmpty()) { %>
                <p class="record-empty">No administrative actions recorded.</p>
            <% } else { %>
                <div class="table-scroll">
                    <table class="profile-history-table" aria-labelledby="history-heading">
                        <thead><tr><th scope="col">Action</th><th scope="col">Administrator</th><th scope="col">Recorded</th></tr></thead>
                        <tbody>
                        <% for (AdminAuditEntry entry : history) { %>
                            <tr>
                                <td><strong><%= Html.escape(entry.action().replace('_', ' ').toLowerCase(java.util.Locale.ROOT)) %></strong>
                                    <small><%= Html.escape(entry.details()) %></small></td>
                                <td><%= Html.escape(entry.adminUsername()) %></td>
                                <td><%= entry.createdAt() == null ? "Unavailable" : entry.createdAt().format(dateFormat) %></td>
                            </tr>
                        <% } %>
                        </tbody>
                    </table>
                </div>
            <% } %>
        </section>
    </div>
</main>
</body>
</html>
