package network;

import java.io.Serializable;
/**
 * Clase que representa un mensaje intercambiado entre cliente y servidor.
 * Implementa Serializable para permitir transmisión mediante ObjectOutputStream.
 */

public class Message implements Serializable {
    // Tipo de mensaje (CHAT, VIDEO, AUDIO, JOIN, LEAVE, etc.)
    private String type;
    // Nombre del usuario que envía el mensaje
    private String sender;
    // Contenido textual (usado principalmente para chat)
    private String text;
    // Datos binarios (frames de video o paquetes de audio)
    private byte[] data;

    
    //Constructor para mensajes de tipo texto.
     
    public Message(String type, String sender, String text) {
        this.type = type;
        this.sender = sender;
        this.text = text;
    }
//Constructor para mensajes binarios (video/audio).
    public Message(String type, String sender, byte[] data) {
        this.type = type;
        this.sender = sender;
        this.data = data;
    }
// ===== Getters =====
    public String getType() { return type; }
    public String getSender() { return sender; }
    public String getText() { return text; }
    public byte[] getData() { return data; }
}
