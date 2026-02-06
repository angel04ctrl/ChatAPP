import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;


public class Main extends Application {

    @Override
    public void start(Stage stage) {

        BorderPane brpn = new BorderPane();
        VBox topBox = new VBox(10); 

        //sirve basicamente para crear la ventana y define los tamaños de ancho y alto
        Scene scene = new Scene(brpn, 900, 600);
        
        //son los elementos que se agregan y el orden importa porque se muestran de arriba hacia abajo
        Label lbl = new Label("Nombre: Juan perez");
        Button sendBtn = new Button("Enviar");
        TextArea chatArea = new TextArea();
        TextField inputField = new TextField();
        
        topBox.setPadding(new Insets(5));
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.getChildren().add(lbl);
        brpn.setTop(topBox);
        
        
        chatArea.setEditable(false);
        chatArea.appendText("Usuario: Hola\n");

        
        HBox bottom = new HBox(15, inputField, sendBtn);
        bottom.setPadding(new Insets(10));
        bottom.setAlignment(Pos.CENTER);
        brpn.setCenter(chatArea);
        brpn.setBottom(bottom);

        stage.setTitle("Mi App");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
