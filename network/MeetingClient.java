package network;

import ui.Main2;
import javafx.application.Platform;
import java.io.*;
import java.net.*;

public class MeetingClient {

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Main2 ui;
    private Socket socket;
    private boolean connected = false;

    public MeetingClient(String host, int port, Main2 ui) throws IOException {

        this.ui = ui;

        // Conectar en thread separado para macOS
        Thread connectThread = new Thread(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 5000); // 5 segundo timeout

                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                connected = true;
                Platform.runLater(() -> ui.addMessage(">> Conectado al servidor", false));

                // Thread de recepción
                new Thread(() -> {
                    try {
                        while (connected) {
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
                        if (connected) {
                            Platform.runLater(() -> ui.addMessage(">> Conexión perdida", false));
                        }
                    }
                }).start();

                sendMessage(new Message(
                        "JOIN",
                        ui.getUsername(),
                        ui.getUsername() + " se unió"
                ));

            } catch (SocketTimeoutException e) {
                Platform.runLater(() -> ui.addMessage(">> Error: Timeout al conectar", false));
            } catch (ConnectException e) {
                Platform.runLater(() -> ui.addMessage(">> Error: Servidor no disponible", false));
            } catch (Exception e) {
                Platform.runLater(() -> ui.addMessage(">> Error: " + e.getMessage(), false));
            }
        });
        connectThread.setDaemon(true);
        connectThread.start();
    }

    public void sendMessage(Message msg) throws IOException {
        if (connected && out != null) {
            out.writeObject(msg);
            out.reset();
            out.flush();
        }
    }
}
