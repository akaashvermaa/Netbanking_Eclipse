package com.netbanking.security;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import static com.netbanking.security.CsrfTokens.Portal.ADMIN;
import static com.netbanking.security.CsrfTokens.Portal.CUSTOMER;

public final class PortalSession {
    private static final long IDLE_MILLIS = Duration.ofMinutes(30).toMillis();

    private PortalSession() {}

    public static boolean adminActive(HttpSession session) {
        if (!(session.getAttribute("adminId") instanceof Number)) return false;
        if (expired(session, "adminLastSeen")) {
            clearAdmin(session);
            return false;
        }
        return true;
    }

    public static boolean customerActive(HttpSession session) {
        if (!(session.getAttribute("customerId") instanceof Long)) return false;
        if (expired(session, "customerLastSeen")) {
            clearCustomer(session);
            return false;
        }
        return true;
    }

    public static void touchAdmin(HttpSession session) {
        session.setAttribute("adminLastSeen", System.currentTimeMillis());
    }

    public static void touchCustomer(HttpSession session) {
        session.setAttribute("customerLastSeen", System.currentTimeMillis());
    }

    public static void clearAdmin(HttpSession session) {
        session.removeAttribute("adminId");
        session.removeAttribute("adminUsername");
        session.removeAttribute("adminLastSeen");
        CsrfTokens.rotate(session, ADMIN);
    }

    public static void clearCustomer(HttpSession session) {
        session.removeAttribute("customerId");
        session.removeAttribute("customerCredentialVersion");
        session.removeAttribute("customerLastSeen");
        session.removeAttribute("pendingCustomer");
        CsrfTokens.rotate(session, CUSTOMER);
    }

    private static boolean expired(HttpSession session, String attribute) {
        return session.getAttribute(attribute) instanceof Long lastSeen
                && System.currentTimeMillis() - lastSeen >= IDLE_MILLIS;
    }
}
