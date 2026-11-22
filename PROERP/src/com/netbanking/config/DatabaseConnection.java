package com.netbanking.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConnection {
    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        Properties properties = new Properties();
        try (InputStream input = DatabaseConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (input == null) {
                throw new SQLException("Missing src/db.properties. Copy db.properties.example and set your MySQL details.");
            }
            properties.load(input);
        } catch (IOException exception) {
            throw new SQLException("Could not read db.properties.", exception);
        }

        String driver = properties.getProperty("db.driver", "com.mysql.cj.jdbc.Driver").trim();
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException exception) {
            throw new SQLException("MySQL Connector/J is missing from the project build path and WEB-INF/lib.", exception);
        }

        String url = properties.getProperty("db.url");
        String username = properties.getProperty("db.username");
        String password = properties.getProperty("db.password");
        if (url == null || username == null || password == null || password.equals("CHANGE_ME")) {
            throw new SQLException("Set db.url, db.username, and db.password in src/db.properties.");
        }
        return DriverManager.getConnection(url.trim(), username.trim(), password);
    }
}
