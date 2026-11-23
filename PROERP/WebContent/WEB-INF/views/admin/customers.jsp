<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.net.URLEncoder,java.nio.charset.StandardCharsets,java.time.format.DateTimeFormatter,com.netbanking.model.CustomerDetails,com.netbanking.model.BankAccount,com.netbanking.util.Html" %>
<%
    String directoryUrl = request.getContextPath() + "/admin/customers";
    String search = (String) request.getAttribute("search");
    String selectedStatus = (String) request.getAttribute("statusFilter");
    List<String> statuses = List.of("", "ACTIVE", "PENDING", "WITH_LOANS", "FROZEN", "SUSPENDED");
    List<String> labels = List.of("All customers", "Active", "Pending review", "With loan debt", "Frozen accounts", "Suspended");
    if (selectedStatus == null || !statuses.contains(selectedStatus)) selectedStatus = "";
    List<CustomerDetails> customers = (List<CustomerDetails>) request.getAttribute("customers");
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy");
    String query = search == null || search.isBlank() ? "" : "&amp;q=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Customer &amp; Account Master Database | PROERP Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/customers.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-console.css?v=kyc">
</head>
<body class="admin-shell customer-directory">
    <%@ include file="navigation.jspf" %>
    <main id="admin-main" tabindex="-1" class="dashboard">
        <header class="directory-header">
            <div>
                <h1>Customer &amp; Account Database</h1>
                <p>Unified customer records with linked bank accounts, account balances, and outstanding debt.</p>
            </div>
            <div class="console-actions">
                <a class="button" href="${pageContext.request.contextPath}/admin/customers?new=1">Add customer</a>
            </div>
        </header>

        <% if (request.getAttribute("errorMessage") != null) { %>
            <div class="notice error" role="alert"><%= Html.escape((String) request.getAttribute("errorMessage")) %></div>
        <% } %>
        <% if (request.getAttribute("successMessage") != null) { %>
            <div class="notice success" role="status"><%= Html.escape((String) request.getAttribute("successMessage")) %></div>
        <% } %>

        <section class="directory-panel" aria-label="Customer master records">
            <div class="directory-toolbar">
                <nav class="directory-tabs" aria-label="Filter customer directory">
                    <% for (int i = 0; i < statuses.size(); i++) {
                           String status = statuses.get(i);
                           boolean current = status.equals(selectedStatus); %>
                        <a href="<%= directoryUrl %>?status=<%= status %><%= query %>" <%= current ? "aria-current=\"page\"" : "" %>><%= labels.get(i) %></a>
                    <% } %>
                </nav>
                <form class="directory-search" method="get" action="<%= directoryUrl %>" role="search" aria-label="Search customer database">
                    <input type="hidden" name="status" value="<%= selectedStatus %>">
                    <label class="directory-visually-hidden" for="customer-search">Name, username or email</label>
                    <input id="customer-search" name="q" type="search" maxlength="100" value="<%= Html.escape(search) %>" placeholder="Name, email or account number">
                    <button class="button" type="submit">Search</button>
                </form>
            </div>

            <% if (customers != null) { %>
                <div class="directory-result">
                    <span><strong><%= customers.size() %></strong> <%= customers.size() == 1 ? "record shown" : "records shown" %><%= customers.size() == 200 ? " · display limit reached" : "" %></span>
                    <% if (!query.isEmpty() || !selectedStatus.isEmpty()) { %><a href="<%= directoryUrl %>">Reset filters</a><% } %>
                </div>
            <% } %>

            <div class="directory-table-wrap">
                <table class="directory-table" role="table">
                    <thead role="rowgroup">
                        <tr role="row">
                            <th scope="col">Customer Identity</th>
                            <th scope="col">Linked Accounts</th>
                            <th scope="col">Total Balance</th>
                            <th scope="col">Loan Exposure</th>
                            <th scope="col">Status</th>
                            <th scope="col">KYC</th>
                            <th scope="col"><span class="directory-visually-hidden">Actions</span></th>
                        </tr>
                    </thead>
                    <tbody role="rowgroup">
                    <% if (customers == null || customers.isEmpty()) { %>
                        <tr role="row"><td role="cell" colspan="7" class="directory-empty">
                            <strong><%= customers == null ? "Records are unavailable" : "No customers match this filter" %></strong>
                            <% if (customers != null && (!query.isEmpty() || !selectedStatus.isEmpty())) { %>
                                <p><a href="<%= directoryUrl %>">View all customers</a></p>
                            <% } %>
                        </td></tr>
                    <% } else {
                           for (CustomerDetails customer : customers) {
                               String status = customer.status();
                               String username = customer.username();
                               String fullName = customer.fullName() == null || customer.fullName().isBlank() ? username : customer.fullName();
                               String initials = fullName.isEmpty() ? "?" : fullName.substring(0, fullName.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
                               String detailUrl = request.getContextPath() + "/admin/customer?id=" + customer.id();
                               List<BankAccount> accList = customer.accounts();
                    %>
                        <tr role="row">
                            <td role="cell" class="directory-identity">
                                <div class="directory-person">
                                    <span class="directory-avatar avatar-<%= Math.floorMod(customer.id(), 3) %>" aria-hidden="true"><%= Html.escape(initials) %></span>
                                    <div>
                                        <a class="directory-name" href="<%= detailUrl %>"><%= Html.escape(fullName) %></a>
                                        <div class="customer-submeta">
                                            <span class="directory-username">@<%= Html.escape(username) %></span>
                                            <span class="directory-email"><%= customer.email() == null ? "" : Html.escape(customer.email()) %></span>
                                            <% if (customer.phone() != null && !customer.phone().isBlank()) { %>
                                                <span class="directory-phone"><%= Html.escape(customer.phone()) %></span>
                                            <% } %>
                                        </div>
                                    </div>
                                </div>
                            </td>
                            <td role="cell">
                                <% if (accList.isEmpty()) { %>
                                    <span class="muted-tag">No accounts</span>
                                <% } else { %>
                                    <div class="account-pill-list">
                                        <% for (BankAccount acc : accList) { %>
                                            <a href="<%= detailUrl %>#account-<%= acc.id() %>" class="account-badge <%= "FROZEN".equals(acc.status()) ? "frozen" : "active" %>">
                                                <strong><%= "SAVINGS".equals(acc.type()) ? "Savings" : "Current" %></strong>
                                                <span><%= Html.escape(acc.number()) %></span>
                                                <span class="workspace-amount">₹<%= acc.balance().toPlainString() %></span>
                                                <% if ("FROZEN".equals(acc.status())) { %><small>Frozen</small><% } %>
                                            </a>
                                        <% } %>
                                    </div>
                                <% } %>
                            </td>
                            <td role="cell">
                                <strong class="balance-cell">₹<%= customer.totalBalance().toPlainString() %></strong>
                            </td>
                            <td role="cell">
                                <% if (customer.activeLoans() > 0) { %>
                                    <span class="badge badge-loan">₹<%= customer.totalDebt().toPlainString() %> debt</span>
                                <% } else { %>
                                    <span class="badge badge-clean">No debt</span>
                                <% } %>
                            </td>
                            <td role="cell">
                                <span class="directory-status status-<%= Html.escape(status.toLowerCase(java.util.Locale.ROOT)) %>">
                                    <%= "PENDING".equals(status) ? "Pending review" : "ACTIVE".equals(status) ? "Active" : "Suspended" %>
                                </span>
                            </td>
                            <td role="cell"><span class="badge kyc-<%= customer.kycStatus().name().toLowerCase(java.util.Locale.ROOT) %>"><%= customer.kycStatus().label() %></span></td>
                            <td role="cell" class="directory-action">
                                <div class="action-cell-group">
                                    <a class="button button-outline button-small" href="<%= detailUrl %>">Customer profile</a>
                                    <% if ("ACTIVE".equals(status) && accList.size() < 2) { %>
                                        <a class="button button-outline button-small" href="<%= detailUrl %>#customer-accounts">Open account</a>
                                    <% } else if ("PENDING".equals(status)) { %>
                                        <a class="button button-small" href="<%= detailUrl %>">Review</a>
                                    <% } %>
                                </div>
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
