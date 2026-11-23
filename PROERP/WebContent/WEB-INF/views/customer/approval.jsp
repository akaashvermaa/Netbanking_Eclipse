<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Waiting for approval | PROERP</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/customer-portal.css">
    <script defer src="${pageContext.request.contextPath}/assets/js/customer-approval.js"></script>
</head>
<body class="bank-login">
    <header class="login-bar">
        <div class="login-bar-inner"><a class="bank-wordmark" href="${pageContext.request.contextPath}/">PROERP <span>Banking</span></a></div>
    </header>
    <main class="login-content">
        <section class="bank-panel form-panel">
            <h1>Waiting for admin approval</h1>
            <p class="subtle">Keep this page open. You’ll be signed in automatically once an administrator approves your registration.</p>
            <p id="approval-status" class="bank-notice" role="status">Approval pending.</p>
            <form method="post" action="${pageContext.request.contextPath}/customer/approval" id="approval-check" class="bank-actions">
                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                <button class="bank-button" type="submit">Check approval now</button>
            </form>
            <form method="post" action="${pageContext.request.contextPath}/customer/logout" class="bank-actions">
                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                <button class="bank-button secondary" type="submit">Leave this session</button>
            </form>
        </section>
    </main>
</body>
</html>
