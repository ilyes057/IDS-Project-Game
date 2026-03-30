package com.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.model.GameMessage;
import com.game.network.RabbitConnector;
import com.rabbitmq.client.Channel;

import java.util.Scanner;

public class TestClientMain {
    public static void main(String[] args) {
        String zoneId = (args.length > 0) ? args[0].toUpperCase() : "A";
        String playerId = (args.length > 1) ? args[1] : "P1";

        ObjectMapper mapper = new ObjectMapper();

        try {
            Channel channel = RabbitConnector.createChannel();
            String routingKey = "player.input." + zoneId;

            System.out.println("=== Test Client ===");
            System.out.println("Zone   : " + zoneId);
            System.out.println("Player : " + playerId);
            System.out.println();
            System.out.println("Commandes :");
            System.out.println("  join");
            System.out.println("  up / down / left / right");
            System.out.println("  z / q / s / d");
            System.out.println("  exit");
            System.out.println();

            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim().toLowerCase();

                if (input.equals("exit")) {
                    break;
                }

                if (input.equals("join")) {
                    GameMessage joinMsg = new GameMessage("JOIN", zoneId);
                    joinMsg.getPayload().put("playerId", playerId);

                    byte[] body = mapper.writeValueAsBytes(joinMsg);
                    channel.basicPublish(RabbitConnector.EXCHANGE_NAME, routingKey, null, body);

                    System.out.println("[SEND] JOIN envoyé pour " + playerId);
                    continue;
                }

                String direction = mapInputToDirection(input);
                if (direction == null) {
                    System.out.println("Commande inconnue.");
                    continue;
                }

                GameMessage moveMsg = new GameMessage("MOVE_INTENT", zoneId);
                moveMsg.getPayload().put("playerId", playerId);
                moveMsg.getPayload().put("direction", direction);

                byte[] body = mapper.writeValueAsBytes(moveMsg);
                channel.basicPublish(RabbitConnector.EXCHANGE_NAME, routingKey, null, body);

                System.out.println("[SEND] MOVE_INTENT " + direction + " pour " + playerId);
            }

            channel.close();
            System.out.println("Client fermé.");

        } catch (Exception e) {
            System.err.println("Erreur dans TestClientMain : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String mapInputToDirection(String input) {
        switch (input) {
            case "up":
            case "z":
                return "UP";
            case "down":
            case "s":
                return "DOWN";
            case "left":
            case "q":
                return "LEFT";
            case "right":
            case "d":
                return "RIGHT";
            default:
                return null;
        }
    }
}