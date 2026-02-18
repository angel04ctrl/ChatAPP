package network;

import java.io.Serializable;

/**
 * Clase que representa un mensaje intercambiado entre cliente y servidor.
 * Implementa Serializable para permitir transmisión mediante ObjectOutputStream.
 * 
 * Funcionamiento general: Esta es una clase POJO (Plain Old Java Object) que encapsula
 * el tipo de mensaje, remitente, y contenido (ya sea texto o datos binarios).
 * Se usa para todos los tipos de comunicación: CHAT (texto), VIDEO/AUDIO (bytes), JOIN/LEAVE (info).
 * Los constructores permiten crear mensajes de texto o binarios.
 */

public class Message implements Serializable {
    // Tipo de mensaje (CHAT, VIDEO, AUDIO, JOIN, LEAVE, etc.). Define la categoría del mensaje.
    private String type;
    // Nombre del usuario que envía el mensaje. Usado para identificar el origen.
    private String sender;
    // Contenido textual (usado principalmente para chat). Para mensajes no textuales, puede ser null o vacío.
    private String text;
    // Datos binarios (frames de video o paquetes de audio). Para video: bytes de imagen JPG; para audio: bytes raw.
    private byte[] data;

    
    // Constructor para mensajes de tipo texto.
    // Funcionamiento: Inicializa type, sender y text. Data queda null.
    public Message(String type, String sender, String text) {
        this.type = type;
        this.sender = sender;
        this.text = text;
    }
    
    // Constructor para mensajes binarios (video/audio).
    // Funcionamiento: Inicializa type, sender y data. Text queda null.
    public Message(String type, String sender, byte[] data) {
        this.type = type;
        this.sender = sender;
        this.data = data;
    }
    
    // ===== Getters =====
    // Funcionamiento: Métodos simples para acceder a los campos privados.
    // No setters, ya que los mensajes son inmutables una vez creados.
    public String getType() { return type; }
    public String getSender() { return sender; }
    public String getText() { return text; }
    public byte[] getData() { return data; }
}