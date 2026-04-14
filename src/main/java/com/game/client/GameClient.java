package com.game.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.model.GameMessage;
import com.game.network.RabbitConnector;
import com.rabbitmq.client.Channel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class GameClient {
    private final String playerId;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Channel inputChannel;
    private final Channel snapshotChannel;
    private final Channel eventChannel;

    private final AtomicReference<String> currentZone;

    public GameClient(String initialZone, String playerId) throws Exception {
        this.playerId = playerId;
        this.currentZone = new AtomicReference<>(initialZone);

        this.inputChannel = RabbitConnector.createChannel();
        this.snapshotChannel = RabbitConnector.createChannel();
        this.eventChannel = RabbitConnector.createChannel();
        startEventListener();
        startSnapshotListener();
    }

    public String getCurrentZone() {
        return currentZone.get();
    }

    public void sendJoin() throws Exception {
        GameMessage joinMsg = new GameMessage("JOIN", currentZone.get());
        joinMsg.getPayload().put("playerId", playerId);

        String routingKey = "player.input." + currentZone.get();
        byte[] body = mapper.writeValueAsBytes(joinMsg);

        inputChannel.basicPublish(
                RabbitConnector.EXCHANGE_NAME,
                routingKey,
                null,
                body
        );

        System.out.println("[CLIENT] JOIN envoyé pour " + playerId
                + " vers " + routingKey);
    }

    public void sendMove(String direction) throws Exception {
        GameMessage moveMsg = new GameMessage("MOVE_INTENT", currentZone.get());
        moveMsg.getPayload().put("playerId", playerId);
        moveMsg.getPayload().put("direction", direction);

        String routingKey = "player.input." + currentZone.get();
        byte[] body = mapper.writeValueAsBytes(moveMsg);

        inputChannel.basicPublish(
                RabbitConnector.EXCHANGE_NAME,
                routingKey,
                null,
                body
        );

        System.out.println("[CLIENT] MOVE_INTENT " + direction
                + " envoyé vers " + routingKey);
    }

    public void close() {
        try {
            eventChannel.close();
            } catch (Exception ignored) {
        }
        try {
            inputChannel.close();
        } catch (Exception ignored) {
        }

        try {
            snapshotChannel.close();
        } catch (Exception ignored) {
        }
    }

    private void startSnapshotListener() throws Exception {
        String queueName = snapshotChannel.queueDeclare().getQueue();

        snapshotChannel.queueBind(
                queueName,
                RabbitConnector.EXCHANGE_NAME,
                "zone.snapshot.*"
        );

        System.out.println("[CLIENT] Écoute des snapshots sur zone.snapshot.*");

        snapshotChannel.basicConsume(queueName, true, (consumerTag, message) -> {
            try {
                GameMessage msg = mapper.readValue(message.getBody(), GameMessage.class);
                handleSnapshot(msg);
            } catch (Exception e) {
                System.err.println("[CLIENT] Erreur lecture snapshot");
                e.printStackTrace();
            }
        }, consumerTag -> {});
    }
    private void startEventListener() throws Exception {
        String queueName = eventChannel.queueDeclare().getQueue();

        eventChannel.queueBind(
                queueName,
                RabbitConnector.EXCHANGE_NAME,
                "zone.event.*"
        );

        System.out.println("[CLIENT] Écoute des événements sur zone.event.*");

        eventChannel.basicConsume(queueName, true, (consumerTag, message) -> {
            try {
                GameMessage msg = mapper.readValue(message.getBody(), GameMessage.class);
                handleEvent(msg);
            } catch (Exception e) {
                System.err.println("[CLIENT] Erreur lecture event");
                e.printStackTrace();
            }
        }, consumerTag -> {});
    }
    private void handleEvent(GameMessage msg) {
        if (!"HELLO_EVENT".equalsIgnoreCase(msg.getType())) {
            return;
        }

        Object p1Obj = msg.getPayload().get("player1");
        Object p2Obj = msg.getPayload().get("player2");
        Object zoneObj = msg.getPayload().get("zoneId");

        if (!(p1Obj instanceof String p1) || !(p2Obj instanceof String p2)) {
            return;
        }

        if (playerId.equals(p1) || playerId.equals(p2)) {
            String otherPlayer = playerId.equals(p1) ? p2 : p1;
            System.out.println("[CLIENT] HELLO avec " + otherPlayer
                    + " en zone " + zoneObj);
        }
    }
    @SuppressWarnings("unchecked")
    private void handleSnapshot(GameMessage msg) {
        if (!"ZONE_SNAPSHOT".equalsIgnoreCase(msg.getType())) {
            return;
        }

        Object playersObj = msg.getPayload().get("players");
        if (!(playersObj instanceof List<?> players)) {
            return;
        }

        for (Object obj : players) {
            if (!(obj instanceof Map<?, ?> rawMap)) {
                continue;
            }

            Map<String, Object> playerMap = (Map<String, Object>) rawMap;

            Object idObj = playerMap.get("playerId");
            Object zoneObj = playerMap.get("zoneId");

            if (playerId.equals(idObj) && zoneObj instanceof String newZone) {
                String oldZone = currentZone.get();

                if (!newZone.equals(oldZone)) {
                    currentZone.set(newZone);
                    System.out.println("[CLIENT] Changement de zone : "
                            + oldZone + " -> " + newZone);
                }

                return;
            }
        }
    }
}