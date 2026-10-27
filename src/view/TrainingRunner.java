package view;

import algorithm.PSO;

import javax.swing.*;

class TrainingRunner extends Thread {
    final TerrainPanel panel;
    final PSO pso;
    final int droneId;
    final long delayMs;
    volatile boolean stopped = false;

    TrainingRunner(TerrainPanel panel, PSO pso, int droneId, long delayMs) {
        this.panel = panel; this.pso = pso; this.droneId = droneId; this.delayMs = delayMs;
        setDaemon(true);
        setName("pso-training");
    }

    public void stopTraining() { stopped = true; interrupt(); }

    @Override
    public void run() {
        try {
            panel.trainingActive = true;
            panel.trainingDroneId = droneId;
            panel.trainingTotal = pso.ITER;
            panel.trainingIteration = 0;
            panel.trainingParticles = pso.snapshot();
            panel.trainingBestPath = pso.bestPath();
            synchronized (panel.trainingCosts) { panel.trainingCosts.clear(); }
            SwingUtilities.invokeLater(panel::repaint);

            for (int it = 0; it < pso.ITER && !stopped; it++) {
                pso.step();
                panel.trainingIteration = pso.iteration;
                panel.trainingParticles = pso.snapshot();
                panel.trainingBestPath = pso.bestPath();
                synchronized (panel.trainingCosts) { panel.trainingCosts.add(pso.gbc); }
                SwingUtilities.invokeLater(panel::repaint);
                Thread.sleep(delayMs);
            }
        } catch (InterruptedException ignored) {
        } finally {
            panel.trainingActive = false;
            SwingUtilities.invokeLater(() -> {
                panel.applyTrainingResult(pso);
                panel.repaint();
            });
        }
    }
}