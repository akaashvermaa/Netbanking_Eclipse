<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.netbanking.util.Html,com.netbanking.model.AccountChoice" %>
<%
    boolean register = Boolean.TRUE.equals(request.getAttribute("registrationPage"));
    boolean approvalExpired = !register && "1".equals(request.getParameter("approvalExpired"));
    String message = (String) request.getAttribute("accessMessage");
    String heading = register ? "Register for online banking" : "Sign in to your account";
    String root = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= register ? "Customer registration" : "Customer sign-in" %> | PROERP</title>
    <link rel="stylesheet" href="<%= root %>/assets/css/customer-portal.css?v=access">
</head>
<body class="bank-login">
    <header class="login-bar">
        <div class="login-bar-inner">
            <a class="bank-wordmark" href="<%= root %>/">PROERP <span>Banking</span></a>
        </div>
    </header>
    <main class="login-content">
        <div class="login-layout">
            <aside class="login-intro" aria-labelledby="access-guide">
                <h2 id="access-guide"><%= register ? "From registration to sign-in" : "Online banking" %></h2>
                <% if (register) { %>
                    <ol class="login-steps">
                        <li><strong>Submit your details</strong><span>Choose the username and password you want to use.</span></li>
                        <li><strong>Wait for approval</strong><span>An administrator reviews your registration.</span></li>
                        <li><strong>Sign in</strong><span>Use the same credentials after approval.</span></li>
                    </ol>
                <% } else { %>
                    <ul class="login-steps">
                        <li><strong>Accounts</strong><span>Check balances and view account details.</span></li>
                        <li><strong>Transfers</strong><span>Send money to another PROERP account.</span></li>
                        <li><strong>Activity</strong><span>Review payments and outstanding loan principal.</span></li>
                    </ul>
                <% } %>
                <p class="login-demo">College banking project using fictional data.</p>
            </aside>
            <section class="login-panel" aria-labelledby="login-title">
                <h1 id="login-title"><%= heading %></h1>
                <p class="subtle"><%= register ? "Create your customer profile. Banking access starts after admin approval." : "Use your PROERP customer username and password." %></p>
                <% if (approvalExpired) { %>
                    <p class="bank-notice" role="status">Sign in again to resume checking your approval.</p>
                <% } %>
                <% if (message != null) { %>
                    <p class="bank-notice error" role="alert"><%= Html.escape(message) %></p>
                <% } %>
                <form method="post" action="<%= root %>/customer/<%= register ? "register" : "login" %>" class="bank-form">
                    <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                    <% if (register) { %>
                        <label for="fullName">Full name
                            <input id="fullName" name="fullName" autocomplete="name" maxlength="100" value="<%= Html.escape(request.getParameter("fullName")) %>" required>
                        </label>
                        <div class="register-fields">
                            <label for="email">Email address
                                <input id="email" type="email" name="email" autocomplete="email" maxlength="254" value="<%= Html.escape(request.getParameter("email")) %>" required>
                            </label>
                            <label for="phone">Phone number
                                <input id="phone" type="tel" name="phone" autocomplete="tel" maxlength="25" value="<%= Html.escape(request.getParameter("phone")) %>" required>
                            </label>
                        </div>
                        <label for="accountChoice">Account type
                            <select id="accountChoice" name="accountChoice" required aria-describedby="account-choice-hint">
                                <option value="">Choose an account type</option>
                                <% for (AccountChoice choice : AccountChoice.values()) {
                                    if (choice == AccountChoice.NONE) continue;
                                %><option value="<%= choice.name() %>" <%= choice.name().equals(request.getParameter("accountChoice")) ? "selected" : "" %>><%= choice.label() %></option><% } %>
                            </select>
                            <span class="input-hint" id="account-choice-hint">An administrator reviews your request before opening an account.</span>
                        </label>
                    <% } %>
                    <label for="username">Username
                        <input id="username" name="username" autocomplete="username" autocapitalize="none" spellcheck="false"
                               maxlength="50" value="<%= Html.escape(request.getParameter("username")) %>"
                               <%= register ? "minlength=\"3\" aria-describedby=\"username-hint\"" : "" %> required>
                        <% if (register) { %><span class="input-hint" id="username-hint">3 to 50 characters. Start with a letter or number. Dots, hyphens and underscores are allowed.</span><% } %>
                    </label>
                    <label for="password">Password
                        <input id="password" name="password" type="password" autocomplete="<%= register ? "new-password" : "current-password" %>"
                               maxlength="128" <%= register ? "minlength=\"10\" aria-describedby=\"password-hint\"" : "" %> required>
                        <% if (register) { %><span class="input-hint" id="password-hint">Use at least 10 characters.</span><% } %>
                    </label>
                    <% if (register) { %>
                        <label for="confirmPassword">Confirm password
                            <input id="confirmPassword" name="confirmPassword" type="password" autocomplete="new-password" minlength="10" maxlength="128" required>
                        </label>
                    <% } %>
                    <button class="bank-button" type="submit"><%= register ? "Submit registration" : "Sign in" %></button>
                </form>
                <% if (!register && request.getAttribute("demoUsername") instanceof String) { %>
                    <form method="post" action="<%= root %>/customer/login" class="bank-actions">
                        <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                        <button class="bank-button secondary" type="submit" name="action" value="demo">Sign in as demo customer</button>
                        <span class="subtle"><%= Html.escape((String) request.getAttribute("demoUsername")) %></span>
                    </form>
                <% } %>
                <footer class="login-alternate">
                    <span><%= register ? "Already registered?" : "New to PROERP?" %></span>
                    <a href="<%= root %>/customer/<%= register ? "login" : "register" %>"><%= register ? "Sign in" : "Register for online banking" %></a>
                </footer>
            </section>
        </div>
    </main>
</body>
</html>
