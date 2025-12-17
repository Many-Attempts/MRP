package org.example.repositories;

import org.example.db.Database;
import org.example.models.MediaEntry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MediaRepository {
    private final Database db = Database.getInstance();

    public MediaEntry findById(UUID id) throws SQLException {
        ResultSet rs = db.query(
            "SELECT m.*, u.username as creator_username, " +
            "COALESCE(AVG(r.stars), 0) as avg_rating, " +
            "COUNT(DISTINCT r.id) as total_ratings " +
            "FROM media_entries m " +
            "JOIN users u ON m.creator_id = u.id " +
            "LEFT JOIN ratings r ON m.id = r.media_id " +
            "WHERE m.id = ? " +
            "GROUP BY m.id, u.username",
            id
        );
        if (rs.next()) {
            return mapResultSetToMedia(rs);
        }
        return null;
    }

    public boolean existsById(UUID id) throws SQLException {
        return db.exists("SELECT 1 FROM media_entries WHERE id = ?", id);
    }

    public UUID getCreatorId(UUID mediaId) throws SQLException {
        Object creatorId = db.getValue("SELECT creator_id FROM media_entries WHERE id = ?", mediaId);
        return creatorId != null ? (UUID) creatorId : null;
    }

    public List<MediaEntry> findAll(Map<String, String> filters, String sortBy) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT m.*, u.username as creator_username, " +
            "COALESCE(AVG(r.stars), 0) as avg_rating, " +
            "COUNT(DISTINCT r.id) as total_ratings " +
            "FROM media_entries m " +
            "JOIN users u ON m.creator_id = u.id " +
            "LEFT JOIN ratings r ON m.id = r.media_id " +
            "WHERE 1=1 "
        );

        List<Object> queryParams = new ArrayList<>();

        if (filters.containsKey("search")) {
            sql.append("AND LOWER(m.title) LIKE LOWER(?) ");
            queryParams.add("%" + filters.get("search") + "%");
        }

        if (filters.containsKey("type")) {
            sql.append("AND m.media_type = ? ");
            queryParams.add(filters.get("type"));
        }

        if (filters.containsKey("genre")) {
            sql.append("AND LOWER(m.genres) LIKE LOWER(?) ");
            queryParams.add("%" + filters.get("genre") + "%");
        }

        if (filters.containsKey("year")) {
            sql.append("AND m.release_year = ? ");
            queryParams.add(Integer.parseInt(filters.get("year")));
        }

        if (filters.containsKey("age")) {
            sql.append("AND m.age_restriction = ? ");
            queryParams.add(filters.get("age"));
        }

        sql.append("GROUP BY m.id, u.username ");

        // Rating filter (HAVING clause after GROUP BY)
        if (filters.containsKey("minRating")) {
            sql.append("HAVING COALESCE(AVG(r.stars), 0) >= ? ");
            queryParams.add(Double.parseDouble(filters.get("minRating")));
        }

        switch (sortBy) {
            case "year":
                sql.append("ORDER BY m.release_year DESC");
                break;
            case "rating":
                sql.append("ORDER BY avg_rating DESC");
                break;
            default:
                sql.append("ORDER BY m.title ASC");
        }

        ResultSet rs = db.query(sql.toString(), queryParams.toArray());
        List<MediaEntry> mediaList = new ArrayList<>();

        while (rs.next()) {
            mediaList.add(mapResultSetToMedia(rs));
        }

        return mediaList;
    }

    public MediaEntry create(MediaEntry media, UUID creatorId) throws SQLException {
        UUID mediaId = db.insert(
            "INSERT INTO media_entries (id, title, description, media_type, release_year, genres, age_restriction, creator_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            media.getTitle(),
            media.getDescription(),
            media.getMediaType(),
            media.getReleaseYear(),
            media.getGenres(),
            media.getAgeRestriction(),
            creatorId
        );
        media.setId(mediaId);
        media.setCreatorId(creatorId);
        return media;
    }

    public int update(MediaEntry media) throws SQLException {
        return db.update(
            "UPDATE media_entries SET title = ?, description = ?, media_type = ?, " +
            "release_year = ?, genres = ?, age_restriction = ? WHERE id = ?",
            media.getTitle(),
            media.getDescription(),
            media.getMediaType(),
            media.getReleaseYear(),
            media.getGenres(),
            media.getAgeRestriction(),
            media.getId()
        );
    }

    public int delete(UUID id) throws SQLException {
        return db.update("DELETE FROM media_entries WHERE id = ?", id);
    }

    private MediaEntry mapResultSetToMedia(ResultSet rs) throws SQLException {
        MediaEntry media = new MediaEntry();
        media.setId(db.getUUID(rs, "id"));
        media.setTitle(rs.getString("title"));
        media.setDescription(rs.getString("description"));
        media.setMediaType(rs.getString("media_type"));
        media.setReleaseYear((Integer) rs.getObject("release_year"));
        media.setGenres(rs.getString("genres"));
        media.setAgeRestriction(rs.getString("age_restriction"));
        media.setCreatorId(db.getUUID(rs, "creator_id"));
        media.setCreatedAt(rs.getTimestamp("created_at"));
        media.setCreatorUsername(rs.getString("creator_username"));
        media.setAverageRating(rs.getDouble("avg_rating"));
        media.setTotalRatings(rs.getInt("total_ratings"));
        return media;
    }
}
