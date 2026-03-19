package main.java.mesh.models;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class MeshMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String senderId;
    private String targetId;
    private String text;
    private Instant timestamp;
    private int ttl;
    private MessageType type;

    public enum MessageType {
        DISCOVERY,      // Поиск соседей
        DISCOVERY_REPLY, // Ответ на поиск
        ROUTING_UPDATE,  // Обновление маршрутов
        USER_MESSAGE,    // Пользовательское сообщение
        BROADCAST,        // Широковещательное сообщение
        ACK  // новый тип для подтверждений
    }

    public MeshMessage(String senderId, String targetId, String text, MessageType type) {
        this.messageId = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.targetId = targetId;
        this.text = text;
        this.timestamp = Instant.now();
        this.ttl = 10; // максимум 10 прыжков
        this.type = type;
    }

    // Геттеры и сеттеры
    public String getMessageId() { return messageId; }
    public String getSenderId() { return senderId; }
    public String getTargetId() { return targetId; }
    public String getText() { return text; }
    public Instant getTimestamp() { return timestamp; }
    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }
    public MessageType getType() { return type; }

    public boolean isExpired() {
        return ttl <= 0;
    }

    public void decrementTtl() {
        ttl--;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s -> %s: %s (TTL: %d)",
                type, senderId, targetId, text, ttl);
    }
}