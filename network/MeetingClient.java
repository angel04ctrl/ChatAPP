package network;

import ui.Main2;
import javafx.application.Platform;
import java.io.*;
import java.net.*;

public class MeetingClient {

    public static final String DEFAULT_HOST = "4.tcp.ngrok.io";
    public static final int DEFAULT_PORT = 13998;

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Main2 ui;
    private Socket socket;
    private boolean connected = false;
    private String host;
    private int port;
    private static final long HEARTBEAT_INTERVAL = 30000; // 30 segundos
    private static final long RECONNECT_DELAY = 5000; // 5 segundos inicial
    private static final long MAX_RECONNECT_DELAY = 60000; // 60 segundos máximo
    private long currentReconnectDelay = RECONNECT_DELAY;
    private boolean isReconnecting = false;

    public MeetingClient(Main2 ui) throws IOException {
        this(DEFAULT_HOST, DEFAULT_PORT, ui);
    }

    public MeetingClient(String host, int port, Main2 ui) throws IOException {
        this.ui = ui;
        this.host = host;
        this.port = port;
        startConnection();
    }

    private void startConnection() {
        // Conectar en thread separado para macOS
        Thread connectThread = new Thread(() -> {
            attemptConnection();
        });
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void attemptConnection() {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000); // 5 segundo timeout

            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            connected = true;
            isReconnecting = false;
            currentReconnectDelay = RECONNECT_DELAY; // Reset delay
            Platform.runLater(() -> ui.addMessage(">> Conectado al servidor", false));

            // Thread de heartbeat para mantener conexión viva
            new Thread(() -> {
                while (connected) {
                    try {
                        Thread.sleep(HEARTBEAT_INTERVAL);
                        if (connected && out != null) {
                            synchronized (out) {
                                out.writeObject(new Message("PING", "client", ""));
                                out.flush();
                            }
                        }
                    } catch (Exception e) {
                        // ignorar errores de heartbeat
                    }
                }
            }).start();

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

                                case "PING":
                                    // Responder al servidor con PONG
                                    try {
                                        sendMessage(new Message("PONG", ui.getUsername(), ""));
                                    } catch (IOException e) {
                                        // ignorar
                                    }
                                    break;

                                case "PONG":
                                    // Ignorar respuesta del servidor
                                    break;

                                case "VIDEO":
                                    if (!msg.getSender().equals(ui.getUsername())) {
                                        ui.receiveVideoFrame(msg.getSender(), msg.getData());
                                    }
                                    break;

                                case "AUDIO":
                                    if (!msg.getSender().equals(ui.getUsername())) {
                                        // Log de audio recibido (reducido)
                                        if (System.currentTimeMillis() % 3000 < 100) {
                                            System.out.println("🔊 Audio recibido de " + msg.getSender() + ": "
                                                    + msg.getData().length + " bytes");
                                        }
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
                        connected = false;
                        Platform.runLater(() -> ui.addMessage(">> Conexión perdida, intentando reconectar...", false));
                        attemptReconnection();
                    }
                }
            }).start();

            sendMessage(new Message(
                    "JOIN",
                    ui.getUsername(),
                    ui.getUsername() + " se unió"));

        } catch (SocketTimeoutException e) {
            Platform.runLater(() -> ui.addMessage(">> Timeout al conectar, reintentando...", false));
            attemptReconnection();
        } catch (ConnectException e) {
            Platform.runLater(() -> ui.addMessage(">> Error: Servidor no disponible, reintentando...", false));
            attemptReconnection();
        } catch (Exception e) {
            Platform.runLater(() -> ui.addMessage(">> Error: " + e.getMessage() + ", reintentando...", false));
            attemptReconnection();
        }
    }

    private void attemptReconnection() {
        if (isReconnecting) {
            return; // Ya hay un reintento en progreso
        }

        isReconnecting = true;

        new Thread(() -> {
            while (!connected) {
                try {
                    long delay = Math.min(currentReconnectDelay, MAX_RECONNECT_DELAY);
                    Platform.runLater(() -> ui.addMessage(">> Reconectando en " + (delay / 1000) + "s...", false));
                    Thread.sleep(delay);

                    // Incrementar delay para próximo intento (exponencial backoff)
                    currentReconnectDelay = Math.min(currentReconnectDelay * 2, MAX_RECONNECT_DELAY);

                    Platform.runLater(() -> ui.addMessage(">> Intentando conectar de nuevo...", false));
                    attemptConnection();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            isReconnecting = false;
        }).start();
    }

    public void sendMessage(Message msg) throws IOException {
        if (connected && out != null) {
            synchronized (out) {
                out.writeObject(msg);
                out.reset();
                out.flush();
            }
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (out != null)
                out.close();
            if (in != null)
                in.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            // ignorar
        }
    }

    public boolean isConnected() {
        return connected;
    }
}
