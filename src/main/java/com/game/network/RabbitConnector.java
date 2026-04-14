package com.game.network;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class RabbitConnector {
    public static final String EXCHANGE_NAME = "game.topic";

    public static Channel createChannel() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();

        String host = System.getProperty(
                "rabbitmq.host",
                System.getenv().getOrDefault("RABBITMQ_HOST", "localhost")
        );

        int port = Integer.parseInt(System.getProperty(
                "rabbitmq.port",
                System.getenv().getOrDefault("RABBITMQ_PORT", "5672")
        ));

        String username = System.getProperty(
                "rabbitmq.user",
                System.getenv().getOrDefault("RABBITMQ_USER", "ids")
        );

        String password = System.getProperty(
                "rabbitmq.password",
                System.getenv().getOrDefault("RABBITMQ_PASSWORD", "ids123")
        );

        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);
        return channel;
    }
}