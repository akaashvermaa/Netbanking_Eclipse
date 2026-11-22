package com.netbanking.security;

import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class CsrfTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SESSION_ATTRIBUTE = "csrfToken";

    private CsrfTokens() {
    }

    public static String ensure(HttpSession session) {
        String token = (String) session.getAttribute(SESSION_ATTRIBUTE);
        if (token == null) {
            token = newToken();
            session.setAttribute(SESSION_ATTRIBUTE, token);
        }
        return token;
    }

    public static String rotate(HttpSession session) {
        String token = newToken();
        session.setAttribute(SESSION_ATTRIBUTE, token);
        return token;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
