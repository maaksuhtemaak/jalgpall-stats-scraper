package ee.jalgpall.scraper.app.model;

import java.util.List;
import java.util.Optional;

public record MatchProtocol(long protocolId, boolean played, List<Appearance> appearances) {

    // The protocol's (VV) marker decides who was in goal. Some older protocols have no markers at all;
    // then the player's registered position decides.
    public Optional<Appearance> inGoal(long playerId, boolean registeredGoalkeeper) {
        boolean goalkeeperMarked = appearances.stream().anyMatch(Appearance::markedAsGoalkeeper);
        return appearances.stream()
                .filter(appearance -> appearance.playerId() == playerId)
                .filter(appearance -> goalkeeperMarked ? appearance.markedAsGoalkeeper() : registeredGoalkeeper)
                .findFirst();
    }
}
