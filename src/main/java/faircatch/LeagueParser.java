package faircatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class LeagueParser {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String summarize(
            String leagueJson,
            String rostersJson,
            String usersJson
    ) {
        try {
            JsonNode league = mapper.readTree(leagueJson);
            JsonNode rosters = mapper.readTree(rostersJson);
            JsonNode users = mapper.readTree(usersJson);

            if (!league.isObject()
                    || !rosters.isArray()
                    || !users.isArray()) {
                throw new IllegalStateException(
                        "Sleeper returned an unexpected data format."
                );
            }

            // Look up managers using their user IDs.
            Map<String, JsonNode> managers = new HashMap<>();

            for (JsonNode user : users) {
                String userId = user.path("user_id").asText("");

                if (!userId.isBlank()) {
                    managers.put(userId, user);
                }
            }

            StringBuilder result = new StringBuilder();

            result.append("League: ")
                    .append(league.path("name").asText("Unnamed league"))
                    .append("\nSeason: ")
                    .append(league.path("season").asText("Unknown"))
                    .append("\nTeams: ")
                    .append(rosters.size())
                    .append("\n\n");

            // Match each roster with its manager.
            for (JsonNode roster : rosters) {
                String ownerId = roster.path("owner_id").asText("");
                JsonNode manager = managers.get(ownerId);

                String managerName = "No manager listed";
                String teamName = "Team "
                        + roster.path("roster_id").asText("?");

                if (manager != null) {
                    managerName = manager.path("display_name")
                            .asText("Unknown manager");

                    String customName = manager.path("metadata")
                            .path("team_name")
                            .asText("");

                    teamName = customName.isBlank()
                            ? managerName + "'s team"
                            : customName;
                }

                result.append(teamName)
                        .append("\nManager: ")
                        .append(managerName)
                        .append("\nPlayers: ")
                        .append(roster.path("players").size())
                        .append("\n\n");
            }

            return result.toString();

        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "Could not read the league data.",
                    error
            );
        }
    }
}