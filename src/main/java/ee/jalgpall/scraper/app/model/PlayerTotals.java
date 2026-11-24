package ee.jalgpall.scraper.app.model;

public record PlayerTotals(int goals, int assists, int yellowCards, int redCards) {
    public static final PlayerTotals EMPTY = new PlayerTotals(0, 0, 0, 0);

    public PlayerTotals plus(PlayerTotals other) {
        return new PlayerTotals(
                goals + other.goals,
                assists + other.assists,
                yellowCards + other.yellowCards,
                redCards + other.redCards);
    }

    public PlayerTotals minus(PlayerTotals other) {
        return new PlayerTotals(
                goals - other.goals,
                assists - other.assists,
                yellowCards - other.yellowCards,
                redCards - other.redCards);
    }
}
