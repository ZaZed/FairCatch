package faircatch;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import com.fasterxml.jackson.databind.JsonNode;

public class FairCatchGUI extends JFrame {
    private final SleeperClient sleeper = new SleeperClient();
    private final TradePanel tradePanel = new TradePanel();

    private final JTextField leagueInput = new JTextField(20);
    private final JButton loadButton = new JButton("Load League");

    private final JSpinner weekInput = new JSpinner(
            new SpinnerNumberModel(1, 1, 18, 1)
    );

    private final JComboBox<String> scoringInput = new JComboBox<>(
            new String[]{
                "Choose scoring...",
                "Standard",
                "Half PPR",
                "PPR"
            }
    );

    private final JButton projectionButton =
            new JButton("Load Projections");

    private final JButton lineupButton =
            new JButton("Best Lineups");

    private final JButton analyzeButton =
            new JButton("Analyze Trade");

    private final JTextArea output = new JTextArea(
            "Enter your league ID, then click Load League."
    );

    private LoadedLeague loadedLeague;
    private Map<String, Projection> projections = Map.of();
    private boolean busy;

    public FairCatchGUI() {
        super("Fair Catch");

        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel leagueControls = new JPanel();
        leagueControls.add(new JLabel("League ID:"));
        leagueControls.add(leagueInput);
        leagueControls.add(loadButton);

        JPanel projectionControls = new JPanel();
        projectionControls.add(new JLabel("Week:"));
        projectionControls.add(weekInput);
        projectionControls.add(new JLabel("Scoring:"));
        projectionControls.add(scoringInput);
        projectionControls.add(projectionButton);
        projectionControls.add(lineupButton);
        projectionControls.add(analyzeButton);

        JPanel controls = new JPanel(new GridLayout(2, 1));
        controls.add(leagueControls);
        controls.add(projectionControls);

        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(output),
                tradePanel
        );

        split.setDividerLocation(550);
        split.setResizeWeight(0.6);

        add(controls, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        loadButton.addActionListener(event -> loadLeague());
        projectionButton.addActionListener(event -> loadProjections());
        lineupButton.addActionListener(event -> showBestLineups());
        analyzeButton.addActionListener(event -> analyzeSelectedTrade());

        weekInput.addChangeListener(event -> clearProjections());
        scoringInput.addActionListener(event -> clearProjections());

        setBusy(false);
    }

    private void loadLeague() {
        if (busy) {
            return;
        }

        String leagueId = leagueInput.getText().trim();

        if (!leagueId.matches("[0-9]+")) {
            showOutput("Enter a league ID containing only numbers.");
            return;
        }

        loadedLeague = null;
        projections = Map.of();
        tradePanel.clearLeague();
        scoringInput.setSelectedIndex(0);

        showOutput("Loading league, teams, and players...");
        setBusy(true);

        var leagueRequest = sleeper.getLeague(leagueId);
        var rostersRequest = sleeper.getRosters(leagueId);
        var usersRequest = sleeper.getUsers(leagueId);
        var playersRequest = sleeper.getPlayers();

        CompletableFuture.allOf(
                leagueRequest,
                rostersRequest,
                usersRequest,
                playersRequest
        ).thenApplyAsync(ignored -> {
            String leagueJson = leagueRequest.join();
            String rostersJson = rostersRequest.join();
            String usersJson = usersRequest.join();
            JsonNode players = playersRequest.join();

            return new LoadedLeague(
                    ProjectionReader.seasonOf(leagueJson),
                    leagueJson,
                    rostersJson,
                    usersJson,
                    players,
                    LeagueParser.summarize(
                            leagueJson,
                            rostersJson,
                            usersJson,
                            players
                    )
            );

        }).whenComplete((league, error) -> {
            SwingUtilities.invokeLater(() -> {
                if (error != null) {
                    showOutput(errorMessage(error));
                } else {
                    loadedLeague = league;

                    tradePanel.setLeague(
                            league.rostersJson(),
                            league.usersJson(),
                            league.players()
                    );

                    showOutput(league.summary());
                }

                setBusy(false);
            });
        });
    }

    private void loadProjections() {
        if (busy) {
            return;
        }

        if (loadedLeague == null
                || scoringInput.getSelectedIndex() == 0) {
            showOutput("Load a league and choose a scoring format first.");
            return;
        }

        LoadedLeague league = loadedLeague;
        int week = (Integer) weekInput.getValue();
        String scoringName = (String) scoringInput.getSelectedItem();

        String pointsField = switch (scoringInput.getSelectedIndex()) {
            case 1 -> "pts_std";
            case 2 -> "pts_half_ppr";
            case 3 -> "pts_ppr";
            default -> throw new IllegalStateException(
                    "Choose a scoring format."
            );
        };

        clearProjections();

        showOutput("Loading projections for week " + week + "...");
        setBusy(true);

        var projectionRequest = sleeper.getProjections(
                league.season(),
                week
        ).thenApplyAsync(json -> ProjectionReader.read(
                json,
                league.season(),
                week,
                pointsField
        ));

        projectionRequest.thenApplyAsync(loaded ->
                ProjectionReader.summarize(
                        loaded,
                        league.rostersJson(),
                        league.players(),
                        week,
                        scoringName
                )
        ).whenComplete((summary, error) -> {
            SwingUtilities.invokeLater(() -> {
                if (error != null) {
                    showOutput(errorMessage(error));
                } else {
                    projections = projectionRequest.join();

                    tradePanel.setProjections(
                            projections,
                            "Week " + week + " | "
                                    + scoringName + " preset"
                    );

                    showOutput(summary);
                }

                setBusy(false);
            });
        });
    }

    private void showBestLineups() {
        if (busy) {
            return;
        }

        if (loadedLeague == null || projections.isEmpty()) {
            showOutput("Load a league and its projections first.");
            return;
        }

        Integer firstId = tradePanel.getFirstRosterId();
        Integer secondId = tradePanel.getSecondRosterId();

        if (firstId == null || secondId == null
                || firstId.equals(secondId)) {
            showOutput("Choose two different teams.");
            return;
        }

        LoadedLeague league = loadedLeague;
        Map<String, Projection> loadedProjections = projections;

        int week = (Integer) weekInput.getValue();
        String scoringName = (String) scoringInput.getSelectedItem();

        showOutput("Finding the best starting lineups...");
        setBusy(true);

        CompletableFuture.supplyAsync(() -> {
            LineupOptimizer.Result first = LineupReader.forRoster(
                    league.leagueJson(),
                    league.rostersJson(),
                    firstId,
                    league.players(),
                    loadedProjections,
                    week
            );

            LineupOptimizer.Result second = LineupReader.forRoster(
                    league.leagueJson(),
                    league.rostersJson(),
                    secondId,
                    league.players(),
                    loadedProjections,
                    week
            );

            return "BEST PROJECTED LINEUPS\n"
                    + "Week " + week + " | "
                    + scoringName + " preset\n"
                    + "Custom league scoring adjustments "
                    + "are not applied.\n\n"
                    + formatLineup("Team A", first, league.players())
                    + "\n"
                    + formatLineup("Team B", second, league.players());

        }).whenComplete((summary, error) -> {
            SwingUtilities.invokeLater(() -> {
                showOutput(
                        error == null ? summary : errorMessage(error)
                );

                setBusy(false);
            });
        });
    }

    private void analyzeSelectedTrade() {
        if (busy) {
            return;
        }

        if (loadedLeague == null || projections.isEmpty()) {
            showOutput("Load a league and its projections first.");
            return;
        }

        TradeSelection trade = tradePanel.getSelection();

        if (trade == null) {
            showOutput(
                    "Choose two different teams "
                    + "and one player from each."
            );
            return;
        }

        LoadedLeague league = loadedLeague;
        Map<String, Projection> loadedProjections = projections;

        int week = (Integer) weekInput.getValue();
        String scoringName = (String) scoringInput.getSelectedItem();

        showOutput("Calculating the trade's lineup impact...");
        setBusy(true);

        CompletableFuture.supplyAsync(() -> {
            TradeAnalyzer.Result result = TradeAnalyzer.analyze(
                    league.leagueJson(),
                    league.rostersJson(),
                    league.players(),
                    loadedProjections,
                    trade,
                    week
            );

            return TradeReport.format(
                    result,
                    trade,
                    league.rostersJson(),
                    league.usersJson(),
                    league.players(),
                    week,
                    scoringName
            );

        }).whenComplete((report, error) -> {
            SwingUtilities.invokeLater(() -> {
                showOutput(
                        error == null ? report : errorMessage(error)
                );

                setBusy(false);
            });
        });
    }

    private static String formatLineup(
            String teamName,
            LineupOptimizer.Result lineup,
            JsonNode players
    ) {
        StringBuilder result = new StringBuilder(teamName + "\n");

        for (LineupOptimizer.Starter starter : lineup.starters()) {
            result.append(starter.slot())
                    .append(": ")
                    .append(LeagueParser.describePlayer(
                            players,
                            starter.playerId()
                    ))
                    .append(" | ")
                    .append(String.format(
                            Locale.ROOT,
                            "%.2f",
                            starter.points()
                    ))
                    .append("\n");
        }

        result.append("TOTAL: ")
                .append(String.format(
                        Locale.ROOT,
                        "%.2f",
                        lineup.totalPoints()
                ))
                .append("\n");

        return result.toString();
    }

    private void clearProjections() {
        projections = Map.of();

        tradePanel.setProjections(
                projections,
                "No projections loaded"
        );

        if (loadedLeague != null) {
            showOutput(loadedLeague.summary());
        }

        setBusy(busy);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;

        boolean leagueReady = !busy && loadedLeague != null;
        boolean projectionsReady =
                leagueReady && !projections.isEmpty();

        loadButton.setEnabled(!busy);
        leagueInput.setEnabled(!busy);

        weekInput.setEnabled(leagueReady);
        scoringInput.setEnabled(leagueReady);

        projectionButton.setEnabled(
                leagueReady && scoringInput.getSelectedIndex() > 0
        );

        lineupButton.setEnabled(projectionsReady);
        analyzeButton.setEnabled(projectionsReady);

        tradePanel.setBusy(busy);
    }

    private void showOutput(String text) {
        output.setText(text);
        output.setCaretPosition(0);
    }

    private String errorMessage(Throwable error) {
        while (error instanceof CompletionException
                && error.getCause() != null) {
            error = error.getCause();
        }

        String message = error.getMessage();

        return message == null || message.isBlank()
                ? "Could not complete the action."
                : message;
    }

    private record LoadedLeague(
            int season,
            String leagueJson,
            String rostersJson,
            String usersJson,
            JsonNode players,
            String summary
    ) {
    }
}