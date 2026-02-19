package ui;

import java.net.URL;
import com.github.sarxos.webcam.Webcam;
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
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Clase principal de la aplicación cliente.
 * Extiende Application para inicializar entorno JavaFX.
 * 
 * Funcionamiento general: Maneja la UI de JavaFX con grid para videos, chat, controles.
 * Captura video de webcam y audio de mic, los envía via MeetingClient.
 * Recibe y muestra video/audio de otros. Limita a 4 usuarios via servidor.
 * Usa AnimationTimer para frames de video (~10 FPS), hilos para audio.
 */

public class Main2 extends Application {
    // Área de visualización del chat. TextArea no editable para mostrar mensajes.
    private TextArea chatArea;
    // Campo de entrada de mensajes. TextField para escribir chat.
    private TextField messageField;
    // Cliente de red. Instancia de MeetingClient para comunicación con servidor.
    private MeetingClient client;
    // Nombre del usuario actual. Solicitado al inicio via dialog.
    private String username;
    // Webcam utilizada para captura de video. Usa sarxos library.
    private Webcam webcam;
    // Mapa que asocia usuario → vista de video. HashMap para acceso rápido.
    private Map<String, ImageView> userVideoMap = new HashMap<>();
    // Grid dinámico donde se muestran videos. GridPane que se redimensiona automáticamente.
    private GridPane videoGrid;
    // Estados de dispositivos. Banderas para togglear cam/mic.
    private boolean cameraOn = true;
    private boolean micOn = true;
    private TargetDataLine microphone; // Línea de captura de audio.
    private AudioFormat audioFormat = new AudioFormat(44100, 16, 1, true, true); // Formato de audio: 44.1kHz, 16bits, mono.
    
    // Cola para buffers de audio recibidos. Concurrent para manejo thread-safe.
    private ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();
    // Línea de reproducción de audio. SourceDataLine para playback continuo.
    private SourceDataLine speakers;
    // Hilo para reproducción de audio. Ejecuta el playback en background.
    private Thread audioPlaybackThread;
    // Bandera para controlar si el playback está activo.
    private boolean audioPlaying = false;

    @Override
    public void start(Stage stage) {

        // Inicialización de webcam con try-catch para manejar errores (e.g., en Mac).
        try {
            webcam = Webcam.getDefault();
            if (webcam != null) {
                webcam.setViewSize(new java.awt.Dimension(320, 240));
                webcam.open();
            } else {
                addMessage(">> No se detectó webcam", false);
            }
        } catch (Exception e) {
            addMessage(">> Error en webcam: " + e.getMessage() + ". Verifica permisos.", false);
        }

        // Diálogo para obtener username. Usa TextInputDialog.
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nombre de usuario");
        dialog.setHeaderText("Ingresa tu nombre:");
        dialog.setContentText("Nombre:");
        username = dialog.showAndWait().orElse("Usuario");

        // Root layout: BorderPane para organizar centro (videos/chat) y bottom (controles).
        BorderPane root = new BorderPane();

        // ================= VIDEO GRID =================
        // Grid para videos: GridPane con gaps y padding, fondo oscuro.
        videoGrid = new GridPane();
        videoGrid.setHgap(10);
        videoGrid.setVgap(10);
        videoGrid.setPadding(new Insets(10));
        videoGrid.setStyle("-fx-background-color: #181818;");

        // Constraints para columnas/filas: Percentuales para redimensionar.
        ColumnConstraints col = new ColumnConstraints();
        col.setHgrow(Priority.ALWAYS);
        col.setPercentWidth(100);

        RowConstraints row = new RowConstraints();
        row.setVgrow(Priority.ALWAYS);
        row.setPercentHeight(100);

        videoGrid.getColumnConstraints().add(col);
        videoGrid.getRowConstraints().add(row);

        // ================= LOCAL VIDEO =================
        // Vista local: Crea ImageView para el video propio y lo agrega al mapa.
        ImageView localView = createVideoView();
        userVideoMap.put(username, localView);
        updateGridLayout(); // Actualiza grid inicial con video local.

        // ================= TIMER =================
        // Timer para captura de frames: AnimationTimer (~10 FPS).
        // Funcionamiento: Cada 100ms, captura imagen de webcam, actualiza vista local y envía si cameraOn.
        AnimationTimer timer = new AnimationTimer() {
            private long lastFrame = 0;

            @Override
            public void handle(long now) {

                if (now - lastFrame < 100_000_000) return;
                lastFrame = now;

                if (webcam != null && webcam.isOpen() && cameraOn) {

                    BufferedImage bufferedImage = webcam.getImage();
                    if (bufferedImage != null) {

                        Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

                        ImageView view = userVideoMap.get(username);
                        if (view != null) {
                            view.setImage(fxImage); // Actualiza imagen local.
                        }

                        sendVideoFrame(bufferedImage); // Envía frame al servidor.
                    } else {
                        System.out.println("Imagen null de webcam"); // Logging para depuración.
                    }
                }
            }
        };
        timer.start(); // Inicia el timer.

        // ================= CHAT =================
        // Box para chat: VBox con label, area, field y botón.
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
        sendButton.setOnAction(e -> sendChat()); // Llama a sendChat al click.

        chatBox.getChildren().addAll(chatLabel, chatArea, messageField, sendButton);

        // ================= SPLITPANE=================
        // Split para videos y chat: SplitPane con divisor en 75%.
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(videoGrid, chatBox);
        splitPane.setDividerPositions(0.75);
        splitPane.setResizableWithParent(chatBox, false);

        root.setCenter(splitPane);

        // ================= CONTROLES =================
        // Box para botones: HBox con mic, cam, salir.
        HBox controls = new HBox(20);
        controls.setPadding(new Insets(10));
        controls.setAlignment(Pos.CENTER);

        Button micButton = new Button("Mic ON");
        Button camButton = new Button("Cam ON");
        Button leaveButton = new Button("Salir");

        leaveButton.setOnAction(e -> {
            // Envía LEAVE antes de salir.
            try {
                client.sendMessage(new Message("LEAVE", username, username + " left"));
            } catch (IOException ignored) {}
            if (webcam != null) webcam.close();
            System.exit(0);
        });

        camButton.setOnAction(e -> toggleCamera(camButton)); // Toggle cam.
        micButton.setOnAction(e -> toggleMic(micButton)); // Toggle mic.

        controls.getChildren().addAll(micButton, camButton, leaveButton);
        root.setBottom(controls);

        // Escena y stage: Configura ventana con título y CSS.
        Scene scene = new Scene(root, 1200, 700);
        stage.setTitle("Mini Meet - " + username);
        stage.setScene(scene);
        stage.show();

        URL cssURL = getClass().getResource("/style.css");
        if (cssURL != null) {
            scene.getStylesheets().add(cssURL.toExternalForm());
        }

        // Conexión al cliente: Try-catch para errores de conexión.
        try {
            client = new MeetingClient("4.tcp.ngrok.io", 11348, this); 
            
            // INICIO AUTOMÁTICO DEL MICRÓFONO
            // Como micOn inicia en true, debemos arrancar la captura aquí.
            startMicrophone();

        } catch (Exception e) {
            addMessage(">> No se pudo conectar al servidor: ", false);
            e.printStackTrace();
        }


        
    }

    // ================= VIDEO VIEW =================
    // Crea una ImageView para video: Con tamaño fijo y smooth.
    private ImageView createVideoView() {

        ImageView view = new ImageView();

        view.setFitWidth(400);     // tamaño visual fijo
        view.setFitHeight(300);
        view.setPreserveRatio(true);
        view.setSmooth(true);

        return view;
    }

    // Crea contenedor para video: StackPane que bindea tamaños.
    private StackPane createVideoContainer(ImageView view) {

        StackPane container = new StackPane(view);

        container.setStyle(
            "-fx-background-color: transparent;"
        );

        view.setPreserveRatio(true);

        view.fitWidthProperty().bind(container.widthProperty());
        view.fitHeightProperty().bind(container.heightProperty());

        return container;
    }

    // ================= GRID LAYOUT =================
    // Actualiza layout del grid: Calcula cols/rows basado en usuarios, limpia y agrega contenedores.
    private void updateGridLayout() {

        videoGrid.getChildren().clear();

        int total = userVideoMap.size();
        if (total == 0) return;

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

    // ================= CAMERA =================
    // Toggle de cámara: Cambia estado, cierra/abre webcam, envía CAM_OFF si off.
    private void toggleCamera(Button button) {

        cameraOn = !cameraOn;

        if (!cameraOn) {
            if (webcam != null) webcam.close();
            button.setText("Cam OFF");
            try {
                client.sendMessage(new Message("CAM_OFF", username, ""));
            } catch (IOException e) {
                addMessage(">> Error enviando cam off: " + e.getMessage(), false);
            }
        } else {
            if (webcam != null) webcam.open();
            button.setText("Cam ON");
        }
    }

    // ================= MIC =================
    // Toggle de mic: Cambia estado, inicia/detiene captura.
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
    /**
     * Envía frame de video al servidor.
     * Convierte BufferedImage a arreglo de bytes en formato JPG.
     */
    // Funcionamiento: Usa ByteArrayOutputStream e ImageIO para convertir imagen a bytes.
    private void sendVideoFrame(BufferedImage image) {
        if (client == null) return;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            client.sendMessage(new Message("VIDEO", username, baos.toByteArray()));
        } catch (Exception ignored) {}
    }

    // Recibe frame: Actualiza ImageView en Platform.runLater, crea si no existe.
    public void receiveVideoFrame(String sender, byte[] imageBytes) {

        Platform.runLater(() -> {

            ImageView view = userVideoMap.get(sender);

            if (view == null) {
                view = createVideoView();
                userVideoMap.put(sender, view);
                updateGridLayout();
            }

            try {
                view.setImage(new Image(new ByteArrayInputStream(imageBytes)));
            } catch (Exception e) {
                addMessage(">> Error mostrando frame de " + sender, false);
            }
        });
    }

    // ================= AUDIO =================
    /**
     * Inicia captura de micrófono en hilo independiente.
     * Envía buffers de audio en tiempo real.
     */
    // Funcionamiento: Abre TargetDataLine, lee buffers de 4096 bytes, envía clones exactos.
    private void startMicrophone() {
        new Thread(() -> {
            try {

                DataLine.Info info =
                        new DataLine.Info(TargetDataLine.class, audioFormat);

                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(audioFormat, 2048);
                microphone.start();

                byte[] buffer = new byte[1024];

                while (micOn && microphone != null) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && client != null) {
                        try{
                            byte[] dataToSend = new byte[bytesRead];
                            System.arraycopy(buffer, 0, dataToSend, 0, bytesRead);
                            client.sendMessage(new Message("AUDIO", username, dataToSend));
                        }catch(IOException ignored){}
                        
                    }
                }

            } catch (LineUnavailableException e) {
                addMessage(">> Mic no disponible: Verifica permisos", false);
            } catch (Exception e) {
                addMessage(">> Error en captura de audio: " + e.getMessage(), false);
            }
        }).start();
    }

    // Getter para username.
    public String getUsername() {
        return username;
    }

    // Reproduce audio: Agrega a cola y inicia playback si no está corriendo.
    public void playAudio(byte[] audioData) {

        audioQueue.add(audioData);
        if (!audioPlaying) {
            startAudioPlayback();
        }
    }

    // Inicia playback: Abre SourceDataLine, escribe de la cola en loop.
    private void startAudioPlayback() {
        audioPlaying = true;
        audioPlaybackThread = new Thread(() -> {
            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                speakers = (SourceDataLine) AudioSystem.getLine(info);
                speakers.open(audioFormat);
                speakers.start();

                while (audioPlaying) {
                    byte[] data = audioQueue.poll();
                    if (data != null) {
                        speakers.write(data, 0, data.length); // Escribe buffer.
                    } else {
                        Thread.sleep(10); // Pequeño sleep para no busy-wait.
                    }
                }

                speakers.drain();
                speakers.close();

            } catch (Exception e) {
                addMessage(">> Error en reproducción de audio: " + e.getMessage(), false);
            } finally {
                audioPlaying = false;
            }
        });
        audioPlaybackThread.start();
    }

    // Maneja CAM_OFF: Limpia imagen de la vista.
    public void handleCameraOff(String user) {

        Platform.runLater(() -> {
            ImageView view = userVideoMap.get(user);
            if (view != null) {
                view.setImage(null);
            }
        });
    }

    // Detiene mic: Cierra línea de captura.
    private void stopMicrophone() {
        try {
            if (microphone != null) {
                microphone.stop();
                microphone.close();
                microphone = null;
            }
        } catch (Exception ignored) {}
    }

    // Agrega mensaje al chat.
    public void addMessage(String message, boolean own) {
        chatArea.appendText(message + "\n");
    }

    // Envía chat: Crea mensaje CHAT y envía via client.
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

    // Agrega placeholder: Crea vista vacía para nuevo usuario.
    public void addUserPlaceholder(String sender) {
        Platform.runLater(() -> {
            if (!userVideoMap.containsKey(sender)) {
                ImageView view = createVideoView();
                view.setImage(null); // Fondo negro inicial.
                userVideoMap.put(sender, view);
                updateGridLayout();
            }
        });
    }

    // Remueve usuario: Quita del mapa y actualiza grid.
    public void removeUser(String sender) {
        Platform.runLater(() -> {
            userVideoMap.remove(sender);
            updateGridLayout();
        });
    }

    public static void main(String[] args) {
        launch();
    }
}