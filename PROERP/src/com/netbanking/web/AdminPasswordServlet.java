package com.netbanking.web;

import com.netbanking.dao.AdminDao;
import com.netbanking.security.CsrfTokens;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

@WebServlet("/admin/password")
public class AdminPasswordServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String VIEW = "/WEB-INF/views/admin/password.jsp";
    private final AdminDao adminDao = new AdminDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        var session = request.getSession(false);
        if (Boolean.TRUE.equals(session.getAttribute("passwordUpdated"))) {
            session.removeAttribute("passwordUpdated");
            request.setAttribute("successMessage", "Password updated.");
        }
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        var session = request.getSession(false);
        if (!(session.getAttribute("adminId") instanceof Number adminId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String current = request.getParameter("currentPassword");
        String replacement = request.getParameter("newPassword");
        String confirmation = request.getParameter("confirmPassword");
        if (current == null || current.isEmpty() || current.length() > 1024 || replacement == null
                || replacement.isBlank() || replacement.length() < 8 || replacement.length() > 128
                || !replacement.equals(confirmation) || replacement.equals(current)) {
            request.setAttribute("errorMessage", "Enter your current password and a different new password of 8 to 128 characters. Confirm it exactly.");
        } else {
            char[] oldPassword = current.toCharArray();
            char[] newPassword = replacement.toCharArray();
            try {
                if (adminDao.changeAdminPassword(adminId.longValue(), oldPassword, newPassword)) {
                    request.changeSessionId();
                    CsrfTokens.rotate(session, CsrfTokens.Portal.ADMIN);
                    session.setAttribute("passwordUpdated", true);
                    response.sendRedirect(request.getContextPath() + "/admin/password");
                    return;
                }
                request.setAttribute("errorMessage", "The current password is incorrect.");
            } catch (SQLException exception) {
                getServletContext().log("Could not update administrator password.", exception);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                request.setAttribute("errorMessage", "The password could not be updated. Try again.");
            } finally {
                Arrays.fill(oldPassword, '\0');
                Arrays.fill(newPassword, '\0');
            }
        }
        request.getRequestDispatcher(VIEW).forward(request, response);
    }
}
