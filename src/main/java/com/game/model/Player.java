package com.game.model;

public class Player {
    private String id;
    private int x;
    private int y;
    private String zone;

    // Constructeur vide : indispensable pour Jackson (la bibliothèque JSON)
    public Player() {}

    // Constructeur pour créer un joueur facilement
    public Player(String id, int x, int y, String zone) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.zone = zone;
    }

    // Getters et Setters : permettent d'accéder aux données privées
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
}