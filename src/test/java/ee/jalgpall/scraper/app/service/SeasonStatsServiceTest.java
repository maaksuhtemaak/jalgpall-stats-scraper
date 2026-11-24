package ee.jalgpall.scraper.app.service;

import ee.jalgpall.scraper.app.Fixtures;
import ee.jalgpall.scraper.app.client.JalgpallClient;
import ee.jalgpall.scraper.app.config.JalgpallProperties;
import ee.jalgpall.scraper.app.model.PlayerSeasonStats;
import ee.jalgpall.scraper.app.model.PlayerSeasonStats.Goalkeeping;
import ee.jalgpall.scraper.app.model.TeamSeasonStats;
import ee.jalgpall.scraper.app.parser.JalgpallParser;
import ee.jalgpall.scraper.app.parser.ProtocolParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeasonStatsServiceTest {
    private static final long TEAM = 4240;
    private static final int YEAR = 2026;
    private static final long TALITURNIIR = 514;

    private final JalgpallClient client = mock(JalgpallClient.class);
    private final SeasonStatsService service = new SeasonStatsService(
            client,
            new JalgpallParser(JsonMapper.builder().build()),
            new ProtocolParser(),
            new JalgpallProperties(URI.create("https://jalgpall.ee/voistlused/"), "test", 4, Duration.ofSeconds(1),
                    List.of("Taliturniir")));

    @BeforeEach
    void stubJalgpall() {
        when(client.teamSeason(TEAM, YEAR)).thenReturn(Fixtures.read("team-season.json"));
        when(client.protocol(1)).thenReturn(Fixtures.read("protocol.html"));
        when(client.protocol(2)).thenReturn(Fixtures.read("protocol-walkover.html"));
        when(client.protocol(3)).thenReturn(Fixtures.read("protocol.html"));

        // Goalkeepers who split the match at half time
        stubPlayer(100, totals(0, 0), matchList(row(1, 46, "Rivals FC - Tallinna FC Zapoos", "3 - 1")));
        stubPlayer(102, totals(0, 0), matchList(row(1, 44, "Rivals FC - Tallinna FC Zapoos", "3 - 1")));
        // Field player who also played in the winter tournament, which is left out
        stubPlayer(101, totals(3, 1, "537", "Teine liiga B", "514", "Taliturniir - E-tasand"), matchList(
                row(1, 90, "Rivals FC - Tallinna FC Zapoos", "3 - 1"),
                row(9, 90, "Tallinna FC Zapoos - Winter FC", "5 - 0"),
                row(3, 90, "Rivals FC - Tallinna FC Zapoos", "- : +")));
        when(client.playerTotals(101, TEAM, YEAR, TALITURNIIR)).thenReturn(totals(1, 0));
        when(client.playerMatches(101, TEAM, YEAR, TALITURNIIR))
                .thenReturn(matchList(row(9, 90, "Tallinna FC Zapoos - Winter FC", "5 - 0")));
        // On the roster, never played
        stubPlayer(104, totals(0, 0), matchList());
        // Only "played" in a walkover
        stubPlayer(105, totals(0, 0), matchList(row(2, 90, "Tallinna FC Zapoos - Rivals FC", "+ : -")));
    }

    @Test
    void collectsEveryPlayerWhoPlayed() {
        TeamSeasonStats season = service.teamSeason(TEAM, YEAR, List.of());

        assertThat(season.players()).extracting(PlayerSeasonStats::name)
                .containsExactly("Mati Maasikas", "Kalle Kask", "Jaan Tamm");

        PlayerSeasonStats fieldPlayer = season.players().getFirst();
        assertThat(fieldPlayer.games()).isEqualTo(2);        // winter tournament game left out
        assertThat(fieldPlayer.minutes()).isEqualTo(180);
        assertThat(fieldPlayer.goals()).isEqualTo(2);        // 3 - 1 scored in the winter tournament
        assertThat(fieldPlayer.yellowCards()).isEqualTo(1);
        assertThat(fieldPlayer.wins()).isEqualTo(1);         // the forfeit win of a game that was played
        assertThat(fieldPlayer.losses()).isEqualTo(1);
        assertThat(fieldPlayer.goalkeeping()).isNull();      // never in goal

        assertThat(season.players().get(1).goalkeeping()).isEqualTo(new Goalkeeping(1, 0, 2));
        assertThat(season.players().get(2).goalkeeping()).isEqualTo(new Goalkeeping(1, 0, 1));
    }

    @Test
    void fetchesEachProtocolOnce() {
        service.teamSeason(TEAM, YEAR, List.of());

        verify(client, times(1)).protocol(1);
        verify(client, never()).protocol(9);
    }

    @Test
    void selectedLeaguesAreAddedUp() {
        when(client.playerTotals(101, TEAM, YEAR, 537L)).thenReturn(totals(2, 1));
        when(client.playerMatches(101, TEAM, YEAR, 537L))
                .thenReturn(matchList(row(1, 90, "Rivals FC - Tallinna FC Zapoos", "3 - 1")));
        when(client.protocol(9)).thenReturn(Fixtures.read("protocol-walkover.html"));

        PlayerSeasonStats stats = service.playerSeason(101, TEAM, YEAR, List.of(537L, TALITURNIIR));

        assertThat(stats.games()).isEqualTo(2);
        assertThat(stats.goals()).isEqualTo(3);
        assertThat(stats.wins()).isEqualTo(1);
        assertThat(stats.losses()).isEqualTo(1);
    }

    @Test
    void unknownPlayerWithoutGamesIsNotFound() {
        stubPlayer(999, totals(0, 0), matchList());

        assertThatThrownBy(() -> service.playerSeason(999, TEAM, YEAR, List.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownTeamIsNotFound() {
        when(client.teamSeason(anyLong(), anyInt())).thenReturn("");

        assertThatThrownBy(() -> service.teamSeason(1, YEAR, List.of()))
                .isInstanceOf(NotFoundException.class);
    }

    private void stubPlayer(long playerId, String totals, String matchList) {
        when(client.playerTotals(playerId, TEAM, YEAR, null)).thenReturn(totals);
        when(client.playerMatches(playerId, TEAM, YEAR, null)).thenReturn(matchList);
    }

    private static String totals(int goals, int yellowCards, String... leagues) {
        StringBuilder options = new StringBuilder("<option value=\\\"null\\\">Kõik</option>");
        for (int i = 0; i < leagues.length; i += 2) {
            options.append("<option value=\\\"").append(leagues[i]).append("\\\">").append(leagues[i + 1]).append("</option>");
        }
        return """
                {"body":{"goals":%d,"assists":0,"yellowCards":%d,"redCards":0,"leagues":"%s"},"status":"success"}
                """.formatted(goals, yellowCards, options);
    }

    private static String matchList(String... rows) {
        return "<table><thead><tr><th>Kuupäev</th></tr></thead><tbody>" + String.join("", rows) + "</tbody></table>";
    }

    private static String row(long protocolId, int minutes, String matchup, String score) {
        return """
                <tr><td>01.05.2026</td><td>Eesti meeste MV</td><td>II.B.N/W</td><td>Tallinna FC Zapoos</td><td>Põhi</td>
                <td>%d</td><td>%s</td><td><a href="/voistlused/protocol/%d">%s</a></td></tr>
                """.formatted(minutes, matchup, protocolId, score);
    }
}
