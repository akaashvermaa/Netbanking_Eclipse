<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.net.URLEncoder,java.nio.charset.StandardCharsets,java.time.format.DateTimeFormatter,com.netbanking.model.CustomerSummary,com.netbanking.util.Html" %>
<%
    String directoryUrl = request.getContextPath() + "/admin/customers";
    String search = (String) request.getAttribute("search");
    String selectedStatus = (String) request.getAttribute("statusFilter");
    List<String> statuses = List.of("", "PENDING", "ACTIVE", "SUSPENDED");
    List<String> labels = List.of("All customers", "Pending review", "Active", "Suspended");
    if (selectedStatus == null || !statuses.contains(selectedStatus)) selectedStatus = "";
    List<CustomerSummary> customers = (List<CustomerSummary>) request.getAttribute("customers");
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy");
    String query = search == null || search.isBlank() ? "" : "&amp;q=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Customer directory | PROERP Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/customers.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-console.css">
</head>
<body class="admin-shell customer-directory">
    <%@ include file="navigation.jspf" %>
    <main id="admin-main" tabindex="-1" class="dashboard">
        <header class="directory-header">
            <div>
                <h1><%= "PENDING".equals(selectedStatus) ? "Review queue" : "Customer directory" %></h1>
                <p><%= "PENDING".equals(selectedStatus) ? "Pending registrations are ordered oldest first." : "Find a customer, update their profile, or manage access." %></p>
            </div>
            <a class="directory-shortcut" href="${pageContext.request.contextPath}/admin/accounts">Bank accounts</a>
        </header>

        <% if (request.getAttribute("errorMessage") != null) { %>
            <div class="notice error" role="alert"><%= Html.escape((String) request.getAttribute("errorMessage")) %></div>
        <% } %>
        <% if (request.getAttribute("successMessage") != null) { %>
            <div class="notice success" role="status"><%= Html.escape((String) request.getAttribute("successMessage")) %></div>
        <% } %>

        <section class="directory-panel" aria-label="Customer records">
            <div class="directory-toolbar">
                <nav class="directory-tabs" aria-label="Filter by customer status">
                    <% for (int i = 0; i < statuses.size(); i++) {
                           String status = statuses.get(i);
                           boolean current = status.equals(selectedStatus); %>
                        <a href="<%= directoryUrl %>?status=<%= status %><%= query %>" <%= current ? "aria-current=\"page\"" : "" %>><%= labels.get(i) %></a>
                    <% } %>
                </nav>
                <form class="directory-search" method="get" action="<%= directoryUrl %>" role="search" aria-label="Search customers">
                    <input type="hidden" name="status" value="<%= selectedStatus %>">
                    <label class="directory-visually-hidden" for="customer-search">Username or email</label>
                    <input id="customer-search" name="q" type="search" maxlength="100" value="<%= Html.escape(search) %>" placeholder="Username or email">
                    <button class="button" type="submit">Search</button>
                </form>
            </div>
            <% if (customers != null) { %>
                <div class="directory-result">
                    <span><strong><%= customers.size() %></strong> <%= customers.size() == 1 ? "customer shown" : "customers shown" %><%= customers.size() == 200 ? " · display limit reached" : "" %></span>
                    <% if (!query.isEmpty()) { %><a href="<%= directoryUrl %>?status=<%= selectedStatus %>">Clear search</a><% } %>
                </div>
            <% } %>
            <div class="directory-table-wrap">
                <table class="directory-table" role="table">
                    <thead role="rowgroup"><tr role="row"><th role="columnheader" scope="col">Customer</th><th role="columnheader" scope="col">Access status</th><th role="columnheader" scope="col">Registered</th><th role="columnheader" scope="col"><span class="directory-visually-hidden">Actions</span></th></tr></thead>
                    <tbody role="rowgroup">
                    <% if (customers == null || customers.isEmpty()) { %>
                        <tr role="row"><td role="cell" colspan="4" class="directory-empty">
                            <strong><%= customers == null ? "Customers are unavailable" : "No customers found" %></strong>
                            <% if (customers != null && (!query.isEmpty() || !selectedStatus.isEmpty())) { %>
                                <p><a href="<%= directoryUrl %>">Clear filters and view all customers</a></p>
                            <% } %>
                        </td></tr>
                    <% } else {
                           for (CustomerSummary customer : customers) {
                               String status = customer.status();
                               int statusIndex = statuses.indexOf(status);
                               String statusLabel = statusIndex > 0 ? labels.get(statusIndex) : status;
                               String username = customer.username();
                               String initialsSource = username.startsWith("demo.") ? username.substring(5) : username;
                               String initials = initialsSource.isEmpty() ? "?" : initialsSource.substring(0, initialsSource.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
                               String detailUrl = request.getContextPath() + "/admin/customer?id=" + customer.id(); %>
                        <tr role="row">
                            <td role="cell" class="directory-identity">
                                <div class="directory-person">
                                    <span class="directory-avatar avatar-<%= Math.floorMod(customer.id(), 3) %>" aria-hidden="true"><%= Html.escape(initials) %></span>
                                    <div>
                                        <a class="directory-name" href="<%= detailUrl %>"><%= Html.escape(username) %></a>
                                        <span class="directory-email"><%= customer.email() == null || customer.email().isBlank() ? "No email on file" : Html.escape(customer.email()) %></span>
                                    </div>
                                </div>
                            </td>
                            <td role="cell"><span class="directory-status status-<%= Html.escape(status.toLowerCase(java.util.Locale.ROOT)) %>"><%= Html.escape(statusLabel) %></span></td>
                            <td role="cell" class="directory-date">
                                <% if (customer.createdAt() != null) { %>
                                    <time datetime="<%= customer.createdAt().toLocalDate() %>"><%= customer.createdAt().format(dateFormat) %></time>
                                <% } else { %>&mdash;<% } %>
                            </td>
                            <td role="cell" class="directory-action">
                                <a class="directory-row-link <%= "PENDING".equals(status) ? "needs-review" : "" %>" href="<%= detailUrl %>"
                                   aria-label="<%= "PENDING".equals(status) ? "Review " : "View " %><%= Html.escape(username) %>">
                                    <%= "PENDING".equals(status) ? "Review" : "View profile" %>
                                </a>
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
