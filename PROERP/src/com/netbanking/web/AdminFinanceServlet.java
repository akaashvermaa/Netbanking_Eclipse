package com.netbanking.web;

import com.netbanking.dao.AdminDao;
import com.netbanking.dao.BankAccountDao;
import com.netbanking.dao.CustomerBankingDao;
import com.netbanking.dao.FinanceDao;
import com.netbanking.dao.FinanceDao.Operation;
import com.netbanking.dao.AccountReviewDao;
import com.netbanking.dao.AccountReviewDao.Category;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

@WebServlet("/admin/finance")
public class AdminFinanceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final FinanceDao finance = new FinanceDao();
    private final CustomerBankingDao banking = new CustomerBankingDao();
    private final BankAccountDao accounts = new BankAccountDao();
    private final AdminDao customers = new AdminDao();
    private final AccountReviewDao review = new AccountReviewDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Long id = positiveId(request.getParameter("accountId"));
        Long customerId = positiveId(request.getParameter("customerId"));
        if ((id == null && request.getParameter("accountId") != null)
                || (customerId == null && request.getParameter("customerId") != null)) {
            response.sendError(400, "Invalid account or customer ID.");
            return;
        }
        try {
            if (id != null) {
                var account = accounts.find(id);
                if (account.isEmpty()) {
                    response.sendError(404, "Bank account not found.");
                    return;
                }
                request.setAttribute("account", account.get());
                boolean customerActive = customers.findCustomer(account.get().customerId())
                        .map(customer -> "ACTIVE".equals(customer.status())).orElse(false);
                request.setAttribute("canPost", customerActive && "ACTIVE".equals(account.get().status()));
            } else if (customerId != null) {
                var customer = customers.findCustomer(customerId);
                if (customer.isEmpty()) { response.sendError(404, "Customer not found."); return; }
                customers.findCustomerProfile(customerId).ifPresent(profile -> request.setAttribute("financeCustomerName", profile.fullName()));
                request.setAttribute("financeCustomer", customer.get());
                request.setAttribute("customerAccounts", accounts.list(customerId, "", ""));
                request.setAttribute("activeAccounts", "ACTIVE".equals(customer.get().status())
                        ? accounts.list(customerId, "", "ACTIVE") : List.of());
            } else {
                String search = request.getParameter("q");
                if (search != null && search.strip().length() > 100) {
                    response.sendError(400, "Search can contain up to 100 characters.");
                    return;
                }
                if (search != null && !search.isBlank()) {
                    request.setAttribute("matches", customers.findCustomerDetailsList(search.strip(), "").stream().limit(20).toList());
                }
            }
            String loanStatus = request.getParameter("loanStatus");
            if (!"OPEN".equalsIgnoreCase(loanStatus) && !"PAID".equalsIgnoreCase(loanStatus)) {
                loanStatus = "";
            }
            request.setAttribute("loanStatus", loanStatus);
            request.setAttribute("totals", finance.totals());
            Long scope = id == null ? customerId : null;
            request.setAttribute("loans", finance.loans(id, loanStatus, scope));
            request.setAttribute("movements", banking.adminActivity(id, scope));
            if (id != null || scope != null) request.setAttribute("selectedTotals", finance.lendingTotals(id, scope));
            request.getRequestDispatcher("/WEB-INF/views/admin/finance.jsp").forward(request, response);
        } catch (SQLException exception) {
            getServletContext().log("Could not load finance workspace", exception);
            response.sendError(503, "Finance data is temporarily unavailable.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!(request.getSession(false).getAttribute("adminId") instanceof Number adminId)) {
            response.sendError(403);
            return;
        }
        Long id = positiveId(request.getParameter("accountId"));
        Long loanId = positiveId(request.getParameter("loanId"));
        String amount = request.getParameter("amount");
        String action = request.getParameter("operation");
        String key = request.getParameter("requestKey");
        if ("FLAG".equals(action)) {
            if (id == null) { response.sendError(400, "Choose an account."); return; }
            String category = request.getParameter("category");
            try {
                boolean changed = review.changeFlag(id, adminId.longValue(), Category.valueOf(category == null ? "" : category));
                response.sendRedirect(request.getContextPath() + "/admin/finance?accountId=" + id
                        + "&result=" + (changed ? "flagged" : "flag_unchanged"));
            } catch (IllegalArgumentException exception) {
                response.sendError(400, "Choose a review category.");
            } catch (SQLException exception) {
                getServletContext().log("Could not flag account", exception);
                response.sendError(503, "The flag could not be saved.");
            }
            return;
        }
        try {
            if (key == null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            response.sendError(400, "Invalid finance operation.");
            return;
        }
        if ("ADJUST_BALANCE".equals(action)) {
            adjustBalance(request, response, id, adminId.longValue(), key);
            return;
        }
        Operation operation;
        try {
            operation = Operation.valueOf(action == null ? "" : action);
        } catch (IllegalArgumentException exception) {
            response.sendError(400, "Invalid finance operation.");
            return;
        }
        if (id == null || (operation == Operation.LOAN_REPAYMENT && loanId == null)) {
            response.sendError(400, "Choose an account and loan where applicable.");
            return;
        }
        if (operation != Operation.LOAN_REPAYMENT) loanId = null;
        String result = "invalid_amount";
        String pattern = operation == Operation.LOAN_REPAYMENT ? "[0-9]{1,17}(\\.[0-9]{1,2})?" : "[0-9]{1,9}(\\.[0-9]{1,2})?";
        if (amount != null && amount.matches(pattern)) {
            try {
                boolean settle = operation == Operation.LOAN_REPAYMENT && "1".equals(request.getParameter("settle"));
                var terms = com.netbanking.model.LoanTerms.offer(request.getParameter("purpose"), request.getParameter("duration"));
                var outcome = operation == Operation.LOAN_DISBURSEMENT
                        ? terms == null ? FinanceDao.Outcome.INVALID_AMOUNT
                                : finance.disburse(id, adminId.longValue(), new BigDecimal(amount), key, terms)
                        : finance.post(operation, id, loanId, adminId.longValue(), new BigDecimal(amount), key, settle);
                result = outcome.name().toLowerCase(Locale.ROOT);
            } catch (SQLException exception) {
                getServletContext().log("Could not post finance operation", exception);
                response.sendError(503, "The operation could not be recorded.");
                return;
            }
        }
        response.sendRedirect(request.getContextPath() + "/admin/finance?accountId=" + id + "&result=" + result);
    }

    private void adjustBalance(HttpServletRequest request, HttpServletResponse response, Long accountId,
                               long adminId, String key) throws IOException {
        if (accountId == null) {
            response.sendError(400, "Choose an account.");
            return;
        }
        try {
            var account = accounts.find(accountId);
            if (account.isEmpty()) {
                response.sendError(404, "Bank account not found.");
                return;
            }
            String expected = request.getParameter("expectedBalance");
            String target = request.getParameter("amount");
            String currencyAmount = "[0-9]{1,17}(\\.[0-9]{1,2})?";
            FinanceDao.Outcome outcome = FinanceDao.Outcome.INVALID_AMOUNT;
            if (expected != null && target != null && expected.matches(currencyAmount) && target.matches(currencyAmount)) {
                outcome = finance.adjustBalance(accountId, adminId, new BigDecimal(expected), new BigDecimal(target), key);
            }
            response.sendRedirect(request.getContextPath() + "/admin/customer?id=" + account.get().customerId()
                    + "&balanceAccount=" + accountId + "&result=balance-" + outcome.name().toLowerCase(Locale.ROOT)
                    + "#account-" + accountId);
        } catch (SQLException exception) {
            getServletContext().log("Could not adjust account balance", exception);
            response.sendError(503, "The balance adjustment could not be recorded.");
        }
    }

    private Long positiveId(String value) {
        try {
            long number = Long.parseLong(value);
            return number > 0 ? number : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
