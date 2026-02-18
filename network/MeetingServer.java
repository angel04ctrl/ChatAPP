package network;

import java.io.*;
import java.net.*;
import java.util.*;

public class MeetingServer {

    private static final int PORT = 5001;
    static Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Servidor iniciado en puerto " + PORT);

        while (true) {
            Socket socket = serverSocket.accept();

            if (clients.size() >= 4) {
                try {
                    ObjectOutputStream tempOut = new ObjectOutputStream(socket.getOutputStream());

                    Message fullMsg = new Message(
                            "INFO",
                            "Servidor",
                            "La sala está llena (máximo 4 usuarios)");

                    tempOut.writeObject(fullMsg);
                    tempOut.flush();
                } catch (IOException e) {
                    // ignorar error
                }

                socket.close();
                continue;
            }

            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            new Thread(handler).start();
        }
    }

    public static void broadcast(Message msg) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.send(msg);
            }
        }
    }

    public static void removeClient(ClientHandler client) {
        clients.remove(client);
    }
}

class ClientHandler implements Runnable {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        try {
            while (true) {

                Message msg = (Message) in.readObject();

                // 🔥 MODIFICADO - usar getters
                if ("JOIN".equals(msg.getType())) {
                    username = msg.getSender();
                }

                // Ignorar PING/PONG internos
                if ("PING".equals(msg.getType())) {
                    send(new Message("PONG", "Servidor", ""));
                    continue;
                }

                if ("PONG".equals(msg.getType())) {
                    continue; // Ignorar PONG
                }

                // 🔥 IMPORTANTE
                // No importa si es CHAT o VIDEO,
                // el servidor actúa como RELAY y reenvía todo.
                if ("VIDEO".equals(msg.getType())) {
                    System.out.println("Retransmitiendo VIDEO de " + msg.getSender() + " a "
                            + MeetingServer.clients.size() + " clientes");
                }

                if ("AUDIO".equals(msg.getType())) {
                    // Log menos frecuente para audio
                    if (System.currentTimeMillis() % 3000 < 100) {
                        System.out.println("🔊 Retransmitiendo AUDIO de " + msg.getSender() + " ("
                                + msg.getData().length + " bytes)");
                    }
                }

                MeetingServer.broadcast(msg);
            }

        } catch (Exception e) {
            // conexión cerrada
        } finally {

            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            MeetingServer.removeClient(this);

            if (username != null) {

                // 🔥 MODIFICADO - usar getters coherentes
                Message leaveMsg = new Message(
                        "LEAVE",
                        "Servidor",
                        username + " salió de la reunión");

                MeetingServer.broadcast(leaveMsg);
            }
        }
    }

    public void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
