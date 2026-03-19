package mesh.models;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;

public class NodeInfo {
    private String nodeId;
    private InetAddress address;
    private int port;
    private Instant lastSeen;
    private int hopCount; // количество прыжков до этого узла

    public NodeInfo(String nodeId, InetAddress address, int port) {
        this.nodeId = nodeId;
        this.address = address;
        this.port = port;
        this.lastSeen = Instant.now();
        this.hopCount = 1;
    }

    // Геттеры и сеттеры
    public String getNodeId() { return nodeId; }
    public InetAddress getAddress() { return address; }
    public int getPort() { return port; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeInfo nodeInfo = (NodeInfo) o;
        return Objects.equals(nodeId, nodeInfo.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }

    @Override
    public String toString() {
        return String.format("Node[%s] at %s:%d (hops: %d)",
                nodeId, address.getHostAddress(), port, hopCount);
    }
}