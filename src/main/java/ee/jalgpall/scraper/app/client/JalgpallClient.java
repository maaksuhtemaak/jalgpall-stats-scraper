package ee.jalgpall.scraper.app.client;

import ee.jalgpall.scraper.app.config.JalgpallProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

@Component
public class JalgpallClient {
    private static final String AJAX_PATH = "ajax.php";
    private static final String AJAX_HEADER = "X-Requested-With";
    private static final int MATCH_LIST_LIMIT = 100;

    private final RestClient restClient;
    private final URI baseUrl;
    private final Semaphore permits;

    public JalgpallClient(RestClient jalgpallRestClient, JalgpallProperties properties) {
        this.restClient = jalgpallRestClient;
        this.baseUrl = properties.baseUrl();
        this.permits = new Semaphore(Math.max(1, properties.maxConcurrentRequests()), true);
    }

    public String teamSeason(long teamId, int year) {
        return ajax(uri -> uri.path(AJAX_PATH)
                        .queryParam("type", "teamplayertable")
                        .queryParam("team_id", teamId)
                        .queryParam("season", year)
                        .build(),
                "team/" + teamId);
    }

    public String protocol(long protocolId) {
        return fetch(uri -> uri.path("protocol/{protocolId}").build(protocolId), null, false);
    }

    public String playerTotals(long playerId, long teamId, int year, @Nullable Long leagueId) {
        return ajax(uri -> playerUri(uri, "playertotal", playerId, teamId, year, leagueId),
                playerPage(playerId, teamId));
    }

    public String playerMatches(long playerId, long teamId, int year, @Nullable Long leagueId) {
        return ajax(uri -> playerUri(uri, "playerstat1", playerId, teamId, year, leagueId),
                playerPage(playerId, teamId));
    }

    private URI playerUri(UriBuilder uri, String type, long playerId, long teamId, int year, @Nullable Long leagueId) {
        uri.path(AJAX_PATH)
                .queryParam("type", type)
                .queryParam("player_id", playerId)
                .queryParam("season", year)
                .queryParam("team_id", teamId)
                .queryParam("limit", MATCH_LIST_LIMIT);
        if (leagueId != null) {
            uri.queryParam("league_id", leagueId);
        }
        return uri.build();
    }

    private static String playerPage(long playerId, long teamId) {
        return "player/" + playerId + "/team/" + teamId;
    }

    private String ajax(Function<UriBuilder, URI> uri, String refererPath) {
        return fetch(uri, refererPath, true);
    }

    private String fetch(Function<UriBuilder, URI> uri, @Nullable String refererPath, boolean ajax) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JalgpallUnavailableException("Interrupted while waiting to call jalgpall.ee", e);
        }
        try {
            String body = restClient.get()
                    .uri(uri)
                    .headers(headers -> {
                        if (refererPath != null) {
                            headers.set(HttpHeaders.REFERER, baseUrl.resolve(refererPath).toString());
                        }
                        if (ajax) {
                            headers.set(AJAX_HEADER, "XMLHttpRequest");
                        }
                    })
                    .retrieve()
                    .body(String.class);
            return body == null ? "" : body;
        } catch (RestClientException e) {
            throw new JalgpallUnavailableException("Request to jalgpall.ee failed: " + e.getMessage(), e);
        } finally {
            permits.release();
        }
    }
}
