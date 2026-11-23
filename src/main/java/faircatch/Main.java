package faircatch;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FairCatchGUI window = new FairCatchGUI();
            window.setVisible(true);
        });
    }
}