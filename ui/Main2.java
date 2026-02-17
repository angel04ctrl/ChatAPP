package ui;

import com.github.sarxos.webcam.Webcam;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream; // 🔥 AGREGADO
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.*;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform; // 🔥 AGREGADO
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import network.*;
import java.util.*; // 🔥 AGREGADO

public class Main2 extends Application {

    private TextArea chatArea;
    private TextField messageField;
    private MeetingClient client;
    private String username;

    private Webcam webcam; // 🔥 AGREGADO
    private Map<String, ImageView> userVideoMap = new HashMap<>(); // 🔥 AGREGADO
    private List<StackPane> videoSlots = new ArrayList<>(); // 🔥 AGREGADO
    private boolean cameraOn = true;
    private boolean micOn = true;

    private TargetDataLine microphone;
    private AudioFormat audioFormat =
            new AudioFormat(44100, 16, 1, true, true);


    @Override
    public void start(Stage stage) {

        webcam = Webcam.getDefault(); // 🔥 MODIFICADO (ahora atributo de clase)

        if (webcam != null) {
            webcam.setViewSize(new java.awt.Dimension(320, 240)); // 🔥 AGREGADO
            webcam.open();
            System.out.println("Cámara abierta: " + webcam.getName());
        } else {
            System.out.println("No se detectó cámara");
        }

        ImageView imageView = new ImageView();
        imageView.setFitWidth(320);
        imageView.setFitHeight(240);

        // ===== PEDIR NOMBRE =====
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nombre de usuario");
        dialog.setHeaderText("Ingresa tu nombre:");
        dialog.setContentText("Nombre:");
        username = dialog.showAndWait().orElse("Usuario");

        BorderPane root = new BorderPane();

        // ===== VIDEO GRID DINÁMICO =====
        GridPane videoGrid = new GridPane();
        videoGrid.setHgap(10);
        videoGrid.setVgap(10);
        videoGrid.setPadding(new Insets(10));

        // 🔥 AGREGADO - crear 4 slots dinámicos
        for (int i = 0; i < 4; i++) {
            StackPane slot = createVideoPane("Vacío");
            videoSlots.add(slot);
            videoGrid.add(slot, i % 2, i / 2);
        }

        root.setCenter(videoGrid); // 🔥 AGREGADO

        // 🔥 AGREGADO - asignar tu cámara al primer slot
        assignUserToSlot(username, imageView);

        // ===== TIMER PARA CAPTURA Y ENVÍO =====
        AnimationTimer timer = new AnimationTimer() {

            private long lastFrame = 0; // 🔥 AGREGADO (limitar FPS)

            @Override
            public void handle(long now) {

                if (now - lastFrame < 100_000_000) return; // 10 FPS
                lastFrame = now;

                if (webcam != null && webcam.isOpen()) {
                    BufferedImage bufferedImage = webcam.getImage();
                    if (bufferedImage != null) {

                        Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                        imageView.setImage(fxImage);

                        sendVideoFrame(bufferedImage); // 🔥 AGREGADO
                    }
                }
            }
        };

        timer.start();

        // ===== CHAT PANEL =====
        VBox chatBox = new VBox(10);
        chatBox.setPadding(new Insets(10));
        chatBox.setPrefWidth(300);

        Label chatLabel = new Label("Chat");

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setPrefHeight(400);

        messageField = new TextField();
        messageField.setPromptText("Escribe un mensaje...");

        Button sendButton = new Button("Enviar");
        sendButton.setOnAction(e -> sendChat());

        chatBox.getChildren().addAll(chatLabel, chatArea, messageField, sendButton);
        root.setRight(chatBox);

        // ===== CONTROLES =====
        HBox controls = new HBox(20);
        controls.setPadding(new Insets(10));
        controls.setAlignment(Pos.CENTER);

        Button micButton = new Button("Mic");
        Button camButton = new Button("Cam");
        Button leaveButton = new Button("Salir");

        leaveButton.setOnAction(e -> {
            if (webcam != null) webcam.close(); // 🔥 AGREGADO
            System.exit(0);
        });

        controls.getChildren().addAll(micButton, camButton, leaveButton);
        root.setBottom(controls);

        Scene scene = new Scene(root, 1200, 700);
        stage.setTitle("Mini Meet - " + username);
        stage.setScene(scene);
        stage.show();

        camButton.setOnAction(e -> {

            cameraOn = !cameraOn;

            if (!cameraOn) {
                webcam.close();
                camButton.setText("Cam OFF");

                try {
                    client.sendMessage(new Message("CAM_OFF", username, "off"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            } else {
                webcam.open();
                camButton.setText("Cam ON");
            }
        });

        micButton.setOnAction(e -> {

            micOn = !micOn;

            if (micOn) {
                micButton.setText("Mic ON");
                startMicrophone();
            } else {
                micButton.setText("Mic OFF");
                stopMicrophone();
            }
        });





        // ===== CONEXIÓN =====
        try {
            client = new MeetingClient("25.xxx.xxx.xxx", 5000, this);
        } catch (Exception e) {
            addMessage(">> No se pudo conectar al servidor", false);
        }
    }

    // 🔥 AGREGADO - enviar frame
    private void sendVideoFrame(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            Message msg = new Message("VIDEO", username, imageBytes);
            client.sendMessage(msg);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void startMicrophone() {

        new Thread(() -> {
            try {

                DataLine.Info info =
                        new DataLine.Info(TargetDataLine.class, audioFormat);

                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(audioFormat);
                microphone.start();

                byte[] buffer = new byte[4096];

                while (micOn && microphone != null) {


                    int bytesRead = microphone.read(buffer, 0, buffer.length);

                    if (bytesRead > 0) {
                        client.sendMessage(
                                new Message("AUDIO", username, buffer.clone())
                        );
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void stopMicrophone() {

        try {
            if (microphone != null) {
                microphone.stop();
                microphone.close();
                microphone = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public void playAudio(byte[] audioData) {

        new Thread(() -> {
            try {

                DataLine.Info info =
                        new DataLine.Info(SourceDataLine.class, audioFormat);

                SourceDataLine speakers =
                        (SourceDataLine) AudioSystem.getLine(info);

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






    // 🔥 AGREGADO - recibir frame remoto
    public void receiveVideoFrame(String sender, byte[] imageBytes) {
        Platform.runLater(() -> {
            try {
                ImageView view = userVideoMap.get(sender);

                if (view == null) {
                    view = new ImageView();
                    view.setFitWidth(320);
                    view.setFitHeight(240);
                    assignUserToSlot(sender, view);
                }

                Image img = new Image(new ByteArrayInputStream(imageBytes));
                view.setImage(img);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // 🔥 AGREGADO - asignar slot dinámico
    private void assignUserToSlot(String username, ImageView view) {
        for (StackPane slot : videoSlots) {
            if (slot.getChildren().size() == 1 && slot.getChildren().get(0) instanceof Label){
                slot.getChildren().clear();
                slot.getChildren().add(view);
                userVideoMap.put(username, view);
                break;
            }
        }
    }

    // 🔥 NUEVO - eliminar usuario cuando se va
    public void removeUser(String username) {

        ImageView view = userVideoMap.remove(username);

        if (view != null) {
            for (StackPane slot : videoSlots) {

                if (slot.getChildren().contains(view)) {

                    slot.getChildren().clear();
                    slot.getChildren().add(new Label("Vacío"));
                    break;
                }
            }
        }
    }


    private StackPane createVideoPane(String name) {
        StackPane pane = new StackPane();
        pane.setPrefSize(400, 300);
        pane.setStyle("-fx-background-color: black; -fx-border-color: gray;");

        Label label = new Label(name);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");

        pane.getChildren().add(label);
        return pane;
    }

    private void sendChat() {
        String text = messageField.getText().trim();

        if (!text.isEmpty()) {
            try {
                Message msg = new Message("CHAT", username, text);
                client.sendMessage(msg);
                messageField.clear();
            } catch (Exception e) {
                addMessage(">> Error enviando mensaje", false);
            }
        }
    }

    public void addMessage(String message, boolean own) {
        chatArea.appendText(message + "\n");
    }

    public String getUsername() {
        return username;
    }

    public static void main(String[] args) {
        launch();
    }
}
