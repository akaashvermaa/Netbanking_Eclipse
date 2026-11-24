<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.math.BigDecimal,java.time.format.DateTimeFormatter,com.netbanking.model.AdminDashboardStats,com.netbanking.model.AdminAuditEntry,com.netbanking.dao.FinanceDao,com.netbanking.util.Html" %>
<%@ page import="java.text.NumberFormat,java.util.Locale" %>
<%
    String context = request.getContextPath();
    FinanceDao.Totals totals = (FinanceDao.Totals) request.getAttribute("financeTotals");
    AdminDashboardStats stats = (AdminDashboardStats) request.getAttribute("stats");
    Long issueCount = (Long) request.getAttribute("issueCount");
    List<FinanceDao.DailyFlow> dailyFlow = (List<FinanceDao.DailyFlow>) request.getAttribute("dailyFlow");
    List<FinanceDao.Loan> loans = (List<FinanceDao.Loan>) request.getAttribute("recentLoans");
    List<AdminAuditEntry> activity = (List<AdminAuditEntry>) request.getAttribute("recentActivity");
    DateTimeFormatter dayLabel = DateTimeFormatter.ofPattern("dd MMM");
    DateTimeFormatter timeLabel = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
    NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
    BigDecimal peak = BigDecimal.ZERO;
    if (dailyFlow != null) {
        for (FinanceDao.DailyFlow flow : dailyFlow) {
            peak = peak.max(flow.deposits()).max(flow.payouts()).max(flow.repayments());
        }
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Operations overview | PROERP Admin</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/app.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-console.css?v=balance-overview">
</head>
<body class="admin-shell">
<%@ include file="navigation.jspf" %>
<main id="admin-main" tabindex="-1" class="dashboard">
    <header class="console-heading heading-split">
        <div><h1>Operations overview</h1><p>Monitor balances, lending, and accounts that need attention.</p></div>
        <a class="button" href="<%= context %>/admin/customers?new=1">Add customer</a>
    </header>
    <% if (request.getAttribute("errorMessage") != null) { %>
        <p class="notice error" role="alert"><%= Html.escape((String) request.getAttribute("errorMessage")) %></p>
    <% } %>
    <% if (totals != null) {
        BigDecimal comparisonPeak = totals.balances().max(totals.outstanding());
        BigDecimal[] amounts = {totals.balances(), totals.outstanding()};
        String[] labels = {"Customer balances", "Outstanding principal"};
        String[] destinations = {"/admin/customers", "/admin/finance?loanStatus=OPEN#loan-register"};
        long openLoans = totals.openLoans();
        String[] contextLabels = {"Total across customer accounts", openLoans == 1 ? "1 open loan" : openLoans + " open loans"};
    %>
        <section class="dashboard-balance-summary" aria-labelledby="dashboard-balances-heading">
            <h2 id="dashboard-balances-heading">Balances and lending</h2>
            <div class="dashboard-balance-cards">
                <% for (int index = 0; index < amounts.length; index++) {
                    BigDecimal amount = amounts[index];
                    BigDecimal share = comparisonPeak.signum() == 0 ? BigDecimal.ZERO
                            : amount.multiply(BigDecimal.valueOf(100)).divide(comparisonPeak, 3, java.math.RoundingMode.HALF_UP);
                %>
                    <a class="dashboard-balance-card" href="<%= context + destinations[index] %>">
                        <span class="dashboard-balance-label"><%= labels[index] %></span>
                        <strong class="dashboard-balance-value"><%= money.format(amount) %></strong>
                        <span class="workspace-bar-track" aria-hidden="true"><span class="workspace-bar" style="width:<%= share.toPlainString() %>%"></span></span>
                        <span class="dashboard-balance-context"><%= contextLabels[index] %></span>
                    </a>
                <% } %>
            </div>
            <p class="workspace-subtitle">Customer balances include loan proceeds still in those accounts.</p>
        </section>
    <% } %>
    <% if (issueCount != null || stats != null) { %>
    <section class="workspace-kpis dashboard-queue-counts" aria-label="Review queues">
        <% if (issueCount != null) { %>
            <a class="workspace-kpi-review" href="<%= context %>/admin/queue"><span>Accounts needing review</span><strong><%= issueCount %></strong></a>
        <% } %>
        <% if (stats != null) { %>
            <a href="<%= context %>/admin/customers?status=PENDING"><span>Pending registrations</span><strong><%= stats.pendingCustomers() %></strong></a>
        <% } %>
    </section>
    <% } %>
    <div class="workspace-grid workspace-analytics">
        <% if (dailyFlow != null) { %>
        <section class="panel" aria-labelledby="flow-heading">
            <div class="console-panel-heading"><h2 id="flow-heading">Money movement</h2><span class="muted">Last 7 days</span></div>
            <p class="workspace-subtitle">Deposits and repayments received, and loans paid out.</p>
            <% if (peak.signum() == 0) { %>
                <p class="workspace-empty">No movements recorded in the last 7 days.</p>
            <% } else { %>
                <div class="workspace-flow" role="group" aria-label="Daily money movements in rupees">
                    <% for (FinanceDao.DailyFlow flow : dailyFlow) {
                           BigDecimal[] amounts = {flow.deposits(), flow.payouts(), flow.repayments()};
                           String[] series = {"Deposits", "Loan payouts", "Repayments"}; %>
                        <div class="workspace-flow-day">
                            <strong><%= flow.date().format(dayLabel) %></strong>
                            <div class="workspace-flow-series">
                                <% for (int number = 0; number < amounts.length; number++) {
                                       BigDecimal amount = amounts[number];
                                       int width = amount.multiply(BigDecimal.valueOf(100)).divide(peak, 0, java.math.RoundingMode.HALF_UP).intValue();
                                       if (amount.signum() > 0) width = Math.max(1, width); %>
                                    <span class="workspace-flow-label"><%= series[number] %></span>
                                    <span class="workspace-bar-track" aria-hidden="true"><span class="workspace-bar series-<%= number %>" style="width:<%= width %>%"></span></span>
                                    <span class="workspace-amount"><%= money.format(amount) %></span>
                                <% } %>
                            </div>
                        </div>
                    <% } %>
                </div>
            <% } %>
        </section>
        <% } %>
        <% if (totals != null) { %>
            <section class="panel" aria-labelledby="snapshot-heading">
                <div class="console-panel-heading"><h2 id="snapshot-heading">Recent open loans</h2></div>
                <% if (loans != null && !loans.isEmpty()) { %>
                    <ul class="overview-list">
                        <% for (FinanceDao.Loan loan : loans) { %>
                            <li class="queue-item">
                                <a class="workspace-record-link" href="<%= context %>/admin/finance?accountId=<%= loan.accountId() %>#loan-<%= loan.id() %>"><%= Html.escape(loan.customer()) %></a>
                                <span class="workspace-amount"><%= money.format(loan.outstanding()) %> principal due</span>
                            </li>
                        <% } %>
                    </ul>
                <% } else { %><p class="workspace-empty">No open loans to show.</p><% } %>
            </section>
        <% } %>
    </div>
    <section class="table-card workspace-table workspace-activity" aria-labelledby="recent-heading">
        <div class="table-heading"><h2 id="recent-heading">Recent actions</h2><a href="<%= context %>/admin/audit">View audit history</a></div>
        <div class="table-scroll">
            <table>
                <thead><tr><th scope="col">When</th><th scope="col">Action</th><th scope="col">Customer or target</th><th scope="col">Administrator</th></tr></thead>
                <tbody>
                <% if (activity == null || activity.isEmpty()) { %><tr><td colspan="4" class="empty-state">No actions to show.</td></tr>
                <% } else { for (AdminAuditEntry entry : activity) { %>
                    <tr><td><%= entry.createdAt() == null ? "Unavailable" : entry.createdAt().format(timeLabel) %></td>
                        <td><%= Html.escape(entry.action().replace('_', ' ').toLowerCase(java.util.Locale.ROOT)) %></td>
                        <td><%= Html.escape(entry.targetUsername()) %></td><td><%= Html.escape(entry.adminUsername()) %></td></tr>
                <% } } %>
                </tbody>
            </table>
        </div>
    </section>
</main>
</body>
</html>
