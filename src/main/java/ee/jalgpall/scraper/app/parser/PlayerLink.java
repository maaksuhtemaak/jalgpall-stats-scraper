package ee.jalgpall.scraper.app.parser;

import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record PlayerLink(long playerId, @Nullable Long teamId, String name) {
    static final String SELECTOR = "a[href*=/player/]";

    private static final Pattern HREF = Pattern.compile("/player/(\\d+)(?:/team/(\\d+))?");

    static Optional<PlayerLink> of(@Nullable Element link) {
        if (link == null) {
            return Optional.empty();
        }
        Matcher matcher = HREF.matcher(link.attr("href"));
        if (!matcher.find()) {
            return Optional.empty();
        }
        Long teamId = matcher.group(2) == null ? null : Long.valueOf(matcher.group(2));
        return Optional.of(new PlayerLink(Long.parseLong(matcher.group(1)), teamId, link.text().trim()));
    }

    boolean belongsTo(long team) {
        return teamId != null && teamId == team;
    }
}
