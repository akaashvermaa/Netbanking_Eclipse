package com.netbanking.setup;

import com.netbanking.dao.AdminDao;
import com.netbanking.security.PasswordHasher;
import java.io.Console;
import java.util.Arrays;
import java.util.Scanner;

public final class InitialAdminSetup {
    private InitialAdminSetup() {
    }

    public static void main(String[] args) throws Exception {
        AdminDao adminDao = new AdminDao();
        if (adminDao.hasAnyAdmin()) {
            System.out.println("An admin account already exists. No changes were made.");
            return;
        }

        Console console = System.console();
        Scanner scanner = console == null ? new Scanner(System.in) : null;
        String username = readLine(console, scanner, "Choose admin username: ").trim();
        if (!username.matches("[A-Za-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException("Use 3-50 letters, digits, dots, underscores, or hyphens for the username.");
        }

        char[] password = readPassword(console, scanner, "Choose admin password (at least 12 characters): ");
        char[] confirmation = readPassword(console, scanner, "Confirm admin password: ");
        try {
            if (password.length < 12 || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Passwords must match and contain at least 12 characters.");
            }
            adminDao.createInitialAdmin(username, PasswordHasher.create(password));
            System.out.println("Initial admin account created. You can now sign in at /admin/login.");
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    private static String readLine(Console console, Scanner scanner, String prompt) {
        return console == null ? readFromScanner(scanner, prompt) : console.readLine(prompt);
    }

    private static char[] readPassword(Console console, Scanner scanner, String prompt) {
        if (console != null) {
            return console.readPassword(prompt);
        }
        return readFromScanner(scanner, prompt).toCharArray();
    }

    private static String readFromScanner(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }
}
