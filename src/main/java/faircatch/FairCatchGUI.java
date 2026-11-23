package faircatch;

import java.awt.BorderLayout;
import javax.swing.*;

public class FairCatchGUI extends JFrame {
    private final SleeperClient sleeper = new SleeperClient();

    private final JTextField leagueInput = new JTextField(20);
    private final JButton loadButton = new JButton("Load League");
    private final JTextArea output = new JTextArea(
            "Enter your league ID, then click Load League."
    );

    public FairCatchGUI() {
        super("Fair Catch");

        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel controls = new JPanel();
        controls.add(new JLabel("League ID:"));
        controls.add(leagueInput);
        controls.add(loadButton);

        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);

        add(controls, BorderLayout.NORTH);
        add(new JScrollPane(output), BorderLayout.CENTER);

        loadButton.addActionListener(event -> loadLeague());
    }

    private void loadLeague() {
        String leagueId = leagueInput.getText().trim();

        if (!leagueId.matches("[0-9]+")) {
            output.setText("Enter a league ID containing only numbers.");
            return;
        }

        output.setText("Loading league...");
        loadButton.setEnabled(false);

        sleeper.getLeague(leagueId).whenComplete((data, error) -> {
            SwingUtilities.invokeLater(() -> {
                loadButton.setEnabled(true);

                if (error != null) {
                    Throwable cause = error.getCause() != null
                            ? error.getCause()
                            : error;

                    String message = cause.getMessage();

                    output.setText(message == null || message.isBlank()
                            ? "Could not load the league. Check your connection."
                            : message);
                } else {
                    output.setText(data);
                }

                output.setCaretPosition(0);
            });
        });
    }
}