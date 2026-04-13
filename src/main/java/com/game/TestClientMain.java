package com.game;

import com.game.client.GameClient;

import java.util.Scanner;

public class TestClientMain {
    public static void main(String[] args) {
        String initialZone = (args.length > 0) ? args[0].toUpperCase() : "A";
        String playerId = (args.length > 1) ? args[1] : "P1";

        try {
            GameClient client = new GameClient(initialZone, playerId);

            Scanner scanner = new Scanner(System.in);

            System.out.println("=== Test Client ===");
            System.out.println("Player : " + playerId);
            System.out.println("Zone initiale : " + initialZone);
            System.out.println("Commandes : join, z, q, s, d, up, down, left, right, exit");

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim().toLowerCase();

                if ("exit".equals(input)) {
                    client.close();
                    break;
                }

                if ("join".equals(input)) {
                    client.sendJoin();
                    continue;
                }

                String direction = mapInputToDirection(input);
                if (direction == null) {
                    System.out.println("Commande inconnue");
                    continue;
                }

                client.sendMove(direction);
                System.out.println("[CLIENT] zone courante = " + client.getCurrentZone());
            }

        } catch (Exception e) {
            System.err.println("Erreur dans TestClientMain");
            e.printStackTrace();
        }
    }

    private static String mapInputToDirection(String input) {
        return switch (input) {
            case "z", "up" -> "UP";
            case "s", "down" -> "DOWN";
            case "q", "left" -> "LEFT";
            case "d", "right" -> "RIGHT";
            default -> null;
        };
    }
}