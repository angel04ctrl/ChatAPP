package network;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Servidor principal del sistema de videoconferencia.
 * Administra conexiones entrantes y retransmite mensajes
 * entre todos los clientes conectados.
 * 
 * Funcionamiento general: Este servidor escucha en un puerto TCP (5000 por defecto),
 * acepta conexiones de clientes (hasta 4), y actúa como un relay: recibe mensajes de un cliente
 * y los envía a todos los demás. No procesa el contenido (video, audio, chat), solo retransmite.
 * Usa un conjunto sincronizado para manejar clientes concurrentemente.
 */

public class MeetingServer {
    // Puerto de escucha del servidor. Este es el puerto donde los clientes se conectan.
    private static final int PORT = 5000;
    // Conjunto sincronizado de clientes conectados. Usamos HashSet con sincronización para manejar accesos concurrentes desde múltiples hilos.
    private static Set<ClientHandler> clients =
            Collections.synchronizedSet(new HashSet<>());

    /**
     * Punto de entrada del servidor.
     * Escucha conexiones de forma indefinida.
     * 
     * Funcionamiento: Crea un ServerSocket, imprime un mensaje de inicio, y entra en un bucle infinito
     * aceptando sockets. Si hay más de 4 clientes, rechaza la conexión enviando un mensaje de "sala llena".
     * Para cada conexión válida, crea un ClientHandler y lo inicia en un hilo separado.
     */
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Servidor iniciado en puerto " + PORT);

        while (true) {
            // Espera conexión entrante. Este método bloquea hasta que llega un cliente.
            Socket socket = serverSocket.accept();
            // Límite de participantes (máx 4). Verificamos el tamaño del set de clientes.
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
                    // ignorar error. Comentario original: Ignoramos errores al rechazar, para no detener el servidor.
                    System.out.println("Error al rechazar conexión: " + e.getMessage());
                }

                socket.close();
                continue;
            }
            // Crear manejador de cliente. El handler maneja la comunicación con este socket específico.
            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            new Thread(handler).start(); // Iniciamos el handler en un hilo separado para no bloquear el main loop.
        }
    }
    
    // Envía un mensaje a todos los clientes conectados.
    // Funcionamiento: Usa un bloque sincronizado para iterar sobre el set de clientes y enviar el mensaje a cada uno.
    // Esto previene problemas de concurrencia si se agregan/quitan clientes mientras se envía.
    public static void broadcast(Message msg) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                try {
                    client.send(msg); // Llamamos al método send del handler.
                } catch (Exception e) {
                    System.out.println("Error en broadcast a cliente: " + e.getMessage());
                    // Si falla el envío a un cliente, lo removemos para limpiar.
                    removeClient(client);
                }
            }
        }
    }
    
    // Elimina cliente de la lista activa.
    // Funcionamiento: Simplemente remueve el handler del set. Se llama cuando un cliente se desconecta o falla.
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Cliente removido: " + client); // Logging para depuración.
    }
}

/**
 * Clase que maneja la comunicación con un cliente individual.
 * Implementa Runnable para ejecutarse en un hilo separado.
 * 
 * Funcionamiento general: Inicializa streams de entrada/salida, escucha mensajes en un bucle,
 * los retransmite via broadcast, y maneja desconexiones notificando a otros clientes.
 */
class ClientHandler implements Runnable {

    private Socket socket; // Socket de conexión con el cliente.
    private ObjectOutputStream out; // Stream para enviar objetos (mensajes) al cliente.
    private ObjectInputStream in; // Stream para recibir objetos del cliente.
    private String username; // Nombre del usuario, registrado al recibir JOIN.

    // Constructor que inicializa streams de comunicación.
    // Funcionamiento: Crea los streams ObjectOutput/Input sobre el socket. Debe hacerse en este orden para evitar deadlocks.
    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush(); // Flush para asegurar que el header del stream se envíe inmediatamente.
        in = new ObjectInputStream(socket.getInputStream());
    }
    
    // Bucle principal de escucha de mensajes.
    // Funcionamiento: En un bucle while(true), lee objetos Message del input stream.
    // Si es JOIN, registra el username. Luego, retransmite el mensaje a todos via broadcast.
    // Si hay excepción (e.g., desconexión), cierra el socket y notifica LEAVE.
    @Override
    public void run() {
        try {
            while (true) {

                Message msg = (Message) in.readObject(); // Lee el mensaje serializado.

                // Registrar usuario cuando envía JOIN. Esto asocia el nombre al handler.
                if ("JOIN".equals(msg.getType())) {
                    username = msg.getSender();
                    System.out.println("Usuario unido: " + username); // Logging.
                }

                // No importa si es CHAT o VIDEO,
                // el servidor actúa como RELAY y reenvía todo.
                // Retransmisión a todos los clientes. El servidor no filtra, solo relay.
                MeetingServer.broadcast(msg);
            }

        } catch (Exception e) {
            // conexión cerrada. Comentario original: Maneja fin de conexión o errores.
            System.out.println("Error en handler: " + e.getMessage()); // Logging para depuración, útil en Mac.
        } finally {

            try {
                socket.close(); // Cierra el socket al finalizar.
            } catch (IOException e) {
                System.out.println("Error cerrando socket: " + e.getMessage());
            }

            MeetingServer.removeClient(this); // Remueve del set de clientes.
            // Notificar salida del usuario. Solo si username fue registrado.
            if (username != null) {

                
                Message leaveMsg = new Message(
                        "LEAVE",
                        "Servidor",
                        username + " salió de la reunión"
                );

                MeetingServer.broadcast(leaveMsg); // Envía notificación a los demás.
            }
        }
    }
  
    // Envía mensaje a este cliente específico.
    // Funcionamiento: Escribe el objeto Message en el output stream y flush para enviarlo inmediatamente.
    public synchronized void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.out.println("Error enviando mensaje: " + e.getMessage()); // Logging.
        }
    }
}