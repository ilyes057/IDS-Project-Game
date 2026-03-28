package com.game.worker;

import com.game.model.*;
import com.game.network.RabbitConnector;
import com.rabbitmq.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.*;

public class ZoneWorker {
    private final ZoneConfig config;
    private final ZoneState state;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Channel channel;

    public ZoneWorker(ZoneConfig config) throws Exception {
        this.config = config;
        int width = config.getMaxX() - config.getMinX();
        int height = config.getMaxY() - config.getMinY();
        this.state = new ZoneState(config.getZoneId(), width, height);
        this.channel = RabbitConnector.createChannel();
        setupQueue();
        startTickLoop();
    }

    private void setupQueue() throws Exception {
        String queueName = "queue." + config.getZoneId();
        channel.queueDeclare(queueName, true, false, false, null);
        String routingKey = "player.input." + config.getZoneId();
        channel.queueBind(queueName, RabbitConnector.EXCHANGE_NAME, routingKey);

        System.out.println("Worker " + config.getZoneId() + " écoute sur : " + routingKey);

        channel.basicConsume(queueName, true, (consumerTag, message) -> {
            try {
                GameMessage msg = mapper.readValue(message.getBody(), GameMessage.class);
                processIncomingMessage(msg);
            } catch (Exception e) {
                System.err.println("Message mal formé ignoré.");
                e.printStackTrace();
            }
        }, consumerTag -> {});
    }

    private void processIncomingMessage(GameMessage msg) {
        System.out.println("[" + config.getZoneId() + "] Message reçu: " + msg.getType());
        if ("JOIN".equalsIgnoreCase(msg.getType())) {
            handleJoin(msg);
        } else if ("MOVE_INTENT".equalsIgnoreCase(msg.getType())) {
            handleMove(msg);
        }
    }

    private void handleJoin(GameMessage msg) {
        if (msg.getPayload() == null || msg.getPayload().get("playerId") == null) {
            System.err.println(" Erreur : Payload ou playerId manquant");
            return;
        }

        String playerId = (String) msg.getPayload().get("playerId");
        int minX = config.getMinX(), maxX = config.getMaxX();
        int minY = config.getMinY(), maxY = config.getMaxY();
        
        boolean success = false;
        int attempts = 0;

        while (!success && attempts < 10) {
            attempts++;
            int startX = ThreadLocalRandom.current().nextInt(minX, maxX);
            int startY = ThreadLocalRandom.current().nextInt(minY, maxY);
            
            Player newPlayer = new Player(playerId, startX, startY, config.getZoneId());
            success = state.updatePosition(newPlayer, startX, startY, minX, minY);
            
            if (success) {
                System.out.println(" [JOIN] " + playerId + " placé en (" + startX + "," + startY + ")");
            }
        }

        if (!success) {
            System.out.println(" [JOIN] Impossible de placer " + playerId + " (Zone pleine)");
        }
    }

    private void handleMove(GameMessage msg) {
        String playerId = (String) msg.getPayload().get("playerId");
        String directionStr = (String) msg.getPayload().get("direction");
        
        Player p = (Player) state.getPlayers().get(playerId);
        if (p == null) {
            System.out.println(" [MOVE] Joueur inconnu : " + playerId);
            return;
        }

        int nextX = p.getX();
        int nextY = p.getY();

        try {
            Direction dir = Direction.valueOf(directionStr.toUpperCase());
            switch (dir) {
                case UP:    nextY--; break;
                case DOWN:  nextY++; break;
                case LEFT:  nextX--; break;
                case RIGHT: nextX++; break;
            }
        } catch (Exception e) {
            System.out.println(" Direction invalide");
            return;
        }

        if (config.isOutside(nextX, nextY)) {
            System.out.println(" MUR : " + playerId + " se cogne en (" + nextX + "," + nextY + ")");
            return;
        }

        if (state.updatePosition(p, nextX, nextY, config.getMinX(), config.getMinY())) {
            System.out.println(" [MOVE] " + playerId + " -> (" + p.getX() + "," + p.getY() + ")");
        } else {
            System.out.println(" COLLISION pour " + playerId);
        }
    }

    private void startTickLoop() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            // Logique de tick future
        }, 0, 200, TimeUnit.MILLISECONDS);
    }
}