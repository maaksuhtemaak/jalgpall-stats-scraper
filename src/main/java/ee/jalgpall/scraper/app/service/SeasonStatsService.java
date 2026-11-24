package ee.jalgpall.scraper.app.service;

import ee.jalgpall.scraper.app.client.JalgpallClient;
import ee.jalgpall.scraper.app.config.JalgpallProperties;
import ee.jalgpall.scraper.app.model.League;
import ee.jalgpall.scraper.app.model.MatchProtocol;
import ee.jalgpall.scraper.app.model.MatchSummary;
import ee.jalgpall.scraper.app.model.PlayedMatch;
import ee.jalgpall.scraper.app.model.PlayerSeasonStats;
import ee.jalgpall.scraper.app.model.PlayerTotals;
import ee.jalgpall.scraper.app.model.RosterEntry;
import ee.jalgpall.scraper.app.model.TeamSeasonStats;
import ee.jalgpall.scraper.app.parser.JalgpallParser;
import ee.jalgpall.scraper.app.parser.ProtocolParser;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import static java.util.Comparator.comparingInt;
import static java.util.Comparator.nullsLast;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Service
public class SeasonStatsService {
    private static final Comparator<PlayerSeasonStats> MOST_PLAYED_FIRST =
            comparingInt(PlayerSeasonStats::games).reversed()
                    .thenComparing(comparingInt(PlayerSeasonStats::minutes).reversed())
                    .thenComparing(PlayerSeasonStats::name, nullsLast(String::compareTo));

    private final JalgpallClient client;
    private final JalgpallParser parser;
    private final ProtocolParser protocolParser;
    private final List<String> excludedLeagues;

    public SeasonStatsService(JalgpallClient client, JalgpallParser parser, ProtocolParser protocolParser,
                              JalgpallProperties properties) {
        this.client = client;
        this.parser = parser;
        this.protocolParser = protocolParser;
        this.excludedLeagues = properties.excludedLeagues().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }

    public TeamSeasonStats teamSeason(long teamId, int year, List<Long> leagueIds) {
        List<PlayerSeason> seasons = inParallel(roster(teamId, year),
                player -> playerSeason(player, teamId, year, leagueIds));
        Map<Long, MatchProtocol> protocols = protocols(seasons, teamId);

        List<PlayerSeasonStats> players = seasons.stream()
                .map(season -> season.toStats(protocols))
                .filter(stats -> stats.games() > 0)
                .sorted(MOST_PLAYED_FIRST)
                .toList();
        return new TeamSeasonStats(teamId, year, players);
    }

    public PlayerSeasonStats playerSeason(long playerId, long teamId, int year, List<Long> leagueIds) {
        RosterEntry player = roster(teamId, year).stream()
                .filter(entry -> entry.playerId() == playerId)
                .findFirst()
                .orElseGet(() -> RosterEntry.unknown(playerId));

        PlayerSeason season = playerSeason(player, teamId, year, leagueIds);
        PlayerSeasonStats stats = season.toStats(protocols(List.of(season), teamId));
        if (player.name() == null && stats.games() == 0) {
            throw new NotFoundException("Player %d did not play for team %d in %d".formatted(playerId, teamId, year));
        }
        return stats;
    }

    private List<RosterEntry> roster(long teamId, int year) {
        List<RosterEntry> roster = parser.parseRoster(client.teamSeason(teamId, year));
        if (roster.isEmpty()) {
            throw new NotFoundException("No players found for team %d in %d".formatted(teamId, year));
        }
        return roster;
    }

    private PlayerSeason playerSeason(RosterEntry player, long teamId, int year, List<Long> leagueIds) {
        return leagueIds.isEmpty()
                ? wholeSeason(player, teamId, year)
                : selectedLeagues(player, teamId, year, leagueIds);
    }

    // Every league of the season, minus the excluded ones (for example the winter tournament)
    private PlayerSeason wholeSeason(RosterEntry player, long teamId, int year) {
        long playerId = player.playerId();
        String totalsJson = client.playerTotals(playerId, teamId, year, null);
        PlayerTotals totals = parser.parseTotals(totalsJson);
        List<PlayedMatch> matches = parser.parseMatches(client.playerMatches(playerId, teamId, year, null));

        for (League league : parser.parseLeagues(totalsJson)) {
            if (!isExcluded(league)) {
                continue;
            }
            totals = totals.minus(parser.parseTotals(client.playerTotals(playerId, teamId, year, league.id())));
            Set<Long> leagueMatches = parser.parseMatches(client.playerMatches(playerId, teamId, year, league.id()))
                    .stream()
                    .map(PlayedMatch::protocolId)
                    .collect(toSet());
            matches = matches.stream()
                    .filter(match -> !leagueMatches.contains(match.protocolId()))
                    .toList();
        }
        return new PlayerSeason(player, totals, matches);
    }

    private PlayerSeason selectedLeagues(RosterEntry player, long teamId, int year, List<Long> leagueIds) {
        long playerId = player.playerId();
        PlayerTotals totals = PlayerTotals.EMPTY;
        List<PlayedMatch> matches = new ArrayList<>();
        for (Long leagueId : leagueIds) {
            totals = totals.plus(parser.parseTotals(client.playerTotals(playerId, teamId, year, leagueId)));
            matches.addAll(parser.parseMatches(client.playerMatches(playerId, teamId, year, leagueId)));
        }
        return new PlayerSeason(player, totals, matches);
    }

    private boolean isExcluded(League league) {
        String name = league.name().toLowerCase(Locale.ROOT);
        return excludedLeagues.stream().anyMatch(name::contains);
    }

    // Each match protocol is fetched once and shared by every player who played in that match
    private Map<Long, MatchProtocol> protocols(List<PlayerSeason> seasons, long teamId) {
        List<Long> protocolIds = seasons.stream()
                .flatMap(season -> season.matches().stream())
                .map(PlayedMatch::protocolId)
                .distinct()
                .toList();
        return inParallel(protocolIds, id -> protocolParser.parse(id, client.protocol(id), teamId)).stream()
                .collect(toMap(MatchProtocol::protocolId, identity()));
    }

    private static <T, R> List<R> inParallel(Collection<T> items, Function<T, R> task) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> futures = items.stream()
                    .map(item -> executor.submit(() -> task.apply(item)))
                    .toList();
            try {
                return futures.stream().map(SeasonStatsService::await).toList();
            } catch (RuntimeException e) {
                executor.shutdownNow();
                throw e;
            }
        }
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while collecting player stats", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    private record PlayerSeason(RosterEntry player, PlayerTotals totals, List<PlayedMatch> matches) {

        // A forfeit result only counts when the game was actually played; a walkover does not
        PlayerSeasonStats toStats(Map<Long, MatchProtocol> protocols) {
            List<PlayedMatch> played = matches.stream()
                    .filter(match -> !match.forfeit() || wasPlayed(protocols.get(match.protocolId())))
                    .toList();
            return PlayerSeasonStats.of(player, totals, MatchSummary.of(player, played, protocols));
        }

        private static boolean wasPlayed(@Nullable MatchProtocol protocol) {
            return protocol != null && protocol.played();
        }
    }
}
