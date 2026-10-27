package model;

public abstract class Threat {
    public double x, y;
    public abstract double cost(double px, double py, double pz);
}