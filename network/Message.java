package network;

import java.io.Serializable;

public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public String type;      // CHAT, JOIN, LEAVE, INFO
    public String sender;
    public String content;

    public Message(String type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
    }
}
