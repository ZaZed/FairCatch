package faircatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TradeAnalyzer {
    private static final ObjectMapper mapper = new ObjectMapper();

    public record TeamImpact(
            int rosterId,
            LineupOptimizer.Result before,
            LineupOptimizer.Result after
    ) {
        public double change() {
            double difference = after.totalPoints() - before.totalPoints();

            return Math.abs(difference) < 1e-9 ? 0 : difference;
        }
    }

    public record Result(
            TeamImpact firstTeam,
            TeamImpact secondTeam
    ) {
    }

    public static Result analyze(
            String leagueJson,
            String rostersJson,
            JsonNode players,
            Map<String, Projection> projections,
            TradeSelection trade,
            int week
    ) {
        String changedRosters = simulate(rostersJson, trade);

        LineupOptimizer.Result firstBefore = LineupReader.forRoster(
                leagueJson,
                rostersJson,
                trade.firstRosterId(),
                players,
                projections,
                week
        );

        LineupOptimizer.Result secondBefore = LineupReader.forRoster(
                leagueJson,
                rostersJson,
                trade.secondRosterId(),
                players,
                projections,
                week
        );

        LineupOptimizer.Result firstAfter = LineupReader.forRoster(
                leagueJson,
                changedRosters,
                trade.firstRosterId(),
                players,
                projections,
                week
        );

        LineupOptimizer.Result secondAfter = LineupReader.forRoster(
                leagueJson,
                changedRosters,
                trade.secondRosterId(),
                players,
                projections,
                week
        );

        return new Result(
                new TeamImpact(
                        trade.firstRosterId(),
                        firstBefore,
                        firstAfter
                ),
                new TeamImpact(
                        trade.secondRosterId(),
                        secondBefore,
                        secondAfter
                )
        );
    }

    public static String simulate(
            String rostersJson,
            TradeSelection trade
    ) {
        validateSelection(trade);

        try {
            JsonNode original = mapper.readTree(rostersJson);

            if (original == null || !original.isArray()) {
                throw new IllegalArgumentException(
                        "Invalid roster information."
                );
            }

            // All changes happen in this copy.
            ArrayNode changed = ((ArrayNode) original).deepCopy();

            ObjectNode first = findRoster(
                    changed, trade.firstRosterId()
            );

            ObjectNode second = findRoster(
                    changed, trade.secondRosterId()
            );

            Set<String> firstPlayers = ownedPlayers(first);
            Set<String> secondPlayers = ownedPlayers(second);

            if (!firstPlayers.contains(trade.firstPlayerId())
                    || !secondPlayers.contains(trade.secondPlayerId())) {
                throw new IllegalArgumentException(
                        "A selected player does not belong to that team."
                );
            }

            if (firstPlayers.contains(trade.secondPlayerId())
                    || secondPlayers.contains(trade.firstPlayerId())) {
                throw new IllegalArgumentException(
                        "A receiving team already owns the incoming player."
                );
            }

            if (isStashed(first, trade.firstPlayerId())
                    || isStashed(second, trade.secondPlayerId())) {
                throw new IllegalArgumentException(
                        "This version supports trades between starters "
                        + "and bench players. IR and taxi trades "
                        + "need additional roster-placement rules."
                );
            }

            exchange(
                    first,
                    trade.firstPlayerId(),
                    trade.secondPlayerId()
            );

            exchange(
                    second,
                    trade.secondPlayerId(),
                    trade.firstPlayerId()
            );

            return changed.toString();

        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException(
                    "Could not read roster information.", error
            );
        }
    }

    private static void validateSelection(TradeSelection trade) {
        if (trade == null) {
            throw new IllegalArgumentException("Choose a trade first.");
        }

        if (trade.firstRosterId() < 1
                || trade.secondRosterId() < 1
                || trade.firstRosterId() == trade.secondRosterId()) {
            throw new IllegalArgumentException(
                    "Choose two different teams."
            );
        }

        if (trade.firstPlayerId() == null
                || trade.secondPlayerId() == null
                || trade.firstPlayerId().isBlank()
                || trade.secondPlayerId().isBlank()
                || trade.firstPlayerId().equals(trade.secondPlayerId())) {
            throw new IllegalArgumentException(
                    "Choose two different players."
            );
        }
    }

    private static ObjectNode findRoster(
            ArrayNode rosters,
            int rosterId
    ) {
        ObjectNode found = null;

        for (JsonNode roster : rosters) {
            if (!roster.isObject()) {
                throw new IllegalArgumentException(
                        "Invalid roster information."
                );
            }

            if (roster.path("roster_id").asInt(-1) == rosterId) {
                if (found != null) {
                    throw new IllegalArgumentException(
                            "Duplicate roster ID: " + rosterId
                    );
                }

                found = (ObjectNode) roster;
            }
        }

        if (found == null) {
            throw new IllegalArgumentException(
                    "Team was not found: " + rosterId
            );
        }

        return found;
    }

    private static Set<String> ownedPlayers(ObjectNode roster) {
        JsonNode playerList = roster.path("players");

        if (!playerList.isArray()) {
            throw new IllegalArgumentException(
                    "A selected team has no player list."
            );
        }

        Set<String> ids = new HashSet<>();

        for (JsonNode player : playerList) {
            String id = player.asText("");

            if (id.isBlank() || !ids.add(id)) {
                throw new IllegalArgumentException(
                        "A roster contains a missing or duplicate player ID."
                );
            }
        }

        return ids;
    }

    private static boolean isStashed(
            ObjectNode roster,
            String playerId
    ) {
        for (String field : List.of("reserve", "taxi")) {
            for (JsonNode id : roster.path(field)) {
                if (playerId.equals(id.asText())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void exchange(
            ObjectNode roster,
            String outgoing,
            String incoming
    ) {
        ArrayNode replacement = mapper.createArrayNode();

        for (JsonNode player : roster.path("players")) {
            String id = player.asText();

            replacement.add(
                    id.equals(outgoing) ? incoming : id
            );
        }

        roster.set("players", replacement);

        // The optimizer will build a fresh starting lineup.
        roster.remove("starters");
    }
}