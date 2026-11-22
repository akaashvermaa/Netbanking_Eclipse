<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.time.format.DateTimeFormatter,com.netbanking.model.AdminDashboardStats,com.netbanking.model.BankAccount,com.netbanking.model.CustomerSummary,com.netbanking.model.AdminAuditEntry,com.netbanking.util.Html" %>
<%
    String context = request.getContextPath();
    List<CustomerSummary> pending = (List<CustomerSummary>) request.getAttribute("pending");
    List<AdminAuditEntry> activity = (List<AdminAuditEntry>) request.getAttribute("recentActivity");
    AdminDashboardStats stats = (AdminDashboardStats) request.getAttribute("stats");
    BankAccount.Counts counts = (BankAccount.Counts) request.getAttribute("accountCounts");
    boolean hasPending = pending != null && !pending.isEmpty();
    DateTimeFormatter shortDate = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Overview | PROERP Admin</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/app.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-console.css">
</head>
<body class="admin-shell">
    <%@ include file="navigation.jspf" %>
    <main id="admin-main" tabindex="-1" class="dashboard console-overview">
        <header class="console-heading">
            <h1>Operations overview</h1>
            <p>Review new registrations, manage bank accounts, and track recent decisions.</p>
        </header>

        <% if (request.getAttribute("errorMessage") != null) { %>
            <p class="notice error" role="alert"><%= Html.escape((String) request.getAttribute("errorMessage")) %></p>
        <% } %>

        <section class="console-priority" aria-labelledby="priority-title">
            <div>
                <p class="console-kicker">CUSTOMER REVIEW QUEUE</p>
                <h2 id="priority-title"><%= stats == null ? "Review new registrations" : stats.pendingCustomers() + (stats.pendingCustomers() == 1 ? " customer needs review" : " customers need review") %></h2>
                <p><%= hasPending ? "Start with the oldest registration." : pending == null ? "The review queue could not be loaded." : "The review queue is clear." %></p>
            </div>
            <% if (hasPending) { %>
                <a class="button" href="<%= context %>/admin/customer?id=<%= pending.get(0).id() %>">Start review</a>
            <% } %>
        </section>

        <section class="console-summary" aria-label="Workspace totals">
            <% if (stats != null) { %>
                <a href="<%= context %>/admin/customers"><strong><%= stats.totalCustomers() %></strong><span>Total customers</span></a>
                <a href="<%= context %>/admin/customers?status=ACTIVE"><strong><%= stats.activeCustomers() %></strong><span>Active customers</span></a>
                <a href="<%= context %>/admin/customers?status=SUSPENDED"><strong><%= stats.suspendedCustomers() %></strong><span>Suspended customers</span></a>
            <% } %>
            <% if (counts != null) { %>
                <a href="<%= context %>/admin/accounts"><strong><%= counts.total() %></strong><span>Bank accounts</span></a>
                <a href="<%= context %>/admin/accounts?status=FROZEN"><strong><%= counts.frozen() %></strong><span>Frozen accounts</span></a>
            <% } %>
        </section>

        <div class="workspace-grid console-overview-grid">
            <section class="panel">
                <div class="console-panel-heading">
                    <h2>Pending registrations</h2>
                    <a href="<%= context %>/admin/customers?status=PENDING">Full queue</a>
                </div>
                <% if (hasPending) { %>
                    <ul class="overview-list">
                    <% for (CustomerSummary customer : pending) { %>
                        <li class="queue-item">
                            <div><strong><%= Html.escape(customer.username()) %></strong>
                                <small class="record-id"><%= customer.createdAt() == null ? "" : "Registered " + customer.createdAt().format(shortDate) %></small></div>
                            <a class="review-link" href="<%= context %>/admin/customer?id=<%= customer.id() %>" aria-label="Review <%= Html.escape(customer.username()) %>">Review</a>
                        </li>
                    <% } %>
                    </ul>
                <% } else { %>
                    <p class="empty-state"><%= pending == null ? "Pending registrations are unavailable." : "No pending registrations." %></p>
                <% } %>
            </section>
            <section class="panel">
                <div class="console-panel-heading"><h2>Recent decisions</h2><a href="<%= context %>/admin/audit">Audit history</a></div>
                <% if (activity != null && !activity.isEmpty()) { %>
                    <ul class="overview-list">
                    <% for (AdminAuditEntry entry : activity) { %>
                        <li class="activity-item overview-event">
                            <strong><%= Html.escape(entry.action().replace('_', ' ')) %></strong>
                            <p><%= Html.escape(entry.targetUsername()) %></p>
                            <small><%= Html.escape(entry.adminUsername()) %> &middot; <%= entry.createdAt() == null ? "" : entry.createdAt().format(shortDate) %></small>
                        </li>
                    <% } %>
                    </ul>
                <% } else { %>
                    <p class="empty-state"><%= activity == null ? "Recent decisions are unavailable." : "No decisions recorded yet." %></p>
                <% } %>
            </section>
        </div>
    </main>
</body>
</html>
