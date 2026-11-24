package com.netbanking.filter;

import com.netbanking.dao.CustomerAccessDao;
import com.netbanking.security.CsrfTokens;
import com.netbanking.security.PortalSession;
import static com.netbanking.security.CsrfTokens.Portal.CUSTOMER;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

@WebFilter("/customer/*")
public class CustomerAuthFilter implements Filter {
    private final CustomerAccessDao customers = new CustomerAccessDao();

    @Override
    public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain)
            throws IOException, ServletException {
        var request = (HttpServletRequest) input;
        var response = (HttpServletResponse) output;
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        var session = request.getSession();
        boolean signedIn = PortalSession.customerActive(session);
        String csrf = CsrfTokens.ensure(session, CUSTOMER);
        request.setAttribute("csrfToken", csrf);
        String path = request.getPathInfo();
        if ("POST".equals(request.getMethod()) && !CsrfTokens.matches(request.getParameter("_csrf"), csrf)) {
            CsrfTokens.expiredForm(request, response, recoveryPath(path, signedIn));
            return;
        }
        boolean publicRoute = "/login".equals(path) || "/register".equals(path) || "/approval".equals(path)
                || "/logout".equals(path);
        if (!signedIn) {
            if (publicRoute) chain.doFilter(request, response);
            else response.sendRedirect(request.getContextPath() + "/customer/login");
            return;
        }

        CustomerAccessDao.Identity customer;
        try {
            customer = session.getAttribute("customerId") instanceof Long id ? customers.activeCustomer(id) : null;
        } catch (SQLException exception) {
            request.getServletContext().log("Could not validate customer access", exception);
            response.sendError(503, "Customer banking is temporarily unavailable.");
            return;
        }
        if (customer == null || !Long.valueOf(customer.credentialVersion()).equals(session.getAttribute("customerCredentialVersion"))) {
            PortalSession.clearCustomer(session);
            response.sendRedirect(request.getContextPath() + "/customer/login");
            return;
        }
        boolean notificationPoll = "GET".equals(request.getMethod()) && "/updates".equals(path)
                && "popup".equals(request.getParameter("view"));
        if (!notificationPoll) PortalSession.touchCustomer(session);
        if (customer.passwordChangeRequired() && !"/password".equals(path) && !"/logout".equals(path)
                && !"/login".equals(path)) {
            response.sendRedirect(request.getContextPath() + "/customer/password");
            return;
        }
        request.setAttribute("customerIdentity", customer);
        chain.doFilter(request, response);
    }

    private String recoveryPath(String path, boolean signedIn) {
        return "/customer" + switch (path == null ? "" : path) {
            case "/login", "/register", "/approval" -> path;
            case "/transfer", "/loans", "/bills", "/deposits", "/goals", "/overview",
                    "/cards", "/payees", "/services", "/password", "/profile" -> signedIn ? path : "/login";
            default -> signedIn ? "/overview" : "/login";
        };
    }
}
