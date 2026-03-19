package main.java.mesh.utils;

import java.net.*;
import java.util.Enumeration;

public class NetworkUtils {

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "127.0.0.1";
    }

    public static String generateNodeId() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            String hostname = localhost.getHostName();
            String ip = localhost.getHostAddress();
            String mac = getMacAddress();
            // Создаем уникальный ID из hostname, IP и MAC
            String rawId = hostname + "-" + ip + "-" + mac;
            return Integer.toHexString(rawId.hashCode());
        } catch (Exception e) {
            return "node-" + System.currentTimeMillis();
        }
    }

    private static String getMacAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                byte[] hardwareAddress = ni.getHardwareAddress();
                if (hardwareAddress != null) {
                    StringBuilder mac = new StringBuilder();
                    for (byte b : hardwareAddress) {
                        mac.append(String.format("%02X", b));
                    }
                    return mac.toString();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "00-00-00-00-00-00";
    }
}