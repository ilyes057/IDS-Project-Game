package com.game.model;

import java.util.HashMap;
import java.util.Map;

public class ZoneConfig {
    private final String zoneId;
    private final int minX, maxX;
    private final int minY, maxY;
    private final Map<Direction, String> neighbors = new HashMap<>();

    public ZoneConfig(String zoneId, int minX, int maxX, int minY, int maxY) {
        this.zoneId = zoneId;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    // Méthode pour ajouter un voisin 
    public void addNeighbor(Direction dir, String neighborZoneId) {
        neighbors.put(dir, neighborZoneId);
    }

    // Vérifie si un joueur est sorti de la zone
    public boolean isOutside(int x, int y) {
        return x < minX || x >= maxX || y < minY || y >= maxY;
    }

    // Getters
    public String getZoneId() { return zoneId; }
    public Map<Direction, String> getNeighbors() { return neighbors; }
    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
}