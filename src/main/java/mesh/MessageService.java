package main.java.mesh;

import main.java.mesh.models.MeshMessage;
import main.java.mesh.models.NodeInfo;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MessageService {
    private static final int MESSAGE_PORT = 8889;
    private static final int MAX_RETRIES = 3; // количество попыток отправки
    private static final int ACK_TIMEOUT = 2000; // таймаут подтверждения (мс)

    private final MeshNode localNode;
    private final RoutingTable routingTable;
    private DatagramSocket socket;
    private boolean running;

    private final Set<String> processedMessages = ConcurrentHashMap.newKeySet();
    private final Set<String> awaitingAck = ConcurrentHashMap.newKeySet();

    public MessageService(MeshNode localNode, RoutingTable routingTable) {
        this.localNode = localNode;
        this.routingTable = routingTable;
    }

    public void start() throws SocketException {
        socket = new DatagramSocket(MESSAGE_PORT);
        socket.setReuseAddress(true);
        socket.setSoTimeout(1000); // таймаут на receive
        running = true;

        new Thread(this::listenForMessages).start();
        System.out.println("📨 Message service запущен на порту " + MESSAGE_PORT);
    }

    private void listenForMessages() {
        byte[] buffer = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                socket.receive(packet);

                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData());
                ObjectInputStream ois = new ObjectInputStream(bais);
                MeshMessage message = (MeshMessage) ois.readObject();

                handleIncomingMessage(message, packet.getAddress());

            } catch (SocketTimeoutException e) {
                // Таймаут - нормально, продолжаем
            } catch (Exception e) {
                if (running) {
                    System.err.println("Ошибка: " + e.getMessage());
                }
            }
        }
    }

    private void handleIncomingMessage(MeshMessage message, InetAddress fromAddress) {
        // Дедупликация
        if (processedMessages.contains(message.getMessageId())) {
            return;
        }
        processedMessages.add(message.getMessageId());

        // Если это подтверждение получения
        if (message.getType() == MeshMessage.MessageType.ACK) {
            awaitingAck.remove(message.getMessageId());
            return;
        }

        message.decrementTtl();

        if (message.isExpired()) {
            return;
        }

        // Проверяем, нам ли сообщение
        boolean isForUs = message.getTargetId().equals(localNode.getNodeId()) ||
                message.getTargetId().equals("ALL") ||
                message.getType() == MeshMessage.MessageType.BROADCAST;

        if (isForUs) {
            displayMessage(message);
            // Отправляем подтверждение
            sendAck(message.getMessageId(), fromAddress);
        }

        // Пересылаем дальше
        forwardMessage(message, fromAddress);
    }

    private void sendAck(String messageId, InetAddress toAddress) {
        try {
            MeshMessage ackMsg = new MeshMessage(
                    localNode.getNodeId(),
                    "ACK",
                    messageId,
                    MeshMessage.MessageType.ACK
            );

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(ackMsg);

            DatagramPacket packet = new DatagramPacket(
                    baos.toByteArray(), baos.size(),
                    toAddress, MESSAGE_PORT
            );
            socket.send(packet);

        } catch (Exception e) {
            // Игнорируем
        }
    }

    private void forwardMessage(MeshMessage message, InetAddress fromAddress) {
        for (NodeInfo neighbor : routingTable.getAllNeighbors()) {
            try {
                if (neighbor.getAddress().equals(fromAddress)) {
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(message);

                DatagramPacket sendPacket = new DatagramPacket(
                        baos.toByteArray(), baos.size(),
                        neighbor.getAddress(), MESSAGE_PORT
                );
                socket.send(sendPacket);

            } catch (Exception e) {
                // Игнорируем ошибки пересылки
            }
        }
    }

    public void sendMessage(String targetId, String text) {
        MeshMessage message = new MeshMessage(
                localNode.getNodeId(),
                targetId,
                text,
                MeshMessage.MessageType.USER_MESSAGE
        );

        processedMessages.add(message.getMessageId());
        awaitingAck.add(message.getMessageId());

        System.out.println("📤 Отправка сообщения узлу " + targetId + "...");

        // Отправляем с подтверждением
        boolean delivered = sendWithAck(message);

        if (delivered) {
            System.out.println("✅ Сообщение доставлено");
        } else {
            System.out.println("❌ Не удалось доставить сообщение (нет подтверждения)");
        }
        System.out.print("mesh> ");
    }

    private boolean sendWithAck(MeshMessage message) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            // Отправляем всем соседям
            for (NodeInfo neighbor : routingTable.getAllNeighbors()) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(message);

                    DatagramPacket packet = new DatagramPacket(
                            baos.toByteArray(), baos.size(),
                            neighbor.getAddress(), MESSAGE_PORT
                    );
                    socket.send(packet);

                } catch (Exception e) {
                    // Игнорируем
                }
            }

            // Ждем подтверждение
            try {
                Thread.sleep(ACK_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!awaitingAck.contains(message.getMessageId())) {
                return true; // получили подтверждение
            }

            System.out.println("🔄 Попытка " + attempt + " из " + MAX_RETRIES);
        }

        awaitingAck.remove(message.getMessageId());
        return false;
    }

    public void broadcastMessage(String text) {
        MeshMessage message = new MeshMessage(
                localNode.getNodeId(),
                "ALL",
                text,
                MeshMessage.MessageType.BROADCAST
        );

        processedMessages.add(message.getMessageId());

        int sentCount = 0;
        for (NodeInfo neighbor : routingTable.getAllNeighbors()) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(message);

                DatagramPacket packet = new DatagramPacket(
                        baos.toByteArray(), baos.size(),
                        neighbor.getAddress(), MESSAGE_PORT
                );
                socket.send(packet);
                sentCount++;

            } catch (Exception e) {
                System.err.println("Ошибка отправки соседу " + neighbor.getNodeId());
            }
        }

        System.out.println("📢 Broadcast отправлен " + sentCount + " соседям");
        System.out.print("mesh> ");
    }

    private void displayMessage(MeshMessage message) {
        System.out.println("\n" + "╔" + "═".repeat(48) + "╗");
        System.out.println("║ 📩 НОВОЕ СООБЩЕНИЕ " + " ".repeat(30) + "║");
        System.out.println("╠" + "═".repeat(48) + "╣");
        System.out.println("║ От:    " + padRight(message.getSenderId(), 40) + "║");
        System.out.println("║ Кому:  " + padRight(message.getTargetId(), 40) + "║");
        System.out.println("║ Текст: " + padRight(message.getText(), 40) + "║");
        System.out.println("╚" + "═".repeat(48) + "╝\n");
        System.out.print("mesh> ");
    }

    private String padRight(String s, int n) {
        if (s.length() > n) return s.substring(0, n-3) + "...";
        return String.format("%-" + n + "s", s);
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}