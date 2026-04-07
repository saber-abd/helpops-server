package helpops.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class SupervisionServer {
    private static final int UDP_PORT = 5000;
    private static final List<InetSocketAddress> clients = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> historique = Collections.synchronizedList(new LinkedList<>());

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
            System.out.println("[SUPERVISION] Serveur pret sur le port " + UDP_PORT);

            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());

                if (message.startsWith("SUB:")) {
                    InetSocketAddress newClient = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    clients.add(newClient);
                    System.out.println("[SUPERVISION] nouvel abonne : " + packet.getAddress());

                    // envoid de l'historique
                    synchronized (historique) {
                        for (String event : historique) {
                            byte[] data = ("[HISTORIQUE] " + event).getBytes();
                            socket.send(new DatagramPacket(data, data.length, newClient));
                        }
                    }
                } else {
                    // maj de l'historique
                    synchronized (historique) {
                        if (historique.size() >= 10) historique.remove(0);
                        historique.add(message);
                    }

                    // diffusion
                    for (InetSocketAddress client : clients) {
                        byte[] data = message.getBytes();
                        socket.send(new DatagramPacket(data, data.length, client));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}