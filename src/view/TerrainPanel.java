package view;

import algorithm.AStarPlanner;
import algorithm.PSO;
import model.Drone;
import model.MountainThreat;
import model.RadarThreat;
import model.Terrain;
import model.Threat;
import model.Vec3;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class TerrainPanel extends JPanel {

    static final double NEAR = 25;
    static final Vec3 LIGHT = new Vec3(-0.45, -0.62, 0.65);
    static final Color FOG = new Color(120, 148, 176);
    static final double CLEARANCE = 9.0;

    Terrain terrain = new Terrain(38, 200);
    final List<Threat> threats = new ArrayList<>();
    final List<Drone> drones = new ArrayList<>();

    // ==== обучение ====
    volatile boolean trainingActive = false;
    volatile int trainingDroneId = 0;
    volatile int trainingIteration = 0;
    volatile int trainingTotal = 1;
    volatile double[][] trainingParticles;
    volatile List<Vec3> trainingBestPath;
    final List<Double> trainingCosts = Collections.synchronizedList(new ArrayList<>());
    TrainingRunner trainingRunner;
    volatile int trainingDelay = 45;

    // ==== камера ====
    double yaw = Math.toRadians(38), pitch = Math.toRadians(46), dist = 340, focal = 980;
    Vec3 target = new Vec3(0, 0, 8);

    boolean showThreats = true, showPath = true, animate = true, showSwarm = true;
    int mountainCount = 3, radarCount = 2;

    double animT = 0;
    Point lastMouse; int mouseButton;
    javax.swing.Timer timer;

    public TerrainPanel() {
        setBackground(Color.BLACK);
        setFocusable(true);
        setPreferredSize(new Dimension(1000, 780));
        setDrones(3);
        regenerate();
        installMouse();
        timer = new javax.swing.Timer(33, e -> {
            if (animate && !drones.isEmpty()) {
                animT += 0.0045;
                if (animT > 1) animT = 0;
                repaint();
            }
            if (trainingActive) repaint();
        });
        timer.start();
    }

    private void installMouse() {
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { lastMouse = e.getPoint(); mouseButton = e.getButton(); requestFocusInWindow(); }
            public void mouseReleased(MouseEvent e) { lastMouse = null; }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (lastMouse == null) return;
                int dx = e.getX() - lastMouse.x, dy = e.getY() - lastMouse.y;
                lastMouse = e.getPoint();
                if (mouseButton == MouseEvent.BUTTON1) {
                    yaw += dx * 0.008;
                    pitch = Math.max(Math.toRadians(6), Math.min(Math.toRadians(86), pitch + dy * 0.006));
                } else {
                    double k = dist / focal;
                    double wx = (dx * Math.cos(yaw) - dy * Math.sin(yaw)) * k * 0.9;
                    double wy = (dx * Math.sin(yaw) + dy * Math.cos(yaw)) * k * 0.9;
                    target = new Vec3(target.x - wx, target.y - wy, target.z);
                }
                repaint();
            }
        });
        addMouseWheelListener(e -> {
            dist = Math.max(80, Math.min(1400, dist * Math.pow(1.1, e.getWheelRotation())));
            repaint();
        });
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_R) resetCamera();
                if (e.getKeyCode() == KeyEvent.VK_SPACE) regenerate();
            }
        });
    }

    void resetCamera() { yaw = Math.toRadians(38); pitch = Math.toRadians(46); dist = 340; target = new Vec3(0, 0, 8); repaint(); }

    private double cosY, sinY, cosP, sinP, vcx, vcy;

    private void setupProjection() {
        cosY = Math.cos(yaw); sinY = Math.sin(yaw);
        cosP = Math.cos(pitch); sinP = Math.sin(pitch);
        vcx = getWidth() / 2.0; vcy = getHeight() / 2.0;
    }

    double[] project(double x, double y, double z) {
        double dx = x - target.x, dy = y - target.y, dz = z - target.z;
        double x1 = dx * cosY - dy * sinY;
        double y1 = dx * sinY + dy * cosY;
        double y2 = y1 * cosP - dz * sinP;
        double z2 = y1 * sinP + dz * cosP;
        double depth = y2 + dist;
        if (depth < NEAR) return null;
        double s = focal / depth;
        return new double[]{ vcx + x1 * s, vcy - z2 * s, depth };
    }

    void addWorldPoly(List<Prim> prims, Vec3[] pts, Color c) {
        int m = pts.length;
        int[] xs = new int[m], ys = new int[m];
        double d = 0;
        for (int i = 0; i < m; i++) {
            double[] pr = project(pts[i].x, pts[i].y, pts[i].z);
            if (pr == null) return;
            xs[i] = (int) Math.round(pr[0]); ys[i] = (int) Math.round(pr[1]); d += pr[2];
        }
        prims.add(new Prim(d / m, xs, ys, c));
    }

    void setDrones(int count) {
        drones.clear();
        for (int i = 0; i < count; i++) {
            Drone d = new Drone(i, Drone.PALETTE[i % Drone.PALETTE.length]);
            double a = 2 * Math.PI * i / Math.max(1, count);
            double R = 14;
            d.startXY[0] = -80 + R * Math.cos(a);
            d.startXY[1] = -80 + R * Math.sin(a);
            d.goalXY[0]  =  80 + R * Math.cos(a + Math.PI);
            d.goalXY[1]  =  80 + R * Math.sin(a + Math.PI);
            d.animPhase  = i / (double) Math.max(1, count);
            drones.add(d);
        }
        replan();
    }

    void regenerate() {
        terrain.randomize(System.nanoTime());
        generateThreats();
        replan();
    }

    void generateThreats() {
        threats.clear();
        Random rnd = new Random(System.nanoTime());
        double lim = terrain.extent * 0.34;
        for (int k = 0; k < mountainCount; k++) {
            MountainThreat m = new MountainThreat();
            placeRandom(m, rnd, lim, 42);
            m.baseZ   = terrain.heightAt(m.x, m.y) - 14;
            m.H       = m.baseZ + 48 + rnd.nextDouble() * 34;
            m.tanTheta = 1.1 + rnd.nextDouble() * 1.1;
            threats.add(m);
        }
        for (int k = 0; k < radarCount; k++) {
            RadarThreat r = new RadarThreat();
            placeRandom(r, rnd, lim, 42);
            r.z = terrain.heightAt(r.x, r.y) + 10;
            r.radius = 32 + rnd.nextDouble() * 18;
            threats.add(r);
        }
    }

    private void placeRandom(Threat t, Random rnd, double lim, double minSep) {
        int tries = 0; boolean ok;
        do {
            t.x = (rnd.nextDouble() - 0.5) * 2 * lim;
            t.y = (rnd.nextDouble() - 0.5) * 2 * lim;
            ok = true;
            for (Threat o : threats) if (Math.hypot(o.x - t.x, o.y - t.y) < minSep) { ok = false; break; }
        } while (!ok && ++tries < 60);
    }

    void replan() {
        for (Drone d : drones) {
            long t0 = System.currentTimeMillis();
            List<Vec3> p = AStarPlanner.plan(terrain, threats, CLEARANCE, d.startXY, d.goalXY);
            if (p.size() > 3) {
                PSO pso = new PSO(terrain, threats, p, CLEARANCE, 22, 40, d.id * 7717L + 1);
                for (int it = 0; it < pso.ITER; it++) pso.step();
                p = pso.bestPath();
            }
            d.path = p;
            d.dense = densify(p, 380);
            d.length = 0; d.risk = 0;
            for (int i = 1; i < d.dense.size(); i++) d.length += d.dense.get(i - 1).dist(d.dense.get(i));
            for (Vec3 v : d.dense) for (Threat th : threats) d.risk += Math.min(60.0, th.cost(v.x, v.y, v.z));
            d.planTimeMs = System.currentTimeMillis() - t0;
        }
        animT = 0;
        repaint();
    }

    List<Vec3> densify(List<Vec3> in, int M) {
        List<Vec3> r = AStarPlanner.resample(in, M);
        List<Vec3> out = new ArrayList<>(r.size());
        for (Vec3 v : r) out.add(new Vec3(v.x, v.y, terrain.heightAt(v.x, v.y) + CLEARANCE));
        return out;
    }

    void startTraining(int droneId) {
        if (trainingActive) return;
        if (droneId < 0 || droneId >= drones.size()) droneId = 0;
        Drone d = drones.get(droneId);
        List<Vec3> init = AStarPlanner.plan(terrain, threats, CLEARANCE, d.startXY, d.goalXY);
        PSO pso = new PSO(terrain, threats, init, CLEARANCE, 28, 60, System.nanoTime());
        trainingRunner = new TrainingRunner(this, pso, droneId, trainingDelay);
        trainingRunner.start();
    }

    void stopTraining() {
        if (trainingRunner != null) trainingRunner.stopTraining();
        trainingActive = false;
        repaint();
    }

    void applyTrainingResult(PSO pso) {
        if (pso == null) return;
        Drone d = drones.get(Math.max(0, Math.min(drones.size() - 1, trainingDroneId)));
        d.path = pso.bestPath();
        d.dense = densify(d.path, 380);
        d.length = 0; d.risk = 0;
        for (int i = 1; i < d.dense.size(); i++) d.length += d.dense.get(i - 1).dist(d.dense.get(i));
        for (Vec3 v : d.dense) for (Threat th : threats) d.risk += Math.min(60.0, th.cost(v.x, v.y, v.z));
        d.planTimeMs = pso.ITER;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        int W = getWidth(), H = getHeight();
        if (W < 20 || H < 20) return;

        setupProjection();

        g.setPaint(new GradientPaint(0, 0, new Color(22, 32, 55), 0, H, new Color(150, 172, 196)));
        g.fillRect(0, 0, W, H);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        List<Prim> prims = new ArrayList<>(terrain.n * terrain.n + 800);
        buildTerrain(prims);
        if (showThreats) for (Threat t : threats) if (t instanceof MountainThreat) emitMountain((MountainThreat) t, prims);

        prims.sort((p, q) -> Double.compare(q.depth, p.depth));
        g.setStroke(new BasicStroke(1f));
        for (Prim p : prims) {
            g.setColor(p.color);
            g.fillPolygon(p.xs, p.ys, p.xs.length);
            g.drawPolygon(p.xs, p.ys, p.xs.length);
        }

        if (showThreats) for (Threat t : threats) if (t instanceof RadarThreat) drawRadar(g, (RadarThreat) t);

        if (!trainingActive && showPath) {
            for (Drone d : drones) drawDronePath(g, d);
        }

        drawStartGoalMarkers(g);

        if (trainingActive && trainingParticles != null) {
            drawTrainingParticles(g);
            if (trainingBestPath != null) drawTrainingBest(g);
        }

        if (!trainingActive && animate && showSwarm && showPath) {
            for (Drone d : drones) drawUav(g, d, (animT + d.animPhase) % 1.0);
        }

        drawLegend(g);
        drawStats(g, W, H);
        if (trainingActive) drawCostChart(g, W, H);
    }

    private void buildTerrain(List<Prim> prims) {
        int n = terrain.n, NV = n + 1;
        double half = terrain.extent / 2, step = terrain.extent / n;
        double range = Math.max(1e-6, terrain.maxH - terrain.minH);

        float[] sx = new float[NV * NV], sy = new float[NV * NV], sd = new float[NV * NV];
        for (int i = 0; i < NV; i++) for (int j = 0; j < NV; j++) {
            double x = -half + i * step, y = -half + j * step;
            double[] pr = project(x, y, terrain.h[i][j]);
            int k = i * NV + j;
            if (pr == null) { sd[k] = -1; continue; }
            sx[k] = (float) pr[0]; sy[k] = (float) pr[1]; sd[k] = (float) pr[2];
        }
        int W = getWidth(), H = getHeight();

        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            int a = i * NV + j, b = (i + 1) * NV + j, c = (i + 1) * NV + (j + 1), d = i * NV + (j + 1);
            if (sd[a] < 0 || sd[b] < 0 || sd[c] < 0 || sd[d] < 0) continue;
            int minX = (int) Math.min(Math.min(sx[a], sx[b]), Math.min(sx[c], sx[d]));
            int maxX = (int) Math.max(Math.max(sx[a], sx[b]), Math.max(sx[c], sx[d]));
            int minY = (int) Math.min(Math.min(sy[a], sy[b]), Math.min(sy[c], sy[d]));
            int maxY = (int) Math.max(Math.max(sy[a], sy[b]), Math.max(sy[c], sy[d]));
            if (maxX < -8 || minX > W + 8 || maxY < -8 || minY > H + 8) continue;

            double dep = (sd[a] + sd[b] + sd[c] + sd[d]) * 0.25;
            double hAvg = (terrain.h[i][j] + terrain.h[i + 1][j] + terrain.h[i + 1][j + 1] + terrain.h[i][j + 1]) * 0.25;
            double tt = (hAvg - terrain.minH) / range;
            double diff = Math.max(0, terrain.nx[i][j] * LIGHT.x + terrain.ny[i][j] * LIGHT.y + terrain.nz[i][j] * LIGHT.z);
            double shade = 0.34 + 0.78 * diff;
            double fog = Math.max(0, Math.min(0.55, (dep - dist) / (2.4 * dist)));

            int[] rgb = ramp(tt);
            int r  = (int) (clamp255(rgb[0] * shade) * (1 - fog) + FOG.getRed()   * fog);
            int gg = (int) (clamp255(rgb[1] * shade) * (1 - fog) + FOG.getGreen() * fog);
            int bb = (int) (clamp255(rgb[2] * shade) * (1 - fog) + FOG.getBlue()  * fog);

            prims.add(new Prim(dep,
                    new int[]{(int) sx[a], (int) sx[b], (int) sx[c], (int) sx[d]},
                    new int[]{(int) sy[a], (int) sy[b], (int) sy[c], (int) sy[d]},
                    new Color(clamp255(r), clamp255(gg), clamp255(bb))));
        }
    }

    private static int clamp255(double v) { int i = (int) Math.round(v); return i < 0 ? 0 : (i > 255 ? 255 : i); }

    private static int[] ramp(double t) {
        t = Math.max(0, Math.min(1, t));
        double r, g, b;
        if (t < 0.25)     { double u = t / 0.25;          r = 34 + 30 * u;  g = 78 + 44 * u;  b = 48 + 18 * u; }
        else if (t < 0.50){ double u = (t - 0.25) / 0.25; r = 64 + 56 * u;  g = 122 + 12 * u; b = 66 + 12 * u; }
        else if (t < 0.72){ double u = (t - 0.50) / 0.22; r = 120 + 38 * u; g = 134 - 4 * u;  b = 78 + 18 * u; }
        else if (t < 0.88){ double u = (t - 0.72) / 0.16; r = 158 + 20 * u; g = 130 + 44 * u; b = 96 + 76 * u; }
        else              { double u = (t - 0.88) / 0.12; r = 178 + 74 * u; g = 174 + 80 * u; b = 172 + 84 * u; }
        return new int[]{(int) r, (int) g, (int) b};
    }

    private void emitMountain(MountainThreat m, List<Prim> prims) {
        final int LAT = 9, LON = 22;
        for (int la = 0; la < LAT; la++) {
            double t0 = (double) la / LAT, t1 = (double) (la + 1) / LAT;
            double z0 = m.baseZ + (m.H - m.baseZ) * t0, z1 = m.baseZ + (m.H - m.baseZ) * t1;
            double r0 = (m.H - z0) / m.tanTheta, r1 = (m.H - z1) / m.tanTheta;
            for (int lo = 0; lo < LON; lo++) {
                double a0 = 2 * Math.PI * lo / LON, a1 = 2 * Math.PI * (lo + 1) / LON;
                Vec3 p0 = new Vec3(m.x + r0 * Math.cos(a0), m.y + r0 * Math.sin(a0), z0);
                Vec3 p1 = new Vec3(m.x + r0 * Math.cos(a1), m.y + r0 * Math.sin(a1), z0);
                Vec3 p2 = new Vec3(m.x + r1 * Math.cos(a1), m.y + r1 * Math.sin(a1), z1);
                Vec3 p3 = new Vec3(m.x + r1 * Math.cos(a0), m.y + r1 * Math.sin(a0), z1);
                Vec3 nrm = p1.sub(p0).cross(p3.sub(p0)).norm();
                if (nrm.z < 0) nrm = nrm.mul(-1);
                double diff = Math.max(0, nrm.dot(LIGHT));
                double shade = 0.35 + 0.75 * diff;
                double zc = (z0 + z1) * 0.5;
                double tt = (zc - m.baseZ) / Math.max(1e-6, m.H - m.baseZ);
                int r = (int) (88 + 120 * tt), gg = (int) (80 + 122 * tt), bb = (int) (72 + 126 * tt);
                if (tt > 0.82) { r = 236; gg = 240; bb = 248; }
                addWorldPoly(prims, new Vec3[]{p0, p1, p2, p3},
                        new Color(clamp255(r * shade), clamp255(gg * shade), clamp255(bb * shade)));
            }
        }
    }

    private void drawRadar(Graphics2D g, RadarThreat r) {
        double[] base = project(r.x, r.y, terrain.heightAt(r.x, r.y));
        double[] top  = project(r.x, r.y, r.z);
        if (base != null && top != null) {
            g.setColor(new Color(220, 230, 240, 190));
            g.setStroke(new BasicStroke(2.5f));
            g.drawLine((int) base[0], (int) base[1], (int) top[0], (int) top[1]);
        }
        if (top != null) { g.setColor(new Color(255, 90, 90)); g.fillOval((int) top[0] - 5, (int) top[1] - 5, 10, 10); }
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(95, 220, 255, 65));
        for (int la = -2; la <= 2; la++) {
            double lat = la * Math.PI / 6.0;
            drawCircle(g, r.x, r.y, r.z + r.radius * Math.sin(lat), r.radius * Math.cos(lat));
        }
        for (int me = 0; me < 8; me++) drawMeridian(g, r, me * Math.PI / 8.0);
    }

    private void drawCircle(Graphics2D g, double cx, double cy, double z, double rr) {
        int SEG = 40; int[] xs = new int[SEG + 1], ys = new int[SEG + 1]; int cnt = 0;
        for (int s = 0; s <= SEG; s++) {
            double a = 2 * Math.PI * s / SEG;
            double[] pr = project(cx + rr * Math.cos(a), cy + rr * Math.sin(a), z);
            if (pr == null) break;
            xs[cnt] = (int) pr[0]; ys[cnt] = (int) pr[1]; cnt++;
        }
        if (cnt > 1) g.drawPolyline(xs, ys, cnt);
    }

    private void drawMeridian(Graphics2D g, RadarThreat r, double ang) {
        int SEG = 24; int[] xs = new int[SEG + 1], ys = new int[SEG + 1]; int cnt = 0;
        for (int s = 0; s <= SEG; s++) {
            double t = Math.PI * s / SEG;
            double rr = r.radius * Math.cos(t), zz = r.z + r.radius * Math.sin(t);
            double[] pr = project(r.x + rr * Math.cos(ang), r.y + rr * Math.sin(ang), zz);
            if (pr == null) break;
            xs[cnt] = (int) pr[0]; ys[cnt] = (int) pr[1]; cnt++;
        }
        if (cnt > 1) g.drawPolyline(xs, ys, cnt);
    }

    /* ---------------------- маршруты дронов ---------------------- */
    private void drawDronePath(Graphics2D g, Drone d) {
        int n = d.dense.size();
        if (n < 2) return;
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            Vec3 v = d.dense.get(i);
            double[] pr = project(v.x, v.y, v.z);
            if (pr == null) return;
            xs[i] = (int) pr[0]; ys[i] = (int) pr[1];
        }

        g.setColor(new Color(d.color.getRed(), d.color.getGreen(), d.color.getBlue(), 40));
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < n; i += 16) {
            Vec3 v = d.dense.get(i);
            double[] a = project(v.x, v.y, v.z);
            double[] b = project(v.x, v.y, terrain.heightAt(v.x, v.y));
            if (a != null && b != null) g.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]);
        }

        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(d.color.getRed(), d.color.getGreen(), d.color.getBlue(), 55));
        g.drawPolyline(xs, ys, n);

        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(d.color);
        g.drawPolyline(xs, ys, n);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawStartGoalMarkers(Graphics2D g) {
        for (Drone d : drones) {
            double[] s = project(d.startXY[0], d.startXY[1], terrain.heightAt(d.startXY[0], d.startXY[1]) + 3);
            double[] e = project(d.goalXY[0],  d.goalXY[1],  terrain.heightAt(d.goalXY[0],  d.goalXY[1])  + 3);
            if (s != null) {
                g.setColor(new Color(d.color.getRed(), d.color.getGreen(), d.color.getBlue(), 200));
                g.fillOval((int) s[0] - 6, (int) s[1] - 6, 12, 12);
                g.setColor(Color.WHITE); g.drawOval((int) s[0] - 6, (int) s[1] - 6, 12, 12);
            }
            if (e != null) {
                g.setColor(d.color);
                int[] xs = {(int) e[0], (int) e[0] - 6, (int) e[0] + 6};
                int[] ys = {(int) e[1] - 7, (int) e[1] + 5, (int) e[1] + 5};
                g.fillPolygon(xs, ys, 3);
                g.setColor(Color.WHITE); g.drawPolygon(xs, ys, 3);
            }
        }
    }

    private void drawUav(Graphics2D g, Drone d, double t) {
        int n = d.dense.size();
        if (n < 2) return;
        int idx = (int) (t * (n - 1));
        idx = Math.max(0, Math.min(n - 2, idx));
        Vec3 p = d.dense.get(idx), p2 = d.dense.get(idx + 1);
        double[] a = project(p.x, p.y, p.z), b = project(p2.x, p2.y, p2.z);
        if (a == null || b == null) return;
        double ang = Math.atan2(b[1] - a[1], b[0] - a[0]);
        int cx = (int) a[0], cy = (int) a[1];

        g.setColor(new Color(d.color.getRed(), d.color.getGreen(), d.color.getBlue(), 60));
        g.fillOval(cx - 14, cy - 14, 28, 28);

        int L = 14;
        int[] xs = { cx + (int) (Math.cos(ang) * L),
                cx + (int) (Math.cos(ang + 2.5) * L * 0.8),
                cx + (int) (Math.cos(ang - 2.5) * L * 0.8) };
        int[] ys = { cy + (int) (Math.sin(ang) * L),
                cy + (int) (Math.sin(ang + 2.5) * L * 0.8),
                cy + (int) (Math.sin(ang - 2.5) * L * 0.8) };
        g.setColor(d.color); g.fillPolygon(xs, ys, 3);
        g.setColor(new Color(30, 30, 30)); g.drawPolygon(xs, ys, 3);

        double[] gl = project(p.x, p.y, terrain.heightAt(p.x, p.y));
        if (gl != null) {
            g.setColor(new Color(255, 255, 255, 110));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
            g.drawLine(cx, cy, (int) gl[0], (int) gl[1]);
            g.setStroke(new BasicStroke(1f));
        }
    }

    private void drawTrainingParticles(Graphics2D g) {
        double[][] ps = trainingParticles;
        if (ps == null) return;

        for (double[] p : ps) {
            List<Vec3> path3d = particleToPath(p);
            g.setColor(new Color(120, 200, 255, 55));
            g.setStroke(new BasicStroke(1f));
            drawPolyline3D(g, path3d);
        }
        if (trainingBestPath != null) {
            g.setColor(new Color(255, 245, 120, 200));
            g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawPolyline3D(g, trainingBestPath);
        }
    }

    private void drawTrainingBest(Graphics2D g) {
        if (trainingBestPath == null) return;
        Drone d = drones.get(Math.max(0, Math.min(drones.size() - 1, trainingDroneId)));
        double[] s = project(d.startXY[0], d.startXY[1], terrain.heightAt(d.startXY[0], d.startXY[1]) + 3);
        if (s != null) {
            g.setColor(new Color(255, 245, 120, 220));
            g.fillOval((int) s[0] - 8, (int) s[1] - 8, 16, 16);
        }
    }

    private List<Vec3> particleToPath(double[] v) {
        int D = v.length / 2;
        List<Vec3> out = new ArrayList<>(D + 2);
        Drone d = drones.get(Math.max(0, Math.min(drones.size() - 1, trainingDroneId)));
        double z0 = terrain.heightAt(d.startXY[0], d.startXY[1]) + CLEARANCE;
        out.add(new Vec3(d.startXY[0], d.startXY[1], z0));
        for (int k = 0; k < D; k++) {
            double x = v[2 * k], y = v[2 * k + 1];
            out.add(new Vec3(x, y, terrain.heightAt(x, y) + CLEARANCE));
        }
        double z1 = terrain.heightAt(d.goalXY[0], d.goalXY[1]) + CLEARANCE;
        out.add(new Vec3(d.goalXY[0], d.goalXY[1], z1));
        return out;
    }

    private void drawPolyline3D(Graphics2D g, List<Vec3> path) {
        int n = path.size();
        if (n < 2) return;
        int[] xs = new int[n], ys = new int[n];
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            Vec3 v = path.get(i);
            double[] pr = project(v.x, v.y, v.z);
            if (pr == null) break;
            xs[cnt] = (int) pr[0]; ys[cnt] = (int) pr[1]; cnt++;
        }
        if (cnt > 1) g.drawPolyline(xs, ys, cnt);
    }

    private void drawLegend(Graphics2D g) {
        int y = 12;
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        for (Drone d : drones) {
            g.setColor(new Color(0, 0, 0, 130));
            g.fillRoundRect(12, y, 185, 20, 8, 8);
            g.setColor(d.color);
            g.fillOval(20, y + 6, 10, 10);
            g.setColor(new Color(230, 235, 245));
            g.drawString(String.format("БПЛА-%d   %.0f ед.   риск %.0f", d.id + 1, d.length, d.risk), 38, y + 15);
            y += 24;
        }
    }

    private void drawStats(Graphics2D g, int W, int H) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(12, H - 92, 340, 82, 12, 12);
        g.setColor(new Color(200, 215, 235));
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString("R — сброс камеры;  ПРОБЕЛ — новый ландшафт", 24, H - 60);
        if (trainingActive)
            g.drawString("ОБУЧЕНИЕ: итерация " + trainingIteration + " / " + trainingTotal, 24, H - 28);
        else
            g.drawString("Дронов: " + drones.size() + ";   Угроз: " + threats.size(), 24, H - 28);
    }

    private void drawCostChart(Graphics2D g, int W, int H) {
        int cw = 240, ch = 120;
        int cx = W - cw - 16, cy = 16;

        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(cx, cy, cw, ch, 12, 12);
        g.setColor(new Color(90, 110, 140));
        g.drawRoundRect(cx, cy, cw, ch, 12, 12);

        g.setColor(new Color(220, 230, 245));
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("Стоимость лучшего пути", cx + 10, cy + 16);

        List<Double> cs;
        synchronized (trainingCosts) { cs = new ArrayList<>(trainingCosts); }
        if (cs.isEmpty()) { g.drawString("нет данных", cx + 10, cy + 40); return; }

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double v : cs) { if (v < min) min = v; if (v > max) max = v; }
        double range = Math.max(1e-6, max - min);

        int px0 = cx + 10, py0 = cy + 26, pw = cw - 20, ph = ch - 44;

        g.setColor(new Color(255, 255, 255, 40));
        for (int i = 0; i <= 4; i++) {
            int yy = py0 + ph * i / 4;
            g.drawLine(px0, yy, px0 + pw, yy);
        }

        g.setColor(new Color(255, 220, 80));
        g.setStroke(new BasicStroke(2f));
        int n = cs.size();
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = px0 + (int) ((double) i / Math.max(1, n - 1) * pw);
            ys[i] = py0 + ph - (int) ((cs.get(i) - min) / range * ph);
        }
        if (n > 1) g.drawPolyline(xs, ys, n);
        g.setStroke(new BasicStroke(1f));

        g.setColor(new Color(190, 210, 235));
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.drawString("min " + String.format("%.1f", min), cx + 8, cy + ch - 6);
        g.drawString("max " + String.format("%.1f", max), cx + cw - 70, cy + ch - 6);
    }

    public List<Drone> getDrones() { return drones; }
}