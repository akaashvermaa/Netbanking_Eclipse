package com.netbanking.web;

import com.netbanking.dao.AdminDao;
import com.netbanking.dao.BankAccountDao;
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
            request.setAttribute("entries", dao.findCustomerAuditEntries(id));
            request.setAttribute("accounts", accounts.list(id, "", ""));
            if ("PENDING".equals(customer.get().status()) || "approved".equals(request.getParameter("result"))) {
                dao.findNextPendingCustomer(id).ifPresent(next -> request.setAttribute("nextPendingId", next));
            }
            request.getRequestDispatcher("/WEB-INF/views/admin/customer.jsp").forward(request, response);
        } catch (SQLException exception) {
            getServletContext().log("Could not load customer details", exception);
            response.sendError(503, "Customer details are temporarily unavailable.");
        }
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
        String email = request.getParameter("email");
        String originalEmail = request.getParameter("originalEmail");
        String reason = request.getParameter("reason");
        String result;
        if (email == null || email.strip().length() > 254 || !EMAIL.matcher(email.strip()).matches()
                || reason == null || reason.isBlank() || reason.strip().length() > 140 || originalEmail == null) {
            result = "invalid-email";
        } else {
            try {
                var updated = dao.updateCustomerEmail(id, adminId.longValue(),
                        originalEmail.isEmpty() ? null : originalEmail, email.strip(), reason.strip());
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
}
