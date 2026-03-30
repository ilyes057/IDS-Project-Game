package com.game.worker;

import java.util.Objects;

public class TargetCell {
    private final int x;
    private final int y;

    public TargetCell(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TargetCell)) return false;
        TargetCell that = (TargetCell) o;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}