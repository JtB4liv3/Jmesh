package main.java.mesh;

import main.java.mesh.models.MeshMessage;
import main.java.mesh.models.NodeInfo;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MessageService {
    private static final int MESSAGE_PORT = 8889;

    private final MeshNode localNode;
    private final RoutingTable routingTable;
    private DatagramSocket socket;
    private boolean running;

    // Множество для дедупликации сообщений
    private final Set<String> processedMessages = ConcurrentHashMap.newKeySet();

    public MessageService(MeshNode localNode, RoutingTable routingTable) {
        this.localNode = localNode;
        this.routingTable = routingTable;
    }

    public void start() throws SocketException {
        socket = new DatagramSocket(MESSAGE_PORT);
        socket.setReuseAddress(true);
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

            } catch (SocketException e) {
                if (running) {
                    System.err.println("Message socket error: " + e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("Ошибка обработки сообщения: " + e.getMessage());
            }
        }
    }

    private void handleIncomingMessage(MeshMessage message, InetAddress fromAddress) {
        // Дедупликация
        if (processedMessages.contains(message.getMessageId())) {
            return; // Уже видели это сообщение
        }
        processedMessages.add(message.getMessageId());

        // Уменьшаем TTL
        message.decrementTtl();

        if (message.isExpired()) {
            System.out.println("⏰ Сообщение истекло (TTL=0): " + message.getMessageId());
            return;
        }

        // Обновляем информацию об отправителе в таблице маршрутизации
        if (!message.getSenderId().equals(localNode.getNodeId())) {
            NodeInfo senderInfo = routingTable.getNeighborInfo(message.getSenderId());
            if (senderInfo == null) {
                // Новый узел, добавляем как соседа
                senderInfo = new NodeInfo(
                        message.getSenderId(),
                        fromAddress,
                        MESSAGE_PORT
                );
                routingTable.updateNeighbor(senderInfo);
            }
        }

        // Проверяем, предназначено ли сообщение нам
        boolean isForUs = message.getTargetId().equals(localNode.getNodeId()) ||
                message.getTargetId().equals("ALL") ||
                message.getType() == MeshMessage.MessageType.BROADCAST;

        if (isForUs) {
            displayMessage(message);
        }

        // Если сообщение не для нас ИЛИ это broadcast, пересылаем дальше
        if (!message.getTargetId().equals(localNode.getNodeId()) ||
                message.getType() == MeshMessage.MessageType.BROADCAST) {
            forwardMessage(message, fromAddress);
        }
    }

    private void forwardMessage(MeshMessage message, InetAddress fromAddress) {
        // Пересылаем всем соседям, кроме того, от кого пришло
        for (NodeInfo neighbor : routingTable.getAllNeighbors()) {
            try {
                // Не отправляем обратно отправителю
                if (neighbor.getAddress().equals(fromAddress)) {
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(message);
                oos.flush();
                byte[] sendData = baos.toByteArray();

                DatagramPacket sendPacket = new DatagramPacket(
                        sendData, sendData.length,
                        neighbor.getAddress(), MESSAGE_PORT
                );
                socket.send(sendPacket);

                System.out.println("🔄 Переслано сообщение " + message.getMessageId() +
                        " узлу " + neighbor.getNodeId());

            } catch (Exception e) {
                System.err.println("Ошибка пересылки: " + e.getMessage());
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

        // Добавляем в обработанные, чтобы не получить свое же сообщение
        processedMessages.add(message.getMessageId());

        // Отправляем всем соседям
        for (NodeInfo neighbor : routingTable.getAllNeighbors()) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(message);
                oos.flush();
                byte[] sendData = baos.toByteArray();

                DatagramPacket sendPacket = new DatagramPacket(
                        sendData, sendData.length,
                        neighbor.getAddress(), MESSAGE_PORT
                );
                socket.send(sendPacket);

                System.out.println("📤 Отправлено сообщение узлу " + neighbor.getNodeId());

            } catch (Exception e) {
                System.err.println("Ошибка отправки: " + e.getMessage());
            }
        }
    }

    public void broadcastMessage(String text) {
        MeshMessage message = new MeshMessage(
                localNode.getNodeId(),
                "ALL",
                text,
                MeshMessage.MessageType.BROADCAST
        );

        processedMessages.add(message.getMessageId());

        for (NodeInfo neighbor : routingTable.getAllNeighbors()) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(message);
                oos.flush();
                byte[] sendData = baos.toByteArray();

                DatagramPacket sendPacket = new DatagramPacket(
                        sendData, sendData.length,
                        neighbor.getAddress(), MESSAGE_PORT
                );
                socket.send(sendPacket);

            } catch (Exception e) {
                System.err.println("Ошибка broadcast: " + e.getMessage());
            }
        }
        System.out.println("📢 Broadcast отправлен всем соседям");
    }

    private void displayMessage(MeshMessage message) {
        System.out.println("\n");
        System.out.println("📩 ПОЛУЧЕНО СООБЩЕНИЕ:");
        System.out.println("   От: " + message.getSenderId());
        System.out.println("   Кому: " + message.getTargetId());
        System.out.println("   Текст: " + message.getText());
        System.out.println("   ID: " + message.getMessageId());
        System.out.println("   TTL: " + message.getTtl());
        System.out.println("\n");
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}