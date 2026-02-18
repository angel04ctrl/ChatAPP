package ui;

import java.net.URL;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import network.*;
import utils.CameraCapture;
import java.util.*;

public class Main2 extends Application {

    private TextArea chatArea;
    private TextField messageField;
    private MeetingClient client;
    private String username;
    private CameraCapture camera;
    private Map<String, ImageView> userVideoMap = new HashMap<>();
    private GridPane videoGrid;
    private boolean cameraOn = true;
    private boolean micOn = true;
    private TargetDataLine microphone;
    private AudioFormat audioFormat = new AudioFormat(44100, 16, 1, true, true);
    private Label camStatusLabel;

    @Override
    public void start(Stage stage) {

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nombre de usuario");
        dialog.setHeaderText("Ingresa tu nombre:");
        dialog.setContentText("Nombre:");
        username = dialog.showAndWait().orElse("Usuario");

        // Initialize camera
        try {
            camera = new CameraCapture(0);
        } catch (Exception e) {
            System.err.println("Warning: Could not initialize camera: " + e.getMessage());
            camera = null;
        }

        BorderPane root = new BorderPane();

        // ================= VIDEO GRID =================
        videoGrid = new GridPane();
        videoGrid.setHgap(10);
        videoGrid.setVgap(10);
        videoGrid.setPadding(new Insets(10));
        videoGrid.setStyle("-fx-background-color: #181818;");

        ColumnConstraints col = new ColumnConstraints();
        col.setHgrow(Priority.ALWAYS);
        col.setPercentWidth(100);

        RowConstraints row = new RowConstraints();
        row.setVgrow(Priority.ALWAYS);
        row.setPercentHeight(100);

        videoGrid.getColumnConstraints().add(col);
        videoGrid.getRowConstraints().add(row);

        // ================= LOCAL VIDEO =================
        ImageView localView = createVideoView();
        userVideoMap.put(username, localView);
        updateGridLayout();

        // ================= TIMER =================
        AnimationTimer timer = new AnimationTimer() {
            private long lastFrame = 0;
            private long lastSend = 0;
            private long lastStatus = 0;

            @Override
            public void handle(long now) {

                if (now - lastFrame < 100_000_000)
                    return;
                lastFrame = now;

                if (cameraOn && camera != null && camera.isOpened()) {
                    Image frame = camera.captureFrame();
                    if (frame != null) {
                        ImageView view = userVideoMap.get(username);
                        if (view != null) {
                            view.setImage(frame);
                        }

                        if (client != null && now - lastSend >= 200_000_000) {
                            BufferedImage buffered = SwingFXUtils.fromFXImage(frame, null);
                            if (buffered != null) {
                                sendVideoFrame(buffered);
                                lastSend = now;
                            }
                        }
                    }
                }

                if (now - lastStatus >= 500_000_000) {
                    updateCameraStatus();
                    lastStatus = now;
                }
            }
        };
        timer.start();

        // ================= CHAT =================
        VBox chatBox = new VBox(10);
        chatBox.setPadding(new Insets(10));
        chatBox.setMinWidth(300);
        chatBox.setPrefWidth(300);
        chatBox.setMaxWidth(300);

        Label chatLabel = new Label("Chat");

        chatArea = new TextArea();
        chatArea.setEditable(false);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        messageField = new TextField();
        messageField.setPromptText("Escribe un mensaje...");

        Button sendButton = new Button("Enviar");
        sendButton.setOnAction(e -> sendChat());

        chatBox.getChildren().addAll(chatLabel, chatArea, messageField, sendButton);

        // ================= SPLITPANE (FIX REAL) =================
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(videoGrid, chatBox);
        splitPane.setDividerPositions(0.75);
        splitPane.setResizableWithParent(chatBox, false);

        root.setCenter(splitPane);

        // ================= CONTROLES =================
        HBox controls = new HBox(20);
        controls.setPadding(new Insets(10));
        controls.setAlignment(Pos.CENTER);

        Button micButton = new Button("Mic ON");
        Button camButton = new Button("Cam ON");
        Button leaveButton = new Button("Salir");

        camStatusLabel = new Label("Cam: iniciando...");

        leaveButton.setOnAction(e -> {
            if (camera != null) {
                camera.close();
            }
            System.exit(0);
        });

        camButton.setOnAction(e -> toggleCamera(camButton));
        micButton.setOnAction(e -> toggleMic(micButton));

        controls.getChildren().addAll(micButton, camButton, leaveButton, camStatusLabel);
        root.setBottom(controls);

        Scene scene = new Scene(root, 1200, 700);
        stage.setTitle("Mini Meet - " + username);
        stage.setScene(scene);
        stage.show();
        stage.centerOnScreen();
        Platform.runLater(() -> {
            stage.toFront();
            stage.requestFocus();
        });

        URL cssURL = getClass().getResource("/style.css");
        if (cssURL != null) {
            scene.getStylesheets().add(cssURL.toExternalForm());
        }

        // Conectar en background (crítico para macOS)
        addMessage(">> Conectando al servidor...", false);
        Thread connectionThread = new Thread(() -> {
            try {
                client = new MeetingClient(this);
            } catch (Exception e) {
                addMessage(">> Error: No se pudo conectar - " + e.getMessage(), false);
                e.printStackTrace();
            }
        });
        connectionThread.setDaemon(true);
        connectionThread.start();

    }

    // ================= VIDEO VIEW FACTORY =================
    private ImageView createVideoView() {

        ImageView view = new ImageView();

        view.setFitWidth(400); // 🔥 tamaño visual fijo
        view.setFitHeight(300);
        view.setPreserveRatio(true);
        view.setSmooth(true);

        return view;
    }

    private StackPane createVideoContainer(ImageView view) {

        StackPane container = new StackPane(view);

        container.setStyle(
                "-fx-background-color: transparent;");

        view.setPreserveRatio(true);

        view.fitWidthProperty().bind(container.widthProperty());
        view.fitHeightProperty().bind(container.heightProperty());

        return container;
    }

    // ================= GRID LAYOUT FIX DEFINITIVO =================
    private void updateGridLayout() {

        videoGrid.getChildren().clear();

        int total = userVideoMap.size();
        if (total == 0)
            return;

        int cols = (int) Math.ceil(Math.sqrt(total));
        int rows = (int) Math.ceil((double) total / cols);

        videoGrid.getColumnConstraints().clear();
        videoGrid.getRowConstraints().clear();

        for (int i = 0; i < cols; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / cols);
            col.setHgrow(Priority.ALWAYS);
            videoGrid.getColumnConstraints().add(col);
        }

        for (int i = 0; i < rows; i++) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(100.0 / rows);
            row.setVgrow(Priority.ALWAYS);
            videoGrid.getRowConstraints().add(row);
        }

        int index = 0;

        for (ImageView view : userVideoMap.values()) {

            StackPane container = createVideoContainer(view);

            int col = index % cols;
            int row = index / cols;

            videoGrid.add(container, col, row);

            index++;
        }
    }

    private void updateCameraStatus() {
        if (camStatusLabel == null) {
            return;
        }

        if (camera == null) {
            camStatusLabel.setText("Cam: sin inicializar");
            return;
        }

        if (!cameraOn) {
            camStatusLabel.setText("Cam: apagada");
            return;
        }

        long lastAt = camera.getLastFrameAt();
        long ageMs = lastAt == 0 ? -1 : (System.currentTimeMillis() - lastAt);
        long frames = camera.getFrameCount();
        String ageText = ageMs < 0 ? "sin frames" : (ageMs + " ms");

        camStatusLabel.setText("Cam: on | frames=" + frames + " | edad=" + ageText);
    }

    // ================= CAMERA =================
    private void toggleCamera(Button button) {

        cameraOn = !cameraOn;

        if (!cameraOn) {
            button.setText("Cam OFF");
            ImageView view = userVideoMap.get(username);
            if (view != null) {
                view.setImage(null);
            }
            if (client != null) {
                try {
                    client.sendMessage(new Message("CAM_OFF", username, ""));
                } catch (IOException ignored) {
                }
            }
        } else {
            button.setText("Cam ON");
        }
    }

    // ================= MIC =================
    private void toggleMic(Button button) {

        micOn = !micOn;

        if (micOn) {
            button.setText("Mic ON");
            startMicrophone();
        } else {
            button.setText("Mic OFF");
            stopMicrophone();
        }
    }

    // ================= NETWORK VIDEO =================
    private void sendVideoFrame(BufferedImage image) {
        if (client == null)
            return;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            client.sendMessage(new Message("VIDEO", username, baos.toByteArray()));
        } catch (Exception ignored) {
        }
    }

    public void receiveVideoFrame(String sender, byte[] imageBytes) {

        Platform.runLater(() -> {

            ImageView view = userVideoMap.get(sender);

            if (view == null) {
                view = createVideoView();
                userVideoMap.put(sender, view);
                updateGridLayout();
            }

            view.setImage(new Image(new ByteArrayInputStream(imageBytes)));
        });
    }

    // ================= AUDIO =================
    private void startMicrophone() {
        new Thread(() -> {
            try {

                DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(audioFormat);
                microphone.start();

                byte[] buffer = new byte[4096];

                while (micOn && microphone != null) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && client != null) {
                        try {
                            client.sendMessage(new Message("AUDIO", username, buffer.clone()));
                        } catch (IOException ignored) {
                        }

                    }
                }

            } catch (Exception ignored) {
            }
        }).start();
    }

    public String getUsername() {
        return username;
    }

    public void playAudio(byte[] audioData) {

        new Thread(() -> {
            try {

                DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

                SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);

                speakers.open(audioFormat);
                speakers.start();

                speakers.write(audioData, 0, audioData.length);

                speakers.drain();
                speakers.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void handleCameraOff(String user) {

        Platform.runLater(() -> {
            ImageView view = userVideoMap.get(user);
            if (view != null) {
                view.setImage(null);
            }
        });
    }

    private void stopMicrophone() {
        try {
            if (microphone != null) {
                microphone.stop();
                microphone.close();
                microphone = null;
            }
        } catch (Exception ignored) {
        }
    }

    public void addMessage(String message, boolean own) {
        chatArea.appendText(message + "\n");
    }

    private void sendChat() {
        String text = messageField.getText().trim();
        if (!text.isEmpty() && client != null) {
            try {
                client.sendMessage(new Message("CHAT", username, text));
                messageField.clear();
            } catch (IOException e) {
                addMessage(">> Error enviando mensaje", false);
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
