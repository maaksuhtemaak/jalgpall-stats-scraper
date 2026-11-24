package ee.jalgpall.scraper.app.export;

import ee.jalgpall.scraper.app.model.PlayerSeasonStats;
import ee.jalgpall.scraper.app.model.PlayerSeasonStats.Goalkeeping;
import ee.jalgpall.scraper.app.model.TeamSeasonStats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonCsvWriterTest {
    private final SeasonCsvWriter writer = new SeasonCsvWriter();

    @Test
    void writesTheSeasonTabLayout() {
        TeamSeasonStats season = new TeamSeasonStats(4240, 2025, List.of(
                new PlayerSeasonStats(17700, "Oliver Karlson", "PK", 28, 2346, 32, 0, 4, 0, 19, 2, 7, null),
                new PlayerSeasonStats(24734, "Madik, Matheas", "VV", 20, 1800, 0, 0, 0, 0, 13, 2, 5,
                        new Goalkeeping(20, 8, 28))));

        String csv = writer.write(season);

        assertThat(csv).isEqualToNormalizingNewlines("""
                Nimi,Mänge,Minuteid,Väravaid,Kollaseid,Punaseid,Min per goal,Min per yellow,Avg min per game,\
                Clean sheets,Sisse lastud väravaid,Avg väravaid per game (gk),Võite,Viike,Kaotusi,\
                Win %,Draw %,Lose %,Assiste
                Oliver Karlson,28,2346,32,4,0,73.313,586.5,83.786,0,0,0,19,2,7,67.86%,7.14%,25%,0
                "Madik, Matheas",20,1800,0,0,0,Pole,Pole,90,8,28,1.4,13,2,5,65%,10%,25%,0
                """);
        assertThat(csv).contains("\r\n").doesNotContainPattern("[^\r]\n");
        assertThat(writer.fileName(season)).isEqualTo("team-4240-2025.csv");
    }
}
