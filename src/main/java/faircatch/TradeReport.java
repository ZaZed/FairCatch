package faircatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TradeReport {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String format(
            TradeAnalyzer.Result analysis,
            TradeSelection trade,
            String rostersJson,
            String usersJson,
            JsonNode players,
            int week,
            String scoringName
    ) {
        Map<Integer, String> names = teamNames(rostersJson, usersJson);

        String firstName = names.getOrDefault(
                trade.firstRosterId(), "Team A"
        );

        String secondName = names.getOrDefault(
                trade.secondRosterId(), "Team B"
        );

        StringBuilder report = new StringBuilder();

        report.append("TRADE LINEUP IMPACT\n")
                .append("Week ")
                .append(week)
                .append(" | ")
                .append(scoringName)
                .append(" preset\n")
                .append("Custom league scoring adjustments are not applied.\n\n");

        report.append(firstName)
                .append(" sends:\n")
                .append(LeagueParser.describePlayer(
                        players, trade.firstPlayerId()
                ))
                .append("\n\n")
                .append(secondName)
                .append(" sends:\n")
                .append(LeagueParser.describePlayer(
                        players, trade.secondPlayerId()
                ))
                .append("\n\n");

        report.append(teamSection(
                "TEAM A - " + firstName,
                analysis.firstTeam(),
                players
        ));

        report.append("\n");

        report.append(teamSection(
                "TEAM B - " + secondName,
                analysis.secondTeam(),
                players
        ));

        return report.toString();
    }

    private static String teamSection(
            String name,
            TradeAnalyzer.TeamImpact impact,
            JsonNode players
    ) {
        Map<String, LineupOptimizer.Starter> before =
                byPlayerId(impact.before());

        Map<String, LineupOptimizer.Starter> after =
                byPlayerId(impact.after());

        List<LineupOptimizer.Starter> entered =
                impact.after().starters().stream()
                        .filter(player ->
                                !before.containsKey(player.playerId())
                        )
                        .toList();

        List<LineupOptimizer.Starter> left =
                impact.before().starters().stream()
                        .filter(player ->
                                !after.containsKey(player.playerId())
                        )
                        .toList();

        StringBuilder section = new StringBuilder();

        section.append(name)
                .append("\n")
                .append(String.format(
                        Locale.ROOT,
                        "Before: %.2f%nAfter: %.2f%nChange: %+.2f%n%n",
                        impact.before().totalPoints(),
                        impact.after().totalPoints(),
                        impact.change()
                ));

        if (entered.isEmpty() && left.isEmpty()) {
            section.append(
                    "The same players remain in the best starting lineup.\n"
            );
        } else {
            appendChanges(
                    section, "Entered the starting lineup", entered, players
            );

            appendChanges(
                    section, "Left the starting lineup", left, players
            );

            double addedPoints = entered.stream()
                    .mapToDouble(LineupOptimizer.Starter::points)
                    .sum();

            double removedPoints = left.stream()
                    .mapToDouble(LineupOptimizer.Starter::points)
                    .sum();

            section.append(String.format(
                    Locale.ROOT,
                    "Entering starter points: %.2f%n"
                    + "Leaving starter points: %.2f%n",
                    addedPoints,
                    removedPoints
            ));
        }

        section.append("\nBEFORE TRADE\n")
                .append(lineupText(impact.before(), players))
                .append("\nAFTER TRADE\n")
                .append(lineupText(impact.after(), players))
                .append("\n");

        return section.toString();
    }

    private static void appendChanges(
            StringBuilder report,
            String title,
            List<LineupOptimizer.Starter> changes,
            JsonNode players
    ) {
        report.append(title).append(":\n");

        if (changes.isEmpty()) {
            report.append("  None\n\n");
            return;
        }

        for (LineupOptimizer.Starter starter : changes) {
            report.append("  ")
                    .append(starter.slot())
                    .append(": ")
                    .append(LeagueParser.describePlayer(
                            players, starter.playerId()
                    ))
                    .append(" | ")
                    .append(String.format(
                            Locale.ROOT, "%.2f", starter.points()
                    ))
                    .append("\n");
        }

        report.append("\n");
    }

    private static String lineupText(
            LineupOptimizer.Result lineup,
            JsonNode players
    ) {
        StringBuilder text = new StringBuilder();

        for (LineupOptimizer.Starter starter : lineup.starters()) {
            text.append(starter.slot())
                    .append(": ")
                    .append(LeagueParser.describePlayer(
                            players, starter.playerId()
                    ))
                    .append(" | ")
                    .append(String.format(
                            Locale.ROOT, "%.2f", starter.points()
                    ))
                    .append("\n");
        }

        return text.toString();
    }

    private static Map<String, LineupOptimizer.Starter> byPlayerId(
            LineupOptimizer.Result lineup
    ) {
        Map<String, LineupOptimizer.Starter> players = new HashMap<>();

        for (LineupOptimizer.Starter starter : lineup.starters()) {
            players.put(starter.playerId(), starter);
        }

        return players;
    }

    private static Map<Integer, String> teamNames(
            String rostersJson,
            String usersJson
    ) {
        try {
            JsonNode rosters = mapper.readTree(rostersJson);
            JsonNode users = mapper.readTree(usersJson);

            if (rosters == null || !rosters.isArray()
                    || users == null || !users.isArray()) {
                throw new IllegalArgumentException(
                        "Invalid team information."
                );
            }

            Map<String, JsonNode> managers = new HashMap<>();

            for (JsonNode user : users) {
                String id = user.path("user_id").asText("");

                if (!id.isBlank()) {
                    managers.put(id, user);
                }
            }

            Map<Integer, String> names = new HashMap<>();

            for (JsonNode roster : rosters) {
                int rosterId = roster.path("roster_id").asInt(-1);
                String name = "Team " + rosterId;

                JsonNode manager = managers.get(
                        roster.path("owner_id").asText("")
                );

                if (manager != null) {
                    String displayName = manager.path("display_name")
                            .asText("");

                    String customName = manager.path("metadata")
                            .path("team_name")
                            .asText("");

                    if (!customName.isBlank()) {
                        name = customName;
                    } else if (!displayName.isBlank()) {
                        name = displayName + "'s team";
                    }

                    if (!displayName.isBlank()) {
                        name += " (" + displayName + ")";
                    }
                }

                names.put(rosterId, name);
            }

            return names;

        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException(
                    "Could not read team names.", error
            );
        }
    }
}