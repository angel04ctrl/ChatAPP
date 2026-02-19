package network;

import ui.Main2;
import javafx.application.Platform;
import java.io.*;
import java.net.*;

/**
 * Cliente TCP que maneja comunicación con el servidor.
 * Funciona como intermediario entre la UI y la red.
 * 
 * Funcionamiento general: Establece conexión TCP al servidor, crea streams para enviar/recibir,
 * lanza un hilo para recibir mensajes y procesarlos en la UI via Platform.runLater (para thread-safety en JavaFX).
 * Envía un JOIN inicial y maneja todos los tipos de mensajes recibidos.
 */

public class MeetingClient {

    private ObjectOutputStream out; // Stream para enviar mensajes al servidor.
    private ObjectInputStream in; // Stream para recibir mensajes del servidor.
    private Main2 ui; // Referencia a la UI para actualizarla con mensajes recibidos.

    // Constructor: establece conexión y lanza hilo receptor.
    // Funcionamiento: Crea socket, inicializa streams, lanza hilo para recepción,
    // y envía mensaje JOIN inicial con el username.
    public MeetingClient(String host, int port, Main2 ui) throws IOException {

        this.ui = ui;

        Socket socket = new Socket(host, port);

        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush(); // Flush para enviar el header del stream inmediatamente.
        in = new ObjectInputStream(socket.getInputStream());
        
        // Hilo para recibir mensajes. Funcionamiento: Bucle while(true) lee mensajes,
        // y usa Platform.runLater para actualizar UI en el thread principal de JavaFX.
        new Thread(() -> {
            try {
                while (true) {

                    Message msg = (Message) in.readObject(); // Lee mensaje serializado.

                    Platform.runLater(() -> {

                        // Switch basado en el tipo de mensaje. Procesa cada tipo de forma específica.
                        switch (msg.getType()) {

                            case "CHAT":
                                ui.addMessage(msg.getSender() + ": " + msg.getText(), false); // Agrega al chat.
                                break;

                            case "JOIN":
                                ui.addMessage(">> " + msg.getText(), false); // Notifica unión.
                                if (!msg.getSender().equals(ui.getUsername())) {
                                    ui.addUserPlaceholder(msg.getSender()); // Crea placeholder para nuevo usuario.
                                }
                                break;
                                
                            case "LEAVE":
                                ui.addMessage(">> " + msg.getText(), false); // Notifica salida.
                                ui.removeUser(msg.getSender()); // Remueve vista del usuario.
                                break;

                            case "INFO":
                                ui.addMessage(">> " + msg.getText(), false); // Mensajes informativos del servidor.
                                break;

                            case "VIDEO":
                                if (!msg.getSender().equals(ui.getUsername())) {
                                    ui.receiveVideoFrame(msg.getSender(), msg.getData()); // Muestra frame recibido.
                                }
                                break;

                            case "AUDIO":
                                if (!msg.getSender().equals(ui.getUsername())) {
                                    ui.playAudio(msg.getData()); // Reproduce audio recibido.
                                }
                                break;

                            case "CAM_OFF":
                                ui.handleCameraOff(msg.getSender()); // Maneja cámara apagada.
                                break;
                        }
                    });
                }

            } catch (Exception e) {
                Platform.runLater(() ->
                        ui.addMessage(">> Conexión perdida: " + e.getMessage(), false)); // Muestra error en UI.
            }

        }).start();

        // Envío inicial de JOIN. Notifica al servidor y otros clientes.
        sendMessage(new Message(
                "JOIN",
                ui.getUsername(),
                ui.getUsername() + " se unió"
        ));
    }
    
    // Envía mensaje al servidor.
    // Funcionamiento: Escribe el objeto en out y flush para enviarlo inmediatamente.
    public synchronized void sendMessage(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
    }
}