package main.java.mesh;

import main.java.mesh.models.MeshMessage;
import main.java.mesh.models.NodeInfo;
import main.java.mesh.utils.NetworkUtils;

import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscoveryService {
    private static final int DISCOVERY_PORT = 8888;
    private static final String BROADCAST_ADDR = "255.255.255.255";
    private static final int DISCOVERY_INTERVAL = 5; // секунд

    private final MeshNode localNode;
    private final RoutingTable routingTable;
    private DatagramSocket socket;
    private ScheduledExecutorService scheduler;
    private boolean running;

    public DiscoveryService(MeshNode localNode, RoutingTable routingTable) {
        this.localNode = localNode;
        this.routingTable = routingTable;
    }

    public void start() throws SocketException {
        socket = new DatagramSocket(DISCOVERY_PORT);
        socket.setBroadcast(true);
        socket.setReuseAddress(true);

        running = true;
        scheduler = Executors.newScheduledThreadPool(2);

        // Поток для прослушивания ответов
        new Thread(this::listenForDiscoveries).start();

        // Периодическая рассылка discovery-сообщений
        scheduler.scheduleAtFixedRate(this::broadcastDiscovery,
                0, DISCOVERY_INTERVAL, TimeUnit.SECONDS);

        // Периодическая очистка неактивных соседей
        scheduler.scheduleAtFixedRate(() ->
                routingTable.cleanupStaleNeighbors(15000), 15, 15, TimeUnit.SECONDS);

        System.out.println("🔍 Discovery service запущен на порту " + DISCOVERY_PORT);
    }

    private void broadcastDiscovery() {
        try {
            // Создаем discovery-сообщение
            MeshMessage discoveryMsg = new MeshMessage(
                    localNode.getNodeId(),
                    "ALL",
                    "DISCOVERY",
                    MeshMessage.MessageType.DISCOVERY
            );

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(discoveryMsg);
            oos.flush();
            byte[] sendData = baos.toByteArray();

            // Отправляем broadcast
            DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length,
                    InetAddress.getByName(BROADCAST_ADDR), DISCOVERY_PORT
            );
            socket.send(sendPacket);

            // System.out.println("📢 Отправлен discovery broadcast");

        } catch (Exception e) {
            System.err.println("Ошибка отправки discovery: " + e.getMessage());
        }
    }

    private void listenForDiscoveries() {
        byte[] buffer = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                socket.receive(packet);

                // Не обрабатываем свои же сообщения
                if (packet.getAddress().getHostAddress().equals(NetworkUtils.getLocalIpAddress())) {
                    continue;
                }

                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData());
                ObjectInputStream ois = new ObjectInputStream(bais);
                MeshMessage message = (MeshMessage) ois.readObject();

                handleDiscoveryMessage(message, packet.getAddress());

            } catch (SocketException e) {
                if (running) {
                    System.err.println("Socket error: " + e.getMessage());
                }
            } catch (EOFException e) {
                // Игнорируем, иногда пакеты обрезаются
            } catch (Exception e) {
                if (running) {
                    System.err.println("Ошибка обработки discovery: " + e.getMessage());
                }
            }
        }
    }

    private void handleDiscoveryMessage(MeshMessage message, InetAddress address) {
        switch (message.getType()) {
            case DISCOVERY:
                // Получили discovery-запрос, отвечаем
                sendDiscoveryReply(address);
                break;

            case DISCOVERY_REPLY:
                // Получили ответ на наш discovery
                NodeInfo newNode = new NodeInfo(
                        message.getSenderId(),
                        address,
                        DISCOVERY_PORT
                );
                routingTable.updateNeighbor(newNode);
                System.out.println("✅ Обнаружен новый узел: " + newNode);
                routingTable.printRoutingTable();
                break;
        }
    }

    private void sendDiscoveryReply(InetAddress targetAddress) {
        try {
            MeshMessage replyMsg = new MeshMessage(
                    localNode.getNodeId(),
                    "ALL",
                    "DISCOVERY_REPLY",
                    MeshMessage.MessageType.DISCOVERY_REPLY
            );

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(replyMsg);
            oos.flush();
            byte[] sendData = baos.toByteArray();

            DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length,
                    targetAddress, DISCOVERY_PORT
            );
            socket.send(sendPacket);

        } catch (Exception e) {
            System.err.println("Ошибка отправки reply: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}