package faircatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ProjectionReader {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static int seasonOf(String leagueJson) {
        JsonNode league = readJson(leagueJson);
        int season = league.path("season").asInt(0);

        if (season < 2000) {
            throw new IllegalStateException(
                    "The league season is missing or invalid."
            );
        }

        return season;
    }

    public static Map<String, Projection> read(
            String json,
            int season,
            int week,
            String pointsField
    ) {
        JsonNode rows = readJson(json);

        if (!rows.isArray()) {
            throw new IllegalStateException(
                    "Sleeper returned an unexpected projection format."
            );
        }

        Map<String, Projection> projections = new HashMap<>();

        for (JsonNode row : rows) {
            if (row.path("season").asInt(-1) != season
                    || row.path("week").asInt(-1) != week
                    || !row.path("season_type").asText().equals("regular")
                    || !row.path("category").asText().equals("proj")) {
                continue;
            }

            String playerId = row.path("player_id").asText("");
            JsonNode points = row.path("stats").path(pointsField);

            // Missing projections are not treated as zero.
            if (playerId.isBlank() || !points.isNumber()) {
                continue;
            }

            double value = points.doubleValue();

            if (!Double.isFinite(value)) {
                continue;
            }

            Projection projection = new Projection(
                    playerId, week, value
            );

            if (projections.putIfAbsent(playerId, projection) != null) {
                throw new IllegalStateException(
                        "Multiple projections were returned for player "
                        + playerId + "."
                );
            }
        }

        if (projections.isEmpty()) {
            throw new IllegalStateException(
                    "No projections are available for season "
                    + season + ", week " + week + "."
            );
        }

        return Map.copyOf(projections);
    }

    public static String summarize(
            Map<String, Projection> projections,
            String rostersJson,
            JsonNode players,
            int week,
            String scoringName
    ) {
        JsonNode rosters = readJson(rostersJson);

        if (!rosters.isArray()) {
            throw new IllegalStateException("Could not read the rosters.");
        }

        Set<String> playerIds = new LinkedHashSet<>();

        for (JsonNode roster : rosters) {
            for (JsonNode playerId : roster.path("players")) {
                playerIds.add(playerId.asText());
            }
        }

        long available = playerIds.stream()
                .filter(projections::containsKey)
                .count();

        StringBuilder result = new StringBuilder();

        result.append("Week: ")
                .append(week)
                .append("\nScoring: ")
                .append(scoringName)
                .append(" preset")
                .append("\nCustom league adjustments are not applied yet.")
                .append("\nProjections available: ")
                .append(available)
                .append(" of ")
                .append(playerIds.size())
                .append(" rostered players\n\n");

        for (String playerId : playerIds) {
            result.append(LeagueParser.describePlayer(players, playerId));

            Projection projection = projections.get(playerId);

            if (projection == null) {
                result.append(" | Projection unavailable");
            } else {
                result.append(" | Projected: ")
                        .append(String.format(
                                Locale.ROOT,
                                "%.2f",
                                projection.points()
                        ));
            }

            result.append("\n");
        }

        return result.toString();
    }

    private static JsonNode readJson(String json) {
        try {
            JsonNode data = mapper.readTree(json);

            if (data == null) {
                throw new IllegalStateException(
                        "Sleeper returned an empty response."
                );
            }

            return data;

        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "Could not read Sleeper's response.", error
            );
        }
    }
}