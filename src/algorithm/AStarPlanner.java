package algorithm;

import model.Terrain;
import model.Threat;
import model.Vec3;

import java.util.*;

public class AStarPlanner {

    static class ANode implements Comparable<ANode> {
        final double f; final int idx;
        ANode(double f, int idx) { this.f = f; this.idx = idx; }
        public int compareTo(ANode o) { return Double.compare(f, o.f); }
    }

    public static List<Vec3> plan(Terrain t, List<Threat> threats, double clearance,
                                  double[] sxy, double[] gxy) {
        int n = t.n, NV = n + 1, N = NV * NV;
        double half = t.extent / 2, step = t.extent / n;
        double[] px = new double[N], py = new double[N], pz = new double[N];
        double[] nodeCost = new double[N];
        for (int i = 0; i < NV; i++) for (int j = 0; j < NV; j++) {
            double x = -half + i * step, y = -half + j * step, z = t.heightAt(x, y) + clearance;
            int k = i * NV + j;
            px[k] = x; py[k] = y; pz[k] = z;
            double c = 0;
            for (Threat th : threats) c += Math.min(60.0, th.cost(x, y, z));
            nodeCost[k] = c;
        }

        int start = nearest(px, py, sxy[0], sxy[1], NV);
        int goal  = nearest(px, py, gxy[0], gxy[1], NV);

        double[] gsc = new double[N]; Arrays.fill(gsc, Double.MAX_VALUE);
        int[] came = new int[N]; Arrays.fill(came, -1);
        boolean[] closed = new boolean[N];

        PriorityQueue<ANode> open = new PriorityQueue<>();
        gsc[start] = 0;
        open.add(new ANode(heur(px, py, pz, start, goal), start));

        while (!open.isEmpty()) {
            int u = open.poll().idx;
            if (closed[u]) continue;
            closed[u] = true;
            if (u == goal) break;

            int ui = u / NV, uj = u % NV;
            for (int di = -1; di <= 1; di++) for (int dj = -1; dj <= 1; dj++) {
                if (di == 0 && dj == 0) continue;
                int vi = ui + di, vj = uj + dj;
                if (vi < 0 || vi >= NV || vj < 0 || vj >= NV) continue;
                int v = vi * NV + vj;
                if (closed[v]) continue;

                double dx = px[v] - px[u], dy = py[v] - py[u], dz = pz[v] - pz[u];
                double base  = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double climb = Math.max(0, dz) * 0.5;
                double pen   = 0.85 * 0.5 * (nodeCost[u] + nodeCost[v]) * (base / step);
                double ng    = gsc[u] + base + climb + pen;

                if (ng < gsc[v]) {
                    gsc[v] = ng; came[v] = u;
                    open.add(new ANode(ng + heur(px, py, pz, v, goal), v));
                }
            }
        }

        LinkedList<Vec3> res = new LinkedList<>();
        int cur = goal, guard = 0;
        while (cur != -1 && guard++ < N) {
            res.addFirst(new Vec3(px[cur], py[cur], pz[cur]));
            if (cur == start) break;
            cur = came[cur];
        }
        return res;
    }

    private static double heur(double[] px, double[] py, double[] pz, int v, int goal) {
        double dx = px[v] - px[goal], dy = py[v] - py[goal], dz = pz[v] - pz[goal];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int nearest(double[] px, double[] py, double x, double y, int NV) {
        int best = 0; double bd = Double.MAX_VALUE;
        for (int i = 0; i < NV; i++) for (int j = 0; j < NV; j++) {
            int k = i * NV + j;
            double dx = px[k] - x, dy = py[k] - y, d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = k; }
        }
        return best;
    }

    public static List<Vec3> resample(List<Vec3> in, int M) {
        if (in.size() < 2) return new ArrayList<>(in);
        if (in.size() == M) return new ArrayList<>(in);
        double[] cum = new double[in.size()];
        for (int i = 1; i < in.size(); i++) cum[i] = cum[i - 1] + in.get(i - 1).dist(in.get(i));
        double total = cum[in.size() - 1];

        List<Vec3> out = new ArrayList<>(M);
        out.add(in.get(0));
        for (int k = 1; k < M - 1; k++) {
            double target = total * k / (M - 1);
            int i = 1;
            while (i < in.size() - 1 && cum[i] < target) i++;
            double segLen = cum[i] - cum[i - 1];
            double u = segLen < 1e-9 ? 0 : (target - cum[i - 1]) / segLen;
            Vec3 a = in.get(i - 1), b = in.get(i);
            out.add(new Vec3(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u, a.z + (b.z - a.z) * u));
        }
        out.add(in.get(in.size() - 1));
        return out;
    }
}