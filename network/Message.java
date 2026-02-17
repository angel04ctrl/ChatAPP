package network;

import java.io.Serializable;

public class Message implements Serializable {

    private String type;
    private String sender;
    private String text;
    private byte[] data;

    public Message(String type, String sender, String text) {
        this.type = type;
        this.sender = sender;
        this.text = text;
    }

    public Message(String type, String sender, byte[] data) {
        this.type = type;
        this.sender = sender;
        this.data = data;
    }

    public String getType() { return type; }
    public String getSender() { return sender; }
    public String getText() { return text; }
    public byte[] getData() { return data; }
}
