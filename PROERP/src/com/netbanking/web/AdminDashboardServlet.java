package com.netbanking.web;

import com.netbanking.dao.AdminDao;
import com.netbanking.dao.BankAccountDao;
import java.sql.SQLException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/admin/dashboard")
public class AdminDashboardServlet extends HttpServlet {
    private final AdminDao adminDao = new AdminDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            request.setAttribute("stats", adminDao.getDashboardStats());
            request.setAttribute("pending", adminDao.findCustomers("", "PENDING").stream().limit(5).toList());
            request.setAttribute("recentActivity", adminDao.findRecentAuditEntries(5));
            request.setAttribute("accountCounts", new BankAccountDao().counts());
        } catch (SQLException exception) {
            getServletContext().log("Could not load admin dashboard metrics.", exception);
            request.setAttribute("errorMessage", "Dashboard data is unavailable. Check the database setup.");
        }
        request.getRequestDispatcher("/WEB-INF/views/admin/dashboard.jsp").forward(request, response);
    }
}
