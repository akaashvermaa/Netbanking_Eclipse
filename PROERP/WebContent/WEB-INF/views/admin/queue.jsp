<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.time.format.DateTimeFormatter,com.netbanking.dao.AccountReviewDao,com.netbanking.util.Html" %>
<%
    String context = request.getContextPath();
    List<AccountReviewDao.Item> items = (List<AccountReviewDao.Item>) request.getAttribute("items");
    Long issueCount = (Long) request.getAttribute("issueCount");
    DateTimeFormatter flagDate = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Review queue | PROERP Admin</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/app.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-console.css?v=workspace">
</head>
<body class="admin-shell">
<%@ include file="navigation.jspf" %>
<main id="admin-main" tabindex="-1" class="dashboard">
    <header class="console-heading">
        <h1>Review queue</h1>
        <p>Accounts with an open flag, a freeze, or suspended customer access.</p>
    </header>
    <% if (request.getAttribute("errorMessage") != null) { %>
        <p class="notice error" role="alert"><%= Html.escape((String) request.getAttribute("errorMessage")) %></p>
    <% } %>
    <% if (request.getAttribute("successMessage") != null) { %>
        <p class="notice success" role="status"><%= Html.escape((String) request.getAttribute("successMessage")) %></p>
    <% } %>
    <section class="table-card workspace-table" aria-labelledby="review-title">
        <div class="table-heading">
            <h2 id="review-title">Accounts needing attention</h2>
            <% if (issueCount != null) { %><span><%= issueCount %> open <%= issueCount == 1 ? "account" : "accounts" %><%= issueCount > 200 ? " | First 200 shown" : "" %></span><% } %>
        </div>
        <div class="table-scroll">
            <table>
                <thead><tr><th scope="col">Customer and account</th><th scope="col">Issue</th><th scope="col">Balance</th><th scope="col">Loan principal</th><th scope="col">Actions</th></tr></thead>
                <tbody>
                <% if (items == null || items.isEmpty()) { %>
                    <tr><td colspan="5" class="empty-state"><%= items == null ? "The review queue could not be loaded." : "No accounts need review." %></td></tr>
                <% } else { for (AccountReviewDao.Item item : items) { %>
                    <tr>
                        <td><a class="workspace-record-link" href="<%= context %>/admin/customer?id=<%= item.customerId() %>#account-<%= item.accountId() %>"><%= Html.escape(item.customer()) %></a>
                            <small class="record-id"><%= Html.escape(item.email()) %></small>
                            <small class="record-id"><%= "SAVINGS".equals(item.type()) ? "Savings" : "Current" %> | <%= Html.escape(item.number()) %></small></td>
                        <td><div class="workspace-tags">
                            <% if (item.frozen()) { %><span class="badge badge-suspended">Frozen</span><% } %>
                            <% if (item.suspended()) { %><span class="badge badge-suspended">Access suspended</span><% } %>
                            <% if (item.flag() != null) { %><span class="badge badge-pending"><%= item.flag().label() %></span><% } %>
                            </div>
                            <% if (item.flaggedAt() != null) { %><small class="record-id">Flagged <%= item.flaggedAt().format(flagDate) %></small><% } %></td>
                        <td class="workspace-amount">₹<%= item.balance().toPlainString() %></td>
                        <td class="workspace-amount">₹<%= item.outstanding().toPlainString() %></td>
                        <td><div class="console-actions">
                            <form class="workspace-inline-form" action="<%= context %>/admin/queue" method="post">
                                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                                <input type="hidden" name="accountId" value="<%= item.accountId() %>">
                                <button class="button button-outline button-small" type="submit" name="action" value="<%= item.frozen() ? "unfreeze" : "freeze" %>"><%= item.frozen() ? "Unfreeze" : "Freeze" %></button>
                                <% if (item.flag() != null) { %><button class="button button-outline button-small" type="submit" name="action" value="resolve">Resolve flag</button><% } %>
                            </form>
                            <% if (item.outstanding().signum() > 0) { %>
                                <a class="button button-outline button-small" href="<%= context %>/admin/finance?accountId=<%= item.accountId() %>&amp;loanStatus=OPEN#loan-register">Manage loan</a>
                            <% } %>
                        </div></td>
                    </tr>
                <% } } %>
                </tbody>
            </table>
        </div>
    </section>
</main>
</body>
</html>
