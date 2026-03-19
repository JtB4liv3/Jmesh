package main.java.mesh;

import main.java.mesh.models.MeshMessage;
import main.java.mesh.models.NodeInfo;
import main.java.mesh.utils.NetworkUtils;

import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;

public class DiscoveryService {
    private static final int DISCOVERY_PORT = 8888;
    private static final String BROADCAST_ADDR = "255.255.255.255";
    private static final int DISCOVERY_INTERVAL = 10; // увеличили до 10 секунд
    private static final int NEIGHBOR_TIMEOUT = 30000; // 30 секунд таймаут

    private final MeshNode localNode;
    private final RoutingTable routingTable;
    private DatagramSocket socket;
    private ScheduledExecutorService scheduler;
    private boolean running;

    // Множество известных узлов, чтобы не спамить сообщениями о новых соседях
    private final Set<String> knownNodes = new HashSet<>();

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

        new Thread(this::listenForDiscoveries).start();

        // Увеличили интервал до 10 секунд
        scheduler.scheduleAtFixedRate(this::broadcastDiscovery,
                0, DISCOVERY_INTERVAL, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() ->
                        routingTable.cleanupStaleNeighbors(NEIGHBOR_TIMEOUT),
                30, 30, TimeUnit.SECONDS); // очистка реже

        System.out.println("🔍 Discovery service запущен (поиск соседей каждые " +
                DISCOVERY_INTERVAL + " секунд)");
    }

    private void broadcastDiscovery() {
        try {
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

            DatagramPacket sendPacket = new DatagramPacket(
                    sendData, sendData.length,
                    InetAddress.getByName(BROADCAST_ADDR), DISCOVERY_PORT
            );
            socket.send(sendPacket);

        } catch (Exception e) {
            // Игнорируем ошибки, чтобы не спамить
        }
    }

    private void listenForDiscoveries() {
        byte[] buffer = new byte[65535];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                socket.receive(packet);

                String senderIp = packet.getAddress().getHostAddress();
                if (senderIp.equals(NetworkUtils.getLocalIpAddress())) {
                    continue;
                }

                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData());
                ObjectInputStream ois = new ObjectInputStream(bais);
                MeshMessage message = (MeshMessage) ois.readObject();

                // Обрабатываем без лишнего вывода
                if (message.getType() == MeshMessage.MessageType.DISCOVERY) {
                    sendDiscoveryReply(packet.getAddress());
                } else if (message.getType() == MeshMessage.MessageType.DISCOVERY_REPLY) {
                    handleDiscoveryReply(message, packet.getAddress());
                }

            } catch (SocketException e) {
                if (running) {
                    System.err.println("Ошибка сокета: " + e.getMessage());
                }
            } catch (Exception e) {
                // Игнорируем остальные ошибки
            }
        }
    }

    private void handleDiscoveryReply(MeshMessage message, InetAddress address) {
        // Проверяем, новый ли это узел
        if (!knownNodes.contains(message.getSenderId())) {
            knownNodes.add(message.getSenderId());

            NodeInfo newNode = new NodeInfo(
                    message.getSenderId(),
                    address,
                    DISCOVERY_PORT
            );
            routingTable.updateNeighbor(newNode);

            // Показываем приглашение ввода после сообщения
            System.out.print("\n✅ Обнаружен новый узел: " + newNode.getNodeId() + "\nmesh> ");
        } else {
            // Просто обновляем время последнего контакта
            NodeInfo existingNode = routingTable.getNeighborInfo(message.getSenderId());
            if (existingNode != null) {
                existingNode.setLastSeen(new java.util.Date().toInstant());
            }
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
            // Игнорируем
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