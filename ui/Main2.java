package ui;

import java.io.IOException;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import network.Message;
import network.ChatClient;

public class Main2 extends Application {
    private ChatClient client;

    VBox messagesBox; // donde van las burbujas
    Label contactName;

    @Override
    public void start(Stage stage) {
        try {
            client = new ChatClient(192.168.1.254, 5000, this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        BorderPane root = new BorderPane();

        // ---------------- LEFT: LISTA DE CHATS ----------------
        VBox chatsList = new VBox(10);
        chatsList.setPadding(new Insets(10));
        chatsList.setPrefWidth(250);
        chatsList.setStyle("-fx-background-color: #202c33;");

        Button chat1 = createChatButton("Juan Pérez");
        Button chat2 = createChatButton("María López");

        chatsList.getChildren().addAll(chat1, chat2);
        root.setLeft(chatsList);

        // ---------------- CENTER: CHAT VIEW ----------------
        BorderPane chatView = new BorderPane();
        root.setCenter(chatView);

        // HEADER
        HBox header = new HBox(10);
        header.setPadding(new Insets(10));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #2a3942;");

        contactName = new Label("Selecciona un chat");
        contactName.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");

        Button audioBtn = new Button("📞");
        Button videoBtn = new Button("📹");

        header.getChildren().addAll(contactName, audioBtn, videoBtn);
        chatView.setTop(header);

        // MENSAJES
        messagesBox = new VBox(8);
        messagesBox.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        chatView.setCenter(scrollPane);

        // INPUT
        HBox inputBar = new HBox(10);
        inputBar.setPadding(new Insets(10));
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setStyle("-fx-background-color: #2a3942;");

        TextArea inputField = new TextArea();
        inputField.setPromptText("Escribe un mensaje...");
        inputField.setPrefRowCount(1);
        inputField.setWrapText(true);

        Button sendBtn = new Button("Enviar");

        inputBar.getChildren().addAll(inputField, sendBtn);
        chatView.setBottom(inputBar);

        // EVENTO ENVIAR MENSAJE
        sendBtn.setOnAction(e -> {
            String text = inputField.getText();
            addMessage(text, true);

            Message msg = new Message();
            msg.sender = "Yo";
            msg.content = text;

            client.sendMessage(msg);
            inputField.clear();
        });

        // EVENTO CAMBIAR CHAT
        chat1.setOnAction(e -> loadChat("Juan Pérez"));
        chat2.setOnAction(e -> loadChat("María López"));

        Scene scene = new Scene(root, 1000, 600);
        stage.setTitle("Chat estilo WhatsApp");
        stage.setScene(scene);
        stage.show();
    }

    // ---------------- MÉTODOS AUXILIARES ----------------

    private Button createChatButton(String name) {
        Button btn = new Button(name);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle("-fx-background-color: #202c33; -fx-text-fill: white;");
        return btn;
    }

    private void loadChat(String name) {
        contactName.setText(name);
        messagesBox.getChildren().clear();

        // Mensaje simulado
        addMessage("Hola 👋", false);
        addMessage("¿Cómo estás?", false);
    }

    public void addMessage(String text, boolean isMine) {
        Label msg = new Label(text);
        msg.setWrapText(true);
        msg.setPadding(new Insets(8));
        msg.setMaxWidth(300);

        HBox container = new HBox(msg);
        container.setPadding(new Insets(2));

        if (isMine) {
            container.setAlignment(Pos.CENTER_RIGHT);
            msg.setStyle("-fx-background-color: #005c4b; -fx-text-fill: white; -fx-background-radius: 8;");
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
            msg.setStyle("-fx-background-color: #3b4a54; -fx-text-fill: white; -fx-background-radius: 8;");
        }

        messagesBox.getChildren().add(container);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
