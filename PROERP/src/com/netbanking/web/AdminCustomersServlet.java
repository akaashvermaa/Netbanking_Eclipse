package com.netbanking.web;

import com.netbanking.dao.AdminDao;
import java.io.IOException;
import java.sql.SQLException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/admin/customers")
public class AdminCustomersServlet extends HttpServlet {
    private final AdminDao adminDao = new AdminDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String search = request.getParameter("q");
        String status = request.getParameter("status");
        try {
            request.setAttribute("customers", adminDao.findCustomers(search, status));
        } catch (SQLException exception) {
            getServletContext().log("Could not load customer records.", exception);
            request.setAttribute("errorMessage", "Customer records are unavailable. Check the database setup.");
        }
        request.setAttribute("search", search == null ? "" : search);
        request.setAttribute("statusFilter", status == null ? "" : status);
        String result = request.getParameter("result");
        if ("approved".equals(result)) {
            request.setAttribute("successMessage", "Customer approved.");
        } else if ("suspended".equals(result)) {
            request.setAttribute("successMessage", "Customer suspended.");
        } else if ("reactivated".equals(result)) {
            request.setAttribute("successMessage", "Customer reactivated.");
        } else if ("unchanged".equals(result)) {
            request.setAttribute("errorMessage", "The customer status did not change. Refresh the list and try again.");
        }
        request.getRequestDispatcher("/WEB-INF/views/admin/customers.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = request.getParameter("action");
        String status = switch (action == null ? "" : action) {
            case "approve", "reactivate" -> "ACTIVE";
            case "suspend" -> "SUSPENDED";
            default -> null;
        };
        long customerId;
        try {
            customerId = Long.parseLong(request.getParameter("customerId"));
            if (customerId <= 0 || status == null) {
                throw new NumberFormatException("Invalid customer action");
            }
        } catch (NumberFormatException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid customer action.");
            return;
        }

        Object adminId = request.getSession(false).getAttribute("adminId");
        String reason = request.getParameter("reason");
        if (reason == null || reason.isBlank() || reason.trim().length() > 140) {
            response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + customerId + "&result=reason");
            return;
        }
        String expectedStatus = "approve".equals(action) ? "PENDING" : ("suspend".equals(action) ? "ACTIVE" : "SUSPENDED");
        String result = "unchanged";
        try {
            if (adminId instanceof Number number && adminDao.changeCustomerStatus(customerId, number.longValue(), status, expectedStatus, reason.trim())) {
                result = "approve".equals(action) ? "approved" : ("suspend".equals(action) ? "suspended" : "reactivated");
            }
        } catch (SQLException exception) {
            getServletContext().log("Could not update customer status.", exception);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not update customer status.");
            return;
        }
        if ("approved".equals(result) && "1".equals(request.getParameter("continueReview"))) {
            try {
                var next = adminDao.findNextPendingCustomer(customerId);
                if (next.isPresent()) {
                    response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + next.getAsLong() + "&result=previous-approved");
                    return;
                }
            } catch (SQLException exception) {
                getServletContext().log("Could not find the next pending customer after approval.", exception);
            }
            response.sendRedirect(request.getContextPath() + "/admin/customers?status=PENDING&result=approved");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + customerId + "&result=" + result);
    }
}
