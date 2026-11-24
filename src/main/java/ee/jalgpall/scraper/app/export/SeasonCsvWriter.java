package ee.jalgpall.scraper.app.export;

import ee.jalgpall.scraper.app.model.PlayerSeasonStats;
import ee.jalgpall.scraper.app.model.PlayerSeasonStats.Goalkeeping;
import ee.jalgpall.scraper.app.model.TeamSeasonStats;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

// Same columns, order and headers as a season tab of the stats sheet, so the output can be imported or pasted as is.
@Component
public class SeasonCsvWriter {
    private static final String NOT_APPLICABLE = "Pole";
    private static final String LINE_END = "\r\n";

    private static final List<Column> COLUMNS = List.of(
            new Column("Nimi", s -> s.name() != null ? s.name() : "#" + s.playerId()),
            new Column("Mänge", s -> number(s.games())),
            new Column("Minuteid", s -> number(s.minutes())),
            new Column("Väravaid", s -> number(s.goals())),
            new Column("Kollaseid", s -> number(s.yellowCards())),
            new Column("Punaseid", s -> number(s.redCards())),
            new Column("Min per goal", s -> orPole(s.minutesPerGoal())),
            new Column("Min per yellow", s -> orPole(s.minutesPerYellowCard())),
            new Column("Avg min per game", s -> orPole(s.averageMinutesPerGame())),
            new Column("Clean sheets", s -> goalkeeping(s, gk -> number(gk.cleanSheets()))),
            new Column("Sisse lastud väravaid", s -> goalkeeping(s, gk -> number(gk.goalsConceded()))),
            new Column("Avg väravaid per game (gk)", s -> goalkeeping(s, gk -> orPole(gk.goalsConcededPerGame()))),
            new Column("Võite", s -> number(s.wins())),
            new Column("Viike", s -> number(s.draws())),
            new Column("Kaotusi", s -> number(s.losses())),
            new Column("Win %", s -> percent(s.wins(), s.games())),
            new Column("Draw %", s -> percent(s.draws(), s.games())),
            new Column("Lose %", s -> percent(s.losses(), s.games())),
            new Column("Assiste", s -> number(s.assists())));

    public String write(TeamSeasonStats season) {
        StringBuilder csv = new StringBuilder();
        csv.append(row(COLUMNS.stream().map(Column::header).toList()));
        for (PlayerSeasonStats player : season.players()) {
            csv.append(row(COLUMNS.stream().map(column -> column.value().apply(player)).toList()));
        }
        return csv.toString();
    }

    public String fileName(TeamSeasonStats season) {
        return "team-%d-%d.csv".formatted(season.teamId(), season.year());
    }

    private static String row(List<String> values) {
        return values.stream().map(SeasonCsvWriter::escape).collect(Collectors.joining(",")) + LINE_END;
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    // Players who were never in goal get 0, like in the sheet
    private static String goalkeeping(PlayerSeasonStats stats, Function<Goalkeeping, String> value) {
        return stats.goalkeeping() == null ? "0" : value.apply(stats.goalkeeping());
    }

    private static String number(int value) {
        return Integer.toString(value);
    }

    private static String orPole(@Nullable Double value) {
        return value == null ? NOT_APPLICABLE : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String percent(int part, int total) {
        if (total == 0) {
            return NOT_APPLICABLE;
        }
        BigDecimal percent = BigDecimal.valueOf(part * 100L)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return percent.toPlainString() + "%";
    }

    private record Column(String header, Function<PlayerSeasonStats, String> value) {}
}
