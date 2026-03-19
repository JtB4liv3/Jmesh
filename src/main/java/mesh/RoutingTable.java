package main.java.mesh;

import main.java.mesh.models.NodeInfo;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingTable {
    private final String localNodeId;
    // Карта соседей: nodeId -> NodeInfo
    private final Map<String, NodeInfo> neighbors;
    // Карта маршрутов: nodeId -> nextHopNodeId
    private final Map<String, String> routes;
    // Время последнего обновления
    private final Map<String, Long> lastUpdate;

    public RoutingTable(String localNodeId) {
        this.localNodeId = localNodeId;
        this.neighbors = new ConcurrentHashMap<>();
        this.routes = new ConcurrentHashMap<>();
        this.lastUpdate = new ConcurrentHashMap<>();
    }

    // Добавить или обновить соседа
    public void updateNeighbor(NodeInfo neighbor) {
        neighbor.setLastSeen(new java.util.Date().toInstant());

        // Проверяем, действительно ли это новый сосед
        boolean isNew = !neighbors.containsKey(neighbor.getNodeId());

        neighbors.put(neighbor.getNodeId(), neighbor);
        lastUpdate.put(neighbor.getNodeId(), System.currentTimeMillis());
        routes.put(neighbor.getNodeId(), neighbor.getNodeId());

        // Выводим сообщение ТОЛЬКО для нового соседа
        if (isNew) {
            System.out.println("\n🔔 Новый сосед обнаружен: " + neighbor);
            System.out.print("mesh> "); // Возвращаем приглашение ввода
        }
    }

// Убираем автоматическую печать таблицы
// routingTable.printRoutingTable(); - удаляем эту строку из DiscoveryService

    // Удалить неактивных соседей
    public void cleanupStaleNeighbors(long timeoutMs) {
        long now = System.currentTimeMillis();
        neighbors.entrySet().removeIf(entry -> {
            Long lastSeen = lastUpdate.get(entry.getKey());
            if (lastSeen != null && (now - lastSeen) > timeoutMs) {
                System.out.println("❌ Узел " + entry.getKey() + " отключился (таймаут)");
                routes.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    // Получить следующий хоп для достижения цели
    public String getNextHop(String targetNodeId) {
        // Если цель - сосед, отправляем напрямую
        if (neighbors.containsKey(targetNodeId)) {
            return targetNodeId;
        }
        // Иначе смотрим в таблице маршрутов
        return routes.get(targetNodeId);
    }

    // Обновить маршрут (получен от другого узла)
    public void updateRoute(String targetNodeId, String nextHopId, int hopCount) {
        String existingNextHop = routes.get(targetNodeId);
        if (existingNextHop == null) {
            routes.put(targetNodeId, nextHopId);
            System.out.println("🔄 Новый маршрут до " + targetNodeId + " через " + nextHopId);
        }
    }

    // Получить всех соседей
    public Collection<NodeInfo> getAllNeighbors() {
        return neighbors.values();
    }

    // Получить список активных узлов (соседи + известные маршруты)
    public Set<String> getAllKnownNodes() {
        Set<String> nodes = new HashSet<>(neighbors.keySet());
        nodes.addAll(routes.keySet());
        nodes.add(localNodeId); // добавляем себя
        return nodes;
    }

    public int getNeighborCount() {
        return neighbors.size();
    }

    public NodeInfo getNeighborInfo(String nodeId) {
        return neighbors.get(nodeId);
    }

    public void printRoutingTable() {
        System.out.println("\n=== Таблица маршрутизации ===");
        System.out.println("Локальный узел: " + localNodeId);
        System.out.println("Прямые соседи:");
        for (NodeInfo neighbor : neighbors.values()) {
            System.out.println("  └─ " + neighbor);
        }
        System.out.println("Маршруты:");
        for (Map.Entry<String, String> route : routes.entrySet()) {
            if (!neighbors.containsKey(route.getKey())) {
                System.out.println("  └─ До " + route.getKey() + " через " + route.getValue());
            }
        }
        System.out.println("============================\n");
    }
}