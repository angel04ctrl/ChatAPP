package network;

import java.io.*;
import java.net.*;
import java.util.*;
/**
 * Servidor principal del sistema de videoconferencia.
 * Administra conexiones entrantes y retransmite mensajes
 * entre todos los clientes conectados.
 */

public class MeetingServer {
// Puerto de escucha del servidor
    private static final int PORT = 5000;
    // Conjunto sincronizado de clientes conectados
    private static Set<ClientHandler> clients =
            Collections.synchronizedSet(new HashSet<>());
     /**
     * Punto de entrada del servidor.
     * Escucha conexiones de forma indefinida.
     */
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Servidor iniciado en puerto " + PORT);

        while (true) {
            // Espera conexión entrante
            Socket socket = serverSocket.accept();
            // Límite de participantes (máx 4)
            if (clients.size() >= 4) {
                try {
                    ObjectOutputStream tempOut =
                            new ObjectOutputStream(socket.getOutputStream());

                    Message fullMsg = new Message(
                            "INFO",
                            "Servidor",
                            "La sala está llena (máximo 4 usuarios)"
                    );

                    tempOut.writeObject(fullMsg);
                    tempOut.flush();
                } catch (IOException e) {
                    // ignorar error
                }

                socket.close();
                continue;
            }
            // Crear manejador de cliente
            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            new Thread(handler).start();
        }
    }
    
     //Envía un mensaje a todos los clientes conectados.
     
    public static void broadcast(Message msg) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.send(msg);
            }
        }
    }
    
     //Elimina cliente de la lista activa.
    
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
    }
}

class ClientHandler implements Runnable {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    //Constructor que inicializa streams de comunicación.
     
    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }
    
  //Bucle principal de escucha de mensajes.
     
    @Override
    public void run() {
        try {
            while (true) {

                Message msg = (Message) in.readObject();

                // Registrar usuario cuando envía JOIN
                if ("JOIN".equals(msg.getType())) {
                    username = msg.getSender();
                }

                // No importa si es CHAT o VIDEO,
                // el servidor actúa como RELAY y reenvía todo.
                // Retransmisión a todos los clientes
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
            // Notificar salida del usuario
            if (username != null) {

                
                Message leaveMsg = new Message(
                        "LEAVE",
                        "Servidor",
                        username + " salió de la reunión"
                );

                MeetingServer.broadcast(leaveMsg);
            }
        }
    }
  
     //Envía mensaje a este cliente específico.
    
    public void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
