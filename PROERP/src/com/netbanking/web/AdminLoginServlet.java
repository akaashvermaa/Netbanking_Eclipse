package com.netbanking.web;

import com.netbanking.config.LocalDemo;
import com.netbanking.model.AdminCredential;
import com.netbanking.security.CsrfTokens;
import com.netbanking.security.PortalSession;
import static com.netbanking.security.CsrfTokens.Portal.ADMIN;
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
        showLogin(request, response, LocalDemo.admin(request));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LocalDemo localDemo = LocalDemo.admin(request);
        boolean demo = "demo".equals(request.getParameter("action"));
        if (demo && localDemo == null) {
            response.sendError(404);
            return;
        }
        String username = demo ? localDemo.username() : request.getParameter("username");
        String submittedPassword = demo ? localDemo.password() : request.getParameter("password");
        char[] password = submittedPassword == null ? new char[0] : submittedPassword.toCharArray();
        try {
            Optional<AdminCredential> admin = authService.authenticate(username, password);
            if (admin.isEmpty()) {
                request.setAttribute("errorMessage", demo ? "Demo sign-in is unavailable. Check the local demo setup."
                        : "Username or password is incorrect.");
                showLogin(request, response, localDemo);
                return;
            }

            request.changeSessionId();
            HttpSession session = request.getSession();
            session.setAttribute("adminId", admin.get().id());
            session.setAttribute("adminUsername", admin.get().username());
            PortalSession.touchAdmin(session);
            CsrfTokens.rotate(session, ADMIN);
            response.sendRedirect(request.getContextPath() + "/admin/dashboard");
        } catch (SQLException exception) {
            getServletContext().log("Admin sign-in could not reach the database.", exception);
            request.setAttribute("errorMessage", "Sign-in is unavailable. Check the database setup and try again.");
            showLogin(request, response, localDemo);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private void showLogin(HttpServletRequest request, HttpServletResponse response, LocalDemo demo)
            throws ServletException, IOException {
        request.setAttribute("adminDemoEnabled", demo != null);
        request.getRequestDispatcher("/WEB-INF/views/admin/login.jsp").forward(request, response);
    }
}
