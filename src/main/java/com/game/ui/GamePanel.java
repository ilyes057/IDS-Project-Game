package com.game.ui;

import javax.swing.JPanel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GamePanel extends JPanel {
    private static final int WORLD_SIZE = 20;
    private static final int CELL_SIZE = 30;

    private List<PlayerView> players = new ArrayList<>();
    private String localPlayerId;

    public GamePanel(String localPlayerId) {
        this.localPlayerId = localPlayerId;
        setPreferredSize(new Dimension(WORLD_SIZE * CELL_SIZE, WORLD_SIZE * CELL_SIZE));
        setFocusable(true);
    }

    public void updatePlayers(List<PlayerView> players) {
        this.players = new ArrayList<>(players);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;

        // fond
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // zones colorées très légèrement
        g2.setColor(new Color(245, 245, 245));
        g2.fillRect(0, 0, 10 * CELL_SIZE, 10 * CELL_SIZE);                // A
        g2.fillRect(10 * CELL_SIZE, 0, 10 * CELL_SIZE, 10 * CELL_SIZE);   // B
        g2.fillRect(0, 10 * CELL_SIZE, 10 * CELL_SIZE, 10 * CELL_SIZE);   // C
        g2.fillRect(10 * CELL_SIZE, 10 * CELL_SIZE, 10 * CELL_SIZE, 10 * CELL_SIZE); // D

        // grille
        g2.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i <= WORLD_SIZE; i++) {
            int p = i * CELL_SIZE;
            g2.drawLine(p, 0, p, WORLD_SIZE * CELL_SIZE);
            g2.drawLine(0, p, WORLD_SIZE * CELL_SIZE, p);
        }

        // séparation des zones
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(3));
        g2.drawLine(10 * CELL_SIZE, 0, 10 * CELL_SIZE, WORLD_SIZE * CELL_SIZE);
        g2.drawLine(0, 10 * CELL_SIZE, WORLD_SIZE * CELL_SIZE, 10 * CELL_SIZE);

        // noms des zones
        g2.setFont(new Font("Arial", Font.BOLD, 18));
        g2.drawString("A", 10, 20);
        g2.drawString("B", 10 * CELL_SIZE + 10, 20);
        g2.drawString("C", 10, 10 * CELL_SIZE + 20);
        g2.drawString("D", 10 * CELL_SIZE + 10, 10 * CELL_SIZE + 20);

        // joueurs
        g2.setFont(new Font("Arial", Font.BOLD, 12));
        for (PlayerView p : players) {
            int px = p.getX() * CELL_SIZE;
            int py = p.getY() * CELL_SIZE;

            if (p.getPlayerId().equals(localPlayerId)) {
                g2.setColor(Color.RED);
            } else {
                g2.setColor(Color.BLUE);
            }

            g2.fillOval(px + 5, py + 5, CELL_SIZE - 10, CELL_SIZE - 10);

            g2.setColor(Color.BLACK);
            g2.drawString(p.getPlayerId(), px + 4, py + CELL_SIZE - 6);
        }
    }
}