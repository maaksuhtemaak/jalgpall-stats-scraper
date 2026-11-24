package ee.jalgpall.scraper.app.model;

import java.util.List;

public record TeamSeasonStats(long teamId, int year, List<PlayerSeasonStats> players) {}
