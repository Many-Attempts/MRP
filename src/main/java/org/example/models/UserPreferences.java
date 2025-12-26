package org.example.models;

import java.util.List;

public class UserPreferences {
    private List<String> genres;
    private List<String> mediaTypes;
    private List<String> ageRestrictions;

    public UserPreferences() {}

    public UserPreferences(List<String> genres, List<String> mediaTypes, List<String> ageRestrictions) {
        this.genres = genres;
        this.mediaTypes = mediaTypes;
        this.ageRestrictions = ageRestrictions;
    }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public List<String> getMediaTypes() { return mediaTypes; }
    public void setMediaTypes(List<String> mediaTypes) { this.mediaTypes = mediaTypes; }

    public List<String> getAgeRestrictions() { return ageRestrictions; }
    public void setAgeRestrictions(List<String> ageRestrictions) { this.ageRestrictions = ageRestrictions; }

    public boolean isEmpty() {
        return (genres == null || genres.isEmpty())
            && (mediaTypes == null || mediaTypes.isEmpty())
            && (ageRestrictions == null || ageRestrictions.isEmpty());
    }
}
