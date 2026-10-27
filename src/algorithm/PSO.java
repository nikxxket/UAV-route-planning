package algorithm;

import model.Terrain;
import model.Threat;
import model.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PSO {
    final Terrain t;
    final List<Threat> threats;
    final double clearance;
    final int M, D, NP;
    public final int ITER;
    final double w = 0.72, c1 = 1.5, c2 = 1.7;
    final double lim;
    final Random rnd;
    final Vec3 start, goal;
    final List<Vec3> base;

    double[][] pos, vel, pb;
    double[]   pbc, gb;
    public double gbc = Double.MAX_VALUE;
    public int iteration = 0;

    public PSO(Terrain t, List<Threat> threats, List<Vec3> init, double clearance,
               int NP, int ITER, long seed) {
        this.t = t; this.threats = threats; this.clearance = clearance;
        this.rnd = new Random(seed);
        this.base = AStarPlanner.resample(init, 20);
        this.M = Math.max(4, base.size());
        this.start = base.get(0);
        this.goal  = base.get(base.size() - 1);
        this.D = M - 2;
        this.NP = NP; this.ITER = ITER;
        this.lim = t.extent / 2 - 4;

        pos = new double[NP][2 * D];
        vel = new double[NP][2 * D];
        pb  = new double[NP][2 * D];
        pbc = new double[NP];
        gb  = new double[2 * D];

        for (int p = 0; p < NP; p++) {
            for (int k = 0; k < D; k++) {
                Vec3 bp = base.get(k + 1);
                pos[p][2 * k]     = clamp(bp.x + (rnd.nextDouble() - 0.5) * 12, -lim, lim);
                pos[p][2 * k + 1] = clamp(bp.y + (rnd.nextDouble() - 0.5) * 12, -lim, lim);
                vel[p][2 * k]     = (rnd.nextDouble() - 0.5) * 1.5;
                vel[p][2 * k + 1] = (rnd.nextDouble() - 0.5) * 1.5;
            }
            System.arraycopy(pos[p], 0, pb[p], 0, 2 * D);
            pbc[p] = evaluate(pos[p]);
            if (pbc[p] < gbc) { gbc = pbc[p]; System.arraycopy(pb[p], 0, gb, 0, 2 * D); }
        }
    }

    public void step() {
        for (int p = 0; p < NP; p++) {
            for (int k = 0; k < 2 * D; k++) {
                double r1 = rnd.nextDouble(), r2 = rnd.nextDouble();
                vel[p][k] = w * vel[p][k]
                        + c1 * r1 * (pb[p][k] - pos[p][k])
                        + c2 * r2 * (gb[k]   - pos[p][k]);
                if (vel[p][k] >  6) vel[p][k] =  6;
                if (vel[p][k] < -6) vel[p][k] = -6;
                pos[p][k] = clamp(pos[p][k] + vel[p][k], -lim, lim);
            }
            double c = evaluate(pos[p]);
            if (c < pbc[p]) { pbc[p] = c; System.arraycopy(pos[p], 0, pb[p], 0, 2 * D); }
            if (c < gbc)    { gbc = c;    System.arraycopy(pb[p], 0, gb, 0, 2 * D); }
        }
        iteration++;
    }

    double evaluate(double[] v) {
        double total = 0;
        Vec3 prev = start;
        for (int k = 0; k <= D; k++) {
            Vec3 cur;
            if (k == D) cur = goal;
            else {
                double x = v[2 * k], y = v[2 * k + 1];
                cur = new Vec3(x, y, t.heightAt(x, y) + clearance);
            }
            total += prev.dist(cur);
            for (int s = 1; s <= 5; s++) {
                double u = s / 5.0;
                double x = prev.x + (cur.x - prev.x) * u;
                double y = prev.y + (cur.y - prev.y) * u;
                double z = t.heightAt(x, y) + clearance;
                double c = 0;
                for (Threat th : threats) c += Math.min(60.0, th.cost(x, y, z));
                total += c * 0.6;
            }
            prev = cur;
        }
        return total;
    }

    public List<Vec3> particlePath(double[] v) {
        List<Vec3> out = new ArrayList<>(M);
        out.add(start);
        for (int k = 0; k < D; k++) {
            double x = v[2 * k], y = v[2 * k + 1];
            out.add(new Vec3(x, y, t.heightAt(x, y) + clearance));
        }
        out.add(goal);
        return out;
    }

    public List<Vec3> bestPath() { return particlePath(gb); }

    public double[][] snapshot() {
        double[][] s = new double[NP][];
        for (int i = 0; i < NP; i++) s[i] = pos[i].clone();
        return s;
    }

    public static double clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }
}