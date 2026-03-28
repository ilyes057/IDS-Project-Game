package com.game.model;

public class WorldTopology {
    public static ZoneConfig getZoneConfig(String id) {
        switch(id) {
            case "A":
                ZoneConfig a = new ZoneConfig("A", 0, 10, 0, 10);
                a.addNeighbor(Direction.RIGHT, "B");
                a.addNeighbor(Direction.DOWN, "C");
                return a;
            case "B":
                ZoneConfig b = new ZoneConfig("B", 10, 20, 0, 10);
                b.addNeighbor(Direction.LEFT, "A");
                b.addNeighbor(Direction.DOWN, "D");
                return b;
            case "C":
                ZoneConfig c = new ZoneConfig("C", 0, 10, 10, 20);
                c.addNeighbor(Direction.UP, "A");
                c.addNeighbor(Direction.RIGHT, "D");
                return c;
            case "D":
                ZoneConfig d = new ZoneConfig("D", 10, 20, 10, 20);
                d.addNeighbor(Direction.UP, "B");
                d.addNeighbor(Direction.LEFT, "C");
                return d;
            default:
                throw new IllegalArgumentException("Zone inconnue");
        }
    }
}