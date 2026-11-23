<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.time.format.DateTimeFormatter,com.netbanking.model.AdminAuditEntry,com.netbanking.util.Html" %>
<%
    String context = request.getContextPath();
    List<AdminAuditEntry> entries = (List<AdminAuditEntry>) request.getAttribute("entries");
    String group = (String) request.getAttribute("auditGroup");
    String search = (String) request.getAttribute("search");
    if (group == null) group = "";
    if (search == null) search = "";
    String query = search.isBlank() ? "" : "&amp;q=" + java.net.URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8);
    DateTimeFormatter fullDate = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Audit history | PROERP Admin</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/app.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-console.css?v=workspace">
</head>
<body class="admin-shell">
    <%@ include file="navigation.jspf" %>
    <main id="admin-main" tabindex="-1" class="dashboard">
        <header class="console-heading">
            <h1>Audit history</h1>
            <p>Find who changed a customer, account, or loan and see what was recorded.</p>
        </header>

        <% if (request.getAttribute("errorMessage") != null) { %>
            <div class="notice error" role="alert"><%= Html.escape((String) request.getAttribute("errorMessage")) %></div>
        <% } %>

        <section class="table-card audit-command-card workspace-table">
            <div class="table-heading">
                <nav class="console-tabs" aria-label="Filter audit history categories">
                    <a href="<%= context %>/admin/audit<%= query.isEmpty() ? "" : "?" + query.substring(5) %>" <%= group.isEmpty() ? "aria-current=\"page\"" : "" %>>All Actions</a>
                    <a href="<%= context %>/admin/audit?group=ACCOUNT<%= query %>" <%= "ACCOUNT".equals(group) ? "aria-current=\"page\"" : "" %>>Account events</a>
                    <a href="<%= context %>/admin/audit?group=CUSTOMER<%= query %>" <%= "CUSTOMER".equals(group) ? "aria-current=\"page\"" : "" %>>Customer events</a>
                    <a href="<%= context %>/admin/audit?group=REQUEST<%= query %>" <%= "REQUEST".equals(group) ? "aria-current=\"page\"" : "" %>>Request decisions</a>
                    <a href="<%= context %>/admin/audit?group=ADMIN<%= query %>" <%= "ADMIN".equals(group) ? "aria-current=\"page\"" : "" %>>Admin events</a>
                </nav>

                <form class="audit-search-form" method="get" action="<%= context %>/admin/audit" role="search" aria-label="Search audit trail">
                    <input type="hidden" name="group" value="<%= Html.escape(group) %>">
                    <label class="directory-visually-hidden" for="audit-search">Search</label>
                    <input id="audit-search" name="q" type="search" maxlength="100" value="<%= Html.escape(search) %>" placeholder="Search by admin, customer, note...">
                    <button class="button" type="submit">Search</button>
                    <% if (!search.isBlank()) { %>
                        <a class="button button-outline" href="<%= context %>/admin/audit?group=<%= Html.escape(group) %>">Clear</a>
                    <% } %>
                </form>
            </div>

            <div class="table-scroll">
                <table class="audit-terminal-table">
                    <thead>
                        <tr>
                            <th scope="col">Recorded</th>
                            <th scope="col">Category</th>
                            <th scope="col">Action</th>
                            <th scope="col">Administrator</th>
                            <th scope="col">Target</th>
                            <th scope="col">Details</th>
                        </tr>
                    </thead>
                    <tbody>
                    <% if (entries == null || entries.isEmpty()) { %>
                        <tr><td colspan="6" class="empty-state">No matching audit events.</td></tr>
                    <% } else {
                           for (AdminAuditEntry entry : entries) {
                               String action = entry.action() == null ? "" : entry.action();
                               String domain = "SYSTEM";
                               String domainClass = "badge-system";
                               if (action.startsWith("LOAN_APPLICATION_") || action.startsWith("SERVICE_REQUEST_")) {
                                   domain = "REQUESTS";
                               } else if (action.startsWith("ACCOUNT_LOAN") || action.startsWith("ACCOUNT_DEPOSIT")) {
                                   domain = "FINANCE";
                                   domainClass = "badge-treasury";
                               } else if (action.startsWith("ACCOUNT_")) {
                                   domain = "ACCOUNTS";
                                   domainClass = "badge-accounts";
                               } else if (action.startsWith("CUSTOMER_")) {
                                   domain = "CUSTOMER";
                                   domainClass = "badge-kyc";
                               } else if (action.startsWith("ADMIN_")) {
                                   domain = "SECURITY";
                                   domainClass = "badge-security";
                               }
                    %>
                        <tr>
                            <td class="audit-time-cell">
                                <%= entry.createdAt() == null ? "—" : entry.createdAt().format(fullDate) %>
                            </td>
                            <td>
                                <span class="audit-domain-pill <%= domainClass %>"><%= domain %></span>
                            </td>
                            <td>
                                <strong class="audit-action-title"><%= Html.escape(action.replace('_', ' ')) %></strong>
                            </td>
                            <td>
                                <span class="audit-actor-chip">
                                    <%= Html.escape(entry.adminUsername()) %>
                                </span>
                            </td>
                            <td>
                                <% if (entry.customerId() != null) { %>
                                    <a class="workspace-record-link" href="<%= context %>/admin/customer?id=<%= entry.customerId() %>"><%= Html.escape(entry.targetUsername()) %></a>
                                <% } else if (entry.targetUsername() != null && !entry.targetUsername().isBlank()) { %>
                                    <span><%= Html.escape(entry.targetUsername()) %></span>
                                <% } else { %>
                                    <span class="muted">System / Self</span>
                                <% } %>
                            </td>
                            <td class="audit-details-cell">
                                <%= Html.escape(entry.details()) %>
                            </td>
                        </tr>
                    <%     }
                       } %>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</body>
</html>
