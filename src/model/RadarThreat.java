package model;

public class RadarThreat extends Threat {
    public double z, radius, K = 2.5e6;

    @Override
    public double cost(double px, double py, double pz) {
        double dx = px - x, dy = py - y, dz = pz - z;
        double d2 = dx * dx + dy * dy + dz * dz;
        return K / Math.max(d2 * d2, 1.0);
    }
}