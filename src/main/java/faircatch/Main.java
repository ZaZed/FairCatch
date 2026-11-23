package faircatch;

import java.awt.BorderLayout;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        IO.println("Welcome to FairCatch!");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("Fair Catch");
            window.setSize(800, 500);
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setLocationRelativeTo(null);

            JTextField leagueInput = new JTextField(20);
            JButton loadButton = new JButton("Load League");

            JPanel controls = new JPanel();
            controls.add(new JLabel("League ID:"));
            controls.add(leagueInput);
            controls.add(loadButton);

            JTextArea output = new JTextArea(
                    "Enter your league ID, then click Load League."
            );
            output.setEditable(false);
            output.setLineWrap(true);
            output.setWrapStyleWord(true);

            window.add(controls, BorderLayout.NORTH);
            window.add(new JScrollPane(output), BorderLayout.CENTER);

            loadButton.addActionListener(event -> {
                String leagueId = leagueInput.getText().trim();

                if (!leagueId.matches("[0-9]+")) {
                    output.setText("Enter a league ID containing only numbers.");
                    return;
                }

                output.setText("Loading league...");
                loadButton.setEnabled(false);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(
                                "https://api.sleeper.app/v1/league/" + leagueId
                        ))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                client.sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                ).whenComplete((response, error) -> {
                    SwingUtilities.invokeLater(() -> {
                        loadButton.setEnabled(true);

                        if (error != null) {
                            output.setText(
                                    "Could not reach Sleeper. "
                                    + "Check your internet connection and try again."
                            );
                        } else if (response.statusCode() != 200) {
                            output.setText(
                                    "Sleeper returned HTTP "
                                    + response.statusCode()
                                    + ". Check your league ID and try again."
                            );
                        } else if (response.body().trim().equals("null")) {
                            output.setText(
                                    "No league found. Check your league ID."
                            );
                        } else {
                            output.setText(response.body());
                        }

                        output.setCaretPosition(0);
                    });
                });
            });

            window.setVisible(true);
        });
        
    }
}