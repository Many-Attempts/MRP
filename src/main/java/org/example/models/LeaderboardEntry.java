package org.example.models;

public class LeaderboardEntry {
    private int rank;
    private String username;
    private int ratingCount;
    private int mediaCreated;
    private int likesGiven;
    private int totalActivity;

    public LeaderboardEntry() {}

    public LeaderboardEntry(String username, int ratingCount, int mediaCreated, int likesGiven) {
        this.username = username;
        this.ratingCount = ratingCount;
        this.mediaCreated = mediaCreated;
        this.likesGiven = likesGiven;
        this.totalActivity = ratingCount + mediaCreated + likesGiven;
    }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }

    public int getMediaCreated() { return mediaCreated; }
    public void setMediaCreated(int mediaCreated) { this.mediaCreated = mediaCreated; }

    public int getLikesGiven() { return likesGiven; }
    public void setLikesGiven(int likesGiven) { this.likesGiven = likesGiven; }

    public int getTotalActivity() { return totalActivity; }
    public void setTotalActivity(int totalActivity) { this.totalActivity = totalActivity; }
}
