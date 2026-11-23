<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.netbanking.util.Html" %>
<% String context = request.getContextPath(); %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Change password | PROERP Admin</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/app.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-console.css?v=operations">
</head>
<body class="admin-shell">
    <%@ include file="navigation.jspf" %>
    <main id="admin-main" tabindex="-1" class="dashboard">
        <header class="console-heading">
            <a class="back-link" href="<%= context %>/admin/dashboard">Overview</a>
            <h1>Administrator password</h1>
            <p>Choose a new password of 8 to 128 characters.</p>
        </header>

        <% if (request.getAttribute("errorMessage") != null) { %>
            <p class="notice error" role="alert"><%= Html.escape((String) request.getAttribute("errorMessage")) %></p>
        <% } %>
        <% if (request.getAttribute("successMessage") != null) { %>
            <p class="notice success" role="status"><%= Html.escape((String) request.getAttribute("successMessage")) %></p>
        <% } %>

        <section class="panel console-entry-panel" aria-label="Password form">
            <form method="post" action="<%= context %>/admin/password">
                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                <div class="console-field-grid">
                    <div>
                        <label for="currentPassword">Current password</label>
                        <input id="currentPassword" name="currentPassword" type="password" maxlength="1024" autocomplete="current-password" required>
                    </div>
                    <div>
                        <label for="newPassword">New password</label>
                        <input id="newPassword" name="newPassword" type="password" minlength="8" maxlength="128" autocomplete="new-password" required>
                    </div>
                    <div>
                        <label for="confirmPassword">Confirm new password</label>
                        <input id="confirmPassword" name="confirmPassword" type="password" minlength="8" maxlength="128" autocomplete="new-password" required>
                    </div>
                </div>
                <div class="console-actions">
                    <button class="button" type="submit">Update password</button>
                    <a class="button button-outline" href="<%= context %>/admin/dashboard">Cancel</a>
                </div>
            </form>
        </section>
    </main>
</body>
</html>
