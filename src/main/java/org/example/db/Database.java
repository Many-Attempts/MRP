package org.example.db;

import org.example.utils.UUIDGenerator;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Database {
    protected static final String URL = "jdbc:postgresql://localhost:5433/mrp_db";
    protected static final String USER = "postgres";
    protected static final String PASSWORD = "postgres";

    protected static Database instance;
    protected Connection connection;

    protected Database() {
        connect();
    }

    public static synchronized Database getInstance() {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    private void connect() {
        try {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Connected to PostgreSQL database!");
        } catch (SQLException e) {
            System.err.println("Connection failed: " + e.getMessage());
            throw new RuntimeException("Failed to connect to database", e);
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
        return connection;
    }

    // Execute a query and return ResultSet
    public ResultSet query(String sql, Object... params) throws SQLException {
        PreparedStatement stmt = prepareStatement(sql, params);
        return stmt.executeQuery();
    }

    // Execute an update (INSERT, UPDATE, DELETE) and return affected rows
    public int update(String sql, Object... params) throws SQLException {
        PreparedStatement stmt = prepareStatement(sql, params);
        return stmt.executeUpdate();
    }

    // Execute an INSERT with pre-generated UUID
    public UUID insert(String sql, Object... params) throws SQLException {
        UUID uuid = UUIDGenerator.generateUUIDv7();

        // Create a new params array with UUID as the first parameter
        Object[] newParams = new Object[params.length + 1];
        newParams[0] = uuid;  // Store as UUID object
        System.arraycopy(params, 0, newParams, 1, params.length);

        PreparedStatement stmt = prepareStatement(sql, newParams);
        int affectedRows = stmt.executeUpdate();

        if (affectedRows == 0) {
            throw new SQLException("Insert failed, no rows affected.");
        }

        return uuid;
    }

    // Execute an INSERT with provided ID (for special cases)
    public void insertWithId(String sql, Object... params) throws SQLException {
        PreparedStatement stmt = prepareStatement(sql, params);
        int affectedRows = stmt.executeUpdate();

        if (affectedRows == 0) {
            throw new SQLException("Insert failed, no rows affected.");
        }
    }

    // Check if a record exists
    public boolean exists(String sql, Object... params) throws SQLException {
        try (ResultSet rs = query(sql, params)) {
            return rs.next();
        }
    }

    // Get a single value
    public Object getValue(String sql, Object... params) throws SQLException {
        try (ResultSet rs = query(sql, params)) {
            if (rs.next()) {
                return rs.getObject(1);
            }
            return null;
        }
    }

    // Get a list of values
    public List<Object> getValues(String sql, Object... params) throws SQLException {
        List<Object> values = new ArrayList<>();
        try (ResultSet rs = query(sql, params)) {
            while (rs.next()) {
                values.add(rs.getObject(1));
            }
        }
        return values;
    }

    // Helper method to create a PreparedStatement with parameters safely set
    // This prevents SQL injection by using parameterized queries instead of string concatenation
    private PreparedStatement prepareStatement(String sql, Object... params) throws SQLException {
        PreparedStatement stmt = getConnection().prepareStatement(sql);
        setParameters(stmt, params);
        return stmt;
    }

    // Safely binds parameter values to the PreparedStatement placeholders (?)
    // Handles all parameter types including native UUID objects
    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    // Transaction support - begins a new transaction by disabling auto-commit
    // Use this when you need multiple operations to succeed or fail as a unit
    public void beginTransaction() throws SQLException {
        getConnection().setAutoCommit(false);
    }

    // Commits the current transaction, making all changes permanent
    // Re-enables auto-commit for future non-transactional operations
    public void commit() throws SQLException {
        getConnection().commit();
        getConnection().setAutoCommit(true);
    }

    // Rolls back the current transaction, undoing all changes since beginTransaction()
    // Use this in catch blocks when an error occurs during a transaction
    public void rollback() throws SQLException {
        getConnection().rollback();
        getConnection().setAutoCommit(true);
    }

    // Helper method to get UUID from ResultSet by column name
    // Retrieves native UUID object from database, handling nulls
    public UUID getUUID(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName, UUID.class);
    }

    // Helper method to get UUID from ResultSet by column index (1-based)
    // Retrieves native UUID object from database, handling nulls
    public UUID getUUID(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getObject(columnIndex, UUID.class);
    }

    // Closes the database connection cleanly
    // Always call this when your application shuts down to free resources
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}