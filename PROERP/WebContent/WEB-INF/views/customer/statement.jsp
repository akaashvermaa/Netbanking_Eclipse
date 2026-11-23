<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Locale,java.math.BigDecimal,java.time.LocalDate,java.text.NumberFormat,java.time.format.DateTimeFormatter" %>
<%@ page import="com.netbanking.dao.CustomerAccessDao.Identity,com.netbanking.dao.CustomerBankingDao.Activity,com.netbanking.dao.CustomerBankingDao.Category,com.netbanking.model.BankAccount,com.netbanking.util.Html" %>
<%
    String context = request.getContextPath();
    String base = context + "/customer";
    Identity customer = (Identity) request.getAttribute("customerIdentity");
    List<BankAccount> accounts = (List<BankAccount>) request.getAttribute("accounts");
    List<Activity> activity = (List<Activity>) request.getAttribute("activity");
    Long selected = (Long) request.getAttribute("selectedAccount");
    LocalDate from = (LocalDate) request.getAttribute("fromDate");
    LocalDate to = (LocalDate) request.getAttribute("toDate");
    Category category = (Category) request.getAttribute("activityCategory");
    String search = (String) request.getAttribute("activitySearch");
    String accountLabel = selected == null ? "All accounts" : accounts.stream()
            .filter(account -> account.id() == selected).map(BankAccount::number).findFirst().orElse("");
    DateTimeFormatter date = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    DateTimeFormatter timestamp = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
    BigDecimal credits = BigDecimal.ZERO;
    BigDecimal debits = BigDecimal.ZERO;
    for (Activity row : activity) {
        if (row.debit()) debits = debits.add(row.amount());
        else credits = credits.add(row.amount());
    }
    boolean fullActivity = true;
%>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Account statement | PROERP Banking</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/customer-portal.css?v=savings-goals">
</head>
<body class="statement-page">
    <main class="bank-main">
        <div class="bank-actions statement-actions">
            <a href="<%= base %>/activity">Back to activity</a>
            <button class="bank-button" type="button" onclick="window.print()">Print or save as PDF</button>
        </div>
        <section class="bank-panel">
            <header>
                <p class="bank-wordmark">PROERP Banking</p>
                <h1>Account statement</h1>
                <p class="subtle">Simulated banking activity</p>
            </header>
            <dl class="identity-facts">
                <div><dt>Customer</dt><dd><%= Html.escape(customer.name()) %></dd></div>
                <div><dt>Account</dt><dd><%= Html.escape(accountLabel) %></dd></div>
                <div><dt>Period</dt><dd><%= from == null ? "Earliest transaction" : from.format(date) %> to <%= to == null ? "Latest transaction" : to.format(date) %></dd></div>
                <div><dt>Transaction type</dt><dd><%= category.label() %></dd></div>
                <% if (!search.isEmpty()) { %><div><dt>Search</dt><dd><%= Html.escape(search) %></dd></div><% } %>
                <div><dt>Totals for this statement</dt><dd><%= currency.format(credits) %> in, <%= currency.format(debits) %> out</dd></div>
            </dl>
            <%@ include file="activity-table.jspf" %>
        </section>
    </main>
</body>
</html>
