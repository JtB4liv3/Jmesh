package main.java.mesh;

import main.java.mesh.models.NodeInfo;
import main.java.mesh.utils.NetworkUtils;

import java.net.SocketException;
import java.util.Scanner;

public class MeshNode {
    private final String nodeId;
    private final RoutingTable routingTable;
    private DiscoveryService discoveryService;
    private MessageService messageService;
    private boolean running;

    public MeshNode() {
        this.nodeId = NetworkUtils.generateNodeId();
        this.routingTable = new RoutingTable(nodeId);
        System.out.println("🚀 Создан Mesh-узел с ID: " + nodeId);
        System.out.println("📡 Локальный IP: " + NetworkUtils.getLocalIpAddress());
    }

    public void start() throws SocketException {
        discoveryService = new DiscoveryService(this, routingTable);
        messageService = new MessageService(this, routingTable);

        discoveryService.start();
        messageService.start();

        running = true;
        startConsoleInterface();
    }

    public void stop() {
        running = false;
        if (discoveryService != null) {
            discoveryService.stop();
        }
        if (messageService != null) {
            messageService.stop();
        }
        System.out.println("👋 Узел остановлен");
    }

    private void startConsoleInterface() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n" + "╔" + "═".repeat(58) + "╗");
        System.out.println("║ " + "🌐 MESH NETWORK CONSOLE".toUpperCase() + " ".repeat(31) + "║");
        System.out.println("╠" + "═".repeat(58) + "╣");
        System.out.println("║ ID: " + padRight(nodeId, 53) + "║");
        System.out.println("║ IP: " + padRight(NetworkUtils.getLocalIpAddress(), 53) + "║");
        System.out.println("╠" + "═".repeat(58) + "╣");
        System.out.println("║ Команды:                                            ║");
        System.out.println("║  /list              - показать все узлы             ║");
        System.out.println("║  /send <id> <текст> - отправить сообщение           ║");
        System.out.println("║  /broadcast <текст> - отправить всем                ║");
        System.out.println("║  /exit              - выход                         ║");
        System.out.println("╚" + "═".repeat(58) + "╝\n");

        while (running) {
            System.out.print("mesh@" + nodeId.substring(0, 6) + "> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            if (input.equals("/q")) {
                stop();
                break;
            } else if (input.equals("/l")) {
                printNodes();
            } else if (input.startsWith("/s ")) {
                handleSendCommand(input);
            } else if (input.startsWith("/b ")) {
                handleBroadcastCommand(input);
            } else {
                System.out.println("❌ Неизвестная команда");
            }
        }
    }

    private String padRight(String s, int n) {
        if (s == null) return " ".repeat(n);
        if (s.length() > n) return s.substring(0, n-3) + "...";
        return String.format("%-" + n + "s", s);
    }

    private void printNodes() {
        System.out.println("\n=== Известные узлы ===");
        System.out.println("📍 Текущий узел: " + nodeId);
        System.out.println("📊 Всего соседей: " + routingTable.getNeighborCount());

        int i = 1;
        for (String nodeId : routingTable.getAllKnownNodes()) {
            if (!nodeId.equals(this.nodeId)) {
                NodeInfo info = routingTable.getNeighborInfo(nodeId);
                if (info != null) {
                    System.out.println("  " + (i++) + ". " + info);
                } else {
                    System.out.println("  " + (i++) + ". Node[" + nodeId + "] (через маршрут)");
                }
            }
        }
        System.out.println("====================\n");
    }

    private void handleSendCommand(String input) {
        String[] parts = input.split(" ", 3);
        if (parts.length < 3) {
            System.out.println("❌ Использование: /send <id> <текст>");
            return;
        }
        String targetId = parts[1];
        String text = parts[2];

        messageService.sendMessage(targetId, text);
    }

    private void handleBroadcastCommand(String input) {
        String text = input.substring(2); // после "/broadcast "
        if (text.isEmpty()) {
            System.out.println("❌ Введите текст для broadcast");
            return;
        }//broadcast
        messageService.broadcastMessage(text);
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    public String getNodeId() {
        return nodeId;
    }

    public static void main(String[] args) {
        MeshNode node = new MeshNode();
        try {
            node.start();
        } catch (SocketException e) {
            System.err.println("❌ Ошибка запуска: " + e.getMessage());
            e.printStackTrace();
        }
    }
}