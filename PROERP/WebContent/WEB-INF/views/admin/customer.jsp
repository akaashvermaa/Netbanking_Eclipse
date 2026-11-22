<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.time.format.DateTimeFormatter,com.netbanking.model.CustomerSummary,com.netbanking.model.AdminAuditEntry,com.netbanking.model.BankAccount,com.netbanking.util.Html" %>
<%
    CustomerSummary customer = (CustomerSummary) request.getAttribute("customer");
    List<BankAccount> accounts = (List<BankAccount>) request.getAttribute("accounts");
    List<AdminAuditEntry> history = (List<AdminAuditEntry>) request.getAttribute("entries");
    Long nextPending = (Long) request.getAttribute("nextPendingId");
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    String context = request.getContextPath();
    String username = customer.username();
    boolean pending = "PENDING".equals(customer.status());
    boolean active = "ACTIVE".equals(customer.status());
    String statusLabel = pending ? "Pending review" : active ? "Active" : "Suspended";
    String action = pending ? "approve" : active ? "suspend" : "reactivate";
    String actionLabel = pending ? "Approve customer" : active ? "Suspend access" : "Reactivate access";
    String result = request.getParameter("result");
    boolean statusChanged = result != null && List.of("approved", "suspended", "reactivated").contains(result);
    String message;
    switch (result == null ? "" : result) {
        case "approved": message = "Customer approved. You can now open a bank account."; break;
        case "previous-approved": message = "Previous customer approved. This is the next pending registration."; break;
        case "suspended": message = "Customer access suspended."; break;
        case "reactivated": message = "Customer access restored."; break;
        case "unchanged": message = "The status changed before your action was saved. Review the current status and try again."; break;
        case "reason": message = "Enter a reason between 1 and 140 characters."; break;
        case "email-updated": message = "Email updated and recorded in the activity history."; break;
        case "email-unchanged": message = "This is already the customer's email address."; break;
        case "email-duplicate": message = "That email address is already in use."; break;
        case "email-stale": message = "The email has changed since you opened this page. Review the current address before editing."; break;
        case "invalid-email": message = "Enter a valid email address and a reason between 1 and 140 characters."; break;
        default: message = ""; break;
    }
    boolean emailFeedback = result != null && (result.startsWith("email-") || "invalid-email".equals(result));
    boolean success = statusChanged || "email-updated".equals(result) || "previous-approved".equals(result);
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Customer record | PROERP Admin</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/app.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/customer-record.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-console.css">
</head>
<body class="admin-shell customer-record">
<%@ include file="navigation.jspf" %>
<main id="admin-main" tabindex="-1" class="dashboard">
    <nav class="record-breadcrumb" aria-label="Breadcrumb">
        <a href="<%= context %>/admin/customers">Customers</a><span aria-hidden="true">/</span><span aria-current="page">Record #<%= customer.id() %></span>
    </nav>
    <header class="record-hero">
        <div>
            <div class="record-tags">
                <span class="badge badge-<%= customer.status().toLowerCase(java.util.Locale.ROOT) %>"><%= statusLabel %></span>
                <% if (username.startsWith("demo.")) { %><span class="record-demo">Demo customer</span><% } %>
            </div>
            <h1><%= Html.escape(username) %></h1>
            <p><%= customer.email() == null || customer.email().isBlank() ? "No email on file" : Html.escape(customer.email()) %></p>
        </div>
        <% if (nextPending != null) { %>
            <a class="button record-next" href="<%= context %>/admin/customer?id=<%= nextPending %>">Next pending customer</a>
        <% } else if ("approved".equals(result)) { %>
            <a class="button record-next" href="<%= context %>/admin/customers?status=PENDING">Back to review queue</a>
        <% } %>
    </header>

    <% if (!message.isEmpty()) { %>
        <p class="notice <%= success ? "success" : "error" %>" role="<%= success ? "status" : "alert" %>"><%= Html.escape(message) %></p>
    <% } %>

    <div class="record-grid">
        <section class="panel record-panel">
            <div class="record-heading"><h2>Customer profile</h2></div>
            <dl class="record-facts">
                <div><dt>Customer ID</dt><dd>#<%= customer.id() %></dd></div>
                <div><dt>Registered</dt><dd><%= customer.createdAt() == null ? "Unavailable" : customer.createdAt().format(dateFormat) %></dd></div>
            </dl>
            <details class="record-edit" <%= emailFeedback && !success ? "open" : "" %>>
                <summary>Correct email address</summary>
                <form method="post" action="<%= context %>/admin/customer">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                    <input type="hidden" name="id" value="<%= customer.id() %>">
                    <input type="hidden" name="originalEmail" value="<%= Html.escape(customer.email()) %>">
                    <label for="email">Customer email</label>
                    <input type="email" name="email" id="email" maxlength="254" value="<%= Html.escape(customer.email()) %>" required>
                    <label for="email-reason">Reason for correction</label>
                    <input type="text" id="email-reason" name="reason" maxlength="140" required>
                    <button class="button button-outline" type="submit">Save email</button>
                </form>
            </details>
        </section>

        <section class="panel record-panel record-decision">
            <div class="record-heading"><h2><%= actionLabel %></h2></div>
            <p class="muted"><%= pending ? "Approval marks this customer as active and allows an administrator to open a bank account."
                    : active ? "Suspension restricts customer access. Manage bank account freezes separately."
                    : "Reactivation restores customer access. Any frozen bank accounts remain frozen." %></p>
            <form method="post" action="<%= context %>/admin/customers">
                <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                <input type="hidden" name="customerId" value="<%= customer.id() %>">
                <input type="hidden" name="action" value="<%= action %>">
                <label for="reason">Decision reason</label>
                <textarea id="reason" name="reason" rows="3" maxlength="140" required aria-describedby="reason-help"></textarea>
                <p class="record-hint" id="reason-help">Up to 140 characters. Saved to the audit history.</p>
                <div class="console-actions">
                    <% if (pending) { %>
                        <button class="button" type="submit" name="continueReview" value="1">Approve &amp; continue</button>
                    <% } %>
                    <button class="button <%= active ? "button-danger" : pending ? "button-outline" : "" %>" type="submit"><%= actionLabel %></button>
                </div>
            </form>
        </section>

        <section class="panel record-panel">
            <div class="record-heading">
                <h2>Bank accounts</h2>
                <a href="<%= context %>/admin/accounts?customerId=<%= customer.id() %>"><%= active ? "Manage accounts" : "View accounts" %></a>
            </div>
            <% if (accounts.isEmpty()) { %>
                <p class="record-empty"><%= active ? "No accounts yet. Open a savings or current account from Manage accounts."
                        : pending ? "No accounts yet. Approve the customer before opening an account."
                        : "No accounts yet. Restore customer access before opening an account." %></p>
            <% } else { %>
                <ul class="record-accounts">
                <% for (BankAccount account : accounts) { %>
                    <li>
                        <div>
                            <a href="<%= context %>/admin/accounts?id=<%= account.id() %>"><%= Html.escape(account.number()) %></a>
                            <small><%= "SAVINGS".equals(account.type()) ? "Savings" : "Current" %></small>
                        </div>
                        <div class="record-balance"><strong>₹<%= account.balance().toPlainString() %></strong>
                            <span class="badge <%= "FROZEN".equals(account.status()) ? "badge-suspended" : "badge-active" %>"><%= "FROZEN".equals(account.status()) ? "Frozen" : "Active" %></span></div>
                    </li>
                <% } %>
                </ul>
            <% } %>
        </section>

        <section class="panel record-panel">
            <div class="record-heading"><h2>Activity history</h2></div>
            <% if (history.isEmpty()) { %>
                <p class="record-empty">Approvals, account actions, and profile corrections will appear here.</p>
            <% } else { %>
                <ol class="record-timeline">
                <% for (AdminAuditEntry entry : history) { %>
                    <li>
                        <strong><%= Html.escape(entry.action().replace('_', ' ')) %></strong>
                        <p><%= Html.escape(entry.details()) %></p>
                        <small><%= Html.escape(entry.adminUsername()) %> &middot; <%= entry.createdAt() == null ? "" : entry.createdAt().format(dateFormat) %></small>
                    </li>
                <% } %>
                </ol>
            <% } %>
        </section>
    </div>
</main>
</body>
</html>
