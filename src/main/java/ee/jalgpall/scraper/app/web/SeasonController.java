package ee.jalgpall.scraper.app.web;

import ee.jalgpall.scraper.app.export.SeasonCsvWriter;
import ee.jalgpall.scraper.app.model.PlayerSeasonStats;
import ee.jalgpall.scraper.app.model.TeamSeasonStats;
import ee.jalgpall.scraper.app.service.SeasonStatsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/teams/{teamId}/seasons/{year}")
public class SeasonController {
    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final SeasonStatsService seasonStatsService;
    private final SeasonCsvWriter csvWriter;

    public SeasonController(SeasonStatsService seasonStatsService, SeasonCsvWriter csvWriter) {
        this.seasonStatsService = seasonStatsService;
        this.csvWriter = csvWriter;
    }

    @GetMapping("/players")
    public TeamSeasonStats teamSeason(
            @PathVariable @Positive long teamId,
            @PathVariable @Min(2000) @Max(2100) int year,
            @RequestParam(required = false) @Nullable List<@Positive Long> leagues) {
        return seasonStatsService.teamSeason(teamId, year, orEmpty(leagues));
    }

    @GetMapping("/players.csv")
    public ResponseEntity<String> teamSeasonCsv(
            @PathVariable @Positive long teamId,
            @PathVariable @Min(2000) @Max(2100) int year,
            @RequestParam(required = false) @Nullable List<@Positive Long> leagues) {
        TeamSeasonStats season = seasonStatsService.teamSeason(teamId, year, orEmpty(leagues));
        ContentDisposition attachment = ContentDisposition.attachment()
                .filename(csvWriter.fileName(season))
                .build();
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment.toString())
                .body(csvWriter.write(season));
    }

    @GetMapping("/players/{playerId}")
    public PlayerSeasonStats playerSeason(
            @PathVariable @Positive long teamId,
            @PathVariable @Min(2000) @Max(2100) int year,
            @PathVariable @Positive long playerId,
            @RequestParam(required = false) @Nullable List<@Positive Long> leagues) {
        return seasonStatsService.playerSeason(playerId, teamId, year, orEmpty(leagues));
    }

    private static List<Long> orEmpty(@Nullable List<Long> leagues) {
        return leagues == null ? List.of() : leagues;
    }
}
