package com.netbanking.web;

import com.netbanking.dao.AdminDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

@WebServlet("/admin/audit")
public class AdminAuditServlet extends HttpServlet {
    private final AdminDao adminDao = new AdminDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String group = request.getParameter("group");
            if (!"ACCOUNT".equals(group) && !"CUSTOMER".equals(group)) group = "";
            request.setAttribute("auditGroup", group);
            request.setAttribute("entries", adminDao.findRecentAuditEntries(100, group.isEmpty() ? "" : group + "_"));
        } catch (SQLException exception) {
            getServletContext().log("Could not load admin audit history.", exception);
            request.setAttribute("errorMessage", "Audit history is unavailable. Run the admin audit schema script.");
        }
        request.getRequestDispatcher("/WEB-INF/views/admin/audit.jsp").forward(request, response);
    }
}
