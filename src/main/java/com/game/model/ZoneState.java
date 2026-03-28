package com.game.model;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ZoneState {
    private final String zoneId;
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final String[][] grid; 

    public ZoneState(String zoneId, int width, int height) {
        this.zoneId = zoneId;
        this.grid = new String[width][height]; 
    }

    public synchronized boolean updatePosition(Player p, int newX, int newY, int minX, int minY) {
        int relX = newX - minX;
        int relY = newY - minY;

        // Sécurité pour ne pas sortir du tableau
        if (relX < 0 || relX >= grid.length || relY < 0 || relY >= grid[0].length) {
            return false; 
        }

        if (grid[relX][relY] == null || grid[relX][relY].equals(p.getId())) {
            // Libérer l'ancienne place 
            grid[p.getX() - minX][p.getY() - minY] = null;
            
            grid[relX][relY] = p.getId();
            p.setX(newX);
            p.setY(newY);
            this.players.put(p.getId(), p);
            return true;
        }
        return false;
    }

    public Map<String, Player> getPlayers() {
    return this.players;
    }
}