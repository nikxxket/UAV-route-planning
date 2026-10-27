package view;

import model.Drone;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ControlPanel extends JPanel {

    public ControlPanel(TerrainPanel view) {
        setPreferredSize(new Dimension(310, 100));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setBackground(new Color(34, 37, 44));

        JLabel title = new JLabel("<html><b><font color='white' size='4'>Роевое планирование БПЛА</font></b><br></font></html>");
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(14));

        // --- рой ---
        add(mkSection("Рой дронов"));
        JLabel lD = mkLabel("Количество БПЛА");
        add(lD);
        JSpinner spD = new JSpinner(new SpinnerNumberModel(3, 1, 8, 1));
        styleSpinner(spD);
        add(spD);
        add(Box.createVerticalStrut(8));

        JButton bNew = mkButton("Новый ландшафт");
        bNew.addActionListener(e -> view.regenerate());
        add(bNew); add(Box.createVerticalStrut(6));

        JButton bCam = mkButton("Сбросить камеру");
        bCam.addActionListener(e -> view.resetCamera());
        add(bCam);

        add(Box.createVerticalStrut(14));
        add(separator());
        add(Box.createVerticalStrut(10));

        // --- обучение ---
        add(mkSection("Симуляция обучения"));
        JLabel lTr = mkLabel("Дрон для обучения");
        add(lTr);
        JComboBox<String> droneChooser = new JComboBox<>();
        droneChooser.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        droneChooser.setAlignmentX(LEFT_ALIGNMENT);
        refreshDroneChooser(droneChooser, view);
        add(droneChooser);
        add(Box.createVerticalStrut(6));

        JLabel lSpd = mkLabel("Скорость итерации");
        add(lSpd);
        JSlider speed = new JSlider(1, 200, 45);
        speed.setInverted(true);
        speed.setBackground(new Color(34, 37, 44));
        speed.setAlignmentX(LEFT_ALIGNMENT);
        speed.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        speed.addChangeListener(e -> view.trainingDelay = speed.getValue());
        add(speed);
        add(Box.createVerticalStrut(6));

        JButton bTrain = mkButton("▶  Запустить обучение");
        bTrain.setBackground(new Color(78, 130, 78));
        bTrain.addActionListener(e -> {
            refreshDroneChooser(droneChooser, view);
            view.startTraining(droneChooser.getSelectedIndex());
        });
        add(bTrain); add(Box.createVerticalStrut(6));

        JButton bStop = mkButton("■  Остановить");
        bStop.addActionListener(e -> view.stopTraining());
        add(bStop);

        add(Box.createVerticalStrut(14));
        add(separator());
        add(Box.createVerticalStrut(10));

        // --- отображение ---
        add(mkSection("Отображение"));
        JCheckBox cbT = mkCheck("Показывать угрозы", true);
        cbT.addActionListener(e -> { view.showThreats = cbT.isSelected(); view.repaint(); });
        add(cbT);
        JCheckBox cbP = mkCheck("Показывать маршруты", true);
        cbP.addActionListener(e -> { view.showPath = cbP.isSelected(); view.repaint(); });
        add(cbP);
        JCheckBox cbA = mkCheck("Анимация роя", true);
        cbA.addActionListener(e -> view.animate = cbA.isSelected());
        add(cbA);

        add(Box.createVerticalStrut(14));
        add(separator());
        add(Box.createVerticalStrut(10));

        // --- генерация угроз ---
        add(mkSection("Генерация угроз"));
        JLabel l1 = mkLabel("Горные вершины (конусы)"); add(l1);
        JSpinner spM = new JSpinner(new SpinnerNumberModel(3, 0, 8, 1)); styleSpinner(spM); add(spM);
        add(Box.createVerticalStrut(6));
        JLabel l2 = mkLabel("Радары"); add(l2);
        JSpinner spR = new JSpinner(new SpinnerNumberModel(2, 0, 6, 1)); styleSpinner(spR); add(spR);
        spM.addChangeListener(e -> {
            view.mountainCount = (Integer) spM.getValue();
            view.radarCount = (Integer) spR.getValue();
            view.generateThreats(); view.replan();
        });
        spR.addChangeListener(e -> {
            view.mountainCount = (Integer) spM.getValue();
            view.radarCount = (Integer) spR.getValue();
            view.generateThreats(); view.replan();
        });

        spD.addChangeListener(e -> {
            view.setDrones((Integer) spD.getValue());
            refreshDroneChooser(droneChooser, view);
        });

        add(Box.createVerticalStrut(16));
        add(separator());
        add(Box.createVerticalStrut(8));

        // --- сводка ---
        JLabel stats = new JLabel();
        stats.setForeground(new Color(190, 210, 235));
        stats.setAlignmentX(LEFT_ALIGNMENT);
        add(stats);
        new javax.swing.Timer(400, e -> {
            StringBuilder sb = new StringBuilder("<html><font color='#9fb0c8' size='2'>");
            sb.append("Дронов: <b>").append(view.getDrones().size()).append("</b><br>");
            double sumLen = 0, sumRisk = 0;
            for (Drone d : view.getDrones()) { sumLen += d.length; sumRisk += d.risk; }
            sb.append("Σ длина: <b><font color='#ffe45a'>").append(String.format("%.0f", sumLen)).append("</font></b> ед.<br>");
            sb.append("Σ риск: <b><font color='#ff9a6a'>").append(String.format("%.0f", sumRisk)).append("</font></b>");
            if (view.trainingActive)
                sb.append("<br>Итерация: <b>").append(view.trainingIteration).append(" / ").append(view.trainingTotal).append("</b>");
            sb.append("</font></html>");
            stats.setText(sb.toString());
        }).start();

        add(Box.createVerticalGlue());
    }

    private void refreshDroneChooser(JComboBox<String> cb, TerrainPanel view) {
        int sel = cb.getSelectedIndex();
        cb.removeAllItems();
        for (Drone d : view.getDrones()) cb.addItem("БПЛА-" + (d.id + 1));
        if (sel >= 0 && sel < cb.getItemCount()) cb.setSelectedIndex(sel);
    }

    private JLabel mkSection(String t) {
        JLabel l = new JLabel(t);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setForeground(new Color(150, 200, 255));
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private JButton mkButton(String text) {
        JButton b = new JButton(text);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        b.setFocusPainted(false);
        b.setBackground(new Color(58, 64, 78));
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        return b;
    }

    private JCheckBox mkCheck(String text, boolean sel) {
        JCheckBox c = new JCheckBox(text, sel);
        c.setAlignmentX(LEFT_ALIGNMENT);
        c.setForeground(new Color(220, 228, 240));
        c.setBackground(new Color(34, 37, 44));
        c.setFocusPainted(false);
        return c;
    }

    private JLabel mkLabel(String t) {
        JLabel l = new JLabel(t);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setForeground(new Color(170, 185, 205));
        return l;
    }

    private void styleSpinner(JSpinner s) {
        s.setAlignmentX(LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JComponent ed = s.getEditor();
        ed.setBackground(new Color(52, 57, 70));
        ed.setForeground(Color.WHITE);
        ((JSpinner.DefaultEditor) ed).getTextField().setCaretColor(Color.WHITE);
    }

    private JComponent separator() {
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(new Color(70, 78, 94));
        return sep;
    }
}