package com.netbanking.config;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

public record LocalDemo(String username, String password) {
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    private static final Set<String> LOCAL_HOST = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1");

    @Override
    public String toString() {
        return "LocalDemo[username=" + username + "]";
    }

    public static LocalDemo customer(HttpServletRequest request) {
        return forRequest(request, "customer");
    }

    public static LocalDemo admin(HttpServletRequest request) {
        if (request.getHeader("Forwarded") != null || request.getHeader("X-Forwarded-For") != null
                || request.getHeader("X-Forwarded-Host") != null) return null;
        return forRequest(request, "admin");
    }

    private static LocalDemo forRequest(HttpServletRequest request, String role) {
        if (!LOOPBACK.contains(request.getRemoteAddr()) || !LOCAL_HOST.contains(request.getServerName())) return null;
        try (var input = LocalDemo.class.getClassLoader().getResourceAsStream("demo.properties")) {
            if (input == null) return null;
            Properties properties = new Properties();
            properties.load(input);
            String username = properties.getProperty("demo." + role + ".username", "").strip();
            String password = properties.getProperty("demo." + role + ".password", "");
            return username.isEmpty() || password.isEmpty() ? null : new LocalDemo(username, password);
        } catch (IOException exception) {
            request.getServletContext().log("Demo sign-in settings could not be loaded.", exception);
            return null;
        }
    }
}
