package network;

import ui.Main2;
import javafx.application.Platform;
import java.io.*;
import java.net.*;

public class MeetingClient {

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Main2 ui;

    public MeetingClient(String host, int port, Main2 ui) throws IOException {

        this.ui = ui;

        Socket socket = new Socket(host, port);

        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        new Thread(() -> {
            try {
                while (true) {

                    Message msg = (Message) in.readObject();

                    Platform.runLater(() -> {

                        switch (msg.getType()) {

                            case "CHAT":
                                ui.addMessage(msg.getSender() + ": " + msg.getText(), false);
                                break;

                            case "JOIN":
                            case "LEAVE":
                            case "INFO":
                                ui.addMessage(">> " + msg.getText(), false);
                                break;

                            case "VIDEO":
                                if (!msg.getSender().equals(ui.getUsername())) {
                                    ui.receiveVideoFrame(msg.getSender(), msg.getData());
                                }
                                break;

                            case "AUDIO":
                                if (!msg.getSender().equals(ui.getUsername())) {
                                    ui.playAudio(msg.getData());
                                }
                                break;

                            case "CAM_OFF":
                                ui.handleCameraOff(msg.getSender());
                                break;
                        }
                    });
                }

            } catch (Exception e) {
                Platform.runLater(() ->
                        ui.addMessage(">> Conexión perdida", false));
            }

        }).start();

        sendMessage(new Message(
                "JOIN",
                ui.getUsername(),
                ui.getUsername() + " se unió"
        ));
    }

    public void sendMessage(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
    }
}
