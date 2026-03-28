package com.game.network;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

public class RabbitConnector {
    // Le nom de l'échange principal que nous utiliserons pour envoyer les messages entre les zones.
    public static final String EXCHANGE_NAME = "game.topic";

    public static Channel createChannel() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost"); // Adresse du Docker
        factory.setUsername("guest");
        factory.setPassword("guest");

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // On déclare l'échange de type "topic"
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        
        return channel;
    }
}