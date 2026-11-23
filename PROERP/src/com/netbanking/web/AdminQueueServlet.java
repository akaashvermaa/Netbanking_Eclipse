package com.netbanking.web;

import com.netbanking.dao.AccountReviewDao;
import com.netbanking.dao.BankAccountDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

@WebServlet("/admin/queue")
public class AdminQueueServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final AccountReviewDao review = new AccountReviewDao();
    private final BankAccountDao accountDao = new BankAccountDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            request.setAttribute("items", review.list());
            request.setAttribute("issueCount", review.count());
        } catch (SQLException exception) {
            getServletContext().log("Could not load triage queue.", exception);
            request.setAttribute("errorMessage", "Triage queue data is unavailable.");
        }

        String result = request.getParameter("result");
        if ("resolved".equals(result)) {
            request.setAttribute("successMessage", "Review flag resolved.");
        } else if ("unfrozen".equals(result)) {
            request.setAttribute("successMessage", "Bank account unfrozen and restored to active state.");
        } else if ("frozen".equals(result)) {
            request.setAttribute("successMessage", "Bank account frozen successfully.");
        } else if ("unchanged".equals(result)) {
            request.setAttribute("errorMessage", "The record changed before the action was saved. Review its current status.");
        }
        request.getRequestDispatcher("/WEB-INF/views/admin/queue.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object admin = request.getSession(false).getAttribute("adminId");
        if (!(admin instanceof Number adminId)) {
            response.sendError(403);
            return;
        }

        String action = request.getParameter("action");
        try {
            long accountId = Long.parseLong(request.getParameter("accountId"));
            if (accountId <= 0) throw new NumberFormatException();
            if ("resolve".equals(action)) {
                boolean changed = review.changeFlag(accountId, adminId.longValue(), null);
                response.sendRedirect(request.getContextPath() + "/admin/queue?result=" + (changed ? "resolved" : "unchanged"));
                return;
            } else if ("unfreeze".equals(action) || "freeze".equals(action)) {
                boolean frozen = "freeze".equals(action);
                boolean changed = accountDao.setFrozen(accountId, adminId.longValue(), frozen, "Review queue");
                response.sendRedirect(request.getContextPath() + "/admin/queue?result=" + (changed ? (frozen ? "frozen" : "unfrozen") : "unchanged"));
                return;
            }
        } catch (NumberFormatException exception) {
            response.sendError(400, "Invalid customer or account ID.");
            return;
        } catch (SQLException exception) {
            getServletContext().log("Could not process triage action", exception);
            response.sendError(503, "The action could not be saved.");
            return;
        }
        response.sendError(400, "Unknown queue action.");
    }
}
