package model;

import java.util.Random;

public class Terrain {
    public final int n;
    public final double extent;
    public double a, b, c, d, e, f, g;
    public double zScale = 6.0;
    public double[][] h;
    public double[][] nx, ny, nz;
    public double minH = 0, maxH = 1;

    public Terrain(int n, double extent) {
        this.n = n; this.extent = extent;
        h  = new double[n + 1][n + 1];
        nx = new double[n][n]; ny = new double[n][n]; nz = new double[n][n];
        randomize(System.nanoTime());
    }

    public void randomize(long seed) {
        Random r = new Random(seed);
        a = r.nextDouble() * 6.283;
        b = 0.55 + r.nextDouble() * 0.80; c = 0.60 + r.nextDouble() * 0.80;
        d = 0.30 + r.nextDouble() * 0.55; e = 0.35 + r.nextDouble() * 0.60;
        f = 0.85 + r.nextDouble() * 0.70; g = 0.25 + r.nextDouble() * 0.50;
        build();
    }

    public double rawHeight(double x, double y) {
        double xn = x * 0.05, yn = y * 0.05, r = Math.sqrt(xn * xn + yn * yn);
        double z = Math.sin(yn + a) + b * Math.sin(xn) + c * Math.cos(d * r)
                + e * Math.cos(yn) + f * Math.sin(f * r) + g * Math.cos(yn);
        return z * zScale;
    }

    public void build() {
        double half = extent / 2, step = extent / n;
        minH = Double.MAX_VALUE; maxH = -Double.MAX_VALUE;
        for (int i = 0; i <= n; i++) for (int j = 0; j <= n; j++) {
            double z = rawHeight(-half + i * step, -half + j * step);
            h[i][j] = z;
            if (z < minH) minH = z; if (z > maxH) maxH = z;
        }
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            Vec3 p1 = new Vec3(step, 0, h[i + 1][j] - h[i][j]);
            Vec3 p3 = new Vec3(0, step, h[i][j + 1] - h[i][j]);
            Vec3 nr = p1.cross(p3).norm();
            if (nr.z < 0) nr = nr.mul(-1);
            nx[i][j] = nr.x; ny[i][j] = nr.y; nz[i][j] = nr.z;
        }
    }

    public double heightAt(double x, double y) {
        double half = extent / 2, step = extent / n;
        double fx = (x + half) / step, fy = (y + half) / step;
        int i = Math.max(0, Math.min(n - 1, (int) Math.floor(fx)));
        int j = Math.max(0, Math.min(n - 1, (int) Math.floor(fy)));
        double tx = Math.max(0, Math.min(1, fx - i)), ty = Math.max(0, Math.min(1, fy - j));
        double h00 = h[i][j], h10 = h[i + 1][j], h01 = h[i][j + 1], h11 = h[i + 1][j + 1];
        return (h00 * (1 - tx) + h10 * tx) * (1 - ty) + (h01 * (1 - tx) + h11 * tx) * ty;
    }
}