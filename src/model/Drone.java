package model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Drone {
    public final int id;
    public final Color color;
    public double[] startXY = {-80, -80};
    public double[] goalXY  = { 80,  80};
    public List<Vec3> path  = new ArrayList<>();
    public List<Vec3> dense = new ArrayList<>();
    public double length, risk;
    public long planTimeMs;
    public double animPhase = 0;

    public Drone(int id, Color c) { this.id = id; this.color = c; }

    public static final Color[] PALETTE = {
            new Color(255, 228, 70),   // жёлтый
            new Color( 80, 220, 255),  // голубой
            new Color(255, 120, 210),  // розовый
            new Color(140, 255, 120),  // лайм
            new Color(255, 160, 80),   // оранжевый
            new Color(180, 140, 255),  // фиолетовый
            new Color(120, 255, 220),  // бирюзовый
            new Color(255, 100, 100)   // красный
    };
}