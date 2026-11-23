package com.netbanking.web;

import com.netbanking.dao.CustomerRequestsDao;
import com.netbanking.dao.FinanceDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

@WebServlet("/admin/requests")
public class AdminRequestsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final CustomerRequestsDao requests = new CustomerRequestsDao();
    private final FinanceDao finance = new FinanceDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            boolean history = "history".equals(request.getParameter("view"));
            request.setAttribute("history", history);
            request.setAttribute("applications", requests.applications(null, history));
            request.setAttribute("tickets", requests.tickets(null, history));
            request.getRequestDispatcher("/WEB-INF/views/admin/requests.jsp").forward(request, response);
        } catch (SQLException exception) {
            unavailable(response, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!(request.getSession().getAttribute("adminId") instanceof Number admin)) {
            response.sendError(403);
            return;
        }
        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            response.sendError(400, "Choose a customer request.");
            return;
        }
        String action = request.getParameter("action");
        try {
            boolean changed;
            if ("approve".equals(action)) {
                changed = finance.approveApplication(id, admin.longValue()) == FinanceDao.Outcome.POSTED;
            } else if ("decline".equals(action) || "resolve".equals(action)) {
                changed = requests.closeRequest(id, admin.longValue(), "decline".equals(action));
            } else {
                response.sendError(400, "Choose a valid action.");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/admin/requests?result=" + (changed ? action : "unavailable"));
        } catch (SQLException exception) {
            unavailable(response, exception);
        }
    }

    private void unavailable(HttpServletResponse response, SQLException exception) throws IOException {
        getServletContext().log("Customer request administration failed", exception);
        response.sendError(503, "Customer requests are temporarily unavailable.");
    }
}
