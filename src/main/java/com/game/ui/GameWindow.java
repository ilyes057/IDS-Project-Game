package com.game.ui;

import com.game.client.GameClient;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameWindow extends JFrame {
    private final GameClient client;
    private final GamePanel gamePanel;
    private final JLabel statusLabel;
    private final JTextArea eventArea;

    private final Map<String, List<PlayerView>> snapshotsByZone = new HashMap<>();

    public GameWindow(GameClient client, String playerId) {
        this.client = client;
        this.gamePanel = new GamePanel(playerId);
        this.statusLabel = new JLabel("Zone courante : " + client.getCurrentZone());
        this.eventArea = new JTextArea(5, 30);

        setTitle("IDS Game Viewer - " + playerId);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        eventArea.setEditable(false);

        JButton joinButton = new JButton("Join");
        joinButton.addActionListener(e -> {
            try {
                client.sendJoin();
                gamePanel.requestFocusInWindow();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(joinButton);
        topPanel.add(statusLabel);

        add(topPanel, BorderLayout.NORTH);
        add(gamePanel, BorderLayout.CENTER);
        add(new JScrollPane(eventArea), BorderLayout.SOUTH);

        setupKeyBindings();
        client.setSnapshotListener((zoneId, players) -> SwingUtilities.invokeLater(() -> {
    snapshotsByZone.put(zoneId, new ArrayList<>(players));

    Set<String> incomingPlayerIds = new HashSet<>();
    for (PlayerView p : players) {
        incomingPlayerIds.add(p.getPlayerId());
    }

    if (!incomingPlayerIds.isEmpty()) {
        for (Map.Entry<String, List<PlayerView>> entry : snapshotsByZone.entrySet()) {
            String otherZoneId = entry.getKey();

            if (!otherZoneId.equals(zoneId)) {
                entry.getValue().removeIf(p -> incomingPlayerIds.contains(p.getPlayerId()));
            }
        }
    }

    refreshWorldView();
    statusLabel.setText("Zone courante : " + client.getCurrentZone());
}));
        client.setHelloListener(message -> SwingUtilities.invokeLater(() -> {
            eventArea.append(message + "\n");
        }));

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        gamePanel.requestFocusInWindow();
    }

    private void refreshWorldView() {
        List<PlayerView> allPlayers = new ArrayList<>();
        for (List<PlayerView> zonePlayers : snapshotsByZone.values()) {
            allPlayers.addAll(zonePlayers);
        }
        gamePanel.updatePlayers(allPlayers);
    }

    private void setupKeyBindings() {
        bindKey("UP", "moveUp", "UP");
        bindKey("DOWN", "moveDown", "DOWN");
        bindKey("LEFT", "moveLeft", "LEFT");
        bindKey("RIGHT", "moveRight", "RIGHT");

        bindKey("Z", "moveUpZ", "UP");
        bindKey("S", "moveDownS", "DOWN");
        bindKey("Q", "moveLeftQ", "LEFT");
        bindKey("D", "moveRightD", "RIGHT");
    }

    private void bindKey(String keyStroke, String actionKey, String direction) {
        gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyStroke), actionKey);

        gamePanel.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                try {
                    client.sendMove(direction);
                    statusLabel.setText("Zone courante : " + client.getCurrentZone());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
}