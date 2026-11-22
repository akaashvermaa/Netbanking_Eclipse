<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="com.netbanking.model.AdminAuditEntry" %>
<%@ page import="com.netbanking.util.Html" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Audit history | Netbanking Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-console.css">
</head>
<body class="admin-shell">
    <%@ include file="navigation.jspf" %>
    <main id="admin-main" tabindex="-1" class="dashboard">
        <header class="console-heading">
            <h1>Audit history</h1>
            <p>Track who changed customer access, profile details, and bank accounts.</p>
        </header>
        <% if (request.getAttribute("errorMessage") != null) { %>
            <div class="notice error" role="alert"><%= Html.escape((String) request.getAttribute("errorMessage")) %></div>
        <% } %>
        <section class="table-card">
            <div class="table-heading">
                <nav class="console-tabs" aria-label="Filter audit history">
                <% String group = (String) request.getAttribute("auditGroup");
                   String[][] groups = {{"", "All events"}, {"CUSTOMER", "Customer events"}, {"ACCOUNT", "Account events"}};
                   for (String[] item : groups) { %>
                    <a href="${pageContext.request.contextPath}/admin/audit?group=<%= item[0] %>" <%= item[0].equals(group) ? "aria-current=\"page\"" : "" %>><%= item[1] %></a>
                <% } %>
                </nav>
                <span>Latest 100 matching events</span>
            </div>
            <div class="table-scroll">
                <table>
                    <thead><tr><th>Time</th><th>Admin</th><th>Action</th><th>Customer</th><th>Details</th></tr></thead>
                    <tbody>
                    <% List<AdminAuditEntry> entries = (List<AdminAuditEntry>) request.getAttribute("entries");
                       if (entries == null || entries.isEmpty()) { %>
                        <tr><td colspan="5" class="empty-state">No events to display in this view.</td></tr>
                    <% } else { for (AdminAuditEntry entry : entries) { %>
                        <tr>
                            <td><%= entry.createdAt() == null ? "—" : entry.createdAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) %></td>
                            <td><%= Html.escape(entry.adminUsername()) %></td>
                            <td><span class="console-event"><%= Html.escape(entry.action().replace('_', ' ')) %></span></td>
                            <td><%= Html.escape(entry.targetUsername()) %></td>
                            <td><%= Html.escape(entry.details()) %></td>
                        </tr>
                    <% } } %>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</body>
</html>
