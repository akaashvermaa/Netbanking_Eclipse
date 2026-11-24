<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Locale,java.text.NumberFormat,com.netbanking.model.CustomerDetails,com.netbanking.model.BankAccount,com.netbanking.util.Html" %>
<%
    String directoryUrl = request.getContextPath() + "/admin/customers";
    String search = (String) request.getAttribute("search");
    String selectedStatus = (String) request.getAttribute("statusFilter");
    List<String> statuses = List.of("", "ACTIVE", "PENDING", "WITH_LOANS", "FROZEN", "SUSPENDED");
    List<String> labels = List.of("All customers", "Active", "Pending review", "With loan debt", "Frozen accounts", "Suspended");
    if (selectedStatus == null || !statuses.contains(selectedStatus)) selectedStatus = "";
    List<CustomerDetails> customers = (List<CustomerDetails>) request.getAttribute("customers");
    NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
    boolean filtered = (search != null && !search.isBlank()) || !selectedStatus.isEmpty();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Customers &amp; Accounts | PROERP Admin</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-console.css?v=kyc">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/customers.css?v=directory-layout">
</head>
<body class="admin-shell customer-directory">
    <%@ include file="navigation.jspf" %>
    <main id="admin-main" tabindex="-1" class="dashboard">
        <header class="directory-header">
            <h1>Customers &amp; Accounts</h1>
            <a class="button" href="<%= directoryUrl %>?new=1">Add customer</a>
        </header>

        <% if (request.getAttribute("errorMessage") != null) { %>
            <div class="notice error" role="alert"><%= Html.escape((String) request.getAttribute("errorMessage")) %></div>
        <% } %>
        <% if (request.getAttribute("successMessage") != null) { %>
            <div class="notice success" role="status"><%= Html.escape((String) request.getAttribute("successMessage")) %></div>
        <% } %>

        <section class="directory-panel" aria-label="Customer directory">
            <div class="directory-toolbar">
                <form class="directory-search" method="get" action="<%= directoryUrl %>" role="search" aria-label="Search customers">
                    <label for="customer-search">Search customers
                        <input id="customer-search" name="q" type="search" maxlength="100" value="<%= Html.escape(search) %>" placeholder="Name, username, email or account number">
                    </label>
                    <label for="customer-filter">Filter
                        <select id="customer-filter" name="status">
                            <% for (int i = 0; i < statuses.size(); i++) { %>
                                <option value="<%= statuses.get(i) %>" <%= statuses.get(i).equals(selectedStatus) ? "selected" : "" %>><%= labels.get(i) %></option>
                            <% } %>
                        </select>
                    </label>
                    <button class="button button-outline" type="submit">Apply</button>
                </form>
                <% if (customers != null) { %>
                    <div class="directory-result">
                        <span><strong><%= customers.size() %></strong> <%= customers.size() == 1 ? "customer shown" : "customers shown" %><%= customers.size() == 200 ? " (display limit)" : "" %></span>
                        <% if (filtered) { %><a href="<%= directoryUrl %>">Clear filters</a><% } %>
                    </div>
                <% } %>
            </div>

            <div class="directory-table-wrap">
                <table class="directory-table" role="table">
                    <thead role="rowgroup">
                        <tr role="row">
                            <th scope="col">Customer</th>
                            <th scope="col">Linked accounts</th>
                            <th scope="col">Balance and loans</th>
                            <th scope="col">Access and KYC</th>
                        </tr>
                    </thead>
                    <tbody role="rowgroup">
                    <% if (customers == null || customers.isEmpty()) { %>
                        <tr role="row"><td role="cell" colspan="4" class="directory-empty">
                            <strong><%= customers == null ? "Records are unavailable" : "No customers match this filter" %></strong>
                        </td></tr>
                    <% } else {
                           for (CustomerDetails customer : customers) {
                               String status = customer.status();
                               String username = customer.username();
                               String fullName = customer.fullName() == null || customer.fullName().isBlank() ? username : customer.fullName();
                               String detailUrl = request.getContextPath() + "/admin/customer?id=" + customer.id();
                               List<BankAccount> accList = customer.accounts();
                               boolean hasEmail = customer.email() != null && !customer.email().isBlank();
                               boolean hasPhone = customer.phone() != null && !customer.phone().isBlank();
                               boolean pending = "PENDING".equals(status);
                               boolean active = "ACTIVE".equals(status);
                    %>
                        <tr role="row">
                            <td role="cell" class="directory-identity">
                                <a class="directory-name" href="<%= detailUrl %>"><%= Html.escape(fullName) %></a>
                                <span class="directory-meta">@<%= Html.escape(username) %></span>
                                <% if (hasEmail || hasPhone) { %>
                                    <details class="directory-contact">
                                        <summary>Contact details<span class="directory-visually-hidden"> for <%= Html.escape(fullName) %></span></summary>
                                        <dl>
                                            <% if (hasEmail) { %><div><dt>Email</dt><dd><%= Html.escape(customer.email()) %></dd></div><% } %>
                                            <% if (hasPhone) { %><div><dt>Phone</dt><dd><%= Html.escape(customer.phone()) %></dd></div><% } %>
                                        </dl>
                                    </details>
                                <% } %>
                            </td>
                            <td role="cell" class="directory-accounts">
                                <span class="directory-mobile-label" aria-hidden="true">Linked accounts</span>
                                <% if (accList.isEmpty()) { %>
                                    <span class="directory-meta">No accounts</span>
                                <% } else { %>
                                    <ul class="directory-account-list">
                                        <% for (BankAccount acc : accList) { %>
                                            <li>
                                                <a href="<%= detailUrl %>#account-<%= acc.id() %>"><%= Html.escape(acc.number()) %></a>
                                                <span class="directory-account-meta">
                                                    <span><%= "SAVINGS".equals(acc.type()) ? "Savings" : "Current" %></span>
                                                    <span class="directory-money"><%= money.format(acc.balance()) %></span>
                                                    <% if ("FROZEN".equals(acc.status())) { %><span class="directory-state state-frozen">Frozen</span><% } %>
                                                </span>
                                            </li>
                                        <% } %>
                                    </ul>
                                <% } %>
                                <% if (active && accList.size() < 2) { %>
                                    <a class="directory-inline-action" href="<%= detailUrl %>#customer-accounts">Open account<span class="directory-visually-hidden"> for <%= Html.escape(fullName) %></span></a>
                                <% } %>
                            </td>
                            <td role="cell" class="directory-finances">
                                <span class="directory-mobile-label" aria-hidden="true">Balance and loans</span>
                                <strong class="directory-money"><%= money.format(customer.totalBalance()) %></strong>
                                <span class="directory-meta">
                                    <% if (customer.activeLoans() > 0) { %>
                                        <span class="directory-money"><%= money.format(customer.totalDebt()) %></span> principal due
                                    <% } else { %>No debt<% } %>
                                </span>
                            </td>
                            <td role="cell" class="directory-access">
                                <span class="directory-mobile-label" aria-hidden="true">Access and KYC</span>
                                <span class="directory-state <%= pending ? "state-pending" : active ? "state-active" : "state-suspended" %>"><%= pending ? "Pending review" : active ? "Active" : "Suspended" %></span>
                                <span class="directory-meta">KYC <%= customer.kycStatus().label().toLowerCase(Locale.ROOT) %></span>
                                <% if (pending) { %>
                                    <a class="directory-inline-action" href="<%= detailUrl %>">Review customer<span class="directory-visually-hidden"> <%= Html.escape(fullName) %></span></a>
                                <% } %>
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
