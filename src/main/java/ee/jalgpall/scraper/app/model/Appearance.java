package ee.jalgpall.scraper.app.model;

public record Appearance(long playerId, boolean markedAsGoalkeeper, int goalsConceded) {

    public boolean cleanSheet() {
        return goalsConceded == 0;
    }
}
