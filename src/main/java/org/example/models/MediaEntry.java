package org.example.models;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class MediaEntry {
    private UUID id;
    private String title;
    private String description;
    private String mediaType; // movie, series, game
    private Integer releaseYear;
    private String genres; // comma-separated
    private String ageRestriction;
    private UUID creatorId;
    private Timestamp createdAt;

    // Additional fields for responses
    private String creatorUsername;
    private double averageRating;
    private int totalRatings;
    private List<Rating> ratings;

    public MediaEntry() {}

    public MediaEntry(UUID id, String title, String description, String mediaType,
                     Integer releaseYear, String genres, String ageRestriction,
                     UUID creatorId, Timestamp createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.mediaType = mediaType;
        this.releaseYear = releaseYear;
        this.genres = genres;
        this.ageRestriction = ageRestriction;
        this.creatorId = creatorId;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public Integer getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }

    public String getGenres() { return genres; }
    public void setGenres(String genres) { this.genres = genres; }

    public String getAgeRestriction() { return ageRestriction; }
    public void setAgeRestriction(String ageRestriction) { this.ageRestriction = ageRestriction; }

    public UUID getCreatorId() { return creatorId; }
    public void setCreatorId(UUID creatorId) { this.creatorId = creatorId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getCreatorUsername() { return creatorUsername; }
    public void setCreatorUsername(String creatorUsername) { this.creatorUsername = creatorUsername; }

    public double getAverageRating() { return averageRating; }
    public void setAverageRating(double averageRating) { this.averageRating = averageRating; }

    public int getTotalRatings() { return totalRatings; }
    public void setTotalRatings(int totalRatings) { this.totalRatings = totalRatings; }

    public List<Rating> getRatings() { return ratings; }
    public void setRatings(List<Rating> ratings) { this.ratings = ratings; }
}