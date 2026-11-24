package com.netbanking.filter;

import com.netbanking.security.CsrfTokens;
import com.netbanking.security.PortalSession;
import static com.netbanking.security.CsrfTokens.Portal.ADMIN;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@WebFilter("/admin/*")
public class AdminAuthFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(true);
        boolean signedIn = PortalSession.adminActive(session);
        String csrfToken = CsrfTokens.ensure(session, ADMIN);
        request.setAttribute("csrfToken", csrfToken);
        String path = request.getServletPath();
        boolean loginRequest = "/admin/login".equals(path);

        if ("POST".equalsIgnoreCase(request.getMethod()) && !CsrfTokens.matches(request.getParameter("_csrf"), csrfToken)) {
            CsrfTokens.expiredForm(request, response, recoveryPath(request, signedIn));
            return;
        }

        if (!loginRequest && !signedIn) {
            response.sendRedirect(request.getContextPath() + "/admin/login");
            return;
        }
        if (signedIn) PortalSession.touchAdmin(session);

        chain.doFilter(request, response);
    }

    private String recoveryPath(HttpServletRequest request, boolean signedIn) {
        String path = request.getServletPath();
        if (!signedIn || "/admin/login".equals(path)) return "/admin/login";
        return switch (path) {
            case "/admin/customers" -> "create".equals(request.getParameter("action"))
                    ? path + "?new=1" : "/admin/customer" + idQuery(request, "customerId", "id");
            case "/admin/customer" -> path + idQuery(request, "id", "id");
            case "/admin/finance" -> path + idQuery(request, "accountId", "accountId");
            case "/admin/password", "/admin/requests", "/admin/accounts", "/admin/queue" -> path;
            default -> "/admin/dashboard";
        };
    }

    private String idQuery(HttpServletRequest request, String parameter, String queryName) {
        String id = request.getParameter(parameter);
        return id != null && id.matches("[1-9][0-9]{0,18}") ? "?" + queryName + "=" + id : "";
    }
}
