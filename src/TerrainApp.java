import view.ControlPanel;
import view.TerrainPanel;
import javax.swing.*;
import java.awt.*;

public class TerrainApp {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
            JFrame frame = new JFrame("Роевое планирование БПЛА");
            TerrainPanel view = new TerrainPanel();
            frame.setLayout(new BorderLayout());
            frame.add(view, BorderLayout.CENTER);
            frame.add(new ControlPanel(view), BorderLayout.EAST);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1420, 880);
            frame.setMinimumSize(new Dimension(1100, 680));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            SwingUtilities.invokeLater(view::requestFocusInWindow);
        });
    }
}