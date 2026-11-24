package ee.jalgpall.scraper.app.parser;

import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record Minute(int regular, int stoppage) implements Comparable<Minute> {
    static final Minute KICK_OFF = new Minute(0, 0);

    private static final Pattern MINUTE_PATTERN = Pattern.compile("(\\d+)\\s*(?:\\+\\s*(\\d+))?");
    private static final Comparator<Minute> ORDER = Comparator.comparingInt(Minute::regular)
            .thenComparingInt(Minute::stoppage);

    static Optional<Minute> parse(String text) {
        Matcher matcher = MINUTE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int stoppage = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return Optional.of(new Minute(Integer.parseInt(matcher.group(1)), stoppage));
    }

    boolean isBefore(Minute other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(@NonNull Minute other) {
        return ORDER.compare(this, other);
    }
}
