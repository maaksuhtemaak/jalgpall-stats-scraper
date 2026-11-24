package ee.jalgpall.scraper.app.web;

import ee.jalgpall.scraper.app.client.JalgpallUnavailableException;
import ee.jalgpall.scraper.app.export.SeasonCsvWriter;
import ee.jalgpall.scraper.app.model.PlayerSeasonStats;
import ee.jalgpall.scraper.app.model.TeamSeasonStats;
import ee.jalgpall.scraper.app.service.NotFoundException;
import ee.jalgpall.scraper.app.service.SeasonStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeasonController.class)
@Import(SeasonCsvWriter.class)
class SeasonControllerTest {
    private static final PlayerSeasonStats OLIVER =
            new PlayerSeasonStats(17700, "Oliver Karlson", "PK", 28, 2346, 32, 0, 4, 0, 19, 2, 7, null);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SeasonStatsService service;

    @Test
    void teamSeasonAsJson() throws Exception {
        when(service.teamSeason(4240, 2025, List.of())).thenReturn(new TeamSeasonStats(4240, 2025, List.of(OLIVER)));

        mvc.perform(get("/api/teams/4240/seasons/2025/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].name").value("Oliver Karlson"))
                .andExpect(jsonPath("$.players[0].minutesPerGoal").value(73.313))
                .andExpect(jsonPath("$.players[0].winRate").value(0.679))
                .andExpect(jsonPath("$.players[0].goalkeeping").doesNotExist());
    }

    @Test
    void passesLeagueFilter() throws Exception {
        when(service.teamSeason(4240, 2025, List.of(537L, 62L))).thenReturn(new TeamSeasonStats(4240, 2025, List.of()));

        mvc.perform(get("/api/teams/4240/seasons/2025/players").param("leagues", "537,62"))
                .andExpect(status().isOk());
        verify(service).teamSeason(4240, 2025, List.of(537L, 62L));
    }

    @Test
    void teamSeasonAsCsvDownload() throws Exception {
        when(service.teamSeason(4240, 2025, List.of())).thenReturn(new TeamSeasonStats(4240, 2025, List.of(OLIVER)));

        mvc.perform(get("/api/teams/4240/seasons/2025/players.csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"team-4240-2025.csv\""))
                .andExpect(content().string(startsWith("Nimi,Mänge,Minuteid")));
    }

    @Test
    void singlePlayer() throws Exception {
        when(service.playerSeason(17700, 4240, 2025, List.of())).thenReturn(OLIVER);

        mvc.perform(get("/api/teams/4240/seasons/2025/players/17700"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games").value(28));
    }

    @Test
    void rejectsImpossibleYear() throws Exception {
        mvc.perform(get("/api/teams/4240/seasons/1800/players"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonNumericLeague() throws Exception {
        mvc.perform(get("/api/teams/4240/seasons/2025/players").param("leagues", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFoundAsProblemDetail() throws Exception {
        when(service.teamSeason(1, 2025, List.of())).thenThrow(new NotFoundException("No players found for team 1 in 2025"));

        mvc.perform(get("/api/teams/1/seasons/2025/players"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No players found for team 1 in 2025"));
    }

    @Test
    void upstreamFailureIsBadGateway() throws Exception {
        when(service.teamSeason(4240, 2025, List.of()))
                .thenThrow(new JalgpallUnavailableException("timeout", new IOException("timeout")));

        mvc.perform(get("/api/teams/4240/seasons/2025/players"))
                .andExpect(status().isBadGateway());
    }
}
