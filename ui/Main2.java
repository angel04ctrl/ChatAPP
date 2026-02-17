package ui;

import com.github.sarxos.webcam.Webcam;

import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import network.*;

public class Main2 extends Application {

    private TextArea chatArea;
    private TextField messageField;
    private MeetingClient client;
    private String username;

    @Override
    public void start(Stage stage) {

        Webcam webcam = Webcam.getDefault();
        webcam.open();
        System.out.println("Cámara abierta: " + webcam.getName());


        // ===== PEDIR NOMBRE =====
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nombre de usuario");
        dialog.setHeaderText("Ingresa tu nombre:");
        dialog.setContentText("Nombre:");

        username = dialog.showAndWait().orElse("Usuario");

        BorderPane root = new BorderPane();

        // ===== VIDEO GRID =====
        GridPane videoGrid = new GridPane();
        videoGrid.setHgap(10);
        videoGrid.setVgap(10);
        videoGrid.setPadding(new Insets(10));

        for (int i = 0; i < 4; i++) {
            StackPane videoPane = createVideoPane("Vacío");
            videoGrid.add(videoPane, i % 2, i / 2);
        }

        root.setCenter(videoGrid);

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
            System.exit(0);
        });

        controls.getChildren().addAll(micButton, camButton, leaveButton);
        root.setBottom(controls);

        Scene scene = new Scene(root, 1200, 700);
        stage.setTitle("Mini Meet - " + username);
        stage.setScene(scene);
        stage.show();

        // ===== CONEXIÓN AL SERVIDOR =====
        try {
            client = new MeetingClient("25.xxx.xxx.xxx", 5000, this);
            // 🔥 IMPORTANTE:
            // Reemplaza 25.xxx.xxx.xxx por la IP Hamachi del servidor
        } catch (Exception e) {
            addMessage(">> No se pudo conectar al servidor", false);
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
