package com.game.ui;

public class PlayerView {
    private final String playerId;
    private final int x;
    private final int y;
    private final String zoneId;

    public PlayerView(String playerId, int x, int y, String zoneId) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.zoneId = zoneId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getZoneId() {
        return zoneId;
    }
}