package network;

import ui.Main2;
import javafx.application.Platform;
import java.io.*;
import java.net.*;

public class ChatClient {

    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ChatClient(String host, int port, Main2 ui) throws IOException {

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
                        ui.addMessage(msg.content, false);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void sendMessage(Message msg) throws IOException {
        out.writeObject(msg);
    }
}