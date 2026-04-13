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
    private final Map<String, PendingTransfer> pendingTransfers = new ConcurrentHashMap<>();
    private final Map<TargetCell, String> reservedCells = new ConcurrentHashMap<>();
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
        channel.queuePurge(queueName);
        channel.queueDeclare(queueName, true, false, false, null);
        String routingKey = "player.input." + config.getZoneId();
        channel.queueBind(queueName, RabbitConnector.EXCHANGE_NAME, routingKey);
        channel.queueBind(queueName, RabbitConnector.EXCHANGE_NAME, "zone.transfer.*");
        channel.queueBind(queueName, RabbitConnector.EXCHANGE_NAME, "zone.reply." + config.getZoneId());

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
        String type = msg.getType();
        System.out.println("[" + config.getZoneId() + "] Message reçu: " + type);
        if ("JOIN".equalsIgnoreCase(msg.getType())) {
            handleJoin(msg);
        } else if ("MOVE_INTENT".equalsIgnoreCase(msg.getType())) {
            enqueueMoveIntent(msg);
        }
        if (msg.getTargetZone() != null && !config.getZoneId().equals(msg.getTargetZone())) {
            return;
        }
        if ("TRANSFER_PREPARE".equalsIgnoreCase(type)) {
            handleTransferPrepare(msg);
        } else if ("TRANSFER_ACCEPT".equalsIgnoreCase(type)) {
            handleTransferAccept(msg);
        } else if ("TRANSFER_REJECT".equalsIgnoreCase(type)) {
            handleTransferReject(msg);
        } else if ("TRANSFER_COMMIT".equalsIgnoreCase(type)) {
            handleTransferCommit(msg);
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
            publishSnapshot();
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

            // if (config.isOutside(nextX, nextY)) {
            //     System.out.println("[TICK] Sortie de zone détectée pour " + pending.getPlayerId()
            //             + " vers (" + nextX + "," + nextY + ") - handover pas encore implémenté");
            //     continue;
            // }
            String targetZone = resolveZoneId(nextX, nextY);

            if (targetZone == null) {
                System.out.println("[TICK] Monde dépassé pour " + pending.getPlayerId()
                        + " vers (" + nextX + "," + nextY + ")");
                continue;
            }

            if (!targetZone.equals(config.getZoneId())) {
                if (pendingTransfers.containsKey(pending.getPlayerId())) {
                    continue;
                }

                startTransfer(player, targetZone, nextX, nextY, pending.getTimestamp());
                continue;
            }

            TargetCell target = new TargetCell(nextX, nextY);
            String reservedFor = reservedCells.get(target);
            if (reservedFor != null && !reservedFor.equals(pending.getPlayerId())) {
                System.out.println("[RESERVATION] " + pending.getPlayerId()
                        + " ne peut pas entrer en (" + nextX + "," + nextY + ")"
                        + " : case réservée pour " + reservedFor);
                continue;
            }
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
        publishSnapshot();
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
private void publishSnapshot() {
    try {
        GameMessage snapshotMsg = new GameMessage("ZONE_SNAPSHOT", config.getZoneId());

        snapshotMsg.setTargetZone(config.getZoneId());

        snapshotMsg.getPayload().put("zoneId", config.getZoneId());

        List<Map<String, Object>> playersData = new ArrayList<>();

        for (Player p : state.getPlayers().values()) {
            Map<String, Object> playerData = new HashMap<>();
            playerData.put("playerId", p.getId());
            playerData.put("x", p.getX());
            playerData.put("y", p.getY());
            playerData.put("zoneId", p.getZone());
            playersData.add(playerData);
        }

        snapshotMsg.getPayload().put("players", playersData);

        String routingKey = "zone.snapshot." + config.getZoneId();
        byte[] body = mapper.writeValueAsBytes(snapshotMsg);

        channel.basicPublish(
                RabbitConnector.EXCHANGE_NAME,
                routingKey,
                null,
                body
        );

        //System.out.println("[PUBLISH] Snapshot envoyé sur " + routingKey);
    } catch (Exception e) {
        System.err.println("[SNAPSHOT] Erreur lors de la publication");
        e.printStackTrace();
    }
}
private String resolveZoneId(int x, int y) {
    if (x < 0 || x >= 20 || y < 0 || y >= 20) {
        return null;
    }
    if (x < 10 && y < 10) return "A";
    if (x >= 10 && y < 10) return "B";
    if (x < 10 && y >= 10) return "C";
    return "D";
}

private void startTransfer(Player player, String targetZone, int newX, int newY, long timestamp) {
    try {
        PendingTransfer transfer = new PendingTransfer(
                player.getId(),
                config.getZoneId(),
                targetZone,
                player.getX(),
                player.getY(),
                newX,
                newY,
                timestamp
        );

        pendingTransfers.put(player.getId(), transfer);

        GameMessage msg = new GameMessage("TRANSFER_PREPARE", config.getZoneId());
        msg.setTargetZone(targetZone);
        msg.getPayload().put("playerId", player.getId());
        msg.getPayload().put("oldX", player.getX());
        msg.getPayload().put("oldY", player.getY());
        msg.getPayload().put("newX", newX);
        msg.getPayload().put("newY", newY);

        String routingKey = "zone.transfer." + config.getZoneId() + "to" + targetZone;
        byte[] body = mapper.writeValueAsBytes(msg);
        channel.basicPublish(RabbitConnector.EXCHANGE_NAME, routingKey, null, body);

        System.out.println("[TRANSFER] PREPARE " + player.getId() + " " + config.getZoneId() + " -> " + targetZone);
    } catch (Exception e) {
        System.err.println("[TRANSFER] Erreur startTransfer");
        e.printStackTrace();
    }
}

private void handleTransferPrepare(GameMessage msg) {
    try {
        String playerId = (String) msg.getPayload().get("playerId");
        int newX = ((Number) msg.getPayload().get("newX")).intValue();
        int newY = ((Number) msg.getPayload().get("newY")).intValue();

        TargetCell target = new TargetCell(newX, newY);
        boolean occupied = isCellOccupied(newX, newY);

        GameMessage reply;
        if (!occupied && !reservedCells.containsKey(target)) {
            reservedCells.put(target, playerId);
            reply = new GameMessage("TRANSFER_ACCEPT", config.getZoneId());
            System.out.println("[TRANSFER] ACCEPT pour " + playerId + " en (" + newX + "," + newY + ")");
        } else {
            reply = new GameMessage("TRANSFER_REJECT", config.getZoneId());
            System.out.println("[TRANSFER] REJECT pour " + playerId + " en (" + newX + "," + newY + ")");
        }

        reply.setTargetZone(msg.getSourceZone());
        reply.getPayload().put("playerId", playerId);
        reply.getPayload().put("newX", newX);
        reply.getPayload().put("newY", newY);

        String routingKey = "zone.reply." + msg.getSourceZone();
        byte[] body = mapper.writeValueAsBytes(reply);
        channel.basicPublish(RabbitConnector.EXCHANGE_NAME, routingKey, null, body);
    } catch (Exception e) {
        System.err.println("[TRANSFER] Erreur handleTransferPrepare");
        e.printStackTrace();
    }
}

private void handleTransferAccept(GameMessage msg) {
    try {
        String playerId = (String) msg.getPayload().get("playerId");
        PendingTransfer transfer = pendingTransfers.get(playerId);

        if (transfer == null) {
            return;
        }

        Player player = state.getPlayers().get(playerId);
        if (player == null) {
            pendingTransfers.remove(playerId);
            return;
        }

        state.removePlayer(playerId, config.getMinX(), config.getMinY());

        GameMessage commit = new GameMessage("TRANSFER_COMMIT", config.getZoneId());
        commit.setTargetZone(transfer.getTargetZone());
        commit.getPayload().put("playerId", playerId);
        commit.getPayload().put("newX", transfer.getNewX());
        commit.getPayload().put("newY", transfer.getNewY());

        String routingKey = "zone.transfer." + transfer.getSourceZone() + "to" + transfer.getTargetZone();
        byte[] body = mapper.writeValueAsBytes(commit);
        channel.basicPublish(RabbitConnector.EXCHANGE_NAME, routingKey, null, body);

        pendingTransfers.remove(playerId);

        System.out.println("[TRANSFER] COMMIT envoyé pour " + playerId);
    } catch (Exception e) {
        System.err.println("[TRANSFER] Erreur handleTransferAccept");
        e.printStackTrace();
    }
}

private void handleTransferReject(GameMessage msg) {
    String playerId = (String) msg.getPayload().get("playerId");
    pendingTransfers.remove(playerId);
    System.out.println("[TRANSFER] REJECT reçu pour " + playerId + " - joueur reste sur place");
}

private void handleTransferCommit(GameMessage msg) {
    try {
        String playerId = (String) msg.getPayload().get("playerId");
        int newX = ((Number) msg.getPayload().get("newX")).intValue();
        int newY = ((Number) msg.getPayload().get("newY")).intValue();

        Player newPlayer = new Player(playerId, newX, newY, config.getZoneId());
        boolean inserted = state.updatePosition(newPlayer, newX, newY, config.getMinX(), config.getMinY());

        reservedCells.remove(new TargetCell(newX, newY));

        if (inserted) {
            System.out.println("[TRANSFER] Joueur " + playerId + " inséré en (" + newX + "," + newY + ")");
        } else {
            System.out.println("[TRANSFER] Échec insertion pour " + playerId);
        }
    } catch (Exception e) {
        System.err.println("[TRANSFER] Erreur handleTransferCommit");
        e.printStackTrace();
    }
}

private boolean isCellOccupied(int x, int y) {
    for (Player p : state.getPlayers().values()) {
        if (p.getX() == x && p.getY() == y) {
            return true;
        }
    }
    return false;
}
}