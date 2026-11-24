package ee.jalgpall.scraper.app.parser;

import ee.jalgpall.scraper.app.Fixtures;
import ee.jalgpall.scraper.app.model.Appearance;
import ee.jalgpall.scraper.app.model.MatchProtocol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolParserTest {
    private static final long ZAPOOS = 4240;
    private static final long RIVALS = 77;

    private final ProtocolParser parser = new ProtocolParser();

    @Test
    void splitsGoalsConcededBetweenGoalkeepers() {
        MatchProtocol protocol = parser.parse(1, Fixtures.read("protocol.html"), ZAPOOS);

        assertThat(protocol.played()).isTrue();
        assertThat(protocol.appearances()).containsExactly(
                new Appearance(100, true, 2),   // 10′ and 45+2′, off at half time
                new Appearance(101, false, 3),  // field player, whole match
                new Appearance(102, true, 1));  // 50′, on at half time
        // 103 stayed on the bench, 900 and 901 play for the other team
    }

    @Test
    void readsTheHomeTeamSide() {
        MatchProtocol protocol = parser.parse(1, Fixtures.read("protocol.html"), RIVALS);

        assertThat(protocol.appearances()).containsExactly(
                new Appearance(900, true, 1),
                new Appearance(901, false, 1));
    }

    @Test
    void walkoverWasNotPlayed() {
        MatchProtocol protocol = parser.parse(2, Fixtures.read("protocol-walkover.html"), ZAPOOS);

        assertThat(protocol.played()).isFalse();
        assertThat(protocol.appearances()).extracting(Appearance::playerId).containsExactly(100L, 105L);
    }

    @Test
    void protocolWithoutLineups() {
        MatchProtocol protocol = parser.parse(3, "<html><body><p>Protokoll puudub</p></body></html>", ZAPOOS);

        assertThat(protocol.played()).isFalse();
        assertThat(protocol.appearances()).isEmpty();
    }
}
