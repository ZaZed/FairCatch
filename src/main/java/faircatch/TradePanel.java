package faircatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.*;

public class TradePanel extends JPanel {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final JComboBox<TeamChoice> firstTeam = new JComboBox<>();
    private final JComboBox<TeamChoice> secondTeam = new JComboBox<>();

    private final JComboBox<PlayerChoice> firstPlayer = new JComboBox<>();
    private final JComboBox<PlayerChoice> secondPlayer = new JComboBox<>();

    private final JButton previewButton = new JButton("Preview Trade");
    private final JTextArea preview = new JTextArea();

    private JsonNode players;
    private Map<String, Projection> projections = Map.of();
    private String projectionContext = "No projections loaded";

    private boolean busy;
    private boolean updating;

    public TradePanel() {
        super(new BorderLayout(8, 8));

        setBorder(BorderFactory.createTitledBorder("Choose a trade"));
        setPreferredSize(new Dimension(380, 500));
        setMinimumSize(new Dimension(280, 200));

        JPanel choices = new JPanel(new GridLayout(0, 1, 4, 4));

        choices.add(new JLabel("Team A:"));
        choices.add(firstTeam);
        choices.add(new JLabel("Player sent by Team A:"));
        choices.add(firstPlayer);

        choices.add(new JLabel("Team B:"));
        choices.add(secondTeam);
        choices.add(new JLabel("Player sent by Team B:"));
        choices.add(secondPlayer);

        choices.add(previewButton);

        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);

        add(choices, BorderLayout.NORTH);
        add(new JScrollPane(preview), BorderLayout.CENTER);

        firstTeam.addActionListener(event ->
                teamChanged(firstTeam, firstPlayer)
        );

        secondTeam.addActionListener(event ->
                teamChanged(secondTeam, secondPlayer)
        );

        firstPlayer.addActionListener(event -> selectionChanged());
        secondPlayer.addActionListener(event -> selectionChanged());

        previewButton.addActionListener(event -> showPreview());

        clearLeague();
    }

    public void setLeague(
            String rostersJson,
            String usersJson,
            JsonNode players
    ) {
        try {
            if (players == null || !players.isObject()) {
                throw new IllegalArgumentException(
                        "Player information is missing."
                );
            }

            List<TeamChoice> teams = readTeams(rostersJson, usersJson);

            updating = true;

            try {
                this.players = players;
                this.projections = Map.of();
                this.projectionContext = "No projections loaded";

                firstTeam.setModel(new DefaultComboBoxModel<>(
                        teams.toArray(new TeamChoice[0])
                ));

                secondTeam.setModel(new DefaultComboBoxModel<>(
                        teams.toArray(new TeamChoice[0])
                ));

                if (teams.size() > 1) {
                    secondTeam.setSelectedIndex(1);
                }
            } finally {
                updating = false;
            }

            teamChanged(firstTeam, firstPlayer);
            teamChanged(secondTeam, secondPlayer);

        } catch (JsonProcessingException | IllegalArgumentException error) {
            clearLeague();
            preview.setText(
                    "Could not load team choices: " + error.getMessage()
            );
        }
    }

    public void clearLeague() {
        updating = true;

        try {
            players = null;
            projections = Map.of();
            projectionContext = "No projections loaded";

            firstTeam.removeAllItems();
            secondTeam.removeAllItems();
            firstPlayer.removeAllItems();
            secondPlayer.removeAllItems();
        } finally {
            updating = false;
        }

        preview.setText("Load a league to choose a trade.");
        updateControls();
    }

    public void setProjections(
            Map<String, Projection> projections,
            String context
    ) {
        this.projections = Map.copyOf(projections);
        this.projectionContext = context;
        selectionChanged();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        updateControls();
    }

    public TradeSelection getSelection() {
        TeamChoice teamA = (TeamChoice) firstTeam.getSelectedItem();
        TeamChoice teamB = (TeamChoice) secondTeam.getSelectedItem();

        PlayerChoice playerA = (PlayerChoice) firstPlayer.getSelectedItem();
        PlayerChoice playerB = (PlayerChoice) secondPlayer.getSelectedItem();

        if (teamA == null || teamB == null
                || playerA == null || playerB == null
                || teamA.rosterId() == teamB.rosterId()
                || playerA.id().equals(playerB.id())
                || !teamA.playerIds().contains(playerA.id())
                || !teamB.playerIds().contains(playerB.id())) {
            return null;
        }

        return new TradeSelection(
                teamA.rosterId(),
                playerA.id(),
                teamB.rosterId(),
                playerB.id()
        );
    }

    private void teamChanged(
            JComboBox<TeamChoice> teamBox,
            JComboBox<PlayerChoice> playerBox
    ) {
        if (updating) {
            return;
        }

        TeamChoice team = (TeamChoice) teamBox.getSelectedItem();
        DefaultComboBoxModel<PlayerChoice> model =
                new DefaultComboBoxModel<>();

        if (team != null && players != null) {
            for (String playerId : team.playerIds()) {
                model.addElement(new PlayerChoice(
                        playerId,
                        LeagueParser.describePlayer(players, playerId)
                ));
            }
        }

        playerBox.setModel(model);
        playerBox.setSelectedIndex(-1);

        selectionChanged();
    }

    private void selectionChanged() {
        if (updating) {
            return;
        }

        TeamChoice teamA = (TeamChoice) firstTeam.getSelectedItem();
        TeamChoice teamB = (TeamChoice) secondTeam.getSelectedItem();

        if (teamA == null || teamB == null) {
            preview.setText("Load a league with at least two teams.");
        } else if (teamA.rosterId() == teamB.rosterId()) {
            preview.setText("Choose two different teams.");
        } else if (getSelection() == null) {
            preview.setText("Choose one player from each team.");
        } else {
            preview.setText("Ready. Click Preview Trade.");
        }

        updateControls();
    }

    private void updateControls() {
        firstTeam.setEnabled(!busy && firstTeam.getItemCount() > 0);
        secondTeam.setEnabled(!busy && secondTeam.getItemCount() > 0);

        firstPlayer.setEnabled(!busy && firstPlayer.getItemCount() > 0);
        secondPlayer.setEnabled(!busy && secondPlayer.getItemCount() > 0);

        previewButton.setEnabled(!busy && getSelection() != null);
    }

    private void showPreview() {
        if (getSelection() == null) {
            return;
        }

        TeamChoice teamA = (TeamChoice) firstTeam.getSelectedItem();
        TeamChoice teamB = (TeamChoice) secondTeam.getSelectedItem();

        PlayerChoice playerA = (PlayerChoice) firstPlayer.getSelectedItem();
        PlayerChoice playerB = (PlayerChoice) secondPlayer.getSelectedItem();

        preview.setText(
                projectionContext
                + "\n\n" + teamA.label() + " sends:\n"
                + playerA.label()
                + "\nProjected points: " + pointsFor(playerA.id())
                + "\n\n" + teamB.label() + " sends:\n"
                + playerB.label()
                + "\nProjected points: " + pointsFor(playerB.id())
        );

        preview.setCaretPosition(0);
    }

    private String pointsFor(String playerId) {
        Projection projection = projections.get(playerId);

        return projection == null
                ? "unavailable"
                : String.format(Locale.ROOT, "%.2f", projection.points());
    }

    private static List<TeamChoice> readTeams(
            String rostersJson,
            String usersJson
    ) throws JsonProcessingException {
        JsonNode rosters = mapper.readTree(rostersJson);
        JsonNode users = mapper.readTree(usersJson);

        if (rosters == null || !rosters.isArray()
                || users == null || !users.isArray()) {
            throw new IllegalArgumentException("Invalid team information.");
        }

        Map<String, JsonNode> managers = new HashMap<>();

        for (JsonNode user : users) {
            String userId = user.path("user_id").asText("");

            if (!userId.isBlank()) {
                managers.put(userId, user);
            }
        }

        List<TeamChoice> teams = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();

        for (JsonNode roster : rosters) {
            int rosterId = roster.path("roster_id").asInt(-1);

            if (rosterId < 1 || !seenIds.add(rosterId)) {
                throw new IllegalArgumentException(
                        "Missing or duplicate roster ID."
                );
            }

            JsonNode manager = managers.get(
                    roster.path("owner_id").asText("")
            );

            String managerName = "No manager listed";
            String teamName = "Team " + rosterId;

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

            List<String> playerIds = new ArrayList<>();

            for (JsonNode playerId : roster.path("players")) {
                playerIds.add(playerId.asText());
            }

            teams.add(new TeamChoice(
                    rosterId,
                    teamName + " (" + managerName + ")",
                    List.copyOf(playerIds)
            ));
        }

        return List.copyOf(teams);
    }

    private record TeamChoice(
            int rosterId,
            String label,
            List<String> playerIds
    ) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record PlayerChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public Integer getFirstRosterId() {
        TeamChoice team = (TeamChoice) firstTeam.getSelectedItem();
        return team == null ? null : team.rosterId();
    }

    public Integer getSecondRosterId() {
        TeamChoice team = (TeamChoice) secondTeam.getSelectedItem();
        return team == null ? null : team.rosterId();
    }
}