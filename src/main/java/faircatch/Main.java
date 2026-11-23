package faircatch;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        IO.println("Welcome to FairCatch!");

        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("Fair Catch");

            window.setSize(800, 500);
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
        
    }
}