package ee.jalgpall.scraper.app.model;

import org.jspecify.annotations.Nullable;

// position is the registered position (VV, K, PK, R); who was in goal is decided per match from the protocol
public record RosterEntry(long playerId, @Nullable String name, @Nullable String position) {
    private static final String GOALKEEPER = "VV";

    public static RosterEntry unknown(long playerId) {
        return new RosterEntry(playerId, null, null);
    }

    public boolean isRegisteredGoalkeeper() {
        return GOALKEEPER.equals(position);
    }
}
