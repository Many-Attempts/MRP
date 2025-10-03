package org.example.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.sql.Timestamp;
import java.util.UUID;

public class User {
    private UUID id;
    private String username;
    @JsonIgnore
    private String passwordHash;
    private Timestamp createdAt;

    public User() {}

    public User(UUID id, String username, String passwordHash, Timestamp createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    // Getters
    public void setId(UUID id) { this.id = id; }

    public void setUsername(String username) { this.username = username; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

}