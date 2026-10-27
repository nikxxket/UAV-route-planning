package view;

import java.awt.Color;

class Prim {
    final double depth;
    final int[] xs, ys;
    final Color color;

    Prim(double depth, int[] xs, int[] ys, Color color) {
        this.depth = depth; this.xs = xs; this.ys = ys; this.color = color;
    }
}