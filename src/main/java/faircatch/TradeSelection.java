package faircatch;

public record TradeSelection(
        int firstRosterId,
        String firstPlayerId,
        int secondRosterId,
        String secondPlayerId
) {
}