<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.netbanking.util.Html" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Refresh your form | PROERP</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/customer-portal.css">
</head>
<body class="bank-login">
    <main class="login-content">
        <section class="bank-panel form-panel">
            <h1>This form expired</h1>
            <p class="subtle">Open a fresh page and try again. Your changes were not submitted.</p>
            <a class="bank-button" href="<%= Html.escape((String) request.getAttribute("retryPath")) %>">Continue to PROERP</a>
        </section>
    </main>
</body>
</html>
