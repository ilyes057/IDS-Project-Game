package com.game.model;

import java.util.HashMap;
import java.util.Map;

public class GameMessage {
    private String type;         // Le type d'action (ex: "MOVE_INTENT", "TRANSFER_RELAY")
    private String sourceZone;   // Quelle zone envoie le message (A, B, C ou D)
    private long timestamp;      // L'heure d'envoi (utile pour l'ordre des messages)
    
    // Le "payload" est un dictionnaire qui contient les données spécifiques.
    // Par exemple, pour un mouvement, on y mettra l'ID du joueur et la direction.
    private Map<String, Object> payload = new HashMap<>();

    public GameMessage() {} // Nécessaire pour Jackson

    public GameMessage(String type, String sourceZone) {
        this.type = type;
        this.sourceZone = sourceZone;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters et Setters pour Jackson
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSourceZone() { return sourceZone; }
    public void setSourceZone(String sourceZone) { this.sourceZone = sourceZone; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}