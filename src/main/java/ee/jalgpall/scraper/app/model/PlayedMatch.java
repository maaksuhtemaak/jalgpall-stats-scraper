package ee.jalgpall.scraper.app.model;

// forfeit: the result was set by a forfeit; such a match only counts when its protocol shows it was played
public record PlayedMatch(long protocolId, int minutes, Outcome outcome, boolean forfeit) {

    public enum Outcome { WIN, DRAW, LOSS }
}
