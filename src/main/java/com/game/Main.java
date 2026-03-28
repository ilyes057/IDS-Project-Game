package com.game;

import com.game.model.WorldTopology;
import com.game.model.ZoneConfig;
import com.game.worker.ZoneWorker;

public class Main {
    public static void main(String[] args) {
        try {
            // On récupère le nom de la zone (A, B, C ou D) depuis les arguments
            String zoneId = (args.length > 0) ? args[0].toUpperCase() : "A";
            
            System.out.println("Démarrage du serveur pour la Zone : " + zoneId);
            // 1. On récupère la configuration via la topologie
            ZoneConfig config = WorldTopology.getZoneConfig(zoneId);
            // 2. On lance le Worker avec cette config
            new ZoneWorker(config);
            System.out.println("Système prêt et en attente de messages...");
        } catch (Exception e) {
            System.err.println("Erreur fatale au démarrage : " + e.getMessage());
            e.printStackTrace();
        }
    }
}