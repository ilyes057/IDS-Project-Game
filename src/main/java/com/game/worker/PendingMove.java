package com.game.worker;

import com.game.model.Direction;

public class PendingMove {
    private final String playerId;
    private final Direction direction;
    private final long timestamp;

    public PendingMove(String playerId, Direction direction, long timestamp) {
        this.playerId = playerId;
        this.direction = direction;
        this.timestamp = timestamp;
    }

    public String getPlayerId() {
        return playerId;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getTimestamp() {
        return timestamp;
    }
}