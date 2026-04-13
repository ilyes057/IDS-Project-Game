package com.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.model.GameMessage;
import com.game.network.RabbitConnector;
import com.rabbitmq.client.Channel;

import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public class TestClientMain {
    public static void main(String[] args) {
        String initialZone = (args.length > 0) ? args[0].toUpperCase() : "A";
        String playerId = (args.length > 1) ? args[1] : "P1";

        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<String> currentZone = new AtomicReference<>(initialZone);

        try {
            Channel publishChannel = RabbitConnector.createChannel();
            Channel snapshotChannel = RabbitConnector.createChannel();

            startSnapshotListener(snapshotChannel, mapper, playerId, currentZone);

            System.out.println("=== Test Client ===");
            System.out.println("Zone initiale : " + currentZone.get());
            System.out.println("Player        : " + playerId);
            System.out.println();
            System.out.println("Commandes :");
            System.out.println("  join");
            System.out.println("  up / down / left / right");
            System.out.println("  z / q / s / d");
            System.out.println("  zone        -> affiche la zone courante");
            System.out.println("  exit");
            System.out.println();

            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim().toLowerCase();

                if (input.equals("exit")) {
                    break;
                }

                if (input.equals("zone")) {
                    System.out.println("[CLIENT] Zone courante : " + currentZone.get());
                    continue;
                }

                String routingKey = "player.input." + currentZone.get();

                if (input.equals("join")) {
                    GameMessage joinMsg = new GameMessage("JOIN", currentZone.get());
                    joinMsg.setTargetZone(currentZone.get());
                    joinMsg.getPayload().put("playerId", playerId);

                    byte[] body = mapper.writeValueAsBytes(joinMsg);
                    publishChannel.basicPublish(RabbitConnector.EXCHANGE_NAME, routingKey, null, body);

                    System.out.println("[SEND] JOIN envoyé pour " + playerId + " vers " + currentZone.get());
                    continue;
                }

                String direction = mapInputToDirection(input);
                if (direction == null) {
                    System.out.println("Commande inconnue.");
                    continue;
                }

                GameMessage moveMsg = new GameMessage("MOVE_INTENT", currentZone.get());
                moveMsg.setTargetZone(currentZone.get());
                moveMsg.getPayload().put("playerId", playerId);
                moveMsg.getPayload().put("direction", direction);

                byte[] body = mapper.writeValueAsBytes(moveMsg);
                publishChannel.basicPublish(RabbitConnector.EXCHANGE_NAME, routingKey, null, body);

                System.out.println("[SEND] MOVE_INTENT " + direction + " pour " + playerId
                        + " vers " + currentZone.get());
            }

            publishChannel.close();
            snapshotChannel.close();
            System.out.println("Client fermé.");

        } catch (Exception e) {
            System.err.println("Erreur dans TestClientMain : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startSnapshotListener(Channel snapshotChannel,
                                              ObjectMapper mapper,
                                              String playerId,
                                              AtomicReference<String> currentZone) throws Exception {
        String queueName = snapshotChannel.queueDeclare("", false, true, true, null).getQueue();
        snapshotChannel.queueBind(queueName, RabbitConnector.EXCHANGE_NAME, "zone.snapshot.*");

        snapshotChannel.basicConsume(queueName, true, (consumerTag, delivery) -> {
            try {
                GameMessage msg = mapper.readValue(delivery.getBody(), GameMessage.class);

                if (!"ZONE_SNAPSHOT".equalsIgnoreCase(msg.getType())) {
                    return;
                }

                Object playersObj = msg.getPayload().get("players");
                if (!(playersObj instanceof List<?>)) {
                    return;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> players = (List<Map<String, Object>>) playersObj;

                for (Map<String, Object> playerData : players) {
                    Object idObj = playerData.get("playerId");
                    if (idObj == null) {
                        continue;
                    }

                    if (playerId.equals(idObj.toString())) {
                        String snapshotZone = null;

                        Object zoneObj = playerData.get("zoneId");
                        if (zoneObj != null) {
                            snapshotZone = zoneObj.toString();
                        } else if (msg.getPayload().get("zoneId") != null) {
                            snapshotZone = msg.getPayload().get("zoneId").toString();
                        }

                        if (snapshotZone != null && !snapshotZone.equals(currentZone.get())) {
                            String oldZone = currentZone.getAndSet(snapshotZone);
                            System.out.println("[CLIENT] Changement de zone détecté : "
                                    + oldZone + " -> " + snapshotZone);
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                System.err.println("[CLIENT] Erreur lecture snapshot : " + e.getMessage());
            }
        }, consumerTag -> {});
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