<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.netbanking.util.Html,com.netbanking.model.AccountChoice,com.netbanking.model.KycStatus" %>
<%
    String context = request.getContextPath();
    String selectedAccounts = request.getParameter("accountChoice");
    String selectedKyc = request.getParameter("kycStatus");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Add customer | PROERP Admin</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/app.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-console.css?v=onboarding">
</head>
<body class="admin-shell">
    <%@ include file="navigation.jspf" %>
    <main id="admin-main" tabindex="-1" class="dashboard">
        <header class="console-heading">
            <a class="back-link" href="<%= context %>/admin/customers">Customer directory</a>
            <h1>Add customer</h1>
            <p>Set up the customer profile, account type, and sign-in access.</p>
        </header>
        <section class="panel console-entry-panel" aria-label="Customer form">
            <% if (request.getAttribute("errorMessage") != null) { %>
                <p class="notice error" role="alert"><%= Html.escape((String) request.getAttribute("errorMessage")) %></p>
            <% } %>
            <form method="post" action="<%= context %>/admin/customers">
                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                <input type="hidden" name="action" value="create">
                <fieldset class="onboarding-fields"><legend>Customer details</legend><div class="console-field-grid">
                    <div><label for="fullName">Full name</label>
                        <input id="fullName" name="fullName" autocomplete="off" required maxlength="100" value="<%= Html.escape(request.getParameter("fullName")) %>"></div>
                    <div><label for="username">Username</label>
                        <input id="username" name="username" autocomplete="off" required minlength="3" maxlength="50" value="<%= Html.escape(request.getParameter("username")) %>"></div>
                    <div><label for="email">Email</label>
                        <input id="email" name="email" type="email" autocomplete="off" required maxlength="254" value="<%= Html.escape(request.getParameter("email")) %>"></div>
                    <div><label for="phone">Phone number</label>
                        <input id="phone" name="phone" type="tel" autocomplete="off" maxlength="25" value="<%= Html.escape(request.getParameter("phone")) %>" required></div>
                    <div><label for="addressLine">Address</label>
                        <input id="addressLine" name="addressLine" autocomplete="street-address" maxlength="160" value="<%= Html.escape(request.getParameter("addressLine")) %>"></div>
                    <div><label for="city">City</label>
                        <input id="city" name="city" autocomplete="address-level2" maxlength="80" value="<%= Html.escape(request.getParameter("city")) %>"></div>
                    <div><label for="region">State or region</label>
                        <input id="region" name="region" autocomplete="address-level1" maxlength="80" value="<%= Html.escape(request.getParameter("region")) %>"></div>
                    <div><label for="postalCode">Postal code</label>
                        <input id="postalCode" name="postalCode" autocomplete="postal-code" maxlength="12" value="<%= Html.escape(request.getParameter("postalCode")) %>"></div>
                </div></fieldset>
                <fieldset class="onboarding-fields"><legend>Account and access</legend><div class="console-field-grid">
                    <div><label for="accountChoice">Accounts to open</label>
                        <select id="accountChoice" name="accountChoice" required>
                            <% for (AccountChoice choice : AccountChoice.values()) { %>
                                <option value="<%= choice.name() %>" <%= choice.name().equals(selectedAccounts) || (selectedAccounts == null && choice == AccountChoice.SAVINGS) ? "selected" : "" %>><%= choice.label() %></option>
                            <% } %>
                        </select></div>
                    <div><label for="status">Access status</label>
                    <select id="status" name="status" required>
                        <option value="ACTIVE">Active</option>
                        <option value="PENDING" <%= "PENDING".equals(request.getParameter("status")) ? "selected" : "" %>>Pending review</option>
                    </select></div>
                    <div><label for="kycStatus">Identity verification (KYC)</label>
                        <select id="kycStatus" name="kycStatus" required>
                            <% for (KycStatus state : KycStatus.values()) { %>
                                <option value="<%= state.name() %>" <%= state.name().equals(selectedKyc) ? "selected" : "" %>><%= state.label() %></option>
                            <% } %>
                        </select></div>
                    <div><label for="temporaryPassword">Temporary password (optional)</label>
                        <input id="temporaryPassword" name="temporaryPassword" type="password" autocomplete="new-password" minlength="10" maxlength="128"></div>
                </div>
                <p class="onboarding-hint">Pending customers get their accounts after approval. A temporary password must be changed at first sign-in and can also be set later.</p>
                </fieldset>
                <div class="console-actions">
                    <button class="button" type="submit">Add customer</button>
                    <a class="button button-outline" href="<%= context %>/admin/customers">Cancel</a>
                </div>
            </form>
        </section>
    </main>
</body>
</html>
