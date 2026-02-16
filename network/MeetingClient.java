package network;

import ui.Main2;
import javafx.application.Platform;
import java.io.*;
import java.net.*;

public class MeetingClient {

    private ObjectOutputStream out;
    private ObjectInputStream in;

    public MeetingClient(String host, int port, Main2 ui) throws IOException {

        Socket socket = new Socket(host, port);

        // IMPORTANTE: output primero
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        // Hilo para ESCUCHAR mensajes
        new Thread(() -> {
            try {
                while (true) {
                    Message msg = (Message) in.readObject();

                    
                    Platform.runLater(() -> {
                        if ("CHAT".equals(msg.type)){
                            ui.addMessage(msg.sender + ": " + msg.content, false);
                        } else if (msg.type.equals("JOIN")) {
                            ui.addMessage(">> " + msg.content, false);
                        } else if (msg.type.equals("LEAVE")) {
                            ui.addMessage(">> " + msg.content, false);
                        }else if ("INFO".equals(msg.type)) {
                            ui.addMessage(">> " + msg.content, false);
                        }

                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    ui.addMessage(">> Conexión perdida con el servidor", false);
                });
            }

        }).start();

        Message joinMsg = new Message("JOIN", ui.getUsername(), 
        ui.getUsername() + " se unió a la reunión");
        sendMessage(joinMsg);
    }

    public void sendMessage(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
    }


    

}