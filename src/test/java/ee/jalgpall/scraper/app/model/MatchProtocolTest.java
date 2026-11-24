package ee.jalgpall.scraper.app.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchProtocolTest {

    @Test
    void goalkeeperMarkerDecidesWhoWasInGoal() {
        MatchProtocol protocol = new MatchProtocol(1, true, List.of(
                new Appearance(100, false, 2),
                new Appearance(101, true, 2)));

        assertThat(protocol.inGoal(101, false)).isPresent();
        // registered as goalkeeper, but played on the field in this match
        assertThat(protocol.inGoal(100, true)).isEmpty();
    }

    @Test
    void withoutMarkersTheRegisteredPositionDecides() {
        MatchProtocol protocol = new MatchProtocol(1, true, List.of(
                new Appearance(100, false, 0),
                new Appearance(101, false, 0)));

        assertThat(protocol.inGoal(100, true)).hasValueSatisfying(appearance -> assertThat(appearance.cleanSheet()).isTrue());
        assertThat(protocol.inGoal(101, false)).isEmpty();
    }
}
