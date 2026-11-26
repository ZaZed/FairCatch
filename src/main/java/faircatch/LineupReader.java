package faircatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LineupReader {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static LineupOptimizer.Result forRoster(
            String leagueJson,
            String rostersJson,
            int rosterId,
            JsonNode players,
            Map<String, Projection> projections,
            int week
    ) {
        try {
            JsonNode league = mapper.readTree(leagueJson);
            JsonNode rosters = mapper.readTree(rostersJson);

            if (league == null || !league.isObject()
                    || rosters == null || !rosters.isArray()
                    || players == null || !players.isObject()) {
                throw new IllegalArgumentException(
                        "Invalid league or player information."
                );
            }

            JsonNode configuredSlots = league.path("roster_positions");

            if (!configuredSlots.isArray()) {
                throw new IllegalStateException(
                        "The league's starting positions are missing."
                );
            }

            List<String> slots = new ArrayList<>();

            for (JsonNode configuredSlot : configuredSlots) {
                String slot = configuredSlot.asText()
                        .strip()
                        .toUpperCase(Locale.ROOT);

                if (!Set.of("BN", "IR", "TAXI").contains(slot)) {
                    slots.add(slot);
                }
            }

            JsonNode selectedRoster = null;

            for (JsonNode roster : rosters) {
                if (roster.path("roster_id").asInt(-1) == rosterId) {
                    if (selectedRoster != null) {
                        throw new IllegalStateException(
                                "Duplicate roster ID: " + rosterId
                        );
                    }

                    selectedRoster = roster;
                }
            }

            if (selectedRoster == null) {
                throw new IllegalArgumentException("Team was not found.");
            }

            Set<String> excluded = new HashSet<>();

            for (String field : List.of("reserve", "taxi")) {
                for (JsonNode playerId : selectedRoster.path(field)) {
                    excluded.add(playerId.asText());
                }
            }

            List<LineupOptimizer.Player> candidates = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (JsonNode playerId : selectedRoster.path("players")) {
                String id = playerId.asText();

                if (!seen.add(id)) {
                    throw new IllegalStateException(
                            "Duplicate roster player: " + id
                    );
                }

                if (excluded.contains(id)) {
                    continue;
                }

                JsonNode info = players.path(id);

                if (!info.isObject()) {
                    throw new IllegalStateException(
                            "Missing player information for " + id
                    );
                }

                Set<String> positions = new HashSet<>();

                for (JsonNode position : info.path("fantasy_positions")) {
                    String value = position.asText("").strip();

                    if (!value.isBlank()) {
                        positions.add(value.toUpperCase(Locale.ROOT));
                    }
                }

                String primary = info.path("position").asText("").strip();

                if (!primary.isBlank()) {
                    positions.add(primary.toUpperCase(Locale.ROOT));
                }

                if (positions.isEmpty()) {
                    throw new IllegalStateException(
                            "Missing position for player " + id
                    );
                }

                boolean canStart = slots.stream().anyMatch(slot ->
                        LineupOptimizer.canFill(slot, positions)
                );

                if (!canStart) {
                    continue;
                }

                Projection projection = projections.get(id);

                if (projection == null) {
                    missing.add(LeagueParser.describePlayer(players, id));
                    continue;
                }

                if (projection.week() != week) {
                    throw new IllegalStateException(
                            "Projection week does not match the selected week."
                    );
                }

                candidates.add(new LineupOptimizer.Player(
                        id,
                        positions,
                        projection.points()
                ));
            }

            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot calculate a complete best lineup. "
                        + "Missing projections for:\n"
                        + String.join("\n", missing)
                );
            }

            return LineupOptimizer.optimize(slots, candidates);

        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "Could not read lineup information.", error
            );
        }
    }
}