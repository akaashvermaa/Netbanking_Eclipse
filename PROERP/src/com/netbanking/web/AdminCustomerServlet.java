package com.netbanking.web;

import com.netbanking.dao.AdminDao;
import com.netbanking.dao.BankAccountDao;
import com.netbanking.dao.CustomerAccessDao;
import com.netbanking.model.CustomerProfile;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.regex.Pattern;

@WebServlet("/admin/customer")
public class AdminCustomerServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final AdminDao dao = new AdminDao();
    private final BankAccountDao accounts = new BankAccountDao();
    private final CustomerAccessDao customerAccess = new CustomerAccessDao();
    private final com.netbanking.dao.FinanceDao financeDao = new com.netbanking.dao.FinanceDao();
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            response.sendError(400, "Invalid customer ID."); return;
        }
        try {
            var customer = dao.findCustomer(id);
            if (customer.isEmpty()) { response.sendError(404, "Customer not found."); return; }
            request.setAttribute("customer", customer.get());
            dao.findCustomerProfile(id).ifPresent(profile -> request.setAttribute("profile", profile));
            request.setAttribute("kycStatus", dao.findKycStatus(id));
            request.setAttribute("entries", dao.findCustomerAuditEntries(id));
            request.setAttribute("accounts", accounts.list(id, "", ""));
            request.setAttribute("loans", financeDao.loansByCustomer(id));
            if ("PENDING".equals(customer.get().status()) || "approved".equals(request.getParameter("result"))) {
                dao.findNextPendingCustomer(id).ifPresent(next -> request.setAttribute("nextPendingId", next));
            }
            if ("PENDING".equals(customer.get().status())) {
                request.setAttribute("requestedAccounts", dao.findRequestedAccounts(id));
            }
            request.getRequestDispatcher("/WEB-INF/views/admin/customer.jsp").forward(request, response);
        } catch (SQLException exception) {
            getServletContext().log("Could not load customer details", exception);
            response.sendError(503, "Customer details are temporarily unavailable.");
        }
    }

    static boolean validEmail(String email) {
        return email != null && email.strip().length() <= 254 && EMAIL.matcher(email.strip()).matches();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            response.sendError(400, "Invalid customer ID.");
            return;
        }
        if (!(request.getSession(false).getAttribute("adminId") instanceof Number adminId)) {
            response.sendError(403);
            return;
        }
        String action = request.getParameter("action");
        if ("reset-password".equals(action)) {
            String temporaryPassword = request.getParameter("temporaryPassword");
            String result = "invalid-customer-password";
            if (CustomerPortalServlet.validNewPassword(temporaryPassword)) {
                char[] password = temporaryPassword.toCharArray();
                try {
                    result = customerAccess.resetPassword(id, adminId.longValue(), password)
                            ? "customer-password-set" : "customer-password-unavailable";
                } catch (SQLException exception) {
                    getServletContext().log("Could not set customer password", exception);
                    response.sendError(503, "The temporary password could not be set.");
                    return;
                } finally {
                    java.util.Arrays.fill(password, '\0');
                }
            }
            response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + id + "&result=" + result + "#profile-details");
            return;
        }
        if ("update-profile".equals(action)) {
            String fullName = request.getParameter("fullName");
            String phone = request.getParameter("phone");
            String reason = request.getParameter("reason");
            fullName = fullName == null ? "" : fullName.strip();
            phone = phone == null ? "" : phone.strip();
            String address = profileField(request, "addressLine");
            String city = profileField(request, "city");
            String region = profileField(request, "region");
            String postalCode = profileField(request, "postalCode");
            if (fullName.isEmpty() || fullName.length() > 100 || !CustomerProfile.validPhone(phone)
                    || address.length() > 160 || city.length() > 80 || region.length() > 80 || postalCode.length() > 12
                    || (reason != null && reason.strip().length() > 140)) {
                response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + id + "&result=invalid-profile");
                return;
            }
            try {
                CustomerProfile contact = new CustomerProfile(fullName, phone, address, city, region, postalCode);
                if (request.getParameter("addressLine") == null) {
                    var current = dao.findCustomerProfile(id);
                    if (current.isPresent()) {
                        var saved = current.get();
                        contact = new CustomerProfile(fullName, phone, saved.addressLine(), saved.city(), saved.region(), saved.postalCode());
                    }
                }
                boolean updated = dao.updateCustomerProfile(id, adminId.longValue(), contact,
                        reason == null ? "" : reason.strip());
                if (!updated) {
                    response.sendError(404, "Customer not found.");
                    return;
                }
                response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + id + "&result=profile-updated");
                return;
            } catch (SQLException exception) {
                getServletContext().log("Could not update customer profile", exception);
                response.sendError(503, "The profile change could not be saved.");
                return;
            }
        }
        String email = request.getParameter("email");
        String originalEmail = request.getParameter("originalEmail");
        String reason = request.getParameter("reason");
        String result;
        if (!validEmail(email)
                || (reason != null && reason.strip().length() > 140) || originalEmail == null) {
            result = "invalid-email";
        } else {
            try {
                var updated = dao.updateCustomerEmail(id, adminId.longValue(),
                        originalEmail.isEmpty() ? null : originalEmail, email.strip(), reason == null ? "" : reason.strip());
                result = switch (updated) {
                    case UPDATED -> "email-updated";
                    case UNCHANGED -> "email-unchanged";
                    case DUPLICATE -> "email-duplicate";
                    case STALE -> "email-stale";
                    case MISSING -> "missing";
                };
            } catch (SQLException exception) {
                getServletContext().log("Could not update customer email", exception);
                response.sendError(503, "The email change could not be saved.");
                return;
            }
        }
        if ("missing".equals(result)) {
            response.sendError(404, "Customer not found.");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + id + "&result=" + result);
    }

    private String profileField(HttpServletRequest request, String field) {
        String value = request.getParameter(field);
        return value == null ? "" : value.strip();
    }
}
