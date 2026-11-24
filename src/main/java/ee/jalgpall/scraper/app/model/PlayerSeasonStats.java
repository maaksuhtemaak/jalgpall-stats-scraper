package ee.jalgpall.scraper.app.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record PlayerSeasonStats(
        long playerId,
        @Nullable String name,
        @Nullable String position,
        int games,
        int minutes,
        int goals,
        int assists,
        int yellowCards,
        int redCards,
        int wins,
        int draws,
        int losses,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Goalkeeping goalkeeping) {

    // Only for players who were in goal in at least one match; games counts those matches
    public record Goalkeeping(int games, int cleanSheets, int goalsConceded) {

        @JsonProperty
        public @Nullable Double goalsConcededPerGame() {
            return ratio(goalsConceded, games);
        }
    }

    public static PlayerSeasonStats of(RosterEntry player, PlayerTotals totals, MatchSummary matches) {
        return new PlayerSeasonStats(
                player.playerId(),
                player.name(),
                player.position(),
                matches.games(),
                matches.minutes(),
                totals.goals(),
                totals.assists(),
                totals.yellowCards(),
                totals.redCards(),
                matches.wins(),
                matches.draws(),
                matches.losses(),
                matches.gamesInGoal() == 0
                        ? null
                        : new Goalkeeping(matches.gamesInGoal(), matches.cleanSheets(), matches.goalsConceded()));
    }

    @JsonProperty
    public @Nullable Double minutesPerGoal() {
        return ratio(minutes, goals);
    }

    @JsonProperty
    public @Nullable Double minutesPerYellowCard() {
        return ratio(minutes, yellowCards);
    }

    @JsonProperty
    public @Nullable Double averageMinutesPerGame() {
        return ratio(minutes, games);
    }

    @JsonProperty
    public @Nullable Double winRate() {
        return ratio(wins, games);
    }

    @JsonProperty
    public @Nullable Double drawRate() {
        return ratio(draws, games);
    }

    @JsonProperty
    public @Nullable Double lossRate() {
        return ratio(losses, games);
    }

    /** Rounded to 3 decimals, like the sheet's {@code round(x, 3)}. */
    private static @Nullable Double ratio(int dividend, int divisor) {
        return divisor == 0 ? null : Math.round(dividend * 1000.0 / divisor) / 1000.0;
    }
}
