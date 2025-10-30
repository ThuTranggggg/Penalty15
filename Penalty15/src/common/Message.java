package common;

import java.io.Serializable;

public class Message implements Serializable {
    private String type;
    private Object content;

    public Message(String type, Object content) {
        this.type = type;
        this.content = content;
    }

    // Getters
    public String getType() {
        return type;
    }

    public Object getContent() {
        return content;
    }
}
