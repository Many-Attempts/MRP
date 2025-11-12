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

    // Statistics fields
    private int totalRatings;
    private int totalFavorites;
    private int totalMediaCreated;

    public User() {}

    public User(UUID id, String username, String passwordHash, Timestamp createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    // Getters
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Timestamp getCreatedAt() { return createdAt; }
    public int getTotalRatings() { return totalRatings; }
    public int getTotalFavorites() { return totalFavorites; }
    public int getTotalMediaCreated() { return totalMediaCreated; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public void setTotalRatings(int totalRatings) { this.totalRatings = totalRatings; }
    public void setTotalFavorites(int totalFavorites) { this.totalFavorites = totalFavorites; }
    public void setTotalMediaCreated(int totalMediaCreated) { this.totalMediaCreated = totalMediaCreated; }
}