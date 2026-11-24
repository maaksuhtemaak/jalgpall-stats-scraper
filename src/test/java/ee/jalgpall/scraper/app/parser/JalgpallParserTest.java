package ee.jalgpall.scraper.app.parser;

import ee.jalgpall.scraper.app.Fixtures;
import ee.jalgpall.scraper.app.model.League;
import ee.jalgpall.scraper.app.model.PlayedMatch;
import ee.jalgpall.scraper.app.model.PlayerTotals;
import ee.jalgpall.scraper.app.model.RosterEntry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static ee.jalgpall.scraper.app.model.PlayedMatch.Outcome.DRAW;
import static ee.jalgpall.scraper.app.model.PlayedMatch.Outcome.LOSS;
import static ee.jalgpall.scraper.app.model.PlayedMatch.Outcome.WIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JalgpallParserTest {
    private final JalgpallParser parser = new JalgpallParser(JsonMapper.builder().build());

    @Test
    void rosterIncludesPlayersOnlyListedInStatistics() {
        List<RosterEntry> roster = parser.parseRoster(Fixtures.read("team-season.json"));

        assertThat(roster).containsExactly(
                new RosterEntry(100, "Kalle Kask", "VV"),
                new RosterEntry(101, "Mati Maasikas", "PK"),
                new RosterEntry(102, "Jaan Tamm", "VV"),
                new RosterEntry(104, "Toomas Saar", "R"),
                new RosterEntry(105, "Peeter Pihlakas", null));
        assertThat(roster.getFirst().isRegisteredGoalkeeper()).isTrue();
        assertThat(roster.get(1).isRegisteredGoalkeeper()).isFalse();
    }

    @Test
    void unknownTeamHasEmptyRoster() {
        assertThat(parser.parseRoster("")).isEmpty();
    }

    @Test
    void parsesTotalsAndLeagues() {
        String json = """
                {"body":{"minutes":2526,"games":30,"goals":32,"assists":2,"yellowCards":4,"redCards":1,
                 "leagues":"<option value=\\"null\\">Kõik</option>\\n<option value=\\"537\\">Teine liiga B</option>\\n<option value=\\"514\\">Taliturniir - E-tasand</option>"},
                 "status":"success"}
                """;

        assertThat(parser.parseTotals(json)).isEqualTo(new PlayerTotals(32, 2, 4, 1));
        assertThat(parser.parseLeagues(json)).containsExactly(
                new League(537, "Teine liiga B"),
                new League(514, "Taliturniir - E-tasand"));
    }

    @Test
    void rejectsResponsesThatAreNotJson() {
        assertThatThrownBy(() -> parser.parseTotals("<html>maintenance</html>"))
                .isInstanceOf(UnexpectedResponseException.class);
    }

    @Test
    void parsesPlayedMatches() {
        List<PlayedMatch> matches = parser.parseMatches(Fixtures.read("match-list.html"));

        assertThat(matches).containsExactly(
                new PlayedMatch(1001, 90, WIN, false),
                new PlayedMatch(1002, 75, LOSS, false),
                new PlayedMatch(1003, 45, DRAW, false),
                // 1004: 0 minutes
                new PlayedMatch(1005, 90, WIN, true),
                // 1006: annulled
                new PlayedMatch(1007, 90, LOSS, false),
                new PlayedMatch(1008, 120, LOSS, false),
                new PlayedMatch(1009, 120, WIN, false));
                // no result yet: skipped
    }
}
