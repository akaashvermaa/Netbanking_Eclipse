<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,java.util.Locale,java.text.NumberFormat,java.time.format.DateTimeFormatter,com.netbanking.dao.CustomerRequestsDao.Application,com.netbanking.dao.CustomerRequestsDao.Ticket,com.netbanking.util.Html" %>
<%
    String context = request.getContextPath();
    String result = request.getParameter("result");
    List<Application> applications = (List<Application>) request.getAttribute("applications");
    List<Ticket> tickets = (List<Ticket>) request.getAttribute("tickets");
    boolean history = Boolean.TRUE.equals(request.getAttribute("history"));
    NumberFormat money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
    DateTimeFormatter date = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    String feedback = "approve".equals(result) ? "Loan approved and funds credited."
            : "decline".equals(result) ? "Application declined."
            : "resolve".equals(result) ? "Service request marked resolved."
            : "unavailable".equals(result) ? "The request has already been handled, or the receiving account is unavailable." : "";
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Customer requests | PROERP Admin</title>
    <link rel="stylesheet" href="<%= context %>/assets/css/app.css">
    <link rel="stylesheet" href="<%= context %>/assets/css/admin-console.css">
</head>
<body class="admin-shell">
<%@ include file="navigation.jspf" %>
<main class="dashboard" id="admin-main">
    <header class="console-heading">
        <h1>Customer requests</h1>
        <nav class="console-tabs" aria-label="Request views">
            <a href="<%= context %>/admin/requests" <%= history ? "" : "aria-current=\"page\"" %>>Open requests</a>
            <a href="<%= context %>/admin/requests?view=history" <%= history ? "aria-current=\"page\"" : "" %>>Completed requests</a>
        </nav>
    </header>
    <% if (!feedback.isEmpty()) { %><p class="notice <%= "unavailable".equals(result) ? "error" : "success" %>" role="status"><%= feedback %></p><% } %>
    <section class="panel">
        <h2><%= history ? "Loan decisions" : "Pending loan applications" %></h2>
        <% if (!history) { %><p class="muted">Approval credits the requested principal to the selected account.</p><% } %>
        <% if (applications.isEmpty()) { %><p><%= history ? "No loan decisions yet." : "No pending loan applications." %></p><% } else { %>
            <div class="table-scroll"><table>
                <thead><tr><th scope="col">Customer</th><th scope="col">Application</th><th scope="col">Principal</th><th scope="col">Terms</th><th scope="col">Submitted</th><th scope="col">Decision</th></tr></thead>
                <tbody>
                    <% for (Application item : applications) { %>
                        <tr>
                            <td><a href="<%= context %>/admin/customer?id=<%= item.customerId() %>"><%= Html.escape(item.customer()) %></a><br><small><%= Html.escape(item.account()) %></small></td>
                            <td>#<%= item.id() %> <%= Html.escape(item.purpose()) %></td><td><%= money.format(item.amount()) %></td>
                            <td><% if (item.terms() == null) { %>Principal only<% } else { %>
                                <strong><%= item.terms().months() %> months at <%= item.terms().annualRate().toPlainString() %>% yearly</strong>
                                <small class="record-id"><%= money.format(item.terms().monthly(item.amount())) %> monthly, <%= money.format(item.terms().finalPayment(item.amount())) %> final</small>
                                <small class="record-id"><%= money.format(item.terms().interest(item.amount())) %> interest, <%= money.format(item.terms().total(item.amount())) %> total</small>
                            <% } %></td>
                            <td><%= item.submittedAt().format(date) %></td>
                            <td>
                            <% if (history) { %>
                                <strong><%= "APPROVED".equals(item.status()) ? "Approved" : "Declined" %></strong>
                                <% if (item.decidedAt() != null) { %><small class="record-id"><%= item.decidedAt().format(date) %></small><% } %>
                                <% if (item.loanId() != null) { %>
                                    <small class="record-id"><a href="<%= context %>/admin/finance?customerId=<%= item.customerId() %>#loan-<%= item.loanId() %>">Loan #<%= item.loanId() %></a></small>
                                <% } %>
                            <% } else { %><form class="console-actions inline-form" method="post" action="<%= context %>/admin/requests">
                                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                                <input type="hidden" name="id" value="<%= item.id() %>">
                                <button class="button button-small" type="submit" name="action" value="approve">Approve and fund</button>
                                <button class="button button-outline button-small" type="submit" name="action" value="decline">Decline</button>
                            </form><% } %>
                            </td>
                        </tr>
                    <% } %>
                </tbody>
            </table></div>
        <% } %>
    </section>
    <section class="panel">
        <h2><%= history ? "Resolved service requests" : "Open service requests" %></h2>
        <% if (tickets.isEmpty()) { %><p><%= history ? "No resolved service requests yet." : "No open service requests." %></p><% } else { %>
            <div class="table-scroll"><table>
                <thead><tr><th scope="col">Customer</th><th scope="col">Request</th><th scope="col">Details</th><th scope="col">Submitted</th><th scope="col"><%= history ? "Resolved" : "Action" %></th></tr></thead>
                <tbody>
                    <% for (Ticket item : tickets) { %>
                        <tr>
                            <td><a href="<%= context %>/admin/customer?id=<%= item.customerId() %>"><%= Html.escape(item.customer()) %></a></td>
                            <td>#<%= item.id() %> <%= com.netbanking.web.CustomerPortalServlet.serviceLabel(item.category()) %></td>
                            <td style="white-space: normal"><%= Html.escape(item.details()) %></td><td><%= item.submittedAt().format(date) %></td>
                            <td>
                            <% if (history) { %>
                                <%= item.resolvedAt() == null ? "Resolved" : item.resolvedAt().format(date) %>
                            <% } else { %><form class="inline-form" method="post" action="<%= context %>/admin/requests">
                                <input type="hidden" name="_csrf" value="${requestScope.csrfToken}">
                                <input type="hidden" name="id" value="<%= item.id() %>">
                                <button class="button button-outline button-small" type="submit" name="action" value="resolve">Mark resolved</button>
                            </form><% } %>
                            </td>
                        </tr>
                    <% } %>
                </tbody>
            </table></div>
        <% } %>
    </section>
</main>
</body>
</html>
