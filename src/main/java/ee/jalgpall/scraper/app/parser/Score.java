package ee.jalgpall.scraper.app.parser;

import ee.jalgpall.scraper.app.model.PlayedMatch.Outcome;

import java.util.Optional;

// forfeit: the result was awarded ("+ : -" or "- : +"), so whether the game was actually played
// has to be checked from the match protocol
record Score(int home, int away, boolean forfeit) {
    private static final String FORFEIT = "-";
    private static final String AWARDED = "+";

    static Score of(int home, int away) {
        return new Score(home, away, false);
    }

    static Optional<Score> parse(String text) {
        String[] parts = text.trim().split(" [-:] ");
        if (parts.length != 2) {
            return Optional.empty();
        }

        String home = parts[0].trim();
        String away = parts[1].trim();
        boolean homeForfeit = FORFEIT.equals(home);
        boolean awayForfeit = FORFEIT.equals(away);

        // If there was a forfeit before the game and lineups confirmed, the score is +:-, but the squad gets all 90 minutes
        // Some games before 2020 got +:- as well fyi, even though the game was played
        // -:- means the result was annulled (e.g., the other team withdrew from the league), so it never counts
        if (homeForfeit && awayForfeit) {
            return Optional.empty();
        }
        if (AWARDED.equals(home) && awayForfeit) {
            return Optional.of(new Score(1, 0, true));
        }
        if (AWARDED.equals(away) && homeForfeit) {
            return Optional.of(new Score(0, 1, true));
        }
        if (homeForfeit) {
            return Optional.of(Score.of(0, 1));
        }
        if (awayForfeit) {
            return Optional.of(Score.of(1, 0));
        }

        try {
            return Optional.of(Score.of(Integer.parseInt(home), Integer.parseInt(away)));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    Outcome outcomeFor(boolean homeTeam) {
        int scored = homeTeam ? home : away;
        int conceded = homeTeam ? away : home;
        if (scored > conceded) {
            return Outcome.WIN;
        }
        return scored == conceded ? Outcome.DRAW : Outcome.LOSS;
    }
}
