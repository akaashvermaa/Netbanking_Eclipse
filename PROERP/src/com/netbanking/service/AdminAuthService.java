package com.netbanking.service;

import com.netbanking.dao.AdminDao;
import com.netbanking.model.AdminCredential;
import com.netbanking.security.PasswordHasher;
import java.sql.SQLException;
import java.util.Optional;

public class AdminAuthService {
    private final AdminDao adminDao;

    public AdminAuthService() {
        this(new AdminDao());
    }

    public AdminAuthService(AdminDao adminDao) {
        this.adminDao = adminDao;
    }

    public Optional<AdminCredential> authenticate(String username, char[] password) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            return Optional.empty();
        }
        Optional<AdminCredential> admin = adminDao.findActiveByUsername(username.trim());
        if (admin.isPresent() && PasswordHasher.verify(password, admin.get().passwordHash(),
                admin.get().passwordSalt(), admin.get().iterations())) {
            return admin;
        }
        return Optional.empty();
    }
}
