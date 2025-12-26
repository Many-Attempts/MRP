package org.example.services;

import org.example.models.LeaderboardEntry;
import org.example.repositories.LeaderboardRepository;

import java.sql.SQLException;
import java.util.List;

public class LeaderboardService {
    private final LeaderboardRepository leaderboardRepository = new LeaderboardRepository();

    private static final int LEADERBOARD_LIMIT = 10;

    public List<LeaderboardEntry> getLeaderboard() throws SQLException {
        List<LeaderboardEntry> entries = leaderboardRepository.findTopUsersByActivity(LEADERBOARD_LIMIT);

        int rank = 1;
        for (LeaderboardEntry entry : entries) {
            entry.setRank(rank++);
        }

        return entries;
    }
}
