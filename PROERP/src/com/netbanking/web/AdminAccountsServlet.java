package com.netbanking.web;

import com.netbanking.dao.AdminDao;
import com.netbanking.dao.BankAccountDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/admin/accounts")
public class AdminAccountsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final BankAccountDao accounts = new BankAccountDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Long id = positiveId(request.getParameter("id"));
        Long customerId = positiveId(request.getParameter("customerId"));
        if (id == null && customerId == null && request.getParameter("q") == null) {
            response.sendRedirect(request.getContextPath() + "/admin/customers");
            return;
        }
        if ((request.getParameter("id") != null && id == null)
                || (request.getParameter("customerId") != null && customerId == null)) {
            response.sendError(400, "Invalid account or customer ID.");
            return;
        }
        try {
            if (id != null) {
                var account = accounts.find(id);
                if (account.isEmpty()) {
                    response.sendError(404, "Account not found.");
                    return;
                }
                request.setAttribute("account", account.get());
            } else {
                if (customerId != null) {
                    var customer = new AdminDao().findCustomer(customerId);
                    if (customer.isEmpty()) {
                        response.sendError(404, "Customer not found.");
                        return;
                    }
                    request.setAttribute("customer", customer.get());
                }
                String search = request.getParameter("q");
                search = search == null ? "" : search.strip();
                String status = request.getParameter("status");
                status = "ACTIVE".equals(status) || "FROZEN".equals(status) ? status : "";
                if (search.length() > 100) {
                    response.sendError(400, "Search must be 100 characters or fewer.");
                    return;
                }
                request.setAttribute("search", search);
                request.setAttribute("status", status);
                request.setAttribute("accounts", accounts.list(customerId, search, status));
            }
            request.getRequestDispatcher("/WEB-INF/views/admin/accounts.jsp").forward(request, response);
        } catch (SQLException exception) {
            getServletContext().log("Could not load bank accounts", exception);
            response.sendError(503, "Bank accounts are currently unavailable.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = request.getParameter("action");
        String reason = request.getParameter("reason");
        Object admin = request.getSession(false).getAttribute("adminId");
        if (!(admin instanceof Number adminId)) {
            response.sendError(403);
            return;
        }
        if (reason != null && reason.strip().length() > 140) {
            response.sendError(400, "Notes can contain up to 140 characters.");
            return;
        }
        String route = request.getContextPath() + "/admin/accounts";
        try {
            if ("open".equals(action)) {
                Long customer = positiveId(request.getParameter("customerId"));
                String type = request.getParameter("type");
                if (customer == null || type == null || !List.of("SAVINGS", "CURRENT").contains(type)) {
                    response.sendError(400, "Choose a customer and valid account type.");
                    return;
                }
                long id = accounts.open(customer, adminId.longValue(), type, reason);
                response.sendRedirect(id == 0
                        ? request.getContextPath() + "/admin/customer?id=" + customer + "&result=account-unavailable"
                        : request.getContextPath() + "/admin/customer?id=" + customer + "&result=account-opened");
            } else if ("freeze".equals(action) || "unfreeze".equals(action)) {
                Long id = positiveId(request.getParameter("id"));
                if (id == null) {
                    response.sendError(400, "Invalid account ID.");
                    return;
                }
                var acc = accounts.find(id);
                boolean changed = accounts.setFrozen(id, adminId.longValue(), "freeze".equals(action), reason);
                if (acc.isPresent()) {
                    response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + acc.get().customerId() + "&result=" + (changed ? "account-updated" : "unchanged"));
                } else {
                    response.sendRedirect(request.getContextPath() + "/admin/customers");
                }
            } else {
                response.sendError(400, "Invalid account action.");
            }
        } catch (SQLException exception) {
            getServletContext().log("Could not change bank account", exception);
            response.sendError(503, "The account update could not be saved.");
        }
    }

    private static Long positiveId(String value) {
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
