package ee.jalgpall.scraper.app.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record MatchSummary(int games, int minutes, int wins, int draws, int losses,
                           int gamesInGoal, int goalsConceded, int cleanSheets) {

    // Goalkeeping stats only count the matches where the player was in goal
    public static MatchSummary of(RosterEntry player, List<PlayedMatch> matches, Map<Long, MatchProtocol> protocols) {
        int minutes = 0;
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int gamesInGoal = 0;
        int goalsConceded = 0;
        int cleanSheets = 0;
        for (PlayedMatch match : matches) {
            minutes += match.minutes();
            switch (match.outcome()) {
                case WIN -> wins++;
                case DRAW -> draws++;
                case LOSS -> losses++;
            }

            Optional<Appearance> inGoal = Optional.ofNullable(protocols.get(match.protocolId()))
                    .flatMap(protocol -> protocol.inGoal(player.playerId(), player.isRegisteredGoalkeeper()));
            if (inGoal.isPresent()) {
                gamesInGoal++;
                goalsConceded += inGoal.get().goalsConceded();
                cleanSheets += inGoal.get().cleanSheet() ? 1 : 0;
            }
        }
        return new MatchSummary(matches.size(), minutes, wins, draws, losses, gamesInGoal, goalsConceded, cleanSheets);
    }
}
