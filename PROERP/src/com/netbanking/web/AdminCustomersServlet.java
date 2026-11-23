package com.netbanking.web;

import com.netbanking.dao.AdminDao;
import com.netbanking.model.AccountChoice;
import com.netbanking.model.CustomerProfile;
import com.netbanking.model.KycStatus;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
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
        if ("1".equals(request.getParameter("new"))) {
            request.getRequestDispatcher("/WEB-INF/views/admin/customer-new.jsp").forward(request, response);
            return;
        }
        String search = request.getParameter("q");
        String status = request.getParameter("status");
        try {
            request.setAttribute("customers", adminDao.findCustomerDetailsList(search, status));
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
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String action = request.getParameter("action");
        if ("create".equals(action)) {
            create(request, response);
            return;
        }
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
        if (reason != null && reason.strip().length() > 140) {
            response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + customerId + "&result=reason");
            return;
        }
        String expectedStatus = "approve".equals(action) ? "PENDING" : ("suspend".equals(action) ? "ACTIVE" : "SUSPENDED");
        String accountSelection = request.getParameter("accountChoice");
        AccountChoice approvedAccounts = AccountChoice.from(accountSelection);
        if ("approve".equals(action) && accountSelection != null && approvedAccounts == null) {
            response.sendError(400, "Choose which accounts to open.");
            return;
        }
        String result = "unchanged";
        try {
            if (adminId instanceof Number number && adminDao.changeCustomerStatus(customerId, number.longValue(), status,
                    expectedStatus, reason == null ? "" : reason.strip(), approvedAccounts)) {
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

    private void create(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (!(request.getSession(false).getAttribute("adminId") instanceof Number adminId)) {
            response.sendError(403);
            return;
        }
        String fullName = field(request, "fullName");
        String username = field(request, "username");
        String email = field(request, "email");
        String phone = field(request, "phone");
        String status = field(request, "status");
        String address = field(request, "addressLine");
        String city = field(request, "city");
        String region = field(request, "region");
        String postalCode = field(request, "postalCode");
        AccountChoice accounts = AccountChoice.from(request.getParameter("accountChoice"));
        String kycValue = field(request, "kycStatus");
        KycStatus kyc = Arrays.stream(KycStatus.values()).filter(value -> value.name().equals(kycValue))
                .findFirst().orElse(null);
        String password = request.getParameter("temporaryPassword");
        if (password == null) password = "";
        String error = null;
        if (fullName.isEmpty() || fullName.length() > 100 || !username.matches("[A-Za-z0-9][A-Za-z0-9._-]{2,49}")
                || !AdminCustomerServlet.validEmail(email) || !CustomerProfile.validPhone(phone)) {
            error = "Enter a name, a valid email and phone number, and a username of 3 to 50 characters.";
        } else if (address.length() > 160 || city.length() > 80 || region.length() > 80 || postalCode.length() > 12) {
            error = "Check the mailing address field lengths.";
        } else if (accounts == null || kyc == null || (!"ACTIVE".equals(status) && !"PENDING".equals(status))) {
            error = "Choose the account, access and identity verification settings.";
        } else if (!password.isEmpty() && !CustomerPortalServlet.validNewPassword(password)) {
            error = "Use 10 to 128 characters for the temporary password.";
        } else {
            char[] temporaryPassword = password.toCharArray();
            try {
                var profile = new CustomerProfile(fullName, phone, address, city, region, postalCode);
                long customerId = adminDao.addCustomer(username, email, profile, status, adminId.longValue(), accounts,
                        kyc, temporaryPassword);
                if (customerId > 0) {
                    response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + customerId + "&result=created");
                    return;
                }
                error = "That username or email is already in use.";
            } catch (SQLException exception) {
                getServletContext().log("Could not add customer.", exception);
                response.setStatus(503);
                error = "The customer could not be saved. Try again.";
            } finally {
                Arrays.fill(temporaryPassword, '\0');
            }
        }
        request.setAttribute("errorMessage", error);
        request.getRequestDispatcher("/WEB-INF/views/admin/customer-new.jsp").forward(request, response);
    }

    private static String field(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.strip();
    }
}
