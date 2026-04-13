package com.game.worker;

public class PendingTransfer {
    private final String playerId;
    private final String sourceZone;
    private final String targetZone;
    private final int oldX;
    private final int oldY;
    private final int newX;
    private final int newY;
    private final long timestamp;

    public PendingTransfer(String playerId, String sourceZone, String targetZone,
                           int oldX, int oldY, int newX, int newY, long timestamp) {
        this.playerId = playerId;
        this.sourceZone = sourceZone;
        this.targetZone = targetZone;
        this.oldX = oldX;
        this.oldY = oldY;
        this.newX = newX;
        this.newY = newY;
        this.timestamp = timestamp;
    }

    public String getPlayerId() { return playerId; }
    public String getSourceZone() { return sourceZone; }
    public String getTargetZone() { return targetZone; }
    public int getOldX() { return oldX; }
    public int getOldY() { return oldY; }
    public int getNewX() { return newX; }
    public int getNewY() { return newY; }
    public long getTimestamp() { return timestamp; }
}