package model;

public class MountainThreat extends Threat {
    public double baseZ, H, tanTheta, dmin = 4;

    public double radiusAt(double z) { return Math.max(0, (H - z) / tanTheta); }

    @Override
    public double cost(double px, double py, double pz) {
        if (pz >= H) return 0;
        double dx = px - x, dy = py - y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double lim = radiusAt(pz) + dmin;
        return dist < lim ? (lim - dist) : 0;
    }
}