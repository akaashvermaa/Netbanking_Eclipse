package com.netbanking.security;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class CsrfTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    public enum Portal {
        ADMIN("adminCsrfToken"), CUSTOMER("customerCsrfToken");
        private final String attribute;
        Portal(String attribute) { this.attribute = attribute; }
    }

    private CsrfTokens() {
    }

    public static String ensure(HttpSession session, Portal portal) {
        synchronized (session) {
            String token = (String) session.getAttribute(portal.attribute);
            return token == null ? rotate(session, portal) : token;
        }
    }

    public static String rotate(HttpSession session, Portal portal) {
        synchronized (session) {
            String token = newToken();
            session.setAttribute(portal.attribute, token);
            return token;
        }
    }

    public static void expiredForm(HttpServletRequest request, HttpServletResponse response, String retryPath)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.setAttribute("retryPath", request.getContextPath() + retryPath);
        request.getRequestDispatcher("/WEB-INF/views/form-expired.jsp").forward(request, response);
    }

    public static boolean matches(String submitted, String expected) {
        return submitted != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), submitted.getBytes(StandardCharsets.UTF_8));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
