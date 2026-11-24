package ee.jalgpall.scraper.app.parser;

import ee.jalgpall.scraper.app.model.League;
import ee.jalgpall.scraper.app.model.PlayedMatch;
import ee.jalgpall.scraper.app.model.PlayedMatch.Outcome;
import ee.jalgpall.scraper.app.model.PlayerTotals;
import ee.jalgpall.scraper.app.model.RosterEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JalgpallParser {
    private static final Pattern PROTOCOL_LINK = Pattern.compile("/protocol/(\\d+)");

    private static final int DEFAULT_POSITION_INDEX = 1;
    private static final int MIN_CELLS = 8;
    private static final int TEAM_NAME_INDEX = 3;
    private static final int MINUTES_INDEX = 5;
    private static final int MATCHUP_INDEX = 6;
    private static final int SCORE_INDEX = 7;

    private static final String POSITION_HEADER = "Positsioon";
    private static final String PENALTIES = "PEN";
    private static final String EXTRA_TIME = "LA";

    private final JsonMapper jsonMapper;

    public JalgpallParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public List<RosterEntry> parseRoster(String teamSeasonJson) {
        if (teamSeasonJson.isBlank()) {
            return List.of();
        }
        JsonNode body = readBody(teamSeasonJson, "team season");

        Map<Long, RosterEntry> players = new LinkedHashMap<>();
        parseRosterTable(body.path("players").asString("")).forEach(p -> players.putIfAbsent(p.playerId(), p));
        for (Element element : Jsoup.parse(body.path("statistics").asString("")).select(PlayerLink.SELECTOR)) {
            PlayerLink.of(element).ifPresent(link ->
                    players.putIfAbsent(link.playerId(), new RosterEntry(link.playerId(), link.name(), null)));
        }
        return List.copyOf(players.values());
    }

    private List<RosterEntry> parseRosterTable(String html) {
        Document doc = Jsoup.parse(html);
        int positionIndex = columnIndex(doc);

        List<RosterEntry> roster = new ArrayList<>();
        for (Element row : doc.select("tbody tr")) {
            Optional<PlayerLink> link = PlayerLink.of(row.selectFirst(PlayerLink.SELECTOR));
            if (link.isEmpty()) {
                continue;
            }
            Elements cells = row.select("td");
            String position = positionIndex < cells.size() ? blankToNull(cells.get(positionIndex).text()) : null;
            roster.add(new RosterEntry(link.get().playerId(), link.get().name(), position));
        }
        return roster;
    }

    private static int columnIndex(Document doc) {
        Elements headers = doc.select("thead th");
        for (int i = 0; i < headers.size(); i++) {
            if (POSITION_HEADER.equalsIgnoreCase(headers.get(i).text().trim())) {
                return i;
            }
        }
        return DEFAULT_POSITION_INDEX;
    }

    public PlayerTotals parseTotals(String playerTotalJson) {
        JsonNode body = readBody(playerTotalJson, "player totals");
        return new PlayerTotals(
                body.path("goals").asInt(0),
                body.path("assists").asInt(0),
                body.path("yellowCards").asInt(0),
                body.path("redCards").asInt(0));
    }

    public List<League> parseLeagues(String playerTotalJson) {
        JsonNode body = readBody(playerTotalJson, "player totals");
        Document options = Jsoup.parse("<select>" + body.path("leagues").asString("") + "</select>");

        List<League> leagues = new ArrayList<>();
        for (Element option : options.select("option[value]")) {
            Long id = parseLong(option.attr("value"));
            if (id != null) {
                leagues.add(new League(id, option.text().trim()));
            }
        }
        return leagues;
    }

    public List<PlayedMatch> parseMatches(String matchListHtml) {
        return Jsoup.parse(matchListHtml).select("tbody tr").stream()
                .map(this::parsePlayedMatch)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<PlayedMatch> parsePlayedMatch(Element row) {
        Elements cells = row.select("td");
        if (cells.size() < MIN_CELLS) {
            return Optional.empty();
        }

        Integer minutes = parseInt(cells.get(MINUTES_INDEX).text());
        boolean didNotPlay = minutes == null || minutes == 0;
        if (didNotPlay) {
            return Optional.empty();
        }

        Element scoreCell = cells.get(SCORE_INDEX);
        Element scoreLink = scoreCell.selectFirst("a");
        Long protocolId = scoreLink == null ? null : protocolId(scoreLink);
        Optional<Score> scoreline = scoreLink == null ? Optional.empty() : Score.parse(scoreLink.text());
        if (protocolId == null || scoreline.isEmpty()) {
            return Optional.empty();
        }

        Score finalScore = parseTieBreak(scoreCell).orElse(scoreline.get());
        Outcome outcome = finalScore.outcomeFor(isHomeTeam(cells));
        return Optional.of(new PlayedMatch(protocolId, minutes, outcome, scoreline.get().forfeit()));
    }

    private static boolean isHomeTeam(Elements cells) {
        String team = cells.get(TEAM_NAME_INDEX).text().trim();
        String homeTeam = cells.get(MATCHUP_INDEX).text().split(" - ", 2)[0].trim();
        return homeTeam.equals(team);
    }

    // Extra time and penalties decide the result: "<a>2 - 2</a> <p>(4 - 5)</p> <p class="small">PEN</p>"
    private static Optional<Score> parseTieBreak(Element scoreCell) {
        Element marker = scoreCell.selectFirst("p.small");
        Element score = scoreCell.selectFirst("p:not(.small)");
        if (marker == null || score == null) {
            return Optional.empty();
        }

        String type = marker.text().trim();
        if (!PENALTIES.equals(type) && !EXTRA_TIME.equals(type)) {
            return Optional.empty();
        }
        return Score.parse(score.text().replaceAll("[()]", ""));
    }

    private static @Nullable Long protocolId(Element link) {
        Matcher matcher = PROTOCOL_LINK.matcher(link.attr("href"));
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private JsonNode readBody(String json, String what) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(json);
        } catch (JacksonException e) {
            throw new UnexpectedResponseException("Could not read " + what + " response from jalgpall.ee", e);
        }
        JsonNode body = root.path("body");
        if (!body.isObject()) {
            throw new UnexpectedResponseException("The " + what + " response from jalgpall.ee has no 'body'");
        }
        return body;
    }

    private static @Nullable Integer parseInt(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static @Nullable Long parseLong(String text) {
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static @Nullable String blankToNull(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
