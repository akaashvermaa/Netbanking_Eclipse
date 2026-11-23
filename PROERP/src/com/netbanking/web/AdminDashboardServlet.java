package com.netbanking.web;

import com.netbanking.dao.AdminDao;
import com.netbanking.dao.FinanceDao;
import com.netbanking.dao.AccountReviewDao;
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
            request.setAttribute("recentActivity", adminDao.findRecentAuditEntries(6));
            FinanceDao financeDao = new FinanceDao();
            request.setAttribute("financeTotals", financeDao.totals());
            request.setAttribute("recentLoans", financeDao.loans(null, "OPEN").stream().limit(4).toList());
            request.setAttribute("dailyFlow", financeDao.recentFlow());
            request.setAttribute("issueCount", new AccountReviewDao().count());
        } catch (SQLException exception) {
            getServletContext().log("Could not load admin dashboard metrics.", exception);
            request.setAttribute("errorMessage", "Dashboard data is unavailable. Check the database setup.");
        }
        request.getRequestDispatcher("/WEB-INF/views/admin/dashboard.jsp").forward(request, response);
    }
}
