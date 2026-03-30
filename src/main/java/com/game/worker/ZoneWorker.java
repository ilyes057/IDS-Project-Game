package com.game.worker;

import com.game.model.*;
import com.game.network.RabbitConnector;
import com.rabbitmq.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;

public class ZoneWorker {
    private final ZoneConfig config;
    private final ZoneState state;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Channel channel;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Queue<PendingMove> pendingMoveQueue = new ConcurrentLinkedQueue<>();

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
            enqueueMoveIntent(msg);
        }
        else {
            System.out.println("Type de message inconnu : " + msg.getType());
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

    private void enqueueMoveIntent(GameMessage msg) {
        if (msg.getPayload() == null) {
            System.out.println("[INTENT] Payload manquant");
            return;
        }

        String playerId = (String) msg.getPayload().get("playerId");
        String directionStr = (String) msg.getPayload().get("direction");

        if (playerId == null || directionStr == null) {
            System.out.println("[INTENT] playerId ou direction manquant");
            return;
        }

        Direction direction;
        try {
            direction = Direction.valueOf(directionStr.toUpperCase());
        } catch (Exception e) {
            System.out.println("[INTENT] Direction invalide : " + directionStr);
            return;
        }
        pendingMoveQueue.offer(new PendingMove(playerId, direction, msg.getTimestamp()));
        System.out.println("[INTENT] " + playerId + " veut aller vers " + direction);
}
    private void startTickLoop() {
        executor.scheduleAtFixedRate(() -> {
            try {
                processTick();
            } catch (Exception e) {
                System.err.println("[TICK] Erreur pendant le traitement");
                e.printStackTrace();
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }
    private void processTick() {
        Map<String, PendingMove> latestIntentByPlayer = new HashMap<>();
        PendingMove move;
        // On ne garde que la dernière intention de chaque joueur dans la file
        while ((move = pendingMoveQueue.poll()) != null) {
            PendingMove existing = latestIntentByPlayer.get(move.getPlayerId());

            if (existing == null || move.getTimestamp() >= existing.getTimestamp()) {
                latestIntentByPlayer.put(move.getPlayerId(), move);
            }
        }

        if (latestIntentByPlayer.isEmpty()) {
            return;
        }

        Map<TargetCell, List<PendingMove>> intentsByTarget = new HashMap<>();

        for (PendingMove pending : latestIntentByPlayer.values()) {
            Player player = state.getPlayers().get(pending.getPlayerId());

            if (player == null) {
                System.out.println("[TICK] Joueur inconnu : " + pending.getPlayerId());
                continue;
            }

            int nextX = player.getX();
            int nextY = player.getY();

            switch (pending.getDirection()) {
                case UP:
                    nextY--;
                    break;
                case DOWN:
                    nextY++;
                    break;
                case LEFT:
                    nextX--;
                    break;
                case RIGHT:
                    nextX++;
                    break;
            }

            if (config.isOutside(nextX, nextY)) {
                System.out.println("[TICK] Sortie de zone détectée pour " + pending.getPlayerId()
                        + " vers (" + nextX + "," + nextY + ") - handover pas encore implémenté");
                continue;
            }

            TargetCell target = new TargetCell(nextX, nextY);
            intentsByTarget.computeIfAbsent(target, k -> new ArrayList<>()).add(pending);
        }

        for (Map.Entry<TargetCell, List<PendingMove>> entry : intentsByTarget.entrySet()) {
            TargetCell target = entry.getKey();
            List<PendingMove> candidates = entry.getValue();

            candidates.sort(
                    Comparator.comparingLong(PendingMove::getTimestamp)
                            .thenComparing(PendingMove::getPlayerId)
            );

            PendingMove winner = candidates.get(0);
            Player winnerPlayer = state.getPlayers().get(winner.getPlayerId());

            if (winnerPlayer == null) {
                continue;
            }

            boolean moved = state.updatePosition(
                    winnerPlayer,
                    target.getX(),
                    target.getY(),
                    config.getMinX(),
                    config.getMinY()
            );

            if (moved) {
                System.out.println("[MOVE] " + winner.getPlayerId() + " -> (" + target.getX() + "," + target.getY() + ")");
            } else {
                System.out.println("[MOVE] déplacement refusé pour " + winner.getPlayerId()
                        + " vers (" + target.getX() + "," + target.getY() + ")");
            }

            for (int i = 1; i < candidates.size(); i++) {
                PendingMove loser = candidates.get(i);
                System.out.println("[COLLISION] " + loser.getPlayerId()
                        + " perd l'accès à (" + target.getX() + "," + target.getY() + ")");
            }
        }

        printState();
    }
    private void printState() {
        Collection<Player> players = state.getPlayers().values();

        if (players.isEmpty()) {
            System.out.println("[SNAPSHOT-" + config.getZoneId() + "] aucun joueur");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[SNAPSHOT-").append(config.getZoneId()).append("] ");

        for (Player p : players) {
            sb.append(p.getId())
            .append("(")
            .append(p.getX())
            .append(",")
            .append(p.getY())
            .append(") ");
        }

        System.out.println(sb);
}

}