<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Admin sign-in | PROERP Netbanking</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-console.css?v=onboarding">
</head>
<body class="auth-page console-login">
    <main class="console-login-layout">
    <section class="console-login-intro">
        <a class="console-brand" href="${pageContext.request.contextPath}/"><span class="console-mark" aria-hidden="true">P</span> PROERP</a>
        <div><p class="console-kicker">BANK OPERATIONS</p><p class="console-login-title">Administration<br>workspace</p>
        <p>Customer reviews, bank accounts, and audit history.</p></div>
        <a href="${pageContext.request.contextPath}/">Back to PROERP Banking</a>
    </section>
    <section class="auth-card">
        <h1>Admin sign-in</h1>

        <% if (request.getAttribute("errorMessage") != null) { %>
            <div class="notice error" role="alert"><%= com.netbanking.util.Html.escape((String) request.getAttribute("errorMessage")) %></div>
        <% } %>

        <form method="post" action="${pageContext.request.contextPath}/admin/login">
            <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
            <label for="username">Admin username</label>
            <input id="username" name="username" type="text" autocomplete="username" placeholder="Enter your username" required maxlength="50">

            <label for="password">Password</label>
            <input id="password" name="password" type="password" autocomplete="current-password" placeholder="Enter your password" required>

            <button class="button full-width" type="submit">Sign in</button>
        </form>
        <% if (Boolean.TRUE.equals(request.getAttribute("adminDemoEnabled"))) { %>
            <form method="post" action="${pageContext.request.contextPath}/admin/login" class="admin-demo-form">
                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                <button class="button button-outline full-width" type="submit" name="action" value="demo">Sign in as demo admin</button>
            </form>
        <% } %>
    </section>
    </main>
</body>
</html>
