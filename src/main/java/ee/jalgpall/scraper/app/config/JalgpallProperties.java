package ee.jalgpall.scraper.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("jalgpall")
public record JalgpallProperties(
        @DefaultValue("https://jalgpall.ee/voistlused/") URI baseUrl,
        @DefaultValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36") String userAgent,
        @DefaultValue("4") int maxConcurrentRequests,
        @DefaultValue("15s") Duration timeout,
        // Leagues left out of season totals unless asked for explicitly, matched by name
        @DefaultValue("Taliturniir") List<String> excludedLeagues) {
}
