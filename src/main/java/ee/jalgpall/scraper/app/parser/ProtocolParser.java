package ee.jalgpall.scraper.app.parser;

import ee.jalgpall.scraper.app.model.Appearance;
import ee.jalgpall.scraper.app.model.MatchProtocol;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ProtocolParser {
    private static final String STARTING_LINEUP = "Põhikoosseis";
    private static final String SUBSTITUTES = "Vahetusmängijad";
    private static final String GOALKEEPER_MARKER = "(VV)";

    public MatchProtocol parse(long protocolId, String protocolHtml, long teamId) {
        Document doc = Jsoup.parse(protocolHtml);
        Element startingLineup = lineupBlock(doc, STARTING_LINEUP);
        if (startingLineup == null) {
            return new MatchProtocol(protocolId, false, List.of());
        }

        boolean homeTeam = startingLineup.select("ul.left " + PlayerLink.SELECTOR).stream()
                .flatMap(link -> PlayerLink.of(link).stream())
                .anyMatch(link -> link.belongsTo(teamId));
        List<Goal> goals = parseGoals(doc);
        List<Minute> goalsAgainst = goals.stream()
                .filter(goal -> goal.byHomeTeam() != homeTeam)
                .map(Goal::minute)
                .toList();

        List<Appearance> appearances = new ArrayList<>(appearances(startingLineup, teamId, false, goalsAgainst));
        Element substitutes = lineupBlock(doc, SUBSTITUTES);
        if (substitutes != null) {
            appearances.addAll(appearances(substitutes, teamId, true, goalsAgainst));
        }

        // A walkover has lineups but no events; a game that was played (even if later forfeited) has some
        boolean substitutionsMade = substitutes != null && !substitutes.select("span.in").isEmpty();
        boolean played = substitutionsMade || !goals.isEmpty();
        return new MatchProtocol(protocolId, played, appearances);
    }

    private static List<Appearance> appearances(Element lineup, long teamId, boolean substitutes,
                                                List<Minute> goalsAgainst) {
        List<Appearance> appearances = new ArrayList<>();
        for (Element entry : lineup.select("li")) {
            Optional<PlayerLink> player = PlayerLink.of(entry.selectFirst(PlayerLink.SELECTOR))
                    .filter(link -> link.belongsTo(teamId));
            Minute cameOn = minute(entry.selectFirst("span.in"));
            boolean stayedOnBench = substitutes && cameOn == null;
            if (player.isEmpty() || stayedOnBench) {
                continue;
            }

            Minute wentOff = minute(entry.selectFirst("span.out"));
            Minute from = cameOn == null ? Minute.KICK_OFF : cameOn;
            int conceded = (int) goalsAgainst.stream()
                    .filter(goal -> !goal.isBefore(from) && (wentOff == null || goal.isBefore(wentOff)))
                    .count();
            appearances.add(new Appearance(player.get().playerId(), isMarkedAsGoalkeeper(entry), conceded));
        }
        return appearances;
    }

    private static boolean isMarkedAsGoalkeeper(Element lineupEntry) {
        return lineupEntry.select("small").stream()
                .anyMatch(marker -> marker.text().contains(GOALKEEPER_MARKER));
    }

    // The goal list shows the running score ("0 - 1", "0 - 2", ...); whichever side went up scored
    private static List<Goal> parseGoals(Document doc) {
        List<Goal> goals = new ArrayList<>();
        Score previous = Score.of(0, 0);
        for (Element goal : doc.select("div.goal-single")) {
            Optional<Score> runningScore = Score.parse(goal.select("div.score").text());
            if (runningScore.isEmpty()) {
                continue;
            }
            Minute minute = Minute.parse(goal.select("span.time").text()).orElse(Minute.KICK_OFF);
            goals.add(new Goal(minute, runningScore.get().home() > previous.home()));
            previous = runningScore.get();
        }
        return goals;
    }

    private static @Nullable Element lineupBlock(Document doc, String title) {
        for (Element block : doc.select("div.block")) {
            Element head = block.selectFirst("div.head");
            if (head != null && title.equalsIgnoreCase(head.text().trim())) {
                return block;
            }
        }
        return null;
    }

    private static @Nullable Minute minute(@Nullable Element element) {
        return element == null ? null : Minute.parse(element.text()).orElse(null);
    }

    private record Goal(Minute minute, boolean byHomeTeam) {}
}
