package com.game.worker;

import com.game.model.*;
import com.game.network.RabbitConnector;
import com.rabbitmq.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.*;

public class ZoneWorker {
    private final ZoneConfig config;
    private final ZoneState state;
    private final ObjectMapper mapper = new ObjectMapper(); // Pour le JSON
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
        channel.queueDeclare(queueName, false, false, true, null);
        
        // On s'abonne aux messages qui nous sont destinés (ex: player.input.A)
        String routingKey = "player.input." + config.getZoneId();
        channel.queueBind(queueName, RabbitConnector.EXCHANGE_NAME, routingKey);

        System.out.println("Worker " + config.getZoneId() + " écoute sur : " + routingKey);
        
        // On commence à consommer les messages
        channel.basicConsume(queueName, true, (consumerTag, message) -> {
            GameMessage msg = mapper.readValue(message.getBody(), GameMessage.class);
            processIncomingMessage(msg);
        }, consumerTag -> {});
    }

    // 2. Traiter les messages reçus
    private void processIncomingMessage(GameMessage msg) {
        System.out.println("[" + config.getZoneId() + "] Message reçu: " + msg.getType());
    }
    private void startTickLoop() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            //vide pour l'instant, on verra plus tard pour les actions à faire à chaque tick
        }, 0, 200, TimeUnit.MILLISECONDS);
    }
}