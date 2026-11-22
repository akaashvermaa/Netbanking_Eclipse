<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.time.format.DateTimeFormatter,com.netbanking.model.BankAccount,com.netbanking.model.CustomerSummary,com.netbanking.util.Html" %>
<%
    BankAccount account = (BankAccount) request.getAttribute("account");
    CustomerSummary customer = (CustomerSummary) request.getAttribute("customer");
    List<BankAccount> accounts = (List<BankAccount>) request.getAttribute("accounts");
    String result = request.getParameter("result");
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Bank accounts | PROERP Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-console.css">
</head>
<body class="admin-shell">
<%@ include file="navigation.jspf" %>
<main id="admin-main" tabindex="-1" class="dashboard">
    <% if (account != null) { %>
        <a class="back-link" href="${pageContext.request.contextPath}/admin/accounts?customerId=<%= account.customerId() %>">Customer accounts</a>
        <div class="page-heading">
            <div><p class="eyebrow">BANK ACCOUNT</p><h1><%= Html.escape(account.number()) %></h1></div>
            <span class="badge <%= "FROZEN".equals(account.status()) ? "badge-suspended" : "badge-active" %>"><%= account.status() %></span>
        </div>
    <% } else { %>
        <header class="console-heading">
        <h1>Bank accounts</h1>
        <% if (customer != null) { %>
            <p class="muted">Accounts for <a href="${pageContext.request.contextPath}/admin/customer?id=<%= customer.id() %>"><%= Html.escape(customer.username()) %></a>. <a href="${pageContext.request.contextPath}/admin/accounts">View all accounts</a>.</p>
        <% } else { %>
            <p class="muted">Find accounts by number or customer username.</p>
            <a class="button button-outline" href="${pageContext.request.contextPath}/admin/customers?status=ACTIVE">Find a customer to open an account</a>
        <% } %>
        </header>
    <% } %>

    <% if ("opened".equals(result) || "updated".equals(result)) { %>
        <p class="notice success" role="status"><%= "opened".equals(result) ? "Bank account opened." : "Account status updated." %> The decision was recorded in audit history.</p>
    <% } else if ("unavailable".equals(result) || "unchanged".equals(result)) { %>
        <p class="notice error" role="alert"><%= "unavailable".equals(result) ? "The customer must be active and can have one account of each type." : "The account status already changed. Review its current state." %></p>
    <% } %>

    <% if (account != null) {
           boolean frozen = "FROZEN".equals(account.status());
           String actionLabel = frozen ? "Unfreeze account" : "Freeze account"; %>
        <div class="workspace-grid">
            <section class="panel">
                <h2>Account details</h2>
                <dl class="detail-list">
                    <dt>Customer</dt><dd><a href="${pageContext.request.contextPath}/admin/customer?id=<%= account.customerId() %>"><%= Html.escape(account.customer()) %></a></dd>
                    <dt>Account type</dt><dd><%= account.type() %></dd>
                    <dt>Balance</dt><dd><strong>₹ <%= account.balance().toPlainString() %></strong></dd>
                    <dt>Opened</dt><dd><%= account.openedAt().format(dateFormat) %></dd>
                </dl>
            </section>
            <section class="panel">
                <h2><%= actionLabel %></h2>
                <form method="post" action="${pageContext.request.contextPath}/admin/accounts">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                    <input type="hidden" name="action" value="<%= frozen ? "unfreeze" : "freeze" %>">
                    <input type="hidden" name="id" value="<%= account.id() %>">
                    <label for="reason">Decision reason</label>
                    <textarea id="reason" name="reason" maxlength="140" rows="3" required></textarea>
                    <button class="button <%= frozen ? "" : "button-danger" %>" type="submit"><%= actionLabel %></button>
                </form>
            </section>
        </div>
    <% } else { %>
        <% if (customer != null && "ACTIVE".equals(customer.status())) { %>
            <section class="panel">
                <h2>Open a bank account</h2>
                <p class="muted">An approved customer can have one savings and one current account. New accounts start at ₹0.00.</p>
                <form method="post" action="${pageContext.request.contextPath}/admin/accounts">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
                    <input type="hidden" name="action" value="open">
                    <input type="hidden" name="customerId" value="<%= customer.id() %>">
                    <label for="type">Account type</label>
                    <select id="type" name="type"><option value="SAVINGS">Savings</option><option value="CURRENT">Current</option></select>
                    <label for="reason">Decision reason</label>
                    <textarea id="reason" name="reason" maxlength="140" rows="2" required></textarea>
                    <button class="button" type="submit">Open account</button>
                </form>
            </section>
        <% } else if (customer != null) { %>
            <p class="notice" role="status">The customer must have active access before an account can be opened.</p>
        <% } %>
        <form method="get" class="filter-form" action="${pageContext.request.contextPath}/admin/accounts">
            <% if (customer != null) { %><input type="hidden" name="customerId" value="<%= customer.id() %>"><% } %>
            <label for="q">Search</label>
            <input id="q" name="q" type="search" maxlength="100" value="<%= Html.escape((String) request.getAttribute("search")) %>" placeholder="Account number or username">
            <label for="status">Status</label>
            <select id="status" name="status">
                <option value="">All statuses</option>
                <option value="ACTIVE" ${status == 'ACTIVE' ? 'selected' : ''}>Active</option>
                <option value="FROZEN" ${status == 'FROZEN' ? 'selected' : ''}>Frozen</option>
            </select>
            <button class="button" type="submit">Apply filters</button>
        </form>
        <section class="table-card">
            <div class="table-heading"><h2>Account directory</h2><span>Showing up to 200 accounts</span></div>
            <div class="table-scroll">
                <table>
                    <thead><tr><th scope="col">Account</th><th scope="col">Customer</th><th scope="col">Type</th><th scope="col">Balance</th><th scope="col">Status</th></tr></thead>
                    <tbody>
                    <% for (BankAccount row : accounts) { %>
                        <tr>
                            <td><a href="${pageContext.request.contextPath}/admin/accounts?id=<%= row.id() %>"><%= Html.escape(row.number()) %></a></td>
                            <td><%= Html.escape(row.customer()) %></td><td><%= row.type() %></td>
                            <td>₹ <%= row.balance().toPlainString() %></td>
                            <td><span class="badge <%= "FROZEN".equals(row.status()) ? "badge-suspended" : "badge-active" %>"><%= row.status() %></span></td>
                        </tr>
                    <% } if (accounts.isEmpty()) { %>
                        <tr><td class="empty-state" colspan="5">No accounts match this view.</td></tr>
                    <% } %>
                    </tbody>
                </table>
            </div>
        </section>
    <% } %>
</main>
</body>
</html>
