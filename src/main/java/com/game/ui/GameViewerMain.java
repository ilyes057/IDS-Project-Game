package com.game.ui;

import com.game.client.GameClient;

import javax.swing.SwingUtilities;

public class GameViewerMain {
    public static void main(String[] args) {
        String initialZone = (args.length > 0) ? args[0].toUpperCase() : "A";
        String playerId = (args.length > 1) ? args[1] : "P1";

        try {
            GameClient client = new GameClient(initialZone, playerId);

            SwingUtilities.invokeLater(() -> {
                new GameWindow(client, playerId);
            });
        } catch (Exception e) {
            System.err.println("Erreur lancement GameViewerMain");
            e.printStackTrace();
        }
    }
}