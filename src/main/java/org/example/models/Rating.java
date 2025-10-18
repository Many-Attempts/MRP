package org.example.models;

import java.sql.Timestamp;
import java.util.UUID;

public class Rating {
    private UUID id;
    private UUID mediaId;
    private UUID userId;
    private int stars; // 1-5
    private String comment;
    private boolean isConfirmed;
    private Timestamp createdAt;

    // Additional fields for responses
    private String username;
    private int likeCount;
    private boolean likedByCurrentUser;

    public Rating() {}

    public Rating(UUID id, UUID mediaId, UUID userId, int stars, String comment,
                 boolean isConfirmed, Timestamp createdAt) {
        this.id = id;
        this.mediaId = mediaId;
        this.userId = userId;
        this.stars = stars;
        this.comment = comment;
        this.isConfirmed = isConfirmed;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMediaId() { return mediaId; }
    public void setMediaId(UUID mediaId) { this.mediaId = mediaId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public boolean isConfirmed() { return isConfirmed; }
    public void setConfirmed(boolean confirmed) { isConfirmed = confirmed; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public boolean isLikedByCurrentUser() { return likedByCurrentUser; }
    public void setLikedByCurrentUser(boolean likedByCurrentUser) {
        this.likedByCurrentUser = likedByCurrentUser;
    }
}