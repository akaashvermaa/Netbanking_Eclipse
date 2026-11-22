package com.netbanking.web;

import com.netbanking.model.AdminCredential;
import com.netbanking.security.CsrfTokens;
import com.netbanking.service.AdminAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

@WebServlet("/admin/login")
public class AdminLoginServlet extends HttpServlet {
    private final AdminAuthService authService = new AdminAuthService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        if ("ADMIN".equals(session.getAttribute("role"))) {
            response.sendRedirect(request.getContextPath() + "/admin/dashboard");
            return;
        }
        request.getRequestDispatcher("/WEB-INF/views/admin/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String username = request.getParameter("username");
        String submittedPassword = request.getParameter("password");
        char[] password = submittedPassword == null ? new char[0] : submittedPassword.toCharArray();
        try {
            Optional<AdminCredential> admin = authService.authenticate(username, password);
            if (admin.isEmpty()) {
                request.setAttribute("errorMessage", "Username or password is incorrect.");
                request.getRequestDispatcher("/WEB-INF/views/admin/login.jsp").forward(request, response);
                return;
            }

            request.changeSessionId();
            HttpSession session = request.getSession();
            session.setAttribute("adminId", admin.get().id());
            session.setAttribute("adminUsername", admin.get().username());
            session.setAttribute("role", "ADMIN");
            CsrfTokens.rotate(session);
            response.sendRedirect(request.getContextPath() + "/admin/dashboard");
        } catch (SQLException exception) {
            getServletContext().log("Admin sign-in could not reach the database.", exception);
            request.setAttribute("errorMessage", "Sign-in is unavailable. Check the database setup and try again.");
            request.getRequestDispatcher("/WEB-INF/views/admin/login.jsp").forward(request, response);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }
}
