<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.netbanking.dao.CustomerAccessDao.Identity,com.netbanking.dao.CustomerBankingDao.Activity,com.netbanking.dao.CustomerRequestsDao.Application,com.netbanking.dao.CustomerRequestsDao.Ticket,com.netbanking.util.Html,java.text.NumberFormat,java.time.format.DateTimeFormatter,java.util.List,java.util.Locale" %>
<%
    Identity owner = (Identity) request.getAttribute("customerIdentity");
    String customerBase = request.getContextPath() + "/customer";
    List<Activity> updates = (List<Activity>) request.getAttribute("updates");
    List<Application> loanUpdates = (List<Application>) request.getAttribute("applications");
    List<Ticket> serviceUpdates = (List<Ticket>) request.getAttribute("tickets");
    NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
    DateTimeFormatter timestamp = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
%>
<div class="bank-service-grid notification-feed" data-notification-feed="<%= owner.id() %>">
    <section class="bank-panel">
        <div class="section-heading"><h3>Account activity</h3><a href="<%= customerBase %>/activity">View activity</a></div>
        <% if (updates.isEmpty()) { %><p class="subtle panel-message">No activity yet.</p><% } %>
        <ul class="bank-list">
            <% for (Activity update : updates) { %>
                <li data-event="activity:<%= Html.escape(update.reference()) %>:<%= update.accountId() %>:<%= Html.escape(update.kind()) %>">
                    <div><strong><%= update.label() %></strong><p class="subtle"><%= update.postedAt().format(timestamp) %></p></div>
                    <strong><%= update.debit() ? "-" : "+" %><%= currency.format(update.amount()) %></strong>
                </li>
            <% } %>
        </ul>
    </section>
    <section class="bank-panel">
        <h3>Applications and requests</h3>
        <% if (loanUpdates.isEmpty() && serviceUpdates.isEmpty()) { %><p class="subtle panel-message">No applications or service requests yet.</p><% } %>
        <ul class="bank-list">
            <% for (Application item : loanUpdates) { %>
                <li data-event="loan:<%= item.id() %>:<%= Html.escape(item.status()) %>"><a href="<%= customerBase %>/loans">Loan application #<%= item.id() %></a>
                    <span class="bank-status"><%= "PENDING".equals(item.status()) ? "Pending review" : "APPROVED".equals(item.status()) ? "Approved" : "Declined" %></span>
                </li>
            <% } %>
            <% for (Ticket item : serviceUpdates) { %>
                <li data-event="service:<%= item.id() %>:<%= Html.escape(item.status()) %>"><a href="<%= customerBase %>/services">Service request #<%= item.id() %></a>
                    <span class="bank-status"><%= "OPEN".equals(item.status()) ? "Open" : "Resolved" %></span>
                </li>
            <% } %>
        </ul>
    </section>
</div>
